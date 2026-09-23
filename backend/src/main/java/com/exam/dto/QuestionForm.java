package com.exam.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 题目新增/修改入参。选项与答案在库里是 JSON 列，入参用结构化对象，转换交给 JsonUtil。
 */
@Data
public class QuestionForm {

    private Long id;

    @NotNull(message = "所属分类不能为空")
    private Long categoryId;

    /** 1单选 2多选 3判断 4填空 5简答，见 Dicts.QType */
    @NotNull(message = "题型不能为空")
    @JsonProperty("qType")
    private Integer qType;

    @NotBlank(message = "题干不能为空")
    @Size(max = 5000, message = "题干不能超过 5000 字")
    private String content;

    /** 单选/多选必填；判断、填空、简答留空 */
    private List<OptionItem> options;

    /** 单选["A"] 多选["A","C"] 判断["T"] 填空每个空一条 简答一条参考答案 */
    @NotNull(message = "标准答案不能为空")
    private List<String> answer;

    @Size(max = 2000, message = "解析不能超过 2000 字")
    private String analysis;

    /** 1最易 - 5最难，不传按 3 */
    private Integer difficulty;

    /** 默认分值，不传按 2.0 */
    @DecimalMin(value = "0.5", message = "每题分值不能低于 0.5")
    @DecimalMax(value = "100", message = "每题分值不能超过 100")
    private BigDecimal score;

    /** 0停用 1启用，不传按 1 */
    private Integer status;

    @Data
    public static class OptionItem {
        private String key;
        private String text;
    }
}
