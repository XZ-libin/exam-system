package com.exam.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * JWT 签发与解析。令牌里只放 id / 登录名 / 姓名 / 班级 / 角色。
 */
@Slf4j
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireMillis;

    public JwtUtil(ExamProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
        this.expireMillis = properties.getJwt().getExpireHours() * 3600_000L;
    }

    public String create(LoginUser user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(user.getUserId()))
                .claim("username", user.getUsername())
                .claim("realName", user.getRealName())
                .claim("className", user.getClassName())
                .claim("roles", user.getRoles())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key)
                .compact();
    }

    @SuppressWarnings("unchecked")
    public LoginUser parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            LoginUser user = new LoginUser();
            user.setUserId(Long.valueOf(claims.getSubject()));
            user.setUsername(claims.get("username", String.class));
            user.setRealName(claims.get("realName", String.class));
            user.setClassName(claims.get("className", String.class));
            user.setRoles((List<String>) claims.get("roles"));
            return user;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("令牌解析失败: {}", e.getMessage());
            throw BizException.of(ErrorCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }
    }

    public long getExpireMillis() {
        return expireMillis;
    }
}
