package com.exam.service;

import com.exam.common.Dicts;
import com.exam.common.ExamProperties;
import com.exam.common.JsonUtil;
import com.exam.entity.ExPaperQuestion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 判分规则单测：客观题边界都在这里，避免成绩算错这种答辩现场翻车的问题。
 */
class JudgeServiceTest {

    private final JudgeService judgeService = new JudgeService(new ExamProperties());

    private ExPaperQuestion question(int type, List<String> answer, String score) {
        ExPaperQuestion entity = new ExPaperQuestion();
        entity.setQType(type);
        entity.setAnswer(JsonUtil.write(answer));
        entity.setScore(new BigDecimal(score));
        return entity;
    }

    private void assertScore(JudgeService.Judged judged, int flag, String score) {
        assertEquals(flag, judged.correctFlag());
        assertEquals(0, new BigDecimal(score).compareTo(judged.score()));
    }

    @Test
    @DisplayName("单选题：字母一致才得分，忽略大小写与空格")
    void singleChoice() {
        var item = question(Dicts.QType.SINGLE, List.of("B"), "4.0");
        assertScore(judgeService.judge(item, List.of("B")), Dicts.CorrectFlag.RIGHT, "4.0");
        assertScore(judgeService.judge(item, List.of(" b ")), Dicts.CorrectFlag.RIGHT, "4.0");
        assertScore(judgeService.judge(item, List.of("A")), Dicts.CorrectFlag.WRONG, "0");
    }

    @Test
    @DisplayName("判断题：T/F 单值")
    void judgeTrueFalse() {
        var item = question(Dicts.QType.JUDGE, List.of("T"), "2.0");
        assertScore(judgeService.judge(item, List.of("T")), Dicts.CorrectFlag.RIGHT, "2.0");
        assertScore(judgeService.judge(item, List.of("F")), Dicts.CorrectFlag.WRONG, "0");
    }

    @Test
    @DisplayName("多选题：漏选且无错选得半分，选错不得分")
    void multipleChoice() {
        var item = question(Dicts.QType.MULTIPLE, List.of("A", "B", "D"), "6.0");
        assertScore(judgeService.judge(item, List.of("D", "B", "A")), Dicts.CorrectFlag.RIGHT, "6.0");
        assertScore(judgeService.judge(item, List.of("A", "B")), Dicts.CorrectFlag.HALF, "3.0");
        assertScore(judgeService.judge(item, List.of("A", "C")), Dicts.CorrectFlag.WRONG, "0");
        assertScore(judgeService.judge(item, List.of("A")), Dicts.CorrectFlag.HALF, "3.0");
    }

    @Test
    @DisplayName("填空题：按空均分，一个空可用 | 写多个可接受答案")
    void blankQuestions() {
        var item = question(Dicts.QType.BLANK, List.of("北京|京", "上海|沪"), "8.0");
        assertScore(judgeService.judge(item, List.of("北京", "沪")), Dicts.CorrectFlag.RIGHT, "8.0");
        assertScore(judgeService.judge(item, List.of("京", "天津")), Dicts.CorrectFlag.HALF, "4.0");
        assertScore(judgeService.judge(item, List.of("南京", "苏州")), Dicts.CorrectFlag.WRONG, "0");
        assertScore(judgeService.judge(item, List.of("北京")), Dicts.CorrectFlag.HALF, "4.0");
    }

    @Test
    @DisplayName("简答题不自动判分，留给阅卷台")
    void essayNeedsReview() {
        var item = question(Dicts.QType.ESSAY, List.of("参考答案要点"), "20.0");
        var judged = judgeService.judge(item, List.of("学生写了一堆"));
        assertTrue(judged.needReview());
        assertNull(judged.score());
    }

    @Test
    @DisplayName("未作答一律 0 分，不抛异常")
    void emptyAnswer() {
        var item = question(Dicts.QType.SINGLE, List.of("A"), "4.0");
        assertScore(judgeService.judge(item, List.of()), Dicts.CorrectFlag.WRONG, "0");
        assertScore(judgeService.judge(item, null), Dicts.CorrectFlag.WRONG, "0");
        assertScore(judgeService.judge(item, List.of("  ")), Dicts.CorrectFlag.WRONG, "0");
    }

    @Test
    @DisplayName("漏选半分开关关闭后，漏选直接 0 分")
    void multipleHalfScoreDisabled() {
        var strict = new ExamProperties();
        strict.setMultipleHalfScore(false);
        var service = new JudgeService(strict);
        var item = question(Dicts.QType.MULTIPLE, List.of("A", "B"), "6.0");
        assertScore(service.judge(item, List.of("A")), Dicts.CorrectFlag.WRONG, "0");
    }
}
