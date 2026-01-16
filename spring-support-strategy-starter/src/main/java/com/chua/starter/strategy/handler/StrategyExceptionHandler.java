package com.chua.starter.strategy.handler;

import com.chua.common.support.lang.code.ReturnCode;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.task.debounce.DebounceException;
import com.chua.starter.strategy.aspect.RateLimiterAspect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 策略异常处理器
 * <p>
 * 全局处理限流和防抖异常，返回统一响应。
 * </p>
 *
 * @author CH
 * @since 2024/12/07
 * @version 1.0.0
 */
@Slf4j
@RestControllerAdvice
@Order(1)
public class StrategyExceptionHandler {

    /**
     * 处理限流异常
     *
     * @param e 限流异常
     * @return 限流响应
     */
    @ExceptionHandler(RateLimiterAspect.RateLimitException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ReturnResult<Void> handleRateLimitException(RateLimiterAspect.RateLimitException e) {
        log.warn("[限流]请求被限流: {}", e.getMessage());
        return ReturnResult.error(ReturnCode.SYSTEM_SERVER_BUSINESS_ERROR,  e.getMessage());
    }

    /**
     * 处理防抖异常
     *
     * @param e 防抖异常
     * @return 防抖响应
     */
    @ExceptionHandler(DebounceException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ReturnResult<Void> handleDebounceException(DebounceException e) {
        log.warn("[防抖]请求被拦截: {}", e.getMessage());
        return ReturnResult.error(ReturnCode.SYSTEM_SERVER_BUSINESS_ERROR, e.getMessage());
    }
}
