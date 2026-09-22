package com.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

// 表：ex_exam
@Data
@TableName("ex_exam")
public class ExExam {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long paperId;

    private String title;

    private String description;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer durationMinutes;

    private Integer lateMinutes;

    private Integer maxAttempts;

    private Integer audienceType;

    private Integer switchLimit;

    private Integer shuffleQuestion;

    private Integer shuffleOption;

    private Integer scorePublished;

    private Integer status;

    private Long creatorId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}
