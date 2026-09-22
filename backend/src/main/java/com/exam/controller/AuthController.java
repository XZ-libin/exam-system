package com.exam.controller;

import com.exam.common.Result;
import com.exam.dto.AuthForm;
import com.exam.service.AuthService;
import com.exam.vo.UserVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "01-认证与个人中心")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody @Valid AuthForm.Login form) {
        return Result.ok(authService.login(form));
    }

    @Operation(summary = "当前登录用户")
    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        return Result.ok(authService.current());
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.ok();
    }

    @Operation(summary = "修改自己的密码")
    @PutMapping("/password")
    public Result<Void> changePassword(@RequestBody @Valid AuthForm.Password form) {
        authService.changePassword(form);
        return Result.ok();
    }

    @Operation(summary = "修改自己的资料")
    @PutMapping("/profile")
    public Result<UserVo> updateProfile(@RequestBody @Valid AuthForm.Profile form) {
        return Result.ok(authService.updateProfile(form));
    }
}
