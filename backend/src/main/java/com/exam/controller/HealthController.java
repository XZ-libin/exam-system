package com.exam.controller;

import com.exam.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("app", "exam-system");
        data.put("time", LocalDateTime.now());
        try {
            dataSource.getConnection().isValid(3);
            data.put("database", jdbcTemplate.queryForObject("select count(*) from information_schema.tables where table_schema = database()", Integer.class) + " tables");
        } catch (Exception e) {
            data.put("database", "unreachable: " + e.getMessage());
        }
        return Result.ok(data);
    }
}
