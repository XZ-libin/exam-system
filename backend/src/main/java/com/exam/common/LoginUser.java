package com.exam.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;

import java.util.List;

/**
 * 当前登录用户，由 AuthInterceptor 解析 JWT 后放入 ThreadLocal。
 */
@Data
public class LoginUser {

    private Long userId;
    private String username;
    private String realName;
    private String className;
    private List<String> roles;

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /** 取当前登录用户 id，未登录抛 401 */
    public static Long userId() {
        LoginUser u = HOLDER.get();
        if (u == null || u.getUserId() == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }
        return u.getUserId();
    }

    public static boolean hasRole(String... codes) {
        LoginUser u = HOLDER.get();
        if (u == null || u.getRoles() == null) {
            return false;
        }
        for (String code : codes) {
            if (u.getRoles().contains(code)) {
                return true;
            }
        }
        return false;
    }

    public static String ipOf(HttpServletRequest request) {
        String ip = request.getHeader("X-Real-IP");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
