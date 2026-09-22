package com.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 认证相关的请求体。
 */
public final class AuthForm {

    private AuthForm() {
    }

    @Data
    public static class Login {
        @NotBlank(message = "请输入登录账号")
        private String username;
        @NotBlank(message = "请输入密码")
        private String password;
    }

    @Data
    public static class Password {
        @NotBlank(message = "请输入原密码")
        private String oldPassword;
        @NotBlank(message = "请输入新密码")
        @Size(min = 6, max = 30, message = "新密码长度需在 6 到 30 位之间")
        private String newPassword;
    }

    @Data
    public static class Profile {
        @NotBlank(message = "请填写姓名")
        private String realName;
        private String phone;
        private String email;
        private String avatar;
    }
}
