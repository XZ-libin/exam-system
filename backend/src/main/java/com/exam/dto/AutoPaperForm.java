package com.exam.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 规则组卷入参：一条规则 = 某个题型按难度抽若干道。
 */
@Data
public class AutoPaperForm {

    @NotBlank(message = "试卷名称不能为空")
    private String title;

    private String description;

    private Long categoryId;

    private BigDecimal passScore;

    private Integer suggestMinutes;

    /** @Valid 让 Rule 里的 @NotNull 生效 */
    @Valid
    private List<Rule> rules;

    @Data
    public static class Rule {

        /** 题型，对应 Dicts.QType */
        @NotNull(message = "题型不能为空")
        @JsonProperty("qType")
        private Integer qType;

        @NotNull(message = "抽题数量不能为空")
        private Integer count;

        /** 难度 1-5，留空表示不限 */
        private Integer difficulty;

        /** 每题分值，留空则用题目自身分值 */
        private BigDecimal scorePerItem;
    }
}
