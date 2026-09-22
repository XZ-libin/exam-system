package com.exam.common;

import com.exam.entity.SysUser;
import com.exam.mapper.SysUserMapper;
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
        LoginUser user = jwtUtil.parse(header.substring(7).trim());
        // 令牌里写着「他是谁」，但账号可能早被禁用或删除：每个请求回查一次，
        // 否则被开除的学生凭 12 小时有效的旧令牌还能继续答题。课程项目量级下这一条主键查询可以接受。
        SysUser account = userMappers.getObject().selectById(user.getUserId());
        if (account == null || account.getStatus() == null || account.getStatus() != Dicts.Status.ENABLED) {
            throw BizException.of(ErrorCode.ACCOUNT_DISABLED, "账号已被禁用或不存在，请重新登录");
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
