package com.chua.starter.account.common;

import lombok.Getter;

/**
 * 账号与渠道模块的契约业务异常。
 *
 * <p>携带机器可读错误码 {@link ApiErrorCode} 与人类可读信息，由全局异常处理器
 * 统一转换为 {@link ApiResult} 信封并返回对应的 HTTP 状态码。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Getter
public class ApiException extends RuntimeException {

    /**
     * 机器可读错误码。
     */
    private final ApiErrorCode errorCode;

    /**
     * 构造业务异常。
     *
     * @param errorCode 错误码
     * @param message   人类可读信息
     */
    public ApiException(ApiErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 构造业务异常（带根因）。
     *
     * @param errorCode 错误码
     * @param message   人类可读信息
     * @param cause     根因
     */
    public ApiException(ApiErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
