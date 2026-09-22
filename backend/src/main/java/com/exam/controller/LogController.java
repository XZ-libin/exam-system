package com.exam.controller;

import com.exam.common.Dicts;
import com.exam.common.PageResult;
import com.exam.common.RequireRole;
import com.exam.common.Result;
import com.exam.entity.SysOpLog;
import com.exam.service.LogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志接口：只有管理员能翻全量日志，查询与分页直接复用 LogService。
 */
@Tag(name = "09-操作日志")
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    @Operation(summary = "操作日志分页：可按 action 精确筛、按用户名/对象关键字模糊筛")
    @GetMapping
    @RequireRole(Dicts.Role.ADMIN)
    public Result<PageResult<SysOpLog>> page(@RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "20") long size,
                                             @RequestParam(required = false) String action,
                                             @RequestParam(required = false) String keyword) {
        return Result.ok(logService.page(page, size, action, keyword));
    }
}
