package com.chua.starter.account.manage.support;

import com.chua.starter.account.common.ApiErrorCode;
import com.chua.starter.account.common.ApiException;
import com.chua.starter.account.common.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 账号模块异常处理器。
 *
 * <p>把本模块的 {@link ApiException} 转成前端契约信封 {@link ApiResult} 与对应 HTTP 状态，
 * 避免被框架通用兜底统一打成 500「系统繁忙」。优先级高于通用兜底，
 * 且只处理本模块自己的异常类型，不影响 AI 模块的同名体系。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class AccountApiExceptionAdvice {

    /**
     * 处理业务契约异常。
     *
     * @param ex 业务异常
     * @return 错误信封与对应 HTTP 状态码
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResult<Void>> handleApiException(ApiException ex) {
        ApiErrorCode code = ex.getErrorCode();
        log.warn("[account-api] 业务异常: code={}, message={}", code.getCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.valueOf(code.getHttpStatus()))
                .body(ApiResult.error(code.getCode(), ex.getMessage()));
    }

    /**
     * 处理缺少必填请求参数。
     *
     * @param ex 缺少参数异常
     * @return 400 与参数错误信封
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResult<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("[account-api] 缺少必填参数: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResult.error(ApiErrorCode.INVALID_PARAM.getCode(),
                        "缺少必填参数：" + ex.getParameterName()));
    }

    /**
     * 处理未预期异常。
     *
     * @param ex 异常
     * @return 500 与内部错误信封
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleException(Exception ex) {
        log.error("[account-api] 未预期异常: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResult.error(ApiErrorCode.INTERNAL_ERROR.getCode(), ex.getMessage()));
    }
}
