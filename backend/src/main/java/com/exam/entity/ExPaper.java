package com.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 表：ex_paper
@Data
@TableName("ex_paper")
public class ExPaper {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String description;

    private Long categoryId;

    private BigDecimal totalScore;

    private BigDecimal passScore;

    private Integer suggestMinutes;

    private Integer questionCount;

    private Integer buildType;

    private Integer status;

    private Long creatorId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}
