package com.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UserForm {

    private Long id;

    /** 学号 / 工号，同时作为登录账号 */
    @Size(max = 50, message = "账号不能超过 50 字")
    private String username;

    @NotBlank(message = "请填写姓名")
    @Size(max = 50, message = "姓名不能超过 50 字")
    private String realName;

    /** 仅新建时生效，留空则使用系统初始密码 */
    private String password;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
    @Pattern(regexp = "^$|^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "邮箱格式不正确")
    private String email;
    @Size(max = 50, message = "班级名不能超过 50 字")
    private String className;
    @Pattern(regexp = "^$|^(https?://|/)[^\\s]*$", message = "头像只能填 http(s) 链接或站内路径")
    private String avatar;
    private Integer status;

    /** ADMIN / TEACHER / STUDENT，新建时不传默认 STUDENT */
    private List<String> roles;
}
