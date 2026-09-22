package com.exam.controller;

import com.exam.common.Dicts;
import com.exam.common.PageResult;
import com.exam.common.RequireRole;
import com.exam.common.Result;
import com.exam.dto.AutoPaperForm;
import com.exam.dto.PaperForm;
import com.exam.dto.PaperQuestionForm;
import com.exam.service.PaperService;
import com.exam.vo.PaperDetailVo;
import com.exam.vo.PaperVo;
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

import java.math.BigDecimal;
import java.util.List;

/**
 * 试卷管理接口。读接口只要登录，写接口限 ADMIN / TEACHER，且只有创建人或管理员能改。
 */
@Tag(name = "试卷管理")
@RestController
@RequestMapping("/api/papers")
@RequiredArgsConstructor
public class PaperController {

    private final PaperService paperService;

    @Operation(summary = "试卷分页列表")
    @GetMapping
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    public Result<PageResult<PaperVo>> page(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Integer status,
                                            @RequestParam(required = false) Long categoryId,
                                            @RequestParam(required = false) Integer buildType) {
        return Result.ok(paperService.page(page, size, keyword, status, categoryId, buildType));
    }

    @Operation(summary = "试卷详情（含题目快照）")
    @GetMapping("/{id}")
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    public Result<PaperDetailVo> detail(@PathVariable Long id) {
        return Result.ok(paperService.detail(id));
    }

    @Operation(summary = "教师端预览整卷")
    @GetMapping("/{id}/preview")
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    public Result<PaperDetailVo> preview(@PathVariable Long id) {
        return Result.ok(paperService.preview(id));
    }

    @Operation(summary = "新建空白草稿卷")
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    @PostMapping
    public Result<Long> create(@RequestBody @Valid PaperForm form) {
        return Result.ok(paperService.create(form));
    }

    @Operation(summary = "修改试卷基本信息（已发布不可改）")
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid PaperForm form) {
        form.setId(id);
        paperService.update(form);
        return Result.ok();
    }

    @Operation(summary = "删除试卷")
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        paperService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "人工组卷：批量加入题目")
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    @PostMapping("/{id}/questions")
    public Result<Void> addQuestions(@PathVariable Long id, @RequestBody List<PaperQuestionForm> questions) {
        paperService.addQuestions(id, questions);
        return Result.ok();
    }

    @Operation(summary = "移出题目")
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    @DeleteMapping("/{id}/questions/{questionId}")
    public Result<Void> removeQuestion(@PathVariable Long id, @PathVariable Long questionId) {
        paperService.removeQuestion(id, questionId);
        return Result.ok();
    }

    @Operation(summary = "调整题目分值，请求体形如 {\"score\": 3.0}")
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    @PutMapping("/{id}/questions/{questionId}")
    public Result<Void> updateQuestionScore(@PathVariable Long id, @PathVariable Long questionId,
                                            @RequestBody ScoreForm body) {
        paperService.updateQuestionScore(id, questionId, body == null ? null : body.score());
        return Result.ok();
    }

    @Operation(summary = "规则抽题自动组卷")
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    @PostMapping("/auto-generate")
    public Result<Long> autoGenerate(@RequestBody @Valid AutoPaperForm form) {
        return Result.ok(paperService.autoGenerate(form));
    }

    @Operation(summary = "发布试卷")
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    @PutMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        paperService.publish(id);
        return Result.ok();
    }

    @Operation(summary = "归档（下架）试卷")
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    @PutMapping("/{id}/archive")
    public Result<Void> archive(@PathVariable Long id) {
        paperService.archive(id);
        return Result.ok();
    }

    /** 只带一个分值的小请求体 */
    public record ScoreForm(BigDecimal score) {
    }
}
