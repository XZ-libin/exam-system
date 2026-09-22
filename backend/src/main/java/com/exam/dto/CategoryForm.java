package com.exam.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 题库分类的新增/修改入参。id 为空表示新增，修改时由路径参数回填。
 */
@Data
public class CategoryForm {

    private Long id;

    /** 父分类 id，0 或不传表示根节点 */
    private Long parentId;

    @NotBlank(message = "分类名称不能为空")
    private String name;

    /** 同级排序号，越小越靠前 */
    private Integer sortNo;

    private String remark;
}
