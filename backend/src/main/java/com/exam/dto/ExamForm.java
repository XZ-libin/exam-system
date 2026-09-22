package com.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 考试场次新增/编辑入参。
 */
@Data
public class ExamForm {

    private Long id;

    @NotNull(message = "请选择试卷")
    private Long paperId;

    @NotBlank(message = "考试名称不能为空")
    private String title;

    private String description;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    /** 答题时长（分钟） */
    @NotNull(message = "答题时长不能为空")
    private Integer durationMinutes;

    private Integer lateMinutes;

    private Integer maxAttempts;

    /** 1全部学生 2指定班级 3指定名单，对应 Dicts.Audience */
    private Integer audienceType;

    /** audienceType=2 时必填 */
    private List<String> classNames;

    private Integer switchLimit;

    private Integer shuffleQuestion;

    private Integer shuffleOption;

    private Integer status;
}
