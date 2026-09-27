package com.chua.payment.support.exception;

import com.chua.common.support.lang.code.ReturnResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

import static com.chua.common.support.lang.code.ReturnCode.REQUEST_PARAM_ERROR;

/**
 * 支付模块异常统一转换为框架的用户报错结果。
 * 这样成功和失败路径都会保持同一套响应协议，避免再次出现
 * `ReturnResult(code=00000, data=Result(code=500,...))` 这类双层封装。
 *
 * @since 2026-03-18
 * @author CH
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理支付业务异常
     *
     * @param e 支付异常
     * @return 转换为用户报错结果
     */
    @ExceptionHandler(PaymentException.class)
    public ReturnResult<Void> handlePaymentException(PaymentException e) {
        log.error("支付异常: {}", e.getMessage(), e);
        return ReturnResult.error(e.getMessage());
    }

    /**
     * 处理参数校验异常
     *
     * @param e 参数校验异常
     * @return 转换为用户报错结果
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ReturnResult<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.error("参数校验失败: {}", message);
        return ReturnResult.error(REQUEST_PARAM_ERROR.getCode(), message);
    }

    /**
     * 处理绑定异常
     *
     * @param e 绑定异常
     * @return 转换为用户报错结果
     */
    @ExceptionHandler(BindException.class)
    public ReturnResult<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.error("参数绑定失败: {}", message);
        return ReturnResult.error(REQUEST_PARAM_ERROR.getCode(), message);
    }

    /**
     * 处理非法参数异常
     *
     * @param e 非法参数异常
     * @return 转换为用户报错结果
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ReturnResult<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("非法参数: {}", e.getMessage(), e);
        return ReturnResult.error(REQUEST_PARAM_ERROR.getCode(), e.getMessage());
    }

    /**
     * 处理通用异常
     *
     * @param e 通用异常
     * @return 转换为用户报错结果
     */
    @ExceptionHandler(Exception.class)
    public ReturnResult<Void> handleException(Exception e) {
        log.error("系统异常: {}", e.getMessage(), e);
        return ReturnResult.error(e.getMessage());
    }
}
