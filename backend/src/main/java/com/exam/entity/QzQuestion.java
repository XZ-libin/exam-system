package com.exam.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 表：qz_question
@Data
@TableName("qz_question")
public class QzQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long categoryId;

    @JsonProperty("qType")

    private Integer qType;

    private String content;

    private String options;

    private String answer;

    private String analysis;

    private Integer difficulty;

    private BigDecimal score;

    private Integer status;

    private Long creatorId;

    private Integer useCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}
