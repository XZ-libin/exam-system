package com.exam.controller;

import com.exam.common.Dicts;
import com.exam.common.RequireRole;
import com.exam.common.Result;
import com.exam.service.StatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 统计分析接口：全部只读，聚合口径见 StatService。
 * 前三个看的是别人的数据，限 ADMIN / TEACHER；/my/analysis 任何登录用户只看本人。
 */
@Tag(name = "08-统计分析")
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatController {

    private final StatService statService;

    @Operation(summary = "总览：考试/试卷/题目/答卷数量、平均分、及格率、分数段与最近考试")
    @GetMapping("/overview")
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    public Result<Map<String, Object>> overview() {
        return Result.ok(statService.overview());
    }

    @Operation(summary = "单场考试分析：分数段、每题正确率与鉴别度、班级对比")
    @GetMapping("/exam/{id}")
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    public Result<Map<String, Object>> exam(@PathVariable Long id) {
        return Result.ok(statService.examDetail(id));
    }

    @Operation(summary = "单题分析：跨所有引用该题的试卷快照统计正确率")
    @GetMapping("/question/{id}")
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    public Result<Map<String, Object>> question(@PathVariable Long id) {
        return Result.ok(statService.questionDetail(id));
    }

    @Operation(summary = "本人成绩分析：趋势、科目/题型正确率、难度雷达（只看已发布成绩的场次）")
    @GetMapping("/my/analysis")
    public Result<Map<String, Object>> myAnalysis() {
        return Result.ok(statService.myAnalysis());
    }
}
