package com.exam.controller;

import com.exam.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("app", "exam-system");
        data.put("time", LocalDateTime.now());
        // 只报「能不能连上」，不把表数量这类内部信息暴露给未登录访问者
        try {
            boolean reachable = dataSource.getConnection().isValid(3);
            data.put("database", reachable ? "ok" : "unreachable");
        } catch (Exception e) {
            data.put("database", "unreachable");
        }
        return Result.ok(data);
    }
}
