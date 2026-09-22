package com.exam.controller;

import com.exam.common.Dicts;
import com.exam.common.PageResult;
import com.exam.common.RequireRole;
import com.exam.common.Result;
import com.exam.service.ScoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

@Tag(name = "07-成绩管理")
@RestController
@RequestMapping("/api/scores")
@RequiredArgsConstructor
public class ScoreController {

    private final ScoreService scoreService;

    @Operation(summary = "全班/全校成绩分页")
    @GetMapping
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    public Result<PageResult<Map<String, Object>>> page(@RequestParam(defaultValue = "1") long page,
                                                         @RequestParam(defaultValue = "10") long size,
                                                         @RequestParam(required = false) Long examId,
                                                         @RequestParam(required = false) String keyword,
                                                         @RequestParam(required = false) Integer status,
                                                         @RequestParam(required = false) Integer passFlag) {
        return Result.ok(scoreService.page(page, size, examId, keyword, status, passFlag));
    }

    @Operation(summary = "我的成绩")
    @GetMapping("/my")
    @RequireRole(Dicts.Role.STUDENT)
    public Result<PageResult<Map<String, Object>>> my(@RequestParam(defaultValue = "1") long page,
                                                      @RequestParam(defaultValue = "10") long size,
                                                      @RequestParam(required = false) Long examId) {
        return Result.ok(scoreService.myPage(page, size, examId));
    }

    @Operation(summary = "导出成绩 CSV（Excel 可直接打开）")
    @GetMapping("/export")
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    public ResponseEntity<byte[]> export(@RequestParam(required = false) Long examId) {
        byte[] body = scoreService.exportCsv(examId).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"scores-" + LocalDate.now() + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }
}
