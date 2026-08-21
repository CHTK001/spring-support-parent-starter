package com.chua.starter.ai.support.nomifun.common;

import com.chua.starter.ai.support.nomifun.common.ApiErrorCode;
import com.chua.starter.ai.support.nomifun.common.ApiException;
import com.chua.starter.ai.support.nomifun.common.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 *
 * <p>将所有异常统一转换为前端契约信封 {@link ApiResult}（{@code success:false,code,error}），
 * 并保证响应体始终为 JSON（非 JSON 响应会被前端判为契约违规）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestControllerAdvice("com.chua.starter.ai.support.nomifun")
public class GlobalApiExceptionHandler {

    /**
     * 处理业务契约异常。
     *
     * @param ex 业务异常
     * @return 错误信封与对应 HTTP 状态码
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResult<Void>> handleApiException(ApiException ex) {
        ApiErrorCode code = ex.getErrorCode();
        log.warn("[api-ex] 业务异常: code={}, message={}", code.getCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.valueOf(code.getHttpStatus()))
                .body(ApiResult.error(code.getCode(), ex.getMessage()));
    }

    /**
     * 处理未预期异常。
     *
     * @param ex 异常
     * @return 内部错误信封（HTTP 500）
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleException(Exception ex) {
        log.error("[api-ex] 未预期异常: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResult.error(ApiErrorCode.INTERNAL_ERROR.getCode(), ex.getMessage()));
    }
}
