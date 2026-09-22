package com.exam.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 表：ex_paper_question
@Data
@TableName("ex_paper_question")
public class ExPaperQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long paperId;

    private Long questionId;

    @JsonProperty("qType")

    private Integer qType;

    private String content;

    private String options;

    private String answer;

    private String analysis;

    private Integer difficulty;

    private BigDecimal score;

    private Integer sortNo;

    private LocalDateTime createTime;
}
