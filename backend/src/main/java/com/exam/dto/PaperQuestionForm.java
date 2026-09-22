package com.exam.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 人工组卷时往试卷里加一道题：只传题目 id 与本卷分值，其余字段由后端从题库取快照。
 */
@Data
public class PaperQuestionForm {

    private Long questionId;

    /** 留空则沿用题库的默认分值 */
    private BigDecimal score;

    private Integer sortNo;
}
