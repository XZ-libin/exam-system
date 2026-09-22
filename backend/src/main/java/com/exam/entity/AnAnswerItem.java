package com.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 表：an_answer_item
@Data
@TableName("an_answer_item")
public class AnAnswerItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recordId;

    private Long paperQuestionId;

    private Long questionId;

    private String userAnswer;

    private Integer correctFlag;

    private BigDecimal score;

    private BigDecimal fullScore;

    private Integer reviewStatus;

    private Long reviewerId;

    private String reviewComment;

    private Integer sortNo;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
