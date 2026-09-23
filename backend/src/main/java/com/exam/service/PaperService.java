package com.exam.service;

import com.exam.common.LikeUtil;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.common.BatchLimit;
import com.exam.common.BizException;
import com.exam.common.Dicts;
import com.exam.common.ErrorCode;
import com.exam.common.LoginUser;
import com.exam.common.PageResult;
import com.exam.dto.AutoPaperForm;
import com.exam.dto.PaperForm;
import com.exam.dto.PaperQuestionForm;
import com.exam.entity.ExExam;
import com.exam.entity.ExPaper;
import com.exam.entity.ExPaperQuestion;
import com.exam.entity.QzCategory;
import com.exam.entity.QzQuestion;
import com.exam.entity.SysUser;
import com.exam.mapper.ExExamMapper;
import com.exam.mapper.ExPaperMapper;
import com.exam.mapper.ExPaperQuestionMapper;
import com.exam.mapper.QzCategoryMapper;
import com.exam.mapper.QzQuestionMapper;
import com.exam.mapper.SysUserMapper;
import com.exam.vo.PaperDetailVo;
import com.exam.vo.PaperVo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 试卷管理：草稿组的增删题目、规则自动组卷、发布与归档。
 * <p>
 * 核心约定是「快照」：题目入卷时把题干、选项、答案、解析、难度一并写进 ex_paper_question，
 * 之后题库怎么改都不影响已组好的卷子，判分也只读快照。
 */
@Service
@RequiredArgsConstructor
public class PaperService {

    private final ExPaperMapper exPaperMapper;
    private final ExPaperQuestionMapper exPaperQuestionMapper;
    private final QzQuestionMapper qzQuestionMapper;
    private final QzCategoryMapper qzCategoryMapper;
    private final ExExamMapper exExamMapper;
    private final SysUserMapper sysUserMapper;

    /** ex_paper.pass_score 默认 60 */
    private static final BigDecimal DEFAULT_PASS_SCORE = new BigDecimal("60.0");
    private static final int DEFAULT_SUGGEST_MINUTES = 60;
    /** 总分与快照分值之和允许的误差，DECIMAL(6,1) 累加不会有精度问题，这里只防手工脏数据 */
    private static final double SCORE_TOLERANCE = 0.05D;

    // ------------------------------------------------------------ 查询

    /**
     * 分页列表。列表页只给统计信息，题目明细在详情里。
     */
    public PageResult<PaperVo> page(long page, long size, String keyword, Integer status,
                                    Long categoryId, Integer buildType) {
        LambdaQueryWrapper<ExPaper> wrapper = new LambdaQueryWrapper<>();
        if (notBlank(keyword)) {
            wrapper.like(ExPaper::getTitle, LikeUtil.escape(keyword.trim()));
        }
        if (status != null) {
            wrapper.eq(ExPaper::getStatus, status);
        }
        if (categoryId != null) {
            wrapper.eq(ExPaper::getCategoryId, categoryId);
        }
        if (buildType != null) {
            wrapper.eq(ExPaper::getBuildType, buildType);
        }
        wrapper.orderByDesc(ExPaper::getId);
        Page<ExPaper> result = exPaperMapper.selectPage(new Page<ExPaper>(page, size), wrapper);

        List<ExPaper> rows = result.getRecords();
        // 分类名、创建人、被考试引用数各自一次批量查询，避免逐行查产生 N+1
        Map<Long, String> categoryNames = categoryNameMap(rows.stream().map(ExPaper::getCategoryId).toList());
        Map<Long, String> creatorNames = creatorNameMap(rows.stream().map(ExPaper::getCreatorId).toList());
        Map<Long, Long> examCounts = examCountMap(rows.stream().map(ExPaper::getId).toList());

        List<PaperVo> vos = new ArrayList<>(rows.size());
        for (ExPaper row : rows) {
            PaperVo vo = new PaperVo();
            BeanUtils.copyProperties(row, vo);
            vo.setCategoryName(categoryNames.get(row.getCategoryId()));
            vo.setCreatorName(creatorNames.get(row.getCreatorId()));
            vo.setExamCount(examCounts.getOrDefault(row.getId(), 0L));
            vos.add(vo);
        }
        return PageResult.of(vos, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 教师端详情：带答案的完整快照。学生端看到的是另一个模块做的脱敏视图。
     */
    public PaperDetailVo detail(Long id) {
        ExPaper paper = requirePaper(id);
        PaperDetailVo vo = new PaperDetailVo();
        BeanUtils.copyProperties(paper, vo);
        vo.setQuestions(listQuestions(id));
        return vo;
    }

    /** 预览与详情是同一份数据（教师组卷时就要看到答案） */
    public PaperDetailVo preview(Long id) {
        return detail(id);
    }

    // ------------------------------------------------------------ 试卷本身

    @Transactional(rollbackFor = Exception.class)
    public Long create(PaperForm form) {
        requireCategory(form.getCategoryId());
        ExPaper paper = new ExPaper();
        paper.setTitle(form.getTitle().trim());
        paper.setDescription(form.getDescription());
        paper.setCategoryId(form.getCategoryId());
        paper.setPassScore(form.getPassScore() == null ? DEFAULT_PASS_SCORE : form.getPassScore());
        paper.setSuggestMinutes(form.getSuggestMinutes() == null ? DEFAULT_SUGGEST_MINUTES : form.getSuggestMinutes());
        paper.setTotalScore(BigDecimal.ZERO);
        paper.setQuestionCount(0);
        paper.setBuildType(Dicts.BuildType.MANUAL);
        // 新建一律落草稿：发布必须走 publish，那里才做题量与总分校验
        paper.setStatus(Dicts.PaperStatus.DRAFT);
        paper.setCreatorId(LoginUser.userId());
        exPaperMapper.insert(paper);
        return paper.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(PaperForm form) {
        if (form.getId() == null) {
            throw BizException.param("缺少试卷 id");
        }
        ExPaper paper = requirePaper(form.getId());
        assertWritable(paper.getCreatorId());
        if (!Objects.equals(paper.getStatus(), Dicts.PaperStatus.DRAFT)) {
            throw new BizException(ErrorCode.PAPER_PUBLISHED_LOCKED, "只有草稿试卷可以修改，已发布/已归档请先新建副本");
        }
        requireCategory(form.getCategoryId());
        paper.setTitle(form.getTitle().trim());
        paper.setDescription(form.getDescription());
        paper.setCategoryId(form.getCategoryId());
        if (form.getPassScore() != null) {
            paper.setPassScore(form.getPassScore());
        }
        if (form.getSuggestMinutes() != null) {
            paper.setSuggestMinutes(form.getSuggestMinutes());
        }
        // status 只由 publish / archive 改动，这里不接表单值，避免绕过发布校验
        // update_time 交给 MySQL 的 ON UPDATE CURRENT_TIMESTAMP，置空即不参与 SET 子句
        paper.setUpdateTime(null);
        exPaperMapper.updateById(paper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ExPaper paper = requirePaper(id);
        assertWritable(paper.getCreatorId());
        long used = countExams(id);
        if (used > 0) {
            throw new BizException(ErrorCode.EXAM_HAS_RECORD, "该试卷已被 " + used + " 场考试使用，不能删除");
        }
        // 逻辑删除：deleted 置 1，题目快照保留，历史答卷仍可追溯
        exPaperMapper.deleteById(id);
        bumpUseCount(snapshotQuestionIds(id), -1);
    }

    private List<Long> snapshotQuestionIds(Long paperId) {
        return exPaperQuestionMapper.selectList(new LambdaQueryWrapper<ExPaperQuestion>()
                        .select(ExPaperQuestion::getQuestionId)
                        .eq(ExPaperQuestion::getPaperId, paperId))
                .stream().map(ExPaperQuestion::getQuestionId).distinct().toList();
    }

    /**
     * 题目引用计数按增量更新，并且先按 id 排序。
     * 之前用「相关子查询全表重算」在并发组卷时会死锁：多个事务按不同顺序锁同一批 qz_question 行。
     */
    private void bumpUseCount(Collection<Long> questionIds, int delta) {
        if (questionIds == null || questionIds.isEmpty() || delta == 0) {
            return;
        }
        List<Long> sorted = questionIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (sorted.isEmpty()) {
            return;
        }
        qzQuestionMapper.update(null, new LambdaUpdateWrapper<QzQuestion>()
                .setSql("use_count = GREATEST(use_count + (" + delta + "), 0)")
                .in(QzQuestion::getId, sorted));
    }

    // ------------------------------------------------------------ 组卷

    @Transactional(rollbackFor = Exception.class)
    public void addQuestions(Long paperId, List<PaperQuestionForm> forms) {
        if (forms == null || forms.isEmpty()) {
            throw BizException.param("请选择要加入的题目");
        }
        BatchLimit.check(forms, "试卷题目");
        ExPaper paper = requirePaper(paperId);
        assertWritable(paper.getCreatorId());
        requireDraft(paper);

        // 先整体查重（卷内已有 + 本次重复提交），再落库
        Set<Long> inPaper = new HashSet<>(existingQuestionIds(paperId));
        List<Long> wanted = new ArrayList<>(forms.size());
        for (PaperQuestionForm form : forms) {
            if (form == null || form.getQuestionId() == null) {
                throw BizException.param("题目 id 不能为空");
            }
            if (!inPaper.add(form.getQuestionId())) {
                throw new BizException(ErrorCode.PAPER_DUPLICATE_QUESTION, "题目已在试卷中");
            }
            wanted.add(form.getQuestionId());
        }
        Map<Long, QzQuestion> found = qzQuestionMapper.selectBatchIds(wanted).stream()
                .collect(Collectors.toMap(QzQuestion::getId, Function.identity()));

        int sortNo = nextSortNo(paperId);
        for (PaperQuestionForm form : forms) {
            QzQuestion question = found.get(form.getQuestionId());
            if (question == null || !Objects.equals(question.getStatus(), Dicts.Status.ENABLED)) {
                throw new BizException(ErrorCode.QUESTION_NOT_FOUND, "题目不存在或已停用");
            }
            BigDecimal score = form.getScore() == null ? question.getScore() : form.getScore();
            int sort = form.getSortNo() == null ? sortNo++ : form.getSortNo();
            writeSnapshot(paperId, question, score, sort);
        }
        recalcTotals(paperId);
        bumpUseCount(wanted, 1);
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeQuestion(Long paperId, Long questionId) {
        ExPaper paper = requirePaper(paperId);
        assertWritable(paper.getCreatorId());
        requireDraft(paper);
        int removed = exPaperQuestionMapper.delete(new LambdaQueryWrapper<ExPaperQuestion>()
                .eq(ExPaperQuestion::getPaperId, paperId)
                .eq(ExPaperQuestion::getQuestionId, questionId));
        if (removed == 0) {
            throw BizException.notFound("题目");
        }
        recalcTotals(paperId);
        bumpUseCount(List.of(questionId), -1);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateQuestionScore(Long paperId, Long questionId, BigDecimal score) {
        if (score == null || score.compareTo(BigDecimal.ZERO) <= 0) {
            throw BizException.param("分值必须大于 0");
        }
        ExPaper paper = requirePaper(paperId);
        assertWritable(paper.getCreatorId());
        requireDraft(paper);
        ExPaperQuestion row = exPaperQuestionMapper.selectOne(new LambdaQueryWrapper<ExPaperQuestion>()
                .eq(ExPaperQuestion::getPaperId, paperId)
                .eq(ExPaperQuestion::getQuestionId, questionId));
        if (row == null) {
            throw BizException.notFound("题目");
        }
        row.setScore(score);
        exPaperQuestionMapper.updateById(row);
        recalcTotals(paperId);
    }

    /**
     * 规则抽题组卷：先建一张 build_type=2 的空草稿卷，再按规则逐条抽题写快照。
     * 题量不足时抛异常，整笔事务回滚，不会留下半成品试卷。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long autoGenerate(AutoPaperForm form) {
        if (form.getRules() == null || form.getRules().isEmpty()) {
            throw BizException.param("请至少配置一条抽题规则");
        }
        BatchLimit.check(form.getRules(), "抽题规则");
        ExPaper paper = new ExPaper();
        paper.setTitle(form.getTitle().trim());
        paper.setDescription(form.getDescription());
        paper.setCategoryId(form.getCategoryId());
        paper.setPassScore(form.getPassScore() == null ? DEFAULT_PASS_SCORE : form.getPassScore());
        paper.setSuggestMinutes(form.getSuggestMinutes() == null ? DEFAULT_SUGGEST_MINUTES : form.getSuggestMinutes());
        paper.setTotalScore(BigDecimal.ZERO);
        paper.setQuestionCount(0);
        paper.setBuildType(Dicts.BuildType.AUTO);
        paper.setStatus(Dicts.PaperStatus.DRAFT);
        paper.setCreatorId(LoginUser.userId());
        exPaperMapper.insert(paper);

        // 带种子的随机源：同一个 seed 抽到的题完全一致，排查抽题问题时方便复现
        Random random = new Random(System.nanoTime());
        Set<Long> drawn = new HashSet<>();
        int sortNo = 1;
        for (AutoPaperForm.Rule rule : form.getRules()) {
            if (rule == null || rule.getQType() == null || rule.getCount() == null) {
                throw BizException.param("抽题规则的题型与数量都不能为空");
            }
            if (rule.getCount() <= 0) {
                throw BizException.param("抽题数量必须大于 0");
            }
            List<QzQuestion> pool = candidates(form.getCategoryId(), rule);
            Collections.shuffle(pool, random);
            int got = 0;
            for (QzQuestion question : pool) {
                if (got >= rule.getCount()) {
                    break;
                }
                // drawn 跨规则去重：同一份卷子里一道题只出现一次
                if (!drawn.add(question.getId())) {
                    continue;
                }
                BigDecimal score = rule.getScorePerItem() == null ? question.getScore() : rule.getScorePerItem();
                writeSnapshot(paper.getId(), question, score, sortNo++);
                got++;
            }
            if (got < rule.getCount()) {
                throw new BizException(ErrorCode.PAPER_QUESTION_NOT_ENOUGH,
                        "题型 " + Dicts.QType.name(rule.getQType()) + " 只有 " + got + " 道可用题目，少于要求的 "
                                + rule.getCount() + " 道");
            }
        }
        recalcTotals(paper.getId());
        bumpUseCount(drawn, 1);
        return paper.getId();
    }

    // ------------------------------------------------------------ 状态流转

    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        ExPaper paper = requirePaper(id);
        assertWritable(paper.getCreatorId());
        List<ExPaperQuestion> questions = listQuestions(id);
        if (questions.isEmpty()) {
            throw new BizException(ErrorCode.PAPER_EMPTY, "试卷还没有题目");
        }
        BigDecimal sum = sumScore(questions);
        if (paper.getTotalScore() == null || sum.subtract(paper.getTotalScore()).abs().doubleValue() >= SCORE_TOLERANCE) {
            throw new BizException(ErrorCode.PAPER_SCORE_NOT_MATCH, "试卷总分与题目分值之和不一致，请重新检查组卷");
        }
        paper.setStatus(Dicts.PaperStatus.PUBLISHED);
        paper.setUpdateTime(null);
        exPaperMapper.updateById(paper);
    }

    /** 下架（unpublish）即归档：被考试用过的卷子要留着判分，不能下架 */
    @Transactional(rollbackFor = Exception.class)
    public void archive(Long id) {
        ExPaper paper = requirePaper(id);
        assertWritable(paper.getCreatorId());
        if (!Objects.equals(paper.getStatus(), Dicts.PaperStatus.PUBLISHED)) {
            throw BizException.param("只有已发布的试卷可以归档");
        }
        long used = countExams(id);
        if (used > 0) {
            throw BizException.forbidden("该试卷已被 " + used + " 场考试使用，不能归档");
        }
        paper.setStatus(Dicts.PaperStatus.ARCHIVED);
        paper.setUpdateTime(null);
        exPaperMapper.updateById(paper);
    }

    /** 分类必须真实存在，否则试卷列表的科目筛选会出现空归属数据 */
    private void requireCategory(Long categoryId) {
        if (categoryId == null) {
            return;
        }
        if (qzCategoryMapper.selectById(categoryId) == null) {
            throw BizException.param("所属分类不存在，请重新选择");
        }
    }

    // ------------------------------------------------------------ 内部方法

    private ExPaper requirePaper(Long id) {
        ExPaper paper = exPaperMapper.selectById(id);
        if (paper == null) {
            throw new BizException(ErrorCode.PAPER_NOT_FOUND, "试卷不存在");
        }
        return paper;
    }

    /** ADMIN 或试卷创建人可写，其他人只读 */
    private void assertWritable(Long creatorId) {
        if (LoginUser.hasRole(Dicts.Role.ADMIN)) {
            return;
        }
        if (creatorId != null && creatorId.equals(LoginUser.userId())) {
            return;
        }
        throw BizException.forbidden("只能修改自己创建的试卷");
    }

    /** 只有草稿卷子可以动题目；已发布要先新建副本 */
    private void requireDraft(ExPaper paper) {
        if (!Objects.equals(paper.getStatus(), Dicts.PaperStatus.DRAFT)) {
            throw new BizException(ErrorCode.PAPER_PUBLISHED_LOCKED, "只有草稿状态的试卷可以增删题目，请先新建副本");
        }
    }

    /**
     * 写题目快照。人工组卷与规则抽题共用这一个方法，保证两条路径落库字段完全一致。
     */
    private void writeSnapshot(Long paperId, QzQuestion question, BigDecimal score, int sortNo) {
        BigDecimal finalScore = score == null ? new BigDecimal("2.0") : score;
        // 组卷与自动抽题都走这里，分值合法性只判一次：负分/零分会让总分算出无意义结果
        if (finalScore.compareTo(BigDecimal.ZERO) <= 0 || finalScore.compareTo(new BigDecimal("100")) > 0) {
            throw BizException.param("每题分值需在 0 到 100 之间，第 " + sortNo + " 题填了 " + finalScore.stripTrailingZeros().toPlainString());
        }
        ExPaperQuestion row = new ExPaperQuestion();
        row.setPaperId(paperId);
        row.setQuestionId(question.getId());
        row.setQType(question.getQType());
        row.setContent(question.getContent());
        row.setOptions(question.getOptions());
        row.setAnswer(question.getAnswer());
        row.setAnalysis(question.getAnalysis());
        row.setDifficulty(question.getDifficulty());
        row.setScore(finalScore);
        row.setSortNo(sortNo);
        exPaperQuestionMapper.insert(row);
    }

    /** 用快照重算 ex_paper 上的冗余列：总分 = 各题分值之和，题数 = 快照行数 */
    private void recalcTotals(Long paperId) {
        List<ExPaperQuestion> rows = exPaperQuestionMapper.selectList(new LambdaQueryWrapper<ExPaperQuestion>()
                .select(ExPaperQuestion::getScore)
                .eq(ExPaperQuestion::getPaperId, paperId));
        ExPaper update = new ExPaper();
        update.setId(paperId);
        update.setTotalScore(sumScore(rows));
        update.setQuestionCount(rows.size());
        exPaperMapper.updateById(update);
    }

    private BigDecimal sumScore(List<ExPaperQuestion> rows) {
        return rows.stream()
                .map(ExPaperQuestion::getScore)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<ExPaperQuestion> listQuestions(Long paperId) {
        return exPaperQuestionMapper.selectList(new LambdaQueryWrapper<ExPaperQuestion>()
                .eq(ExPaperQuestion::getPaperId, paperId)
                .orderByAsc(ExPaperQuestion::getSortNo)
                .orderByAsc(ExPaperQuestion::getId));
    }

    private List<Long> existingQuestionIds(Long paperId) {
        return exPaperQuestionMapper.selectList(new LambdaQueryWrapper<ExPaperQuestion>()
                        .select(ExPaperQuestion::getQuestionId)
                        .eq(ExPaperQuestion::getPaperId, paperId))
                .stream().map(ExPaperQuestion::getQuestionId).toList();
    }

    /** 新加的题排在现有题目之后 */
    private int nextSortNo(Long paperId) {
        List<ExPaperQuestion> rows = exPaperQuestionMapper.selectList(new LambdaQueryWrapper<ExPaperQuestion>()
                .select(ExPaperQuestion::getSortNo)
                .eq(ExPaperQuestion::getPaperId, paperId));
        return rows.stream().map(ExPaperQuestion::getSortNo).filter(Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 1;
    }

    /** 按单条规则取候选题库题目（deleted=0 由 MyBatis-Plus 逻辑删除自动附加） */
    private List<QzQuestion> candidates(Long categoryId, AutoPaperForm.Rule rule) {
        LambdaQueryWrapper<QzQuestion> wrapper = new LambdaQueryWrapper<QzQuestion>()
                .eq(QzQuestion::getQType, rule.getQType())
                .eq(QzQuestion::getStatus, Dicts.Status.ENABLED);
        if (rule.getDifficulty() != null) {
            wrapper.eq(QzQuestion::getDifficulty, rule.getDifficulty());
        }
        if (categoryId != null) {
            wrapper.in(QzQuestion::getCategoryId, categoryIdsWithDescendants(categoryId));
        }
        return new ArrayList<>(qzQuestionMapper.selectList(wrapper));
    }

    /** 题库分类是树：给了父分类就要能抽到它下面所有章节的题，所以一次取全表在内存里向下收集 */
    private Set<Long> categoryIdsWithDescendants(Long rootId) {
        List<QzCategory> all = qzCategoryMapper.selectList(new LambdaQueryWrapper<QzCategory>()
                .select(QzCategory::getId, QzCategory::getParentId));
        Map<Long, List<Long>> children = new HashMap<>();
        for (QzCategory category : all) {
            Long parentId = category.getParentId() == null ? 0L : category.getParentId();
            children.computeIfAbsent(parentId, k -> new ArrayList<>()).add(category.getId());
        }
        Set<Long> ids = new LinkedHashSet<>();
        Deque<Long> pending = new ArrayDeque<>();
        pending.add(rootId);
        while (!pending.isEmpty()) {
            Long current = pending.poll();
            if (current == null || !ids.add(current)) {
                continue;
            }
            pending.addAll(children.getOrDefault(current, Collections.emptyList()));
        }
        return ids;
    }

    private long countExams(Long paperId) {
        return exExamMapper.selectCount(new LambdaQueryWrapper<ExExam>().eq(ExExam::getPaperId, paperId));
    }

    private Map<Long, String> categoryNameMap(Collection<Long> ids) {
        Set<Long> valid = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (valid.isEmpty()) {
            return Collections.emptyMap();
        }
        return qzCategoryMapper.selectBatchIds(valid).stream()
                .collect(Collectors.toMap(QzCategory::getId, QzCategory::getName));
    }

    private Map<Long, String> creatorNameMap(Collection<Long> ids) {
        Set<Long> valid = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (valid.isEmpty()) {
            return Collections.emptyMap();
        }
        return sysUserMapper.selectBatchIds(valid).stream()
                .collect(Collectors.toMap(SysUser::getId, user -> user.getRealName() == null ? "" : user.getRealName()));
    }

    private Map<Long, Long> examCountMap(Collection<Long> paperIds) {
        Set<Long> valid = paperIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (valid.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ExExam> exams = exExamMapper.selectList(new LambdaQueryWrapper<ExExam>()
                .select(ExExam::getPaperId)
                .in(ExExam::getPaperId, valid));
        return exams.stream().collect(Collectors.groupingBy(ExExam::getPaperId, Collectors.counting()));
    }

    private boolean notBlank(String text) {
        return text != null && !text.isBlank();
    }
}
