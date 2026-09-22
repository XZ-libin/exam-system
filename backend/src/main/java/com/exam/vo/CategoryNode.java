package com.exam.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 分类树节点。questionCount 只统计本分类直属的题目，不含子分类。
 */
@Data
public class CategoryNode {

    private Long id;

    private Long parentId;

    private String name;

    private Integer sortNo;

    private String remark;

    /** 本分类下未删除的题目数 */
    private Integer questionCount;

    private List<CategoryNode> children = new ArrayList<>();
}
