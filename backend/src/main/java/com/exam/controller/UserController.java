package com.exam.controller;

import com.exam.common.Dicts;
import com.exam.common.PageResult;
import com.exam.common.RequireRole;
import com.exam.common.Result;
import com.exam.dto.UserForm;
import com.exam.service.UserService;
import com.exam.vo.UserVo;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "02-用户中心")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "用户分页列表")
    @GetMapping
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    public Result<PageResult<UserVo>> page(@RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "10") long size,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String roleCode,
                                           @RequestParam(required = false) String className,
                                           @RequestParam(required = false) Integer status) {
        return Result.ok(userService.page(page, size, keyword, roleCode, className, status));
    }

    @Operation(summary = "表单选项：角色列表与班级列表")
    @GetMapping("/options")
    public Result<Map<String, Object>> options() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("roles", userService.listRoles());
        data.put("classNames", userService.listClassNames());
        data.put("defaultPassword", "由管理员在重置密码处查看");
        return Result.ok(data);
    }

    @Operation(summary = "用户详情")
    @GetMapping("/{id}")
    @RequireRole({Dicts.Role.ADMIN, Dicts.Role.TEACHER})
    public Result<UserVo> detail(@PathVariable Long id) {
        return Result.ok(userService.detail(id));
    }

    @Operation(summary = "新建用户")
    @PostMapping
    @RequireRole(Dicts.Role.ADMIN)
    public Result<Long> create(@RequestBody @Valid UserForm form) {
        return Result.ok(userService.create(form));
    }

    @Operation(summary = "修改用户")
    @PutMapping("/{id}")
    @RequireRole(Dicts.Role.ADMIN)
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid UserForm form) {
        form.setId(id);
        userService.update(form);
        return Result.ok();
    }

    @Operation(summary = "分配角色")
    @PostMapping("/{id}/roles")
    @RequireRole(Dicts.Role.ADMIN)
    public Result<Void> assignRoles(@PathVariable Long id, @RequestBody Map<String, List<String>> body) {
        userService.assignRoles(id, body.get("roles"));
        return Result.ok();
    }

    @Operation(summary = "启用/禁用")
    @PutMapping("/{id}/status")
    @RequireRole(Dicts.Role.ADMIN)
    public Result<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        userService.changeStatus(id, body.get("status"));
        return Result.ok();
    }

    @Operation(summary = "重置密码，返回新的初始密码")
    @PostMapping("/{id}/reset-password")
    @RequireRole(Dicts.Role.ADMIN)
    public Result<String> resetPassword(@PathVariable Long id) {
        return Result.ok(userService.resetPassword(id));
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    @RequireRole(Dicts.Role.ADMIN)
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.ok();
    }
}
