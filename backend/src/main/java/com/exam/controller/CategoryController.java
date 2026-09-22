package com.exam.controller;

import com.exam.common.Dicts;
import com.exam.common.RequireRole;
import com.exam.common.Result;
import com.exam.dto.CategoryForm;
import com.exam.service.CategoryService;
import com.exam.vo.CategoryNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "题库分类", description = "题目分类树的增删改查")
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "分类树", description = "带每个分类的题目数；keyword 非空时只保留命中的分类及其祖先")
    @GetMapping("/tree")
    public Result<List<CategoryNode>> tree(@RequestParam(required = false) String keyword) {
        return Result.ok(categoryService.listTree(keyword));
    }

    @Operation(summary = "新增分类")
    @PostMapping
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    public Result<Long> create(@Valid @RequestBody CategoryForm form) {
        return Result.ok(categoryService.create(form));
    }

    @Operation(summary = "修改分类", description = "不能把分类挂到自己或自己的子分类下")
    @PutMapping("/{id}")
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CategoryForm form) {
        form.setId(id);
        categoryService.update(form);
        return Result.ok();
    }

    @Operation(summary = "删除分类", description = "有子分类或题目时不允许删除")
    @DeleteMapping("/{id}")
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.ok();
    }
}
