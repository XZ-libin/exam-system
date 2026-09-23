package com.exam.service;

import com.exam.common.LikeUtil;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.common.BatchLimit;
import com.exam.common.BizException;
import com.exam.common.Dicts;
import com.exam.common.ErrorCode;
import com.exam.common.LoginUser;
import com.exam.common.PageResult;
import com.exam.dto.ExamForm;
import com.exam.entity.AnExamRecord;
import com.exam.entity.ExExam;
import com.exam.entity.ExExamClass;
import com.exam.entity.ExPaper;
import com.exam.entity.SysUser;
import com.exam.mapper.AnExamRecordMapper;
import com.exam.mapper.ExExamClassMapper;
import com.exam.mapper.ExExamMapper;
import com.exam.mapper.ExPaperMapper;
import com.exam.mapper.SysUserMapper;
import com.exam.vo.ExamVo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 考试管理：场次增删改、发布与结束、成绩发布、强制交卷入口。
 * <p>
 * ex_exam.status 只存人工状态（草稿/已发布/已结束），「进行中」是时间推导出来的，见 deriveState。
 */
@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExExamMapper exExamMapper;
    private final ExExamClassMapper exExamClassMapper;
    private final ExPaperMapper exPaperMapper;
    private final AnExamRecordMapper anExamRecordMapper;
    private final SysUserMapper sysUserMapper;
    /** 作答过程模块，强制交卷的写记录逻辑在那边 */
    private final AttemptService attemptService;

    /** ex_exam.late_minutes 默认 15 */
    private static final int DEFAULT_LATE_MINUTES = 15;
    private static final int DEFAULT_MAX_ATTEMPTS = 1;
    private static final int DEFAULT_DURATION_MINUTES = 60;

    private static final int STATE_NOT_STARTED = 1;
    private static final int STATE_RUNNING = 2;
    private static final int STATE_ENDED = 3;

    // ------------------------------------------------------------ 查询

    public PageResult<ExamVo> page(long page, long size, String keyword, Integer status) {
        LambdaQueryWrapper<ExExam> wrapper = new LambdaQueryWrapper<>();
        if (notBlank(keyword)) {
            wrapper.like(ExExam::getTitle, LikeUtil.escape(keyword.trim()));
        }
        if (status != null) {
            wrapper.eq(ExExam::getStatus, status);
        }
        wrapper.orderByDesc(ExExam::getStartTime);
        Page<ExExam> result = exExamMapper.selectPage(new Page<ExExam>(page, size), wrapper);

        List<ExExam> rows = result.getRecords();
        List<Long> examIds = rows.stream().map(ExExam::getId).toList();
        // 试卷标题、答卷统计、可见班级各一次批量查询，避免逐行查
        Map<Long, ExPaper> papers = paperMap(rows.stream().map(ExExam::getPaperId).toList());
        Map<Long, List<AnExamRecord>> records = recordsByExam(examIds);
        Map<Long, List<String>> classes = classesByExam(examIds);
        LocalDateTime now = LocalDateTime.now();
        // 同一份试卷集合下「应交人数」是同一个数，缓存一下省掉重复的学生计数
        Map<String, Long> expectedCache = new HashMap<>();

        List<ExamVo> vos = new ArrayList<>(rows.size());
        for (ExExam row : rows) {
            vos.add(toVo(row, papers.get(row.getPaperId()),
                    records.getOrDefault(row.getId(), Collections.emptyList()),
                    classes.getOrDefault(row.getId(), Collections.emptyList()), now, expectedCache));
        }
        return PageResult.of(vos, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public ExamVo detail(Long id) {
        ExExam exam = requireExam(id);
        ExPaper paper = exPaperMapper.selectById(exam.getPaperId());
        List<AnExamRecord> rows = recordsByExam(List.of(id)).getOrDefault(id, Collections.emptyList());
        return toVo(exam, paper, rows, classNamesOf(id), LocalDateTime.now(), new HashMap<>());
    }

    // ------------------------------------------------------------ 写

    @Transactional(rollbackFor = Exception.class)
    public Long create(ExamForm form) {
        ExPaper paper = requirePublishedPaper(form.getPaperId());
        ExExam exam = new ExExam();
        applyForm(exam, form, paper);
        exam.setScorePublished(Dicts.ScorePublish.UNPUBLISHED);
        exam.setStatus(resolveStatus(form.getStatus(), Dicts.ExamStatus.DRAFT, paper.getId()));
        exam.setCreatorId(LoginUser.userId());
        exExamMapper.insert(exam);
        syncClasses(exam.getId(), exam.getAudienceType(), form.getClassNames());
        return exam.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(ExamForm form) {
        if (form.getId() == null) {
            throw BizException.param("缺少考试 id");
        }
        ExExam exam = requireExam(form.getId());
        assertWritable(exam.getCreatorId());
        if (recordCount(exam.getId()) > 0) {
            throw new BizException(ErrorCode.EXAM_IN_PROGRESS_CANNOT_EDIT, "已有学生开始作答，考试信息不能修改");
        }
        ExPaper paper = requirePublishedPaper(form.getPaperId());
        applyForm(exam, form, paper);
        exam.setStatus(resolveStatus(form.getStatus(), exam.getStatus(), paper.getId()));
        // update_time 交给 MySQL 的 ON UPDATE CURRENT_TIMESTAMP，置空即不参与 SET 子句
        exam.setUpdateTime(null);
        exExamMapper.updateById(exam);
        // 没有作答记录时才允许整班重配
        syncClasses(exam.getId(), exam.getAudienceType(), form.getClassNames());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ExExam exam = requireExam(id);
        assertWritable(exam.getCreatorId());
        if (recordCount(id) > 0) {
            throw new BizException(ErrorCode.EXAM_HAS_RECORD, "该考试已有作答记录，不能删除；可改为结束");
        }
        exExamMapper.deleteById(id);
        exExamClassMapper.delete(new LambdaQueryWrapper<ExExamClass>().eq(ExExamClass::getExamId, id));
    }

    /** 只用来发布（1）和结束（3）；草稿转回草稿走编辑接口，进行中由时间推导 */
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        ExExam exam = requireExam(id);
        assertWritable(exam.getCreatorId());
        if (status == null || (status != Dicts.ExamStatus.PUBLISHED && status != Dicts.ExamStatus.CLOSED)) {
            throw BizException.param("考试状态只能改为 1已发布 或 3已结束");
        }
        if (status == Dicts.ExamStatus.PUBLISHED) {
            requirePublishedPaper(exam.getPaperId());
        }
        ExExam update = new ExExam();
        update.setId(id);
        update.setStatus(status);
        exExamMapper.updateById(update);
    }

    /** 发布成绩：阅卷完成后教师手动点，学生端凭此显示分数 */
    @Transactional(rollbackFor = Exception.class)
    public void publishScore(Long id) {
        ExExam exam = requireExam(id);
        assertWritable(exam.getCreatorId());
        long waiting = anExamRecordMapper.selectCount(new LambdaQueryWrapper<AnExamRecord>()
                .eq(AnExamRecord::getExamId, id)
                .eq(AnExamRecord::getStatus, Dicts.RecordStatus.WAIT_REVIEW));
        if (waiting > 0) {
            throw BizException.param("还有 " + waiting + " 份答卷的主观题未阅完，暂不能发布成绩");
        }
        ExExam update = new ExExam();
        update.setId(exam.getId());
        update.setScorePublished(Dicts.ScorePublish.PUBLISHED);
        exExamMapper.updateById(update);
    }

    /** 强制收卷：交卷明细由 AttemptService 负责 */
    @Transactional(rollbackFor = Exception.class)
    public int forceSubmit(Long id) {
        ExExam exam = requireExam(id);
        assertWritable(exam.getCreatorId());
        return attemptService.forceSubmitExam(id);
    }

    // ------------------------------------------------------------ 组装 VO

    private ExamVo toVo(ExExam exam, ExPaper paper, List<AnExamRecord> rows, List<String> classNames,
                        LocalDateTime now, Map<String, Long> expectedCache) {
        ExamVo vo = new ExamVo();
        BeanUtils.copyProperties(exam, vo);
        vo.setPaperTitle(paper == null ? null : paper.getTitle());
        vo.setPaperTotalScore(paper == null ? null : paper.getTotalScore());
        vo.setClassNames(classNames);
        vo.setState(deriveState(exam, now));
        vo.setExpectedCount(expectedCache.computeIfAbsent(expectedKey(exam, classNames),
                key -> expectedCount(exam, classNames)));
        // 已交人数按学生去重：maxAttempts > 1 时同一学生多次交卷只算一人，和应交人数口径一致
        vo.setSubmittedCount(rows.stream().filter(r -> Dicts.RecordStatus.submitted(r.getStatus()))
                .map(AnExamRecord::getUserId).filter(Objects::nonNull).distinct().count());
        vo.setAvgScore(avgScore(exam, rows));
        return vo;
    }

    /** 未开始 / 进行中 / 已结束：人工结束优先级最高，其余按时间窗比较 */
    private int deriveState(ExExam exam, LocalDateTime now) {
        if (Objects.equals(exam.getStatus(), Dicts.ExamStatus.CLOSED) || now.isAfter(exam.getEndTime())) {
            return STATE_ENDED;
        }
        if (now.isBefore(exam.getStartTime())) {
            return STATE_NOT_STARTED;
        }
        return STATE_RUNNING;
    }

    /** 平均分只在成绩已发布后给出，否则返回 null 让前端显示占位 */
    private Double avgScore(ExExam exam, List<AnExamRecord> rows) {
        if (!Objects.equals(exam.getScorePublished(), Dicts.ScorePublish.PUBLISHED)) {
            return null;
        }
        List<BigDecimal> scores = rows.stream().map(AnExamRecord::getTotalScore).filter(Objects::nonNull).toList();
        if (scores.isEmpty()) {
            return null;
        }
        double avg = scores.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0D);
        return Math.round(avg * 100) / 100D;
    }

    /**
     * 应交人数：1 全部学生 = 启用中的学生账号数；2 指定班级 = 这些班的学生数；
     * 3 指定名单没有独立的名单表（见 01_schema.sql），这里退化为「本场考试已建过答卷的学生数」做近似统计。
     */
    private long expectedCount(ExExam exam, List<String> classNames) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, Dicts.Status.ENABLED)
                .inSql(SysUser::getId, "select ur.user_id from sys_user_role ur"
                        + " join sys_role r on r.id = ur.role_id where r.code = '" + Dicts.Role.STUDENT + "'");
        int audience = audienceOf(exam);
        if (audience == Dicts.Audience.CLASS) {
            if (classNames == null || classNames.isEmpty()) {
                return 0L;
            }
            wrapper.in(SysUser::getClassName, classNames);
        } else if (audience == Dicts.Audience.NAMED) {
            wrapper.inSql(SysUser::getId,
                    "select rr.user_id from an_exam_record rr where rr.exam_id = " + exam.getId());
        }
        return sysUserMapper.selectCount(wrapper);
    }

    /** 应交人数的缓存键：同样可见范围的考生数复用一次查询结果 */
    private String expectedKey(ExExam exam, List<String> classNames) {
        int audience = audienceOf(exam);
        if (audience == Dicts.Audience.CLASS) {
            List<String> sorted = classNames == null ? new ArrayList<>() : new ArrayList<>(classNames);
            Collections.sort(sorted);
            return "class:" + String.join(",", sorted);
        }
        if (audience == Dicts.Audience.NAMED) {
            return "named:" + exam.getId();
        }
        return "all";
    }

    private int audienceOf(ExExam exam) {
        return exam.getAudienceType() == null ? Dicts.Audience.ALL : exam.getAudienceType();
    }

    // ------------------------------------------------------------ 校验与内部方法

    /** 表单 -> 实体；只做字段落库与基础校验，状态单独处理 */
    private void applyForm(ExExam exam, ExamForm form, ExPaper paper) {
        if (form.getStartTime() == null || form.getEndTime() == null) {
            throw BizException.param("考试开始与结束时间不能为空");
        }
        if (!form.getEndTime().isAfter(form.getStartTime())) {
            throw BizException.param("结束时间必须晚于开始时间");
        }
        int duration = form.getDurationMinutes() == null ? DEFAULT_DURATION_MINUTES : form.getDurationMinutes();
        if (duration <= 0) {
            throw BizException.param("答题时长必须大于 0");
        }
        Duration window = Duration.between(form.getStartTime(), form.getEndTime());
        if (Duration.ofMinutes(duration).compareTo(window) > 0) {
            throw BizException.param("答题时长不能长于考试时间窗");
        }
        int audience = form.getAudienceType() == null ? Dicts.Audience.ALL : form.getAudienceType();
        if (audience != Dicts.Audience.ALL && audience != Dicts.Audience.CLASS && audience != Dicts.Audience.NAMED) {
            throw BizException.param("可见范围只能是 1全部学生 / 2指定班级 / 3指定名单");
        }
        BatchLimit.check(form.getClassNames(), "参考班级");
        if (audience == Dicts.Audience.CLASS && (form.getClassNames() == null || form.getClassNames().isEmpty())) {
            throw BizException.param("指定班级参考时必须填写班级名称");
        }
        if (form.getLateMinutes() != null && form.getLateMinutes() < 0) {
            throw BizException.param("迟到允许进入的分钟数不能为负数");
        }
        if (form.getMaxAttempts() != null && form.getMaxAttempts() < 1) {
            throw BizException.param("允许作答次数至少为 1");
        }
        if (form.getSwitchLimit() != null && form.getSwitchLimit() < 0) {
            throw BizException.param("切屏次数限制不能为负数，0 表示不限制");
        }
        exam.setPaperId(paper.getId());
        // 不填名称时沿用试卷名，前端组卷创建考试时可以少填一个字段
        exam.setTitle(notBlank(form.getTitle()) ? form.getTitle().trim() : paper.getTitle());
        exam.setDescription(form.getDescription());
        exam.setStartTime(form.getStartTime());
        exam.setEndTime(form.getEndTime());
        exam.setDurationMinutes(duration);
        exam.setLateMinutes(form.getLateMinutes() == null ? DEFAULT_LATE_MINUTES : form.getLateMinutes());
        exam.setMaxAttempts(form.getMaxAttempts() == null ? DEFAULT_MAX_ATTEMPTS : form.getMaxAttempts());
        exam.setAudienceType(audience);
        exam.setSwitchLimit(form.getSwitchLimit() == null ? 0 : form.getSwitchLimit());
        exam.setShuffleQuestion(flag(form.getShuffleQuestion()));
        exam.setShuffleOption(flag(form.getShuffleOption()));
    }

    private int flag(Integer value) {
        return value == null || value == 0 ? 0 : 1;
    }

    /** 人工状态只允许草稿/已发布/已结束，发布前试卷必须仍是已发布状态 */
    private int resolveStatus(Integer want, Integer current, Long paperId) {
        if (want == null) {
            return current == null ? Dicts.ExamStatus.DRAFT : current;
        }
        if (want != Dicts.ExamStatus.DRAFT && want != Dicts.ExamStatus.PUBLISHED && want != Dicts.ExamStatus.CLOSED) {
            throw BizException.param("考试状态只能是 0草稿 / 1已发布 / 3已结束，进行中由时间推导");
        }
        if (want == Dicts.ExamStatus.PUBLISHED) {
            requirePublishedPaper(paperId);
        }
        return want;
    }

    /** 指定班级时整表重刷，其他可见范围清空班级配置 */
    private void syncClasses(Long examId, Integer audienceType, List<String> classNames) {
        exExamClassMapper.delete(new LambdaQueryWrapper<ExExamClass>().eq(ExExamClass::getExamId, examId));
        if (audienceType == null || audienceType != Dicts.Audience.CLASS) {
            return;
        }
        if (classNames == null || classNames.isEmpty()) {
            throw BizException.param("指定班级参考时必须填写班级名称");
        }
        List<String> names = classNames.stream().filter(ExamService::notBlank).map(String::trim).distinct().toList();
        if (names.isEmpty()) {
            throw BizException.param("指定班级参考时必须填写班级名称");
        }
        for (String name : names) {
            ExExamClass row = new ExExamClass();
            row.setExamId(examId);
            row.setClassName(name);
            exExamClassMapper.insert(row);
        }
    }

    private ExExam requireExam(Long id) {
        ExExam exam = exExamMapper.selectById(id);
        if (exam == null) {
            throw new BizException(ErrorCode.EXAM_NOT_FOUND, "考试不存在");
        }
        return exam;
    }

    /** ADMIN 或考试创建人可写，其他人只读 */
    private void assertWritable(Long creatorId) {
        if (LoginUser.hasRole(Dicts.Role.ADMIN)) {
            return;
        }
        if (creatorId != null && creatorId.equals(LoginUser.userId())) {
            return;
        }
        throw BizException.forbidden("只能修改自己创建的考试");
    }

    private ExPaper requirePublishedPaper(Long paperId) {
        ExPaper paper = paperId == null ? null : exPaperMapper.selectById(paperId);
        if (paper == null) {
            throw BizException.param("所选试卷不存在");
        }
        if (!Objects.equals(paper.getStatus(), Dicts.PaperStatus.PUBLISHED)) {
            throw BizException.param("试卷尚未发布，不能用于考试");
        }
        return paper;
    }

    private long recordCount(Long examId) {
        return anExamRecordMapper.selectCount(new LambdaQueryWrapper<AnExamRecord>()
                .eq(AnExamRecord::getExamId, examId));
    }

    private List<String> classNamesOf(Long examId) {
        return classesByExam(List.of(examId)).getOrDefault(examId, Collections.emptyList());
    }

    private Map<Long, List<String>> classesByExam(Collection<Long> examIds) {
        if (examIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ExExamClass> rows = exExamClassMapper.selectList(new LambdaQueryWrapper<ExExamClass>()
                .in(ExExamClass::getExamId, examIds)
                .orderByAsc(ExExamClass::getId));
        return rows.stream().collect(Collectors.groupingBy(ExExamClass::getExamId,
                Collectors.mapping(ExExamClass::getClassName, Collectors.toList())));
    }

    private Map<Long, List<AnExamRecord>> recordsByExam(Collection<Long> examIds) {
        if (examIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<AnExamRecord> rows = anExamRecordMapper.selectList(new LambdaQueryWrapper<AnExamRecord>()
                .select(AnExamRecord::getExamId, AnExamRecord::getUserId, AnExamRecord::getStatus,
                        AnExamRecord::getTotalScore)
                .in(AnExamRecord::getExamId, examIds));
        return rows.stream().collect(Collectors.groupingBy(AnExamRecord::getExamId));
    }

    private Map<Long, ExPaper> paperMap(Collection<Long> paperIds) {
        Set<Long> valid = paperIds.stream().filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        if (valid.isEmpty()) {
            return Collections.emptyMap();
        }
        return exPaperMapper.selectBatchIds(valid).stream()
                .collect(Collectors.toMap(ExPaper::getId, Function.identity()));
    }

    private static boolean notBlank(String text) {
        return text != null && !text.isBlank();
    }
}
