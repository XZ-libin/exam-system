package com.exam.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @Size(max = 100, message = "考试名称不能超过 100 字")
    private String title;

    private String description;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    /** 答题时长（分钟） */
    @NotNull(message = "答题时长不能为空")
    @Min(value = 1, message = "答题时长至少 1 分钟")
    @Max(value = 600, message = "答题时长不能超过 600 分钟")
    private Integer durationMinutes;

    @Min(value = 0, message = "迟到分钟数不能为负")
    @Max(value = 600, message = "迟到分钟数不能超过考试时长")
    private Integer lateMinutes;

    @Min(value = 1, message = "作答次数至少 1 次")
    @Max(value = 10, message = "作答次数不能超过 10 次")
    private Integer maxAttempts;

    /** 1全部学生 2指定班级 3指定名单，对应 Dicts.Audience */
    private Integer audienceType;

    /** audienceType=2 时必填 */
    private List<String> classNames;

    @Min(value = 0, message = "切屏上限不能为负")
    @Max(value = 100, message = "切屏上限过大")
    private Integer switchLimit;

    private Integer shuffleQuestion;

    private Integer shuffleOption;

    private Integer status;
}
