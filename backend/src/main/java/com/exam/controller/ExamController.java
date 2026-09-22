package com.exam.controller;

import com.exam.common.Dicts;
import com.exam.common.PageResult;
import com.exam.common.RequireRole;
import com.exam.common.Result;
import com.exam.dto.ExamForm;
import com.exam.service.ExamService;
import com.exam.vo.ExamVo;
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

/**
 * 考试管理接口。读接口只要登录，写接口限 ADMIN / TEACHER，且只有创建人或管理员能改。
 */
@Tag(name = "考试管理")
@RestController
@RequestMapping("/api/exams")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;

    @Operation(summary = "考试分页列表")
    @GetMapping
    public Result<PageResult<ExamVo>> page(@RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "10") long size,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) Integer status) {
        return Result.ok(examService.page(page, size, keyword, status));
    }

    @Operation(summary = "考试详情")
    @GetMapping("/{id}")
    public Result<ExamVo> detail(@PathVariable Long id) {
        return Result.ok(examService.detail(id));
    }

    @Operation(summary = "新建考试")
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    @PostMapping
    public Result<Long> create(@RequestBody @Valid ExamForm form) {
        return Result.ok(examService.create(form));
    }

    @Operation(summary = "修改考试（已有作答记录后不可改）")
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid ExamForm form) {
        form.setId(id);
        examService.update(form);
        return Result.ok();
    }

    @Operation(summary = "删除考试")
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        examService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "发布或结束考试，请求体形如 {\"status\": 1}")
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestBody StatusForm body) {
        examService.changeStatus(id, body == null ? null : body.status());
        return Result.ok();
    }

    @Operation(summary = "发布成绩")
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    @PostMapping("/{id}/publish-score")
    public Result<Void> publishScore(@PathVariable Long id) {
        examService.publishScore(id);
        return Result.ok();
    }

    @Operation(summary = "强制收卷，返回被关掉的答卷数")
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    @PostMapping("/{id}/force-submit")
    public Result<Integer> forceSubmit(@PathVariable Long id) {
        return Result.ok(examService.forceSubmit(id));
    }

    /** 只带一个状态的小请求体 */
    public record StatusForm(Integer status) {
    }
}
