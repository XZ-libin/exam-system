package com.exam.service;

import com.exam.common.BizException;
import com.exam.common.Dicts;
import com.exam.common.LoginUser;
import com.exam.mapper.StatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计分析：总览、单场考试、单题、本人成绩分析。
 * <p>
 * 分工：SQL 只做取数与口径过滤（已交卷 = status IN (1,2,3)，出分才参与均分），
 * 百分率、四舍五入、固定档位补 0 都在 Java 里做，统一 RoundingMode.HALF_UP。
 * 返回结构不新建 VO，直接给 LinkedHashMap，key 与前端契约一一对应。
 */
@Service
@RequiredArgsConstructor
public class StatService {

    /** 固定 5 档分数段，按百分率分档，缺档补 0 */
    private static final String[] SECTION_LABELS = {"0-59", "60-69", "70-79", "80-89", "90-100"};
    private static final int[] QUESTION_TYPES = {Dicts.QType.SINGLE, Dicts.QType.MULTIPLE, Dicts.QType.JUDGE,
            Dicts.QType.BLANK, Dicts.QType.ESSAY};
    private static final int[] DIFFICULTIES = {1, 2, 3, 4, 5};
    /** 鉴别度：高低分组各取 27%，每组不足 3 人就不算 */
    private static final double GROUP_RATIO = 0.27D;
    private static final int MIN_GROUP_SIZE = 3;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final StatMapper statMapper;

    // ------------------------------------------------------------ 总览

    public Map<String, Object> overview() {
        Map<String, Object> counts = statMapper.overviewCounts();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("examCount", longOf(counts, "examCount"));
        data.put("paperCount", longOf(counts, "paperCount"));
        data.put("questionCount", longOf(counts, "questionCount"));
        data.put("accountCount", longOf(counts, "accountCount"));
        // 已交卷份数（含待阅卷与超时强制交卷），不是人数
        data.put("attemptCount", longOf(counts, "attemptCount"));
        data.put("avgScore", round1(doubleOf(counts, "avgScore")));
        data.put("passRate", percent(longOf(counts, "passCount"), longOf(counts, "scoredCount")));
        data.put("scoreSections", sectionsOf(null));
        data.put("typeDifficulty", typeDifficulty());
        data.put("recentExams", recentExams());
        return data;
    }

    /** 五档分数段：SQL 返回单行 seg0..seg4，这里补 label 并保证顺序与缺档 0 */
    private List<Map<String, Object>> sectionsOf(Long examId) {
        Map<String, Object> row = statMapper.scoreSections(examId);
        List<Map<String, Object>> list = new ArrayList<>(SECTION_LABELS.length);
        for (int i = 0; i < SECTION_LABELS.length; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("label", SECTION_LABELS[i]);
            item.put("count", longOf(row, "seg" + i));
            list.add(item);
        }
        return list;
    }

    private Map<String, Object> typeDifficulty() {
        Map<Integer, Integer> typeCount = countBy(statMapper.questionTypeCounts(), "qType");
        Map<Integer, Integer> diffCount = countBy(statMapper.questionDifficultyCounts(), "difficulty");

        List<Map<String, Object>> questionTypes = new ArrayList<>(QUESTION_TYPES.length);
        for (int type : QUESTION_TYPES) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("qType", type);
            item.put("qTypeName", Dicts.QType.name(type));
            item.put("count", typeCount.getOrDefault(type, 0));
            questionTypes.add(item);
        }
        List<Map<String, Object>> difficulties = new ArrayList<>(DIFFICULTIES.length);
        for (int difficulty : DIFFICULTIES) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("difficulty", difficulty);
            item.put("count", diffCount.getOrDefault(difficulty, 0));
            difficulties.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("questionTypes", questionTypes);
        data.put("difficulties", difficulties);
        return data;
    }

    private List<Map<String, Object>> recentExams() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : nullSafe(statMapper.recentExams())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("examId", longOf(row, "examId"));
            item.put("title", strOf(row, "title"));
            item.put("endTime", strOf(row, "endTime"));
            item.put("submitCount", intOf(row, "submitCount"));
            item.put("avgRate", round1(doubleOf(row, "avgRate")));
            list.add(item);
        }
        return list;
    }

    // ------------------------------------------------------------ 单场考试

    public Map<String, Object> examDetail(Long examId) {
        Map<String, Object> summary = statMapper.examSummary(examId);
        if (summary == null || summary.get("examId") == null) {
            throw BizException.notFound("考试");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("examId", longOf(summary, "examId"));
        data.put("title", strOf(summary, "title"));
        data.put("paperTitle", strOf(summary, "paperTitle"));
        data.put("totalScore", decimalOf(summary, "totalScore"));
        data.put("passScore", decimalOf(summary, "passScore"));
        data.put("expectedCount", longOf(summary, "expectedCount"));
        // 参加人数 = 建过答卷的学生数（含还在作答的）
        data.put("joinCount", longOf(summary, "joinCount"));
        data.put("submitCount", longOf(summary, "submitCount"));
        data.put("avgScore", round1(doubleOf(summary, "avgScore")));
        data.put("maxScore", round1(doubleOf(summary, "maxScore")));
        data.put("minScore", round1(doubleOf(summary, "minScore")));
        // 整卷满分固定，得分率 = 平均分 / 满分；及格率 = 及格份数 / 已判分份数
        BigDecimal paperTotal = decimalOf(summary, "totalScore");
        data.put("avgRate", percent(decimalOf(summary, "avgScore"), paperTotal));
        data.put("passRate", percent(longOf(summary, "passCount"), longOf(summary, "scoredCount")));
        data.put("scoreSections", sectionsOf(examId));

        Map<Long, BigDecimal[]> groupCredits = groupCredits(examId);
        data.put("questionStats", questionStats(examId, groupCredits));
        data.put("classStats", classStats(examId));
        return data;
    }

    /** 高低分组每题正确率：key = 题目 id，value = {高分组正确率, 低分组正确率}；凑不满两组时返回空表 */
    private Map<Long, BigDecimal[]> groupCredits(Long examId) {
        List<Map<String, Object>> ranked = nullSafe(statMapper.examScoredRecords(examId));
        int size = (int) Math.round(ranked.size() * GROUP_RATIO);
        if (size < MIN_GROUP_SIZE || size * 2 > ranked.size()) {
            return Map.of();
        }
        List<Long> highIds = new ArrayList<>(size);
        List<Long> lowIds = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            highIds.add(longOf(ranked.get(i), "recordId"));
            lowIds.add(longOf(ranked.get(ranked.size() - 1 - i), "recordId"));
        }
        Map<Long, BigDecimal[]> credits = new HashMap<>();
        for (Map<String, Object> row : nullSafe(statMapper.examGroupCredits(highIds, lowIds))) {
            Long questionId = longOf(row, "paperQuestionId");
            BigDecimal credit = decimalOf(row, "credit");
            if (questionId == null || credit == null) {
                continue;
            }
            BigDecimal[] pair = credits.computeIfAbsent(questionId, key -> new BigDecimal[2]);
            Integer grp = intOf(row, "grp");
            pair[grp != null && grp == 1 ? 0 : 1] = credit;
        }
        return credits;
    }

    private List<Map<String, Object>> questionStats(Long examId, Map<Long, BigDecimal[]> groupCredits) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : nullSafe(statMapper.examQuestionStats(examId))) {
            Integer qType = intOf(row, "qType");
            long answerCount = longOrZero(row, "answerCount");
            long correctCount = longOrZero(row, "correctCount");
            Long paperQuestionId = longOf(row, "paperQuestionId");

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("paperQuestionId", paperQuestionId);
            item.put("questionId", longOf(row, "questionId"));
            item.put("qType", qType);
            item.put("qTypeName", Dicts.QType.name(qType));
            item.put("brief", brief(strOf(row, "content")));
            item.put("fullScore", decimalOf(row, "fullScore"));
            item.put("answerCount", (int) answerCount);
            item.put("correctCount", (int) correctCount);
            // 口径与 v_question_accuracy 一致：correct_flag=1 才算答对
            item.put("accuracy", zeroIfNull(percent(correctCount, answerCount)));
            item.put("difficulty", intOf(row, "difficulty"));
            item.put("discrimination",
                    paperQuestionId == null ? null : discrimination(groupCredits.get(paperQuestionId)));
            list.add(item);
        }
        return list;
    }

    /** 鉴别度 = 高分组正确率 - 低分组正确率，任一组缺数据则为 null */
    private Double discrimination(BigDecimal[] pair) {
        if (pair == null || pair[0] == null || pair[1] == null) {
            return null;
        }
        return round(pair[0].subtract(pair[1]), 2);
    }

    private List<Map<String, Object>> classStats(Long examId) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : nullSafe(statMapper.examClassStats(examId))) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("className", strOf(row, "className"));
            item.put("count", (int) longOrZero(row, "cnt"));
            item.put("avgScore", round1(doubleOf(row, "avgScore")));
            item.put("passRate", percent(longOf(row, "passCount"), longOf(row, "cnt")));
            list.add(item);
        }
        return list;
    }

    // ------------------------------------------------------------ 单题

    public Map<String, Object> questionDetail(Long questionId) {
        Map<String, Object> row = statMapper.questionStat(questionId);
        if (row == null || row.get("questionId") == null) {
            throw BizException.notFound("题目");
        }
        long answerCount = longOrZero(row, "answerCount");
        long correctCount = longOrZero(row, "correctCount");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("questionId", longOf(row, "questionId"));
        data.put("content", strOf(row, "content"));
        data.put("qType", intOf(row, "qType"));
        data.put("qTypeName", Dicts.QType.name(intOf(row, "qType")));
        data.put("difficulty", intOf(row, "difficulty"));
        data.put("analysis", strOf(row, "analysis"));
        data.put("paperCount", intOf(row, "paperCount"));
        data.put("answerCount", (int) answerCount);
        data.put("correctCount", (int) correctCount);
        data.put("accuracy", zeroIfNull(percent(correctCount, answerCount)));
        return data;
    }

    // ------------------------------------------------------------ 本人分析

    public Map<String, Object> myAnalysis() {
        Long userId = LoginUser.userId();
        Map<String, Object> summary = statMapper.mySummary(userId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("examCount", intOf(summary, "examCount"));
        data.put("submitCount", intOf(summary, "submitCount"));
        data.put("avgRate", round1(doubleOf(summary, "avgRate")));
        data.put("passCount", intOf(summary, "passCount"));
        data.put("wrongCount", intOf(summary, "wrongCount"));
        data.put("trend", trend(userId));
        data.put("subjectAccuracy", accuracyRows(statMapper.mySubjectAccuracy(userId), "name", null));
        data.put("typeAccuracy", accuracyRows(statMapper.myTypeAccuracy(userId), "qType", "qTypeName"));
        data.put("difficultyRadar", difficultyRadar(userId));
        return data;
    }

    /** 逐次作答趋势：未出分的场次不会进来（SQL 已按 score_published=1 过滤），单份未判完时分数为 null */
    private List<Map<String, Object>> trend(Long userId) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : nullSafe(statMapper.myTrend(userId))) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("examId", longOf(row, "examId"));
            item.put("examTitle", strOf(row, "examTitle"));
            item.put("recordId", longOf(row, "recordId"));
            item.put("date", strOf(row, "date"));
            item.put("score", decimalOf(row, "score"));
            item.put("totalScore", decimalOf(row, "totalScore"));
            item.put("rate", round1(doubleOf(row, "rate")));
            item.put("passFlag", intOf(row, "passFlag"));
            list.add(item);
        }
        return list;
    }

    /** 正确率行：labelKey 是分组列名，qTypeName 非空时补题型名 */
    private List<Map<String, Object>> accuracyRows(List<Map<String, Object>> rows, String labelKey, String typeKey) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : nullSafe(rows)) {
            long answerCount = longOrZero(row, "answerCount");
            long correctCount = longOrZero(row, "correctCount");
            Map<String, Object> item = new LinkedHashMap<>();
            Object label = "name".equals(labelKey) ? strOf(row, labelKey) : intOf(row, labelKey);
            item.put(labelKey, label);
            if (typeKey != null) {
                item.put(typeKey, Dicts.QType.name(intOf(row, labelKey)));
            }
            item.put("answerCount", (int) answerCount);
            item.put("accuracy", zeroIfNull(percent(correctCount, answerCount)));
            list.add(item);
        }
        return list;
    }

    /** 难度雷达固定 5 档，缺档补 0 */
    private List<Map<String, Object>> difficultyRadar(Long userId) {
        Map<Integer, long[]> byDifficulty = new HashMap<>();
        for (Map<String, Object> row : nullSafe(statMapper.myDifficultyAccuracy(userId))) {
            Integer difficulty = intOf(row, "difficulty");
            if (difficulty != null) {
                byDifficulty.put(difficulty,
                        new long[]{longOrZero(row, "answerCount"), longOrZero(row, "correctCount")});
            }
        }
        List<Map<String, Object>> list = new ArrayList<>(DIFFICULTIES.length);
        for (int difficulty : DIFFICULTIES) {
            long[] pair = byDifficulty.getOrDefault(difficulty, new long[]{0L, 0L});
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("difficulty", difficulty);
            item.put("label", "难度" + difficulty);
            item.put("answerCount", (int) pair[0]);
            item.put("accuracy", zeroIfNull(percent(pair[1], pair[0])));
            list.add(item);
        }
        return list;
    }

    // ------------------------------------------------------------ 小工具

    private Map<Integer, Integer> countBy(List<Map<String, Object>> rows, String key) {
        Map<Integer, Integer> map = new HashMap<>();
        for (Map<String, Object> row : nullSafe(rows)) {
            Integer value = intOf(row, key);
            if (value != null) {
                map.put(value, (int) longOrZero(row, "cnt"));
            }
        }
        return map;
    }

    /** 题干前 30 字，压掉换行，图表里当标题用 */
    private static String brief(String content) {
        if (content == null) {
            return "";
        }
        String text = content.replaceAll("\\s+", " ").trim();
        return text.length() <= 30 ? text : text.substring(0, 30);
    }

    /** 百分率，0-100 一位小数；分母为 0 或缺数据时返回 null */
    private static Double percent(Number part, Number whole) {
        if (part == null || whole == null) {
            return null;
        }
        BigDecimal denominator = toDecimal(whole);
        if (denominator.signum() == 0) {
            return null;
        }
        return toDecimal(part).multiply(HUNDRED).divide(denominator, 1, RoundingMode.HALF_UP).doubleValue();
    }

    private static Double round1(Double value) {
        return round(value == null ? null : BigDecimal.valueOf(value), 1);
    }

    private static Double round(BigDecimal value, int scale) {
        return value == null ? null : value.setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private static Double zeroIfNull(Double value) {
        return value == null ? 0D : value;
    }

    private static BigDecimal toDecimal(Number value) {
        return value instanceof BigDecimal decimal
                ? decimal : new BigDecimal(value.toString());
    }

    private static List<Map<String, Object>> nullSafe(List<Map<String, Object>> rows) {
        return rows == null ? List.of() : rows;
    }

    private static Object value(Map<String, Object> row, String key) {
        return row == null ? null : row.get(key);
    }

    private static Long longOf(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value instanceof Number number ? number.longValue() : null;
    }

    /** COUNT(...) 恒有值，取不到就按 0 算，省掉逐处判空 */
    private static long longOrZero(Map<String, Object> row, String key) {
        Long value = longOf(row, key);
        return value == null ? 0L : value;
    }

    private static Integer intOf(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value instanceof Number number ? number.intValue() : null;
    }

    private static Double doubleOf(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private static BigDecimal decimalOf(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null ? null : toDecimal((Number) value);
    }

    private static String strOf(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null ? null : String.valueOf(value);
    }
}
