package com.chua.starter.ai.support.manage.common;

import com.chua.common.support.lang.code.ReturnResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * {@link ApiException} 全局兜底处理器。
 *
 * <p>{@code GlobalApiExceptionHandler} 只覆盖 {@code support.manage} 包，控制台业务控制器位于
 * {@code support.controller} 等包，其抛出的 {@link ApiException} 会被框架的通用运行时异常兜底成
 * 500「系统繁忙」，用户看不到可操作的提示。本处理器按异常自带的状态码返回，优先级高于通用兜底。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class AiApiExceptionAdvice {

    /**
     * 处理业务契约异常，保持异常声明的 HTTP 状态与提示语。
     *
     * @param e 业务异常
     * @return 带原始提示语的标准响应体
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ReturnResult<Object>> handleApiException(ApiException e) {
        int httpStatus = e.getErrorCode().getHttpStatus();
        log.warn("[业务异常] code={}, http={}, msg={}", e.getErrorCode().getCode(), httpStatus, e.getMessage());
        // 直接用异常自带的业务码：框架按 HTTP 状态映射会把 403 统一打成 A0403S0000，
        // 那个码是 oauth 表达「未登录/请重新登录」用的，会让越权被前端误判为会话失效
        return ResponseEntity.status(httpStatus)
                .body(ReturnResult.error(e.getErrorCode().getCode(), e.getMessage()));
    }
}
