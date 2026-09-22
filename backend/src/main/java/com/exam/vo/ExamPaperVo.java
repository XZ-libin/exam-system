package com.exam.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 学生进入考试时拿到的试卷：不含答案与解析。
 */
@Data
public class ExamPaperVo {

    private Long recordId;
    private Long examId;
    private String examTitle;
    private Long paperId;
    private String paperTitle;
    private BigDecimal totalScore;
    private BigDecimal passScore;
    private Integer questionCount;
    private Integer durationMinutes;

    private LocalDateTime startTime;
    private LocalDateTime deadlineTime;
    /** 服务端剩余秒数，前端倒计时只用于展示 */
    private Long remainingSeconds;

    private Integer attemptNo;
    private Integer switchCount;
    private Integer switchLimit;
    private Boolean resumed;
    private Boolean submitted;

    private List<Question> questions;
    /** paperQuestionId -> 已保存的答案 */
    private Map<Long, List<String>> answers;

    @Data
    public static class Question {
        private Long paperQuestionId;
        private Long questionId;
        @JsonProperty("qType")
        private Integer qType;
        @JsonProperty("qTypeName")
        private String qTypeName;
        private String content;
        private BigDecimal score;
        private Integer sortNo;
        /** 填空空的个数，前端据此渲染多个输入框 */
        private Integer blankCount;
        private List<Option> options;
    }

    @Data
    public static class Option {
        private String key;
        private String text;
    }
}
