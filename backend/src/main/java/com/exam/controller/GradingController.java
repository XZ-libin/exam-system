package com.exam.controller;

import com.exam.common.Dicts;
import com.exam.common.PageResult;
import com.exam.common.RequireRole;
import com.exam.common.Result;
import com.exam.dto.GradingForm;
import com.exam.service.GradingService;
import com.exam.service.ScoreService;
import com.exam.vo.RecordDetailVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "06-主观题阅卷")
@RestController
@RequestMapping("/api/grading")
@RequiredArgsConstructor
@RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
public class GradingController {

    private final GradingService gradingService;
    private final ScoreService scoreService;

    @Operation(summary = "待阅卷列表")
    @GetMapping("/pending")
    public Result<PageResult<Map<String, Object>>> pending(@RequestParam(defaultValue = "1") long page,
                                                           @RequestParam(defaultValue = "10") long size,
                                                           @RequestParam(required = false) Long examId,
                                                           @RequestParam(required = false) String keyword) {
        return Result.ok(gradingService.pending(page, size, examId, keyword));
    }

    @Operation(summary = "待阅卷份数")
    @GetMapping("/pending-count")
    public Result<Map<String, Object>> pendingCount(@RequestParam(required = false) Long examId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", gradingService.pendingCount(examId));
        return Result.ok(data);
    }

    @Operation(summary = "打开一份待阅答卷（含标准答案与解析）")
    @GetMapping("/record/{recordId}")
    public Result<RecordDetailVo> detail(@PathVariable Long recordId) {
        return Result.ok(scoreService.detail(recordId));
    }

    @Operation(summary = "提交主观题得分并合分")
    @PostMapping("/submit")
    public Result<Map<String, Object>> submit(@RequestBody @Valid GradingForm form) {
        return Result.ok(gradingService.grade(form));
    }
}
