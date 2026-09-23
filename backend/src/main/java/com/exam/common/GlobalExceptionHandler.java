package com.exam.common;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.stream.Collectors;

/**
 * 全局异常处理：把异常翻译成统一响应体，避免把堆栈抛给前端。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException e) {
        HttpStatus status = HttpStatus.OK;
        if (e.getCode() == ErrorCode.UNAUTHORIZED) {
            status = HttpStatus.UNAUTHORIZED;
        } else if (e.getCode() == ErrorCode.FORBIDDEN) {
            status = HttpStatus.FORBIDDEN;
        }
        return ResponseEntity.status(status).body(Result.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValidation(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return Result.fail(ErrorCode.BAD_PARAM, message.isBlank() ? "请求参数不合法" : message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraint(ConstraintViolationException e) {
        return Result.fail(ErrorCode.BAD_PARAM, e.getConstraintViolations().stream()
                .map(v -> v.getMessage()).collect(Collectors.joining("；")));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.fail(ErrorCode.BAD_PARAM, "缺少参数：" + e.getParameterName());
    }

    /** 路径参数或查询参数类型不对，属于请求问题，不该报 500 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.fail(ErrorCode.BAD_PARAM, "参数 " + e.getName() + " 取值不合法");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Result.fail(ErrorCode.BAD_PARAM, "接口不支持 " + e.getMethod() + " 请求"));
    }

    /** 路径写错或资源不存在，应当是 404，不该冒 500 */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<Result<Void>> handleNotFound(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.fail(ErrorCode.NOT_FOUND, "接口不存在"));
    }

    /** 字段超长、数值越界等数据库层拦下来的写入，翻译成参数错误而不是 500 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Void> handleIntegrity(DataIntegrityViolationException e) {
        log.warn("数据写入被拒绝: {}", e.getMostSpecificCause().getMessage());
        return Result.fail(ErrorCode.BAD_PARAM, "字段长度或取值超出允许范围，请检查后重试");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleUnreadable(HttpMessageNotReadableException e) {
        return Result.fail(ErrorCode.BAD_PARAM, "请求体格式不正确");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception e) {
        log.error("未预期的异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(ErrorCode.SERVER_ERROR, "服务异常，请稍后重试"));
    }
}
