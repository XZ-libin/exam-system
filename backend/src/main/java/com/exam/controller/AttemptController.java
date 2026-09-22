package com.exam.controller;

import com.exam.common.Dicts;
import com.exam.common.RequireRole;
import com.exam.common.Result;
import com.exam.dto.AttemptForm;
import com.exam.service.AttemptService;
import com.exam.service.ScoreService;
import com.exam.vo.ExamPaperVo;
import com.exam.vo.RecordDetailVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "05-在线答题")
@RestController
@RequestMapping("/api/exam")
@RequiredArgsConstructor
public class AttemptController {

    private final AttemptService attemptService;
    private final ScoreService scoreService;

    @Operation(summary = "我的考试列表")
    @GetMapping("/my-exams")
    @RequireRole({Dicts.Role.STUDENT, Dicts.Role.TEACHER, Dicts.Role.ADMIN})
    public Result<List<Map<String, Object>>> myExams() {
        return Result.ok(attemptService.myExams());
    }

    @Operation(summary = "进入考试：首次开考或断点续考")
    @PostMapping("/{examId}/enter")
    @RequireRole(Dicts.Role.STUDENT)
    public Result<ExamPaperVo> enter(@PathVariable Long examId) {
        return Result.ok(attemptService.enter(examId));
    }

    @Operation(summary = "批量保存答案（每答一题与每 30 秒各调一次）")
    @PostMapping("/record/{recordId}/answers")
    @RequireRole(Dicts.Role.STUDENT)
    public Result<Map<String, Object>> saveAnswers(@PathVariable Long recordId,
                                                   @RequestBody AttemptForm.Save form) {
        return Result.ok(attemptService.saveAnswers(recordId, form));
    }

    @Operation(summary = "上报一次切屏，达到上限会强制交卷")
    @PostMapping("/record/{recordId}/switch")
    @RequireRole(Dicts.Role.STUDENT)
    public Result<Map<String, Object>> reportSwitch(@PathVariable Long recordId) {
        return Result.ok(attemptService.reportSwitch(recordId));
    }

    @Operation(summary = "交卷并自动判分")
    @PostMapping("/record/{recordId}/submit")
    @RequireRole(Dicts.Role.STUDENT)
    public Result<Map<String, Object>> submit(@PathVariable Long recordId) {
        return Result.ok(attemptService.submit(recordId));
    }

    @Operation(summary = "答卷结果明细（成绩未发布时学生只能看到状态）")
    @GetMapping("/record/{recordId}/result")
    public Result<RecordDetailVo> result(@PathVariable Long recordId) {
        return Result.ok(scoreService.detail(recordId));
    }
}
