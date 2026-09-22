package com.exam.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 Controller 方法或类上，限定可访问的角色编码。
 * 不加该注解的接口只要求登录态。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequireRole {

    /** ADMIN / TEACHER / STUDENT */
    String[] value();
}
