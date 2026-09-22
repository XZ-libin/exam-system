package com.exam.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class UserForm {

    private Long id;

    /** 学号 / 工号，同时作为登录账号 */
    private String username;

    @NotBlank(message = "请填写姓名")
    private String realName;

    /** 仅新建时生效，留空则使用系统初始密码 */
    private String password;

    private String phone;
    private String email;
    private String className;
    private String avatar;
    private Integer status;

    /** ADMIN / TEACHER / STUDENT，新建时不传默认 STUDENT */
    private List<String> roles;
}
