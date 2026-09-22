package com.exam.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 统计分析专用查询：只做聚合，不映射任何实体，所以不继承 BaseMapper；
 * 放在 com.exam.mapper 包下由 ExamApplication 的 @MapperScan 扫描到。
 * <p>
 * SQL 全部在 resources/mapper/StatMapper.xml。列别名一律写成 camelCase 且不带下划线，
 * 这样 map-underscore-to-camel-case=true 时（MyBatis-Plus 会连 Map 的 key 一起转换）
 * key 也不会被改坏，前端契约里的 key 就是这里写的 key。
 * 聚合结果只做取数与判分口径过滤，四舍五入统一在 StatService 里用 HALF_UP 完成。
 */
public interface StatMapper {

    // -------------------------------------------------------- 总览

    /** 题量 / 卷量 / 账号数 / 已交卷份数 / 平均分 / 及格份数，单行 */
    Map<String, Object> overviewCounts();

    /** 五档分数段计数（单行 seg0..seg4 五列，缺档为 0）；examId 为 null 时统计全部考试 */
    Map<String, Object> scoreSections(@Param("examId") Long examId);

    /** 按题型统计题目数 */
    List<Map<String, Object>> questionTypeCounts();

    /** 按难度统计题目数 */
    List<Map<String, Object>> questionDifficultyCounts();

    /** 最近 5 场考试（按 end_time 倒序）：交卷人数与平均得分率 */
    List<Map<String, Object>> recentExams();

    // -------------------------------------------------------- 单场考试

    /** 本场考试的概要统计：应交/参加/交卷/平均分/最高/最低/及格数，单行；考试不存在时返回 null */
    Map<String, Object> examSummary(@Param("examId") Long examId);

    /** 每题作答数与正确数，按试卷题号 sort_no 排序 */
    List<Map<String, Object>> examQuestionStats(@Param("examId") Long examId);

    /** 本场已交卷且已出分的答卷，按百分率倒序，用于高低分组 */
    List<Map<String, Object>> examScoredRecords(@Param("examId") Long examId);

    /** 高低分组里每题的正确率（correct_flag=1 记 1，2 记 0.5），按 题 + 组 聚合 */
    List<Map<String, Object>> examGroupCredits(@Param("highIds") List<Long> highIds,
                                               @Param("lowIds") List<Long> lowIds);

    /** 按班级统计本场已判分答卷：份数、平均分、及格份数 */
    List<Map<String, Object>> examClassStats(@Param("examId") Long examId);

    // -------------------------------------------------------- 单题

    /** 题目本身 + 跨所有引用它的试卷快照的作答统计，单行；题目不存在或已删除时返回 null */
    Map<String, Object> questionStat(@Param("questionId") Long questionId);

    // -------------------------------------------------------- 我的分析（只看本人，只看已发布成绩的场次）

    /** 参加场次数、交卷份数、平均得分率、及格份数、错题数，单行 */
    Map<String, Object> mySummary(@Param("userId") Long userId);

    /** 逐次作答趋势，按时间正序 */
    List<Map<String, Object>> myTrend(@Param("userId") Long userId);

    /** 按一级分类（科目）的正确率 */
    List<Map<String, Object>> mySubjectAccuracy(@Param("userId") Long userId);

    /** 按题型的正确率 */
    List<Map<String, Object>> myTypeAccuracy(@Param("userId") Long userId);

    /** 按难度的正确率 */
    List<Map<String, Object>> myDifficultyAccuracy(@Param("userId") Long userId);
}
