package com.exam.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 答卷明细：阅卷台与学生成绩单共用，showAnswer=true 时才带标准答案与解析。
 */
@Data
public class RecordDetailVo {

    private Long recordId;
    private Long examId;
    private String examTitle;
    private Long paperId;
    private String paperTitle;

    private Long studentId;
    private String studentName;
    private String username;
    private String className;

    private LocalDateTime startTime;
    private LocalDateTime submitTime;
    private LocalDateTime deadlineTime;
    private Integer usedSeconds;

    private Integer status;
    private String statusName;
    private BigDecimal objectiveScore;
    private BigDecimal subjectiveScore;
    private BigDecimal totalScore;
    private BigDecimal fullScore;
    private BigDecimal passScore;
    private Integer passFlag;
    private Integer switchCount;
    private Integer scorePublished;

    private List<Item> items;

    @Data
    public static class Item {
        private Long answerItemId;
        private Long paperQuestionId;
        private Long questionId;
        private Integer sortNo;
        @JsonProperty("qType")
        private Integer qType;
        @JsonProperty("qTypeName")
        private String qTypeName;
        private String content;
        private List<ExamPaperVo.Option> options;
        private Integer blankCount;
        private List<String> userAnswer;
        /** 仅教师/管理员，或成绩已发布时的学生可见 */
        private List<String> standardAnswer;
        private String analysis;
        private BigDecimal fullScore;
        private BigDecimal score;
        private Integer correctFlag;
        private String correctFlagName;
        private Integer reviewStatus;
        private String reviewComment;
    }
}
