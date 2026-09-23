package com.exam.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 试卷新增/编辑入参。status 不在这里改，走发布/归档接口。
 */
@Data
public class PaperForm {

    private Long id;

    @NotBlank(message = "试卷名称不能为空")
    @Size(max = 100, message = "标题不能超过 100 字")
    private String title;

    @Size(max = 500, message = "说明不能超过 500 字")
    private String description;

    private Long categoryId;

    @DecimalMin(value = "0", message = "及格线不能为负")
    @DecimalMax(value = "1000", message = "及格线超出范围")
    private BigDecimal passScore;

    @Min(value = 1, message = "建议时长至少 1 分钟")
    @Max(value = 600, message = "建议时长不能超过 600 分钟")
    private Integer suggestMinutes;

    private Integer status;
}
