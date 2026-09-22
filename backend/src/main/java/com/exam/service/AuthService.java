package com.exam.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.exam.common.BizException;
import com.exam.common.Dicts;
import com.exam.common.ErrorCode;
import com.exam.common.JwtUtil;
import com.exam.common.LoginUser;
import com.exam.dto.AuthForm;
import com.exam.entity.SysUser;
import com.exam.mapper.SysUserMapper;
import com.exam.vo.UserVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final SysUserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final LogService logService;

    @Transactional
    public Map<String, Object> login(AuthForm.Login form) {
        SysUser user = userService.findByUsername(form.getUsername().trim());
        if (user == null || !UserService.matches(form.getPassword(), user.getPassword())) {
            throw BizException.of(ErrorCode.LOGIN_FAILED, "账号或密码不正确");
        }
        if (user.getStatus() == null || user.getStatus() != Dicts.Status.ENABLED) {
            throw BizException.of(ErrorCode.ACCOUNT_DISABLED, "该账号已被禁用，请联系管理员");
        }
        LoginUser principal = new LoginUser();
        principal.setUserId(user.getId());
        principal.setUsername(user.getUsername());
        principal.setRealName(user.getRealName());
        principal.setClassName(user.getClassName());
        principal.setRoles(userService.roleCodes(user.getId()));
        if (principal.getRoles().isEmpty()) {
            throw BizException.of(ErrorCode.FORBIDDEN, "该账号还没有分配角色，请联系管理员");
        }

        SysUser touch = new SysUser();
        touch.setId(user.getId());
        touch.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(touch);

        logService.record("LOGIN", "sys_user:" + user.getId(), "账号 " + user.getUsername() + " 登录");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", jwtUtil.create(principal));
        data.put("expiresIn", jwtUtil.getExpireMillis() / 1000);
        data.put("user", userService.detail(user.getId()));
        return data;
    }

    public Map<String, Object> current() {
        Long userId = LoginUser.userId();
        UserVo vo = userService.detail(userId);
        LoginUser principal = LoginUser.get();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", vo);
        data.put("roles", principal == null ? vo.getRoles() : principal.getRoles());
        data.put("isStudent", userService.isStudent(userId));
        return data;
    }

    @Transactional
    public void changePassword(AuthForm.Password form) {
        Long userId = LoginUser.userId();
        SysUser user = userService.requireById(userId);
        if (!UserService.matches(form.getOldPassword(), user.getPassword())) {
            throw BizException.of(ErrorCode.OLD_PASSWORD_WRONG, "原密码不正确");
        }
        if (form.getOldPassword().equals(form.getNewPassword())) {
            throw BizException.param("新密码不能与原密码相同");
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(UserService.encode(form.getNewPassword()));
        userMapper.updateById(update);
        logService.record("CHANGE_PASSWORD", "sys_user:" + userId, "修改密码");
    }

    @Transactional
    public UserVo updateProfile(AuthForm.Profile form) {
        Long userId = LoginUser.userId();
        // 逐列 set，保证「把手机号删掉」这类操作真的写回数据库
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getRealName, form.getRealName())
                .set(SysUser::getPhone, form.getPhone() == null || form.getPhone().isBlank() ? null : form.getPhone().trim())
                .set(SysUser::getEmail, form.getEmail() == null || form.getEmail().isBlank() ? null : form.getEmail().trim())
                .set(SysUser::getAvatar, form.getAvatar()));
        return userService.detail(userId);
    }

    public void logout() {
        logService.record("LOGOUT", null, "退出登录");
    }
}
