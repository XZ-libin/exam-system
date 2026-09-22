package com.exam.controller;

import com.exam.common.BizException;
import com.exam.common.Dicts;
import com.exam.common.PageResult;
import com.exam.common.RequireRole;
import com.exam.common.Result;
import com.exam.dto.QuestionForm;
import com.exam.service.QuestionService;
import com.exam.vo.QuestionVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Tag(name = "题库管理", description = "题目的增删改查、批量录入与文本导入")
@RestController
@RequestMapping("/api/questions")
@RequiredArgsConstructor
public class QuestionController {

    private static final String TEMPLATE_FILE_NAME = "question-import-template.txt";

    private final QuestionService questionService;

    @Operation(summary = "题目分页列表", description = "所有筛选条件可空；categoryId 会连带子分类一起查")
    @GetMapping
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    public Result<PageResult<QuestionVo>> page(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) Long categoryId,
                                              @RequestParam(required = false) Integer qType,
                                              @RequestParam(required = false) Integer difficulty,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Integer status,
                                              @RequestParam(required = false) Long creatorId) {
        return Result.ok(questionService.page(page, size, categoryId, qType, difficulty, keyword, status, creatorId));
    }

    @Operation(summary = "题目详情")
    @GetMapping("/{id}")
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    public Result<QuestionVo> detail(@PathVariable Long id) {
        return Result.ok(questionService.detail(id));
    }

    @Operation(summary = "新增题目")
    @PostMapping
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    public Result<Long> create(@Valid @RequestBody QuestionForm form) {
        return Result.ok(questionService.create(form));
    }

    @Operation(summary = "修改题目")
    @PutMapping("/{id}")
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody QuestionForm form) {
        form.setId(id);
        questionService.update(form);
        return Result.ok();
    }

    @Operation(summary = "删除题目", description = "已被试卷引用（use_count > 0）的题目不能删除")
    @DeleteMapping("/{id}")
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    public Result<Void> delete(@PathVariable Long id) {
        questionService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "批量新增题目", description = "请求体 {\"items\":[...]}，单条不合格只记 message")
    @PostMapping("/batch")
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    public Result<Map<String, Object>> batchCreate(@RequestBody Map<String, List<QuestionForm>> payload) {
        List<QuestionForm> items = payload == null ? List.of() : payload.get("items");
        return Result.ok(questionService.batchCreate(items));
    }

    @Operation(summary = "下载导入模板", description = "text/plain 附件，改完直接上传到 /import")
    @GetMapping(value = "/template", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> template() {
        // 这个接口要给浏览器直接下载，所以是唯一不走统一 Result 包装的方法
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(TEMPLATE_FILE_NAME, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(questionService.importTemplate());
    }

    @Operation(summary = "文本导入题目", description = "上传 UTF-8 的 txt，格式与模板一致")
    @PostMapping("/import")
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    public Result<Map<String, Object>> importQuestions(@RequestParam("file") MultipartFile file,
                                                       @RequestParam Long categoryId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw BizException.param("请选择要导入的 txt 文件");
        }
        String text = new String(file.getBytes(), StandardCharsets.UTF_8);
        return Result.ok(questionService.importFromText(text, categoryId));
    }
}
