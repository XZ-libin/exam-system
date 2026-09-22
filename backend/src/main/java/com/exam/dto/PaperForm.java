package com.exam.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 试卷新增/编辑入参。status 不在这里改，走发布/归档接口。
 */
@Data
public class PaperForm {

    private Long id;

    @NotBlank(message = "试卷名称不能为空")
    private String title;

    private String description;

    private Long categoryId;

    private BigDecimal passScore;

    private Integer suggestMinutes;

    private Integer status;
}
