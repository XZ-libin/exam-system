package com.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.common.BizException;
import com.exam.common.Dicts;
import com.exam.common.ErrorCode;
import com.exam.common.JsonUtil;
import com.exam.common.LoginUser;
import com.exam.common.PageResult;
import com.exam.entity.AnAnswerItem;
import com.exam.entity.AnExamRecord;
import com.exam.entity.ExExam;
import com.exam.entity.ExPaper;
import com.exam.entity.ExPaperQuestion;
import com.exam.entity.SysUser;
import com.exam.mapper.AnAnswerItemMapper;
import com.exam.mapper.AnExamRecordMapper;
import com.exam.mapper.ExPaperMapper;
import com.exam.mapper.ExPaperQuestionMapper;
import com.exam.mapper.SysUserMapper;
import com.exam.vo.ExamPaperVo;
import com.exam.vo.RecordDetailVo;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 成绩管理：成绩单列表、答卷明细、导出。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreService {

    private static final ObjectMapper OPTION_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AnExamRecordMapper recordMapper;
    private final AnAnswerItemMapper answerItemMapper;
    private final ExPaperQuestionMapper paperQuestionMapper;
    private final ExPaperMapper paperMapper;
    private final SysUserMapper userMapper;
    private final AttemptService attemptService;

    /** 教师/管理员看全班成绩 */
    public PageResult<Map<String, Object>> page(long page, long size, Long examId, String keyword,
                                                Integer status, Integer passFlag) {
        if (!attemptService.canReview()) {
            throw BizException.forbidden("只有教师与管理员可以查看全班成绩");
        }
        LambdaQueryWrapper<AnExamRecord> wrapper = new LambdaQueryWrapper<AnExamRecord>()
                .eq(examId != null, AnExamRecord::getExamId, examId)
                .eq(status != null, AnExamRecord::getStatus, status)
                .eq(passFlag != null, AnExamRecord::getPassFlag, passFlag)
                .orderByDesc(AnExamRecord::getId);
        if (keyword != null && !keyword.isBlank()) {
            List<Long> userIds = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                            .like(SysUser::getRealName, keyword).or().like(SysUser::getUsername, keyword))
                    .stream().map(SysUser::getId).toList();
            if (userIds.isEmpty()) {
                return PageResult.of(List.of(), 0, page, size);
            }
            wrapper.in(AnExamRecord::getUserId, userIds);
        }
        Page<AnExamRecord> result = recordMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(toRows(result.getRecords()), result.getTotal(), result.getCurrent(), result.getSize());
    }

    /** 学生看自己的成绩 */
    public PageResult<Map<String, Object>> myPage(long page, long size, Long examId) {
        Long me = LoginUser.userId();
        Page<AnExamRecord> result = recordMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<AnExamRecord>()
                        .eq(AnExamRecord::getUserId, me)
                        .eq(examId != null, AnExamRecord::getExamId, examId)
                        .orderByDesc(AnExamRecord::getId));
        List<Map<String, Object>> rows = toRows(result.getRecords());
        rows.forEach(row -> {
            ExExam exam = attemptService.requireExam(((Long) row.get("examId")));
            if (notPublished(exam)) {
                row.put("totalScore", null);
                row.put("passFlag", null);
                row.put("scoreHint", "成绩尚未发布");
            }
        });
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public List<Map<String, Object>> toRows(List<AnExamRecord> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        Map<Long, SysUser> userById = userMapper.selectBatchIds(records.stream()
                        .map(AnExamRecord::getUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Map<Long, ExExam> examById = records.stream().map(AnExamRecord::getExamId).distinct()
                .map(id -> Map.entry(id, attemptService.requireExam(id)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<Long, ExPaper> paperById = examById.values().stream().map(ExExam::getPaperId).distinct()
                .map(id -> Map.entry(id, paperMapper.selectById(id)))
                .filter(e -> e.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (AnExamRecord record : records) {
            ExExam exam = examById.get(record.getExamId());
            ExPaper paper = exam == null ? null : paperById.get(exam.getPaperId());
            SysUser student = userById.get(record.getUserId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("recordId", record.getId());
            row.put("examId", record.getExamId());
            row.put("examTitle", exam == null ? "" : exam.getTitle());
            row.put("paperTitle", paper == null ? "" : paper.getTitle());
            row.put("attemptNo", record.getAttemptNo());
            row.put("studentId", record.getUserId());
            row.put("studentName", student == null ? "已删除用户" : student.getRealName());
            row.put("username", student == null ? "" : student.getUsername());
            row.put("className", student == null ? "" : student.getClassName());
            row.put("startTime", record.getStartTime());
            row.put("submitTime", record.getSubmitTime());
            row.put("status", record.getStatus());
            row.put("statusName", statusName(record.getStatus()));
            row.put("objectiveScore", record.getObjectiveScore());
            row.put("subjectiveScore", record.getSubjectiveScore());
            boolean published = exam != null && !notPublished(exam);
            row.put("totalScore", published ? record.getTotalScore() : null);
            row.put("passFlag", published ? record.getPassFlag() : null);
            row.put("fullScore", paper == null ? null : paper.getTotalScore());
            row.put("scorePublished", exam == null ? 0 : exam.getScorePublished());
            row.put("switchCount", record.getSwitchCount());
            rows.add(row);
        }
        return rows;
    }

    /** 答卷明细：教师阅卷、学生看成绩都走这里 */
    public RecordDetailVo detail(Long recordId) {
        AnExamRecord record = attemptService.requireVisibleRecord(recordId);
        ExExam exam = attemptService.requireExam(record.getExamId());
        ExPaper paper = paperMapper.selectById(exam.getPaperId());
        boolean reviewer = attemptService.canReview();
        // 学生只有在「成绩已发布」且「自己已交卷」后才能看到答案，防止答题中途用地址栏偷看
        boolean showAnswer = reviewer
                || (!notPublished(exam) && Dicts.RecordStatus.submitted(record.getStatus()));

        RecordDetailVo vo = new RecordDetailVo();
        vo.setRecordId(record.getId());
        vo.setExamId(exam.getId());
        vo.setExamTitle(exam.getTitle());
        vo.setPaperId(exam.getPaperId());
        vo.setPaperTitle(paper == null ? "" : paper.getTitle());
        vo.setStudentId(record.getUserId());
        SysUser student = userMapper.selectById(record.getUserId());
        vo.setStudentName(student == null ? "已删除用户" : student.getRealName());
        vo.setUsername(student == null ? "" : student.getUsername());
        vo.setClassName(student == null ? "" : student.getClassName());
        vo.setStartTime(record.getStartTime());
        vo.setSubmitTime(record.getSubmitTime());
        vo.setDeadlineTime(record.getDeadlineTime());
        vo.setUsedSeconds(usedSeconds(record));
        vo.setStatus(record.getStatus());
        vo.setStatusName(statusName(record.getStatus()));
        vo.setObjectiveScore(showAnswer ? record.getObjectiveScore() : null);
        vo.setSubjectiveScore(showAnswer ? record.getSubjectiveScore() : null);
        vo.setTotalScore(showAnswer ? record.getTotalScore() : null);
        vo.setPassFlag(showAnswer ? record.getPassFlag() : null);
        vo.setFullScore(paper == null ? null : paper.getTotalScore());
        vo.setPassScore(paper == null ? null : paper.getPassScore());
        vo.setSwitchCount(record.getSwitchCount());
        vo.setScorePublished(exam.getScorePublished());

        List<AnAnswerItem> items = answerItemMapper.selectList(new LambdaQueryWrapper<AnAnswerItem>()
                .eq(AnAnswerItem::getRecordId, record.getId())
                .orderByAsc(AnAnswerItem::getSortNo));
        Map<Long, ExPaperQuestion> questionById = items.isEmpty() ? Map.of()
                : paperQuestionMapper.selectBatchIds(items.stream()
                        .map(AnAnswerItem::getPaperQuestionId).distinct().toList()).stream()
                .collect(Collectors.toMap(ExPaperQuestion::getId, Function.identity()));

        List<RecordDetailVo.Item> list = new ArrayList<>();
        for (AnAnswerItem item : items) {
            ExPaperQuestion question = questionById.get(item.getPaperQuestionId());
            if (question == null) {
                continue;
            }
            RecordDetailVo.Item row = new RecordDetailVo.Item();
            row.setAnswerItemId(item.getId());
            row.setPaperQuestionId(question.getId());
            row.setQuestionId(question.getQuestionId());
            row.setSortNo(item.getSortNo());
            row.setQType(question.getQType());
            row.setQTypeName(Dicts.QType.name(question.getQType()));
            row.setContent(question.getContent());
            row.setOptions(parseOptions(question.getOptions()));
            row.setBlankCount(blankCount(question.getContent()));
            row.setUserAnswer(JsonUtil.readStringList(item.getUserAnswer()));
            row.setFullScore(item.getFullScore());
            row.setReviewStatus(item.getReviewStatus());
            if (showAnswer) {
                // 未发布成绩时连「这题对不对」都不能给，否则学生可逐题试错重考
                row.setScore(item.getScore());
                row.setCorrectFlag(item.getCorrectFlag());
                row.setCorrectFlagName(correctName(item.getCorrectFlag()));
                row.setReviewComment(item.getReviewComment());
                row.setStandardAnswer(JsonUtil.readStringList(question.getAnswer()));
                row.setAnalysis(question.getAnalysis());
            }
            list.add(row);
        }
        vo.setItems(list);
        return vo;
    }

    public String exportCsv(Long examId) {
        if (!attemptService.canReview()) {
            throw BizException.forbidden("只有教师与管理员可以导出成绩");
        }
        List<AnExamRecord> records = recordMapper.selectList(new LambdaQueryWrapper<AnExamRecord>()
                .eq(examId != null, AnExamRecord::getExamId, examId)
                .orderByAsc(AnExamRecord::getExamId).orderByAsc(AnExamRecord::getId));
        StringBuilder text = new StringBuilder();
        text.append('\uFEFF'); // BOM：Excel 打开 CSV 时正确识别中文
        text.append("考试,试卷,学号,姓名,班级,状态,客观题得分,主观题得分,总分,是否及格,交卷时间\n");
        for (Map<String, Object> row : toRows(records)) {
            text.append(csv(row.get("examTitle"))).append(',')
                    .append(csv(row.get("paperTitle"))).append(',')
                    .append(csv(row.get("username"))).append(',')
                    .append(csv(row.get("studentName"))).append(',')
                    .append(csv(row.get("className"))).append(',')
                    .append(csv(row.get("statusName"))).append(',')
                    .append(csv(row.get("objectiveScore"))).append(',')
                    .append(csv(row.get("subjectiveScore"))).append(',')
                    .append(csv(row.get("totalScore"))).append(',')
                    .append(csv(passText(row.get("passFlag")))).append(',')
                    .append(csv(row.get("submitTime") == null ? ""
                            : ((LocalDateTime) row.get("submitTime")).format(DAY))).append('\n');
        }
        return text.toString();
    }

    /** 合分：阅卷后重算总分与及格标记 */
    public void recalcTotals(Long recordId) {
        List<AnAnswerItem> items = answerItemMapper.selectList(new LambdaQueryWrapper<AnAnswerItem>()
                .eq(AnAnswerItem::getRecordId, recordId));
        if (items.isEmpty()) {
            return;
        }
        boolean waiting = items.stream().anyMatch(i ->
                i.getReviewStatus() != null && i.getReviewStatus() == Dicts.ReviewStatus.WAIT_REVIEW);
        BigDecimal objective = sum(items, Dicts.ReviewStatus.AUTO_JUDGED);
        BigDecimal subjective = sum(items, Dicts.ReviewStatus.REVIEWED);

        AnExamRecord record = attemptService.requireRecord(recordId);
        ExExam exam = attemptService.requireExam(record.getExamId());
        ExPaper paper = paperMapper.selectById(exam.getPaperId());
        BigDecimal passScore = paper == null || paper.getPassScore() == null
                ? BigDecimal.ZERO : paper.getPassScore();

        LambdaUpdateWrapper<AnExamRecord> wrapper = new LambdaUpdateWrapper<AnExamRecord>()
                .eq(AnExamRecord::getId, recordId)
                .set(AnExamRecord::getObjectiveScore, objective)
                .set(AnExamRecord::getSubjectiveScore, subjective);
        if (waiting) {
            // 还有题没阅完：总分与及格标记要显式清空，updateById 会跳过 null 而留下旧值
            wrapper.set(AnExamRecord::getStatus, Dicts.RecordStatus.WAIT_REVIEW)
                    .set(AnExamRecord::getTotalScore, null)
                    .set(AnExamRecord::getPassFlag, null);
        } else {
            BigDecimal total = objective.add(subjective);
            wrapper.set(AnExamRecord::getTotalScore, total)
                    .set(AnExamRecord::getPassFlag, total.compareTo(passScore) >= 0 ? 1 : 0)
                    .set(AnExamRecord::getStatus,
                            record.getStatus() != null && record.getStatus() == Dicts.RecordStatus.TIMEOUT_SUBMIT
                                    ? Dicts.RecordStatus.TIMEOUT_SUBMIT : Dicts.RecordStatus.FINISHED);
        }
        recordMapper.update(null, wrapper);
    }

    private BigDecimal sum(List<AnAnswerItem> items, int reviewStatus) {
        return items.stream()
                .filter(i -> i.getReviewStatus() != null && i.getReviewStatus() == reviewStatus)
                .map(AnAnswerItem::getScore)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean notPublished(ExExam exam) {
        return exam.getScorePublished() == null || exam.getScorePublished() != Dicts.ScorePublish.PUBLISHED;
    }

    private Integer usedSeconds(AnExamRecord record) {
        if (record.getSubmitTime() == null) {
            return null;
        }
        return (int) Math.max(0, Duration.between(record.getStartTime(), record.getSubmitTime()).getSeconds());
    }

    private String statusName(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case Dicts.RecordStatus.DOING -> "作答中";
            case Dicts.RecordStatus.WAIT_REVIEW -> "待阅卷";
            case Dicts.RecordStatus.FINISHED -> "已完成";
            case Dicts.RecordStatus.TIMEOUT_SUBMIT -> "超时强制交卷";
            default -> "未知";
        };
    }

    private String correctName(Integer flag) {
        if (flag == null) {
            return "未判定";
        }
        return switch (flag) {
            case Dicts.CorrectFlag.RIGHT -> "正确";
            case Dicts.CorrectFlag.HALF -> "部分正确";
            default -> "错误";
        };
    }

    private List<ExamPaperVo.Option> parseOptions(String json) {
        List<ExamPaperVo.Option> options = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return options;
        }
        try {
            var node = OPTION_MAPPER.readTree(json);
            if (node.isArray()) {
                node.forEach(n -> {
                    ExamPaperVo.Option option = new ExamPaperVo.Option();
                    option.setKey(n.path("key").asText(""));
                    option.setText(n.path("text").asText(""));
                    options.add(option);
                });
            }
        } catch (Exception e) {
            log.warn("选项解析失败: {}", e.getMessage());
        }
        return options;
    }

    private Integer blankCount(String content) {
        if (content == null) {
            return 0;
        }
        int count = 0;
        boolean inRun = false;
        for (char c : content.toCharArray()) {
            if (c == '_' && !inRun) {
                count++;
                inRun = true;
            } else if (c != '_') {
                inRun = false;
            }
        }
        return count;
    }

    private String passText(Object flag) {
        if (flag == null) {
            return "";
        }
        return Objects.equals(flag, 1) ? "及格" : "不及格";
    }

    private String csv(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }
}
