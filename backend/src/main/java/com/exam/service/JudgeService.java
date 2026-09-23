package com.exam.service;


import com.exam.common.Dicts;
import com.exam.common.ExamProperties;
import com.exam.common.JsonUtil;
import com.exam.entity.ExPaperQuestion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 客观题自动判分。简答题返回 null，交给阅卷台。
 * 这里不放任何数据库访问，便于用单测覆盖各种边界。
 */
@Service
@RequiredArgsConstructor
public class JudgeService {

    private final ExamProperties properties;

    /**
     * @param snapshot  试卷题目快照（含答案与满分）
     * @param userAnswer 学生答案，统一是字符串数组
     */
    public Judged judge(ExPaperQuestion snapshot, List<String> userAnswer) {
        BigDecimal full = snapshot.getScore() == null ? BigDecimal.ZERO : snapshot.getScore();
        List<String> given = normalize(userAnswer);
        List<String> standard = JsonUtil.readStringList(snapshot.getAnswer()).stream()
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        int type = snapshot.getQType() == null ? 0 : snapshot.getQType();

        if (type == Dicts.QType.ESSAY) {
            // 简答题一律转人工，留空也算：否则它会被当成客观题记 0 分，永远进不了阅卷台
            return new Judged(null, null, full);
        }
        if (given.isEmpty()) {
            return new Judged(Dicts.CorrectFlag.WRONG, BigDecimal.ZERO, full);
        }
        return switch (type) {
            case Dicts.QType.SINGLE, Dicts.QType.JUDGE -> judgeExact(standard, given, full);
            case Dicts.QType.MULTIPLE -> judgeMultiple(standard, given, full);
            case Dicts.QType.BLANK -> judgeBlanks(standard, given, full);
            default -> new Judged(Dicts.CorrectFlag.WRONG, BigDecimal.ZERO, full);
        };
    }

    private Judged judgeExact(List<String> standard, List<String> given, BigDecimal full) {
        if (standard.isEmpty()) {
            return new Judged(Dicts.CorrectFlag.WRONG, BigDecimal.ZERO, full);
        }
        boolean right = given.size() == 1 && given.get(0).equalsIgnoreCase(standard.get(0));
        return new Judged(right ? Dicts.CorrectFlag.RIGHT : Dicts.CorrectFlag.WRONG,
                right ? full : BigDecimal.ZERO, full);
    }

    private Judged judgeMultiple(List<String> standard, List<String> given, BigDecimal full) {
        Set<String> right = new HashSet<>(upper(standard));
        Set<String> picked = new HashSet<>(upper(given));
        boolean hasWrongChoice = picked.stream().anyMatch(k -> !right.contains(k));
        if (!hasWrongChoice && picked.equals(right)) {
            return new Judged(Dicts.CorrectFlag.RIGHT, full, full);
        }
        if (!hasWrongChoice && properties.isMultipleHalfScore()) {
            return new Judged(Dicts.CorrectFlag.HALF, half(full), full);
        }
        return new Judged(Dicts.CorrectFlag.WRONG, BigDecimal.ZERO, full);
    }

    /**
     * 填空题按空比对：参考答案里用 | 表示同一个空的多个可接受答案，
     * 例如 ["北京|京", "上海|沪"] 表示两个空。得分按答对的空数均分。
     */
    private Judged judgeBlanks(List<String> standard, List<String> given, BigDecimal full) {
        if (standard.isEmpty()) {
            return new Judged(Dicts.CorrectFlag.WRONG, BigDecimal.ZERO, full);
        }
        int correctBlank = 0;
        int compareCount = Math.min(standard.size(), given.size());
        for (int i = 0; i < compareCount; i++) {
            Set<String> accepts = Arrays.stream(standard.get(i).split("\\|"))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(String::toUpperCase).collect(Collectors.toSet());
            if (accepts.contains(given.get(i).toUpperCase())) {
                correctBlank++;
            }
        }
        if (correctBlank == 0) {
            return new Judged(Dicts.CorrectFlag.WRONG, BigDecimal.ZERO, full);
        }
        BigDecimal score = full.multiply(BigDecimal.valueOf(correctBlank))
                .divide(BigDecimal.valueOf(standard.size()), 1, RoundingMode.HALF_UP);
        boolean allRight = correctBlank == standard.size() && given.size() == standard.size();
        return new Judged(allRight ? Dicts.CorrectFlag.RIGHT : Dicts.CorrectFlag.HALF, score, full);
    }

    /** 学生答案允许提交 "A" 这种单个字符串，统一成数组 */
    public List<String> normalize(List<String> answers) {
        if (answers == null) {
            return List.of();
        }
        return answers.stream()
                .filter(s -> s != null && !s.trim().isEmpty())
                .map(String::trim)
                .toList();
    }

    private List<String> upper(List<String> values) {
        return values.stream().map(String::toUpperCase).toList();
    }

    private BigDecimal half(BigDecimal full) {
        return full.divide(BigDecimal.valueOf(2), 1, RoundingMode.HALF_UP);
    }

    /**
     * 判分结果。score 为 null 表示这题需要人工阅卷。
     */
    public record Judged(Integer correctFlag, BigDecimal score, BigDecimal fullScore) {

        public boolean needReview() {
            return score == null;
        }
    }
}
