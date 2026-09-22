package com.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.common.BizException;
import com.exam.common.Dicts;
import com.exam.common.ErrorCode;
import com.exam.common.LoginUser;
import com.exam.common.PageResult;
import com.exam.dto.GradingForm;
import com.exam.entity.AnAnswerItem;
import com.exam.entity.AnExamRecord;
import com.exam.entity.ExExam;
import com.exam.entity.SysUser;
import com.exam.mapper.AnAnswerItemMapper;
import com.exam.mapper.AnExamRecordMapper;
import com.exam.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 主观题阅卷台：待阅列表、逐题给分、合分。
 */
@Service
@RequiredArgsConstructor
public class GradingService {

    private final AnExamRecordMapper recordMapper;
    private final AnAnswerItemMapper answerItemMapper;
    private final SysUserMapper userMapper;
    private final ScoreService scoreService;
    private final AttemptService attemptService;
    private final LogService logService;

    public PageResult<Map<String, Object>> pending(long page, long size, Long examId, String keyword) {
        LambdaQueryWrapper<AnExamRecord> wrapper = new LambdaQueryWrapper<AnExamRecord>()
                .eq(AnExamRecord::getStatus, Dicts.RecordStatus.WAIT_REVIEW)
                .eq(examId != null, AnExamRecord::getExamId, examId)
                .orderByAsc(AnExamRecord::getId);
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
        List<AnExamRecord> records = result.getRecords();
        Map<Long, SysUser> userById = records.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(records.stream().map(AnExamRecord::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(SysUser::getId, Function.identity()));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (AnExamRecord record : records) {
            ExExam exam = attemptService.requireExam(record.getExamId());
            SysUser student = userById.get(record.getUserId());
            long pendingCount = answerItemMapper.selectCount(new LambdaQueryWrapper<AnAnswerItem>()
                    .eq(AnAnswerItem::getRecordId, record.getId())
                    .eq(AnAnswerItem::getReviewStatus, Dicts.ReviewStatus.WAIT_REVIEW));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("recordId", record.getId());
            row.put("examId", exam.getId());
            row.put("examTitle", exam.getTitle());
            row.put("studentId", record.getUserId());
            row.put("studentName", student == null ? "已删除用户" : student.getRealName());
            row.put("username", student == null ? "" : student.getUsername());
            row.put("className", student == null ? "" : student.getClassName());
            row.put("submitTime", record.getSubmitTime());
            row.put("objectiveScore", record.getObjectiveScore());
            row.put("pendingCount", pendingCount);
            rows.add(row);
        }
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /** 待阅总量，用于阅卷台角标 */
    public long pendingCount(Long examId) {
        return recordMapper.selectCount(new LambdaQueryWrapper<AnExamRecord>()
                .eq(AnExamRecord::getStatus, Dicts.RecordStatus.WAIT_REVIEW)
                .eq(examId != null, AnExamRecord::getExamId, examId));
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> grade(GradingForm form) {
        AnExamRecord record = attemptService.requireRecord(form.getRecordId());
        if (!Dicts.RecordStatus.submitted(record.getStatus())) {
            throw BizException.of(ErrorCode.GRADING_NOT_PENDING, "该答卷还在作答中，暂不能阅卷");
        }
        Map<Long, AnAnswerItem> items = answerItemMapper.selectList(new LambdaQueryWrapper<AnAnswerItem>()
                        .eq(AnAnswerItem::getRecordId, form.getRecordId())).stream()
                .collect(Collectors.toMap(AnAnswerItem::getId, Function.identity()));

        int graded = 0;
        Long reviewer = LoginUser.userId();
        if (form.getItems() != null) {
            for (GradingForm.Item line : form.getItems()) {
                AnAnswerItem item = items.get(line.getAnswerItemId());
                if (item == null) {
                    throw BizException.param("答题明细 " + line.getAnswerItemId() + " 不属于这份答卷");
                }
                if (item.getReviewStatus() != null && item.getReviewStatus() == Dicts.ReviewStatus.AUTO_JUDGED) {
                    throw BizException.of(ErrorCode.GRADING_NOT_PENDING, "客观题由系统判分，不能人工修改");
                }
                BigDecimal full = item.getFullScore() == null ? BigDecimal.ZERO : item.getFullScore();
                BigDecimal score = line.getScore() == null ? BigDecimal.ZERO : line.getScore();
                if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(full) > 0) {
                    throw BizException.of(ErrorCode.GRADING_SCORE_INVALID,
                            "得分需在 0 到 " + full.stripTrailingZeros().toPlainString() + " 分之间");
                }
                score = score.setScale(1, RoundingMode.HALF_UP);

                AnAnswerItem update = new AnAnswerItem();
                update.setId(item.getId());
                update.setScore(score);
                update.setReviewStatus(Dicts.ReviewStatus.REVIEWED);
                update.setReviewerId(reviewer);
                update.setReviewComment(line.getComment());
                int flag = score.compareTo(full) >= 0 ? Dicts.CorrectFlag.RIGHT
                        : (score.compareTo(BigDecimal.ZERO) > 0 ? Dicts.CorrectFlag.HALF : Dicts.CorrectFlag.WRONG);
                update.setCorrectFlag(flag);
                answerItemMapper.updateById(update);
                graded++;
            }
        }

        scoreService.recalcTotals(form.getRecordId());
        AnExamRecord after = attemptService.requireRecord(form.getRecordId());
        long remaining = answerItemMapper.selectCount(new LambdaQueryWrapper<AnAnswerItem>()
                .eq(AnAnswerItem::getRecordId, form.getRecordId())
                .eq(AnAnswerItem::getReviewStatus, Dicts.ReviewStatus.WAIT_REVIEW));
        logService.record("GRADING", "an_exam_record:" + form.getRecordId(),
                "本次批改 " + graded + " 题，剩余待阅 " + remaining + " 题");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recordId", after.getId());
        data.put("gradedCount", graded);
        data.put("remainingCount", remaining);
        data.put("status", after.getStatus());
        data.put("objectiveScore", after.getObjectiveScore());
        data.put("subjectiveScore", after.getSubjectiveScore());
        data.put("totalScore", after.getTotalScore());
        return data;
    }
}
