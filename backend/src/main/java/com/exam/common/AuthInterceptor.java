package com.exam.common;

import com.exam.entity.SysUser;
import com.exam.mapper.SysUserMapper;
import com.exam.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录态与角色校验。令牌解析失败或缺角色时抛 BizException，
 * 由 GlobalExceptionHandler 统一转成 {code, message} 响应。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    /**
     * 延迟拿 Mapper：直接注入会让 MybatisPlusAutoConfiguration → WebConfig → AuthInterceptor
     * → SysUserMapper → sqlSessionFactory 形成启动期循环依赖。
     */
    private final ObjectProvider<SysUserMapper> userMappers;
    private final ObjectProvider<UserService> userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw BizException.of(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        LoginUser parsed = jwtUtil.parse(header.substring(7).trim());
        // 令牌只当作「某个用户 id 的签名声明」用：角色、班级、姓名一律回库取最新值。
        // 否则拿到默认密钥就能自签 roles:["ADMIN"] 提权；换班、改角色之后旧令牌也会继续越权。
        SysUser account = userMappers.getObject().selectById(parsed.getUserId());
        if (account == null || account.getStatus() == null || account.getStatus() != Dicts.Status.ENABLED) {
            throw BizException.of(ErrorCode.ACCOUNT_DISABLED, "账号已被禁用或不存在，请重新登录");
        }
        LoginUser user = new LoginUser();
        user.setUserId(account.getId());
        user.setUsername(account.getUsername());
        user.setRealName(account.getRealName());
        user.setClassName(account.getClassName());
        user.setRoles(userService.getObject().roleCodes(account.getId()));
        if (user.getRoles().isEmpty()) {
            throw BizException.forbidden("该账号还没有分配角色，请联系管理员");
        }
        LoginUser.set(user);

        RequireRole require = method.getMethodAnnotation(RequireRole.class);
        if (require == null) {
            require = method.getBeanType().getAnnotation(RequireRole.class);
        }
        if (require != null && !user.hasRole(require.value())) {
            throw BizException.forbidden("当前账号没有该操作权限");
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        LoginUser.clear();
    }
}
