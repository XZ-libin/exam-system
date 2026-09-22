package com.exam.common;

import lombok.Getter;

/**
 * 业务异常。抛出后由 GlobalExceptionHandler 转成统一响应体。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BizException of(int code, String message) {
        return new BizException(code, message);
    }

    public static BizException notFound(String what) {
        return new BizException(ErrorCode.NOT_FOUND, what + "不存在");
    }

    public static BizException param(String message) {
        return new BizException(ErrorCode.BAD_PARAM, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(ErrorCode.FORBIDDEN, message);
    }
}
