package com.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 表：an_exam_record
@Data
@TableName("an_exam_record")
public class AnExamRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long examId;

    private Long userId;

    private Integer attemptNo;

    private LocalDateTime startTime;

    private LocalDateTime deadlineTime;

    private LocalDateTime submitTime;

    private String questionOrder;

    private BigDecimal objectiveScore;

    private BigDecimal subjectiveScore;

    private BigDecimal totalScore;

    private Integer passFlag;

    private Integer status;

    private Integer switchCount;

    private String clientIp;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
