package com.exam.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.exam.dto.QuestionForm;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 题目出参：JSON 列解析成结构化字段，并补上分类名、题型名、创建人姓名，前端无需二次查询。
 */
@Data
public class QuestionVo {

    private Long id;

    private Long categoryId;

    @JsonProperty("qType")

    private Integer qType;

    private String content;

    private List<QuestionForm.OptionItem> options;

    private List<String> answer;

    private String analysis;

    private Integer difficulty;

    private BigDecimal score;

    private Integer status;

    private Long creatorId;

    private Integer useCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private String categoryName;

    /** Dicts.QType.name(qType) */
    @JsonProperty("qTypeName")
    private String qTypeName;

    private String creatorName;
}
