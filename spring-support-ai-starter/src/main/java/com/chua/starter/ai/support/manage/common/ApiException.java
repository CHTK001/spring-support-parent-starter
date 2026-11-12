package com.chua.starter.ai.support.manage.common;

import lombok.Getter;

import java.util.Map;

/**
 * 前端契约业务异常。
 *
 * <p>携带机器可读错误码 {@link ApiErrorCode} 与人类可读信息，由 {@link GlobalApiExceptionHandler}
 * 统一转换为 {@link ApiResult} 信封并返回对应的 HTTP 状态码。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Getter
public class ApiException extends RuntimeException {

    /**
     * 机器可读错误码
     */
    private final ApiErrorCode errorCode;

    /**
     * 结构化排查信息（可选）
     *
     * <p>用于「用户必须自己定位配置问题」的失败：把生效模型、脱敏地址、失败节点
     * 这类明细随错误体一起下发，前端折叠展示，而不是把它们塞进一句中文。</p>
     */
    private final Map<String, Object> detail;

    /**
     * 构造业务异常。
     *
     * @param errorCode 错误码
     * @param message   人类可读信息
     */
    public ApiException(ApiErrorCode errorCode, String message) {
        /**
         * 父。
         * @param message 消息
         */
        super(message);
        this.errorCode = errorCode;
        this.detail = null;
    }

    /**
     * 构造业务异常（带根因）。
     *
     * @param errorCode 错误码
     * @param message   人类可读信息
     * @param cause     根因
     */
    public ApiException(ApiErrorCode errorCode, String message, Throwable cause) {
        /**
         * 父。
         * @param message 消息
         * @param cause cause
         */
        super(message, cause);
        this.errorCode = errorCode;
        this.detail = null;
    }

    /**
     * 构造业务异常（带结构化排查信息）。
     *
     * @param errorCode 错误码
     * @param message   人类可读信息
     * @param detail    结构化排查信息（生效模型 / 脱敏地址 / 失败节点等）
     */
    public ApiException(ApiErrorCode errorCode, String message, Map<String, Object> detail) {
        super(message);
        this.errorCode = errorCode;
        this.detail = detail;
    }
}
