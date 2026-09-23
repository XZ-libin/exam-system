package com.exam.service;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.exam.common.BatchLimit;
import com.exam.common.BizException;
import com.exam.common.Dicts;
import com.exam.common.ErrorCode;
import com.exam.common.JsonUtil;
import com.exam.common.LoginUser;
import com.exam.dto.AttemptForm;
import com.exam.entity.AnAnswerItem;
import com.exam.entity.AnExamRecord;
import com.exam.entity.ExExam;
import com.exam.entity.ExExamClass;
import com.exam.entity.ExPaper;
import com.exam.entity.ExPaperQuestion;
import com.exam.mapper.AnAnswerItemMapper;
import com.exam.mapper.AnExamRecordMapper;
import com.exam.mapper.ExExamClassMapper;
import com.exam.mapper.ExExamMapper;
import com.exam.mapper.ExPaperMapper;
import com.exam.mapper.ExPaperQuestionMapper;
import com.exam.vo.ExamPaperVo;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 在线答题：开考、续考、自动保存、切屏上报、交卷与判分入口。
 * 计时以服务端为准：deadline_time 在开考时算好，之后所有写操作都校验它。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttemptService {

    private static final ObjectMapper OPTION_MAPPER = new ObjectMapper();

    private final ExExamMapper examMapper;
    private final ExExamClassMapper examClassMapper;
    private final ExPaperMapper paperMapper;
    private final ExPaperQuestionMapper paperQuestionMapper;
    private final AnExamRecordMapper recordMapper;
    private final AnAnswerItemMapper answerItemMapper;
    private final JudgeService judgeService;
    private final LogService logService;

    // ----------------------------------------------------------- 学生考试列表

    public List<Map<String, Object>> myExams() {
        LoginUser me = requireLogin();
        LocalDateTime now = LocalDateTime.now();
        // 进行中的排前面，再补最近 60 天内已结束的，历史考试去「我的成绩」看
        List<ExExam> exams = examMapper.selectList(new LambdaQueryWrapper<ExExam>()
                .eq(ExExam::getStatus, Dicts.ExamStatus.PUBLISHED)
                .ge(ExExam::getEndTime, now.minusDays(60))
                .orderByAsc(ExExam::getEndTime));
        if (exams.isEmpty()) {
            return List.of();
        }
        exams = exams.stream().filter(e -> visibleTo(e, me)).toList();
        if (exams.isEmpty()) {
            return List.of();
        }
        Map<Long, ExPaper> paperById = paperMapper.selectBatchIds(exams.stream()
                        .map(ExExam::getPaperId).distinct().toList()).stream()
                .collect(Collectors.toMap(ExPaper::getId, Function.identity()));
        List<AnExamRecord> myRecords = recordMapper.selectList(new LambdaQueryWrapper<AnExamRecord>()
                .eq(AnExamRecord::getUserId, me.getUserId()));
        Map<Long, List<AnExamRecord>> byExam = myRecords.stream()
                .collect(Collectors.groupingBy(AnExamRecord::getExamId));

        List<Map<String, Object>> list = new ArrayList<>();
        for (ExExam exam : exams) {
            Map<String, Object> row = new LinkedHashMap<>();
            ExPaper paper = paperById.get(exam.getPaperId());
            row.put("id", exam.getId());
            row.put("title", exam.getTitle());
            row.put("paperTitle", paper == null ? "" : paper.getTitle());
            row.put("description", exam.getDescription());
            row.put("startTime", exam.getStartTime());
            row.put("endTime", exam.getEndTime());
            row.put("durationMinutes", exam.getDurationMinutes());
            row.put("totalScore", paper == null ? null : paper.getTotalScore());
            row.put("questionCount", paper == null ? 0 : paper.getQuestionCount());
            row.put("state", stateOf(exam));
            row.put("scorePublished", exam.getScorePublished());
            List<AnExamRecord> records = byExam.getOrDefault(exam.getId(), List.of());
            row.put("usedAttempts", records.size());
            row.put("maxAttempts", exam.getMaxAttempts());
            records.stream().filter(r -> Dicts.RecordStatus.submitted(r.getStatus()))
                    .max((a, b) -> a.getId().compareTo(b.getId()))
                    .ifPresent(r -> {
                        row.put("lastRecordId", r.getId());
                        row.put("lastStatus", r.getStatus());
                        row.put("lastScore", r.getTotalScore());
                    });
            records.stream().filter(r -> r.getStatus() == Dicts.RecordStatus.DOING).findFirst()
                    .ifPresent(r -> row.put("resumableRecordId", r.getId()));
            list.add(row);
        }
        return list;
    }

    /** 1 未开始 2 进行中 3 已结束 */
    public int stateOf(ExExam exam) {
        LocalDateTime now = LocalDateTime.now();
        if (exam.getStatus() != null && exam.getStatus() == Dicts.ExamStatus.CLOSED) {
            return 3;
        }
        if (now.isBefore(exam.getStartTime())) {
            return 1;
        }
        if (now.isAfter(exam.getEndTime())) {
            return 3;
        }
        return 2;
    }

    // ----------------------------------------------------------- 开考 / 续考

    @Transactional(rollbackFor = Exception.class)
    public ExamPaperVo enter(Long examId) {
        LoginUser me = requireLogin();
        ExExam exam = requireExam(examId);
        LocalDateTime now = LocalDateTime.now();
        // 锁住考试这一行，串行化 attempt_no 的分配：
        // 不锁的话并发进入会撞 uk_exam_user_attempt（或因为锁空集产生间隙锁而死锁），给学生一个 500
        examMapper.selectOne(new LambdaQueryWrapper<ExExam>()
                .eq(ExExam::getId, examId)
                .last("for update"));

        // 先做与「新开/续考」都相关的状态校验：考试被结束或时间已过，就不能再作答了
        if (exam.getStatus() != null && exam.getStatus() == Dicts.ExamStatus.CLOSED) {
            throw BizException.of(ErrorCode.EXAM_ENDED, "本场考试已被结束");
        }
        if (exam.getStatus() == null || exam.getStatus() != Dicts.ExamStatus.PUBLISHED) {
            throw BizException.of(ErrorCode.EXAM_NOT_START, "该考试尚未发布");
        }
        if (now.isBefore(exam.getStartTime())) {
            throw BizException.of(ErrorCode.EXAM_NOT_START, "考试还未开始");
        }
        if (now.isAfter(exam.getEndTime())) {
            throw BizException.of(ErrorCode.EXAM_ENDED, "考试已结束");
        }
        if (!visibleTo(exam, me)) {
            throw BizException.of(ErrorCode.EXAM_NO_PERMISSION, "你不在本场考试的参考范围内");
        }

        AnExamRecord resumable = recordMapper.selectOne(new LambdaQueryWrapper<AnExamRecord>()
                .eq(AnExamRecord::getExamId, examId)
                .eq(AnExamRecord::getUserId, me.getUserId())
                .eq(AnExamRecord::getStatus, Dicts.RecordStatus.DOING)
                .orderByDesc(AnExamRecord::getId)
                .last("limit 1 for update"));
        if (resumable != null) {
            if (!now.isAfter(resumable.getDeadlineTime())) {
                return buildPaperVo(exam, resumable, true);
            }
            closeTimedOut(resumable);
            throw BizException.of(ErrorCode.RECORD_TIME_UP, "本次作答时间已用完，请重新进入查看结果");
        }

        // 以下只对「新开一次作答」生效
        int lateMinutes = exam.getLateMinutes() == null ? 0 : exam.getLateMinutes();
        if (now.isAfter(exam.getStartTime().plusMinutes(lateMinutes))) {
            throw BizException.of(ErrorCode.EXAM_TOO_LATE, lateMinutes <= 0
                    ? "本场考试不允许迟到入场，已超过开始时间"
                    : "开考超过 " + lateMinutes + " 分钟，不能再进入本场考试");
        }
        int maxAttempts = exam.getMaxAttempts() == null ? 1 : exam.getMaxAttempts();
        long used = recordMapper.selectCount(new LambdaQueryWrapper<AnExamRecord>()
                .eq(AnExamRecord::getExamId, examId)
                .eq(AnExamRecord::getUserId, me.getUserId()));
        if (used >= maxAttempts) {
            throw BizException.of(ErrorCode.EXAM_ATTEMPT_LIMIT,
                    "本场考试允许 " + maxAttempts + " 次作答，你已用完");
        }

        List<ExPaperQuestion> questions = loadQuestions(exam.getPaperId());
        if (questions.isEmpty()) {
            throw BizException.of(ErrorCode.PAPER_EMPTY, "试卷没有题目，请联系教师");
        }
        if (exam.getShuffleQuestion() != null && exam.getShuffleQuestion() == 1) {
            Collections.shuffle(questions, new Random(me.getUserId() * 131 + examId));
        }

        int duration = exam.getDurationMinutes() == null ? 60 : exam.getDurationMinutes();
        LocalDateTime deadline = now.plusMinutes(duration);
        if (deadline.isAfter(exam.getEndTime())) {
            deadline = exam.getEndTime();
        }

        AnExamRecord record = new AnExamRecord();
        record.setExamId(examId);
        record.setUserId(me.getUserId());
        record.setAttemptNo((int) used + 1);
        record.setStartTime(now);
        record.setDeadlineTime(deadline);
        record.setQuestionOrder(JsonUtil.write(questions.stream().map(ExPaperQuestion::getId).toList()));
        record.setStatus(Dicts.RecordStatus.DOING);
        record.setSwitchCount(0);
        record.setClientIp(clientIp());
        try {
            recordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 双击或两个标签页同时进入：另一个请求已建好答卷，这里直接续考它，而不是给学生一个 500
            AnExamRecord other = recordMapper.selectOne(new LambdaQueryWrapper<AnExamRecord>()
                    .eq(AnExamRecord::getExamId, examId)
                    .eq(AnExamRecord::getUserId, me.getUserId())
                    .orderByDesc(AnExamRecord::getId)
                    .last("limit 1 for update"));
            if (other == null) {
                throw e;
            }
            return buildPaperVo(exam, other, true);
        }

        int sort = 1;
        for (ExPaperQuestion question : questions) {
            AnAnswerItem item = new AnAnswerItem();
            item.setRecordId(record.getId());
            item.setPaperQuestionId(question.getId());
            item.setQuestionId(question.getQuestionId());
            item.setFullScore(question.getScore());
            item.setReviewStatus(Dicts.ReviewStatus.AUTO_JUDGED);
            item.setSortNo(sort++);
            answerItemMapper.insert(item);
        }
        logService.record("EXAM_ENTER", "ex_exam:" + examId, "开始作答，record=" + record.getId());
        return buildPaperVo(exam, record, false);
    }

    // ----------------------------------------------------------- 自动保存

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveAnswers(Long recordId, AttemptForm.Save form) {
        requireOwnRecord(recordId);
        BatchLimit.check(form == null ? null : form.getAnswers(), "作答内容");
        // 行锁住这份答卷：避免「自动保存」与「定时/手动交卷」同时写，导致判分漏掉最后一条答案
        AnExamRecord record = recordMapper.selectOne(new LambdaQueryWrapper<AnExamRecord>()
                .eq(AnExamRecord::getId, recordId)
                .last("for update"));
        LocalDateTime now = LocalDateTime.now();
        if (!record.getStatus().equals(Dicts.RecordStatus.DOING)) {
            throw BizException.of(ErrorCode.RECORD_ALREADY_SUBMITTED, "本次答卷已交，不能再修改");
        }
        if (now.isAfter(record.getDeadlineTime())) {
            closeTimedOut(record);
            throw BizException.of(ErrorCode.RECORD_TIME_UP, "作答时间已到，系统已自动交卷");
        }
        // 教师中途结束考试或考试窗口已过：把这份答卷就地交掉并告知前端，
        // 注意这里不能抛异常，否则本事务里的交卷结果会被一起回滚
        ExExam exam = requireExam(record.getExamId());
        boolean examClosed = (exam.getStatus() != null && exam.getStatus() == Dicts.ExamStatus.CLOSED)
                || now.isAfter(exam.getEndTime());
        if (examClosed) {
            Map<String, Object> done = doSubmit(record, true);
            done.put("examClosed", true);
            done.put("message", "本场考试已结束，你的作答已自动提交");
            return done;
        }
        int saved = 0;
        if (form != null && form.getAnswers() != null) {
            Map<Long, AnAnswerItem> items = answerItemMapper.selectList(
                            new LambdaQueryWrapper<AnAnswerItem>().eq(AnAnswerItem::getRecordId, recordId)).stream()
                    .collect(Collectors.toMap(AnAnswerItem::getPaperQuestionId, Function.identity()));
            for (AttemptForm.Item item : form.getAnswers()) {
                // 前端可能传来 null 项或不属于本卷的题号，跳过而不是当成已保存
                if (item == null || item.getPaperQuestionId() == null) {
                    continue;
                }
                AnAnswerItem exists = items.get(item.getPaperQuestionId());
                if (exists == null) {
                    continue;
                }
                AnAnswerItem update = new AnAnswerItem();
                update.setId(exists.getId());
                update.setUserAnswer(JsonUtil.write(judgeService.normalize(item.getAnswer())));
                answerItemMapper.updateById(update);
                saved++;
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("savedCount", saved);
        data.put("remainingSeconds", remainingSeconds(record));
        data.put("serverTime", now);
        return data;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reportSwitch(Long recordId) {
        AnExamRecord record = requireOwnRecord(recordId);
        if (!record.getStatus().equals(Dicts.RecordStatus.DOING)) {
            return Map.of("switchCount", nz(record.getSwitchCount()), "forced", false, "remainingSeconds", 0);
        }
        ExExam exam = requireExam(record.getExamId());
        // 原子自增：读-改-写在并发上报时会少计（5 次只记成 1 次）
        recordMapper.update(null, new LambdaUpdateWrapper<AnExamRecord>()
                .setSql("switch_count = switch_count + 1")
                .eq(AnExamRecord::getId, recordId));
        int count = nz(recordMapper.selectById(recordId).getSwitchCount());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("switchCount", count);
        data.put("limit", exam.getSwitchLimit());
        int limit = nz(exam.getSwitchLimit());
        if (limit > 0 && count >= limit) {
            doSubmit(record, true);
            // 这里不能再抛 BizException：本方法带事务，抛出会把刚写入的交卷结果整体回滚，
            // 于是切屏计数与强制交卷都不生效。改为返回 forced=true 让前端处理。
            data.put("forced", true);
            data.put("message", "切屏达到 " + limit + " 次上限，系统已强制交卷");
            logService.record("EXAM_SWITCH_LIMIT", "an_exam_record:" + recordId,
                    "切屏 " + count + " 次达到上限，系统强制交卷");
            return data;
        }
        data.put("forced", false);
        data.put("remainingSeconds", remainingSeconds(record));
        return data;
    }

    // ----------------------------------------------------------- 交卷与判分

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submit(Long recordId) {
        AnExamRecord record = requireOwnRecord(recordId);
        if (!record.getStatus().equals(Dicts.RecordStatus.DOING)) {
            throw BizException.of(ErrorCode.RECORD_ALREADY_SUBMITTED, "本次答卷已经提交过了");
        }
        Map<String, Object> result = doSubmit(record, false);
        if (Boolean.TRUE.equals(result.get("alreadySubmitted"))) {
            // 并发点了两次交卷：抢到状态的那次已经判分，这次按「已提交」告诉前端
            throw BizException.of(ErrorCode.RECORD_ALREADY_SUBMITTED, "本次答卷已经提交过了");
        }
        return result;
    }

    /** 学生对自己的答卷才有写权限；教师强制收卷走 forceSubmitExam */
    private AnExamRecord requireOwnRecord(Long recordId) {
        AnExamRecord record = requireRecord(recordId);
        if (!record.getUserId().equals(LoginUser.userId())) {
            throw BizException.forbidden("只能操作本人的答卷");
        }
        return record;
    }

    /** 教师手动强制收卷 */
    @Transactional(rollbackFor = Exception.class)
    public int forceSubmitExam(Long examId) {
        List<AnExamRecord> doing = recordMapper.selectList(new LambdaQueryWrapper<AnExamRecord>()
                .eq(AnExamRecord::getExamId, examId)
                .eq(AnExamRecord::getStatus, Dicts.RecordStatus.DOING));
        for (AnExamRecord record : doing) {
            doSubmit(record, true);
        }
        return doing.size();
    }

    /** 定时任务：把超过 deadline 仍未交卷的答卷强制交卷 */
    @Transactional(rollbackFor = Exception.class)
    public int submitTimedOut() {
        List<AnExamRecord> overdue = recordMapper.selectList(new LambdaQueryWrapper<AnExamRecord>()
                .eq(AnExamRecord::getStatus, Dicts.RecordStatus.DOING)
                .le(AnExamRecord::getDeadlineTime, LocalDateTime.now()));
        for (AnExamRecord record : overdue) {
            doSubmit(record, true);
        }
        return overdue.size();
    }

    private void closeTimedOut(AnExamRecord record) {
        doSubmit(record, true);
    }

    /**
     * 判分主流程：客观题立即打分，简答题进阅卷队列。
     * 没有简答题时答卷直接 FINISHED，否则 WAIT_REVIEW 等人工阅卷合分。
     */
    private Map<String, Object> doSubmit(AnExamRecord record, boolean forced) {
        // 先原子占位：只有把 status 从 0 改走的线程才有权判分，避免重复交卷/定时任务与手动交卷撞车
        int claimed = recordMapper.update(null, new LambdaUpdateWrapper<AnExamRecord>()
                .set(AnExamRecord::getStatus, Dicts.RecordStatus.WAIT_REVIEW)
                .set(AnExamRecord::getSubmitTime, LocalDateTime.now())
                .eq(AnExamRecord::getId, record.getId())
                .eq(AnExamRecord::getStatus, Dicts.RecordStatus.DOING));
        if (claimed == 0) {
            return Map.of("recordId", record.getId(), "alreadySubmitted", true);
        }
        List<AnAnswerItem> items = answerItemMapper.selectList(new LambdaQueryWrapper<AnAnswerItem>()
                .eq(AnAnswerItem::getRecordId, record.getId()));
        Map<Long, ExPaperQuestion> questionById = paperQuestionMapper.selectBatchIds(items.stream()
                        .map(AnAnswerItem::getPaperQuestionId).distinct().toList()).stream()
                .collect(Collectors.toMap(ExPaperQuestion::getId, Function.identity()));

        BigDecimal objective = BigDecimal.ZERO;
        boolean needReview = false;
        for (AnAnswerItem item : items) {
            ExPaperQuestion question = questionById.get(item.getPaperQuestionId());
            if (question == null) {
                continue;
            }
            List<String> userAnswer = JsonUtil.readStringList(item.getUserAnswer());
            JudgeService.Judged judged = judgeService.judge(question, userAnswer);
            AnAnswerItem update = new AnAnswerItem();
            update.setId(item.getId());
            if (judged.needReview()) {
                update.setReviewStatus(Dicts.ReviewStatus.WAIT_REVIEW);
                needReview = true;
            } else {
                update.setReviewStatus(Dicts.ReviewStatus.AUTO_JUDGED);
                update.setCorrectFlag(judged.correctFlag());
                update.setScore(judged.score());
                objective = objective.add(judged.score());
            }
            answerItemMapper.updateById(update);
        }

        ExExam exam = requireExam(record.getExamId());
        ExPaper paper = paperMapper.selectById(exam.getPaperId());
        BigDecimal passScore = paper == null || paper.getPassScore() == null
                ? BigDecimal.valueOf(60) : paper.getPassScore();

        AnExamRecord update = new AnExamRecord();
        update.setId(record.getId());
        update.setSubmitTime(LocalDateTime.now());
        update.setObjectiveScore(objective);
        update.setStatus(needReview ? Dicts.RecordStatus.WAIT_REVIEW
                : (forced ? Dicts.RecordStatus.TIMEOUT_SUBMIT : Dicts.RecordStatus.FINISHED));
        if (!needReview) {
            update.setSubjectiveScore(BigDecimal.ZERO);
            update.setTotalScore(objective);
            update.setPassFlag(objective.compareTo(passScore) >= 0 ? 1 : 0);
        }
        recordMapper.updateById(update);
        logService.record(forced ? "EXAM_FORCE_SUBMIT" : "EXAM_SUBMIT",
                "an_exam_record:" + record.getId(),
                needReview ? "客观题 " + objective + " 分，简答题待阅卷" : "交卷得分 " + objective);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recordId", record.getId());
        data.put("status", update.getStatus());
        data.put("needReview", needReview);
        data.put("objectiveScore", objective);
        data.put("totalScore", needReview ? null : objective);
        return data;
    }

    // ----------------------------------------------------------- 供其它模块复用

    public AnExamRecord requireRecord(Long recordId) {
        AnExamRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw BizException.of(ErrorCode.RECORD_NOT_FOUND, "答卷不存在");
        }
        return record;
    }

    /** 学生只能看自己的答卷，教师与管理员不限 */
    public AnExamRecord requireVisibleRecord(Long recordId) {
        AnExamRecord record = requireRecord(recordId);
        if (!canReview() && !record.getUserId().equals(LoginUser.userId())) {
            throw BizException.forbidden("只能查看本人的答卷");
        }
        return record;
    }

    public boolean canReview() {
        return LoginUser.hasRole(Dicts.Role.ADMIN, Dicts.Role.TEACHER);
    }

    public ExExam requireExam(Long examId) {
        ExExam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw BizException.of(ErrorCode.EXAM_NOT_FOUND, "考试不存在");
        }
        return exam;
    }

    public List<ExPaperQuestion> loadQuestions(Long paperId) {
        return paperQuestionMapper.selectList(new LambdaQueryWrapper<ExPaperQuestion>()
                .eq(ExPaperQuestion::getPaperId, paperId)
                .orderByAsc(ExPaperQuestion::getSortNo)
                .orderByAsc(ExPaperQuestion::getId));
    }

    // ----------------------------------------------------------- 组装试卷视图

    private ExamPaperVo buildPaperVo(ExExam exam, AnExamRecord record, boolean resumed) {
        ExPaper paper = paperMapper.selectById(exam.getPaperId());
        List<Long> orderedIds = JsonUtil.readStringList(record.getQuestionOrder()).stream()
                .map(Long::valueOf).toList();
        Map<Long, ExPaperQuestion> byId = paperQuestionMapper.selectBatchIds(
                        orderedIds.isEmpty() ? List.of(-1L) : orderedIds).stream()
                .collect(Collectors.toMap(ExPaperQuestion::getId, Function.identity()));
        List<ExPaperQuestion> questions = orderedIds.stream()
                .map(byId::get).filter(Objects::nonNull).toList();
        if (questions.isEmpty()) {
            questions = loadQuestions(exam.getPaperId());
        }

        ExamPaperVo vo = new ExamPaperVo();
        vo.setRecordId(record.getId());
        vo.setExamId(exam.getId());
        vo.setExamTitle(exam.getTitle());
        vo.setPaperId(exam.getPaperId());
        vo.setPaperTitle(paper == null ? "" : paper.getTitle());
        vo.setTotalScore(paper == null ? null : paper.getTotalScore());
        vo.setPassScore(paper == null ? null : paper.getPassScore());
        vo.setQuestionCount(questions.size());
        // 死线会被考试结束时间截断，所以这里返回本次真实可用的分钟数，
        // 避免界面写着「限时 30 分钟」而实际只剩 5 分钟
        vo.setDurationMinutes((int) Math.max(1, Math.ceil(remainingSeconds(record) / 60.0)));
        vo.setStartTime(record.getStartTime());
        vo.setDeadlineTime(record.getDeadlineTime());
        vo.setRemainingSeconds(remainingSeconds(record));
        vo.setAttemptNo(record.getAttemptNo());
        vo.setSwitchCount(nz(record.getSwitchCount()));
        vo.setSwitchLimit(nz(exam.getSwitchLimit()));
        vo.setResumed(resumed);
        vo.setSubmitted(Dicts.RecordStatus.submitted(record.getStatus()));

        boolean shuffleOption = exam.getShuffleOption() != null && exam.getShuffleOption() == 1;
        List<ExamPaperVo.Question> list = new ArrayList<>();
        Map<Long, List<String>> answers = new LinkedHashMap<>();
        int sort = 1;
        for (ExPaperQuestion question : questions) {
            ExamPaperVo.Question item = new ExamPaperVo.Question();
            item.setPaperQuestionId(question.getId());
            item.setQuestionId(question.getQuestionId());
            item.setQType(question.getQType());
            item.setQTypeName(Dicts.QType.name(question.getQType()));
            item.setContent(question.getContent());
            item.setScore(question.getScore());
            item.setSortNo(sort++);
            item.setBlankCount(blankCount(question.getContent()));
            List<ExamPaperVo.Option> options = parseOptions(question.getOptions());
            if (shuffleOption) {
                Collections.shuffle(options, new Random(record.getId() * 31L + question.getId()));
            }
            item.setOptions(options);
            list.add(item);
        }
        vo.setQuestions(list);

        for (AnAnswerItem item : answerItemMapper.selectList(new LambdaQueryWrapper<AnAnswerItem>()
                .eq(AnAnswerItem::getRecordId, record.getId()))) {
            answers.put(item.getPaperQuestionId(), JsonUtil.readStringList(item.getUserAnswer()));
        }
        vo.setAnswers(answers);
        return vo;
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

    /** 题干里连续两个以上下划线算一个空 */
    private Integer blankCount(String content) {
        if (content == null) {
            return 0;
        }
        int count = 0;
        boolean inRun = false;
        for (char c : content.toCharArray()) {
            if (c == '_') {
                if (!inRun) {
                    count++;
                    inRun = true;
                }
            } else {
                inRun = false;
            }
        }
        return count;
    }

    private long remainingSeconds(AnExamRecord record) {
        long seconds = Duration.between(LocalDateTime.now(), record.getDeadlineTime()).getSeconds();
        return Math.max(seconds, 0);
    }

    private LoginUser requireLogin() {
        LoginUser me = LoginUser.get();
        if (me == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return me;
    }

    /** audience_type：1 全部学生，2 指定班级，3 指定学号（ex_exam_class 存学号） */
    private boolean visibleTo(ExExam exam, LoginUser me) {
        int type = exam.getAudienceType() == null ? Dicts.Audience.ALL : exam.getAudienceType();
        if (type == Dicts.Audience.ALL) {
            return true;
        }
        List<String> scope = examClassMapper.selectList(new LambdaQueryWrapper<ExExamClass>()
                        .eq(ExExamClass::getExamId, exam.getId()))
                .stream().map(ExExamClass::getClassName).toList();
        if (scope.isEmpty()) {
            return true;
        }
        if (type == Dicts.Audience.NAMED) {
            return scope.stream().anyMatch(s -> s.equalsIgnoreCase(me.getUsername()));
        }
        return me.getClassName() != null && scope.contains(me.getClassName());
    }

    private String clientIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String ip = request.getHeader("X-Real-IP");
        return ip == null || ip.isBlank() ? request.getRemoteAddr() : ip;
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
