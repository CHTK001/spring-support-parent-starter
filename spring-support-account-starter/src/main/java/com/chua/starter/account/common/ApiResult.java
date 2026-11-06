package com.chua.starter.account.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

/**
 * 账号与渠道模块的前端统一响应信封。
 *
 * <p>与前端解包约定保持一致：成功响应必须含 {@code data} 字段（无数据时为 {@code null}），
 * 失败响应返回 {@code success=false} 与机器可读 {@code code}。</p>
 *
 * @param success 是否成功
 * @param data    业务数据
 * @param code    机器可读错误码
 * @param error   人类可读错误信息
 * @param msg     附加提示信息
 * @param <T>     业务数据类型
 * @author CH
 * @since 4.0.0.42
 */
public record ApiResult<T>(
        boolean success,
        @JsonInclude(JsonInclude.Include.ALWAYS) T data,
        @JsonInclude(JsonInclude.Include.NON_NULL) String code,
        @JsonInclude(JsonInclude.Include.NON_NULL) String error,
        @JsonInclude(JsonInclude.Include.NON_NULL) String msg) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 构造成功响应。
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return 响应信封
     */
    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(true, data, null, null, null);
    }

    /**
     * 构造成功响应（无业务数据）。
     *
     * @return 响应信封
     */
    public static ApiResult<Void> ok() {
        return ok(null);
    }

    /**
     * 构造失败响应。
     *
     * @param code  机器可读错误码
     * @param error 人类可读错误信息
     * @return 响应信封
     */
    public static ApiResult<Void> error(String code, String error) {
        return new ApiResult<>(false, null, code, error, null);
    }

    /**
     * 构造失败响应（含附加提示）。
     *
     * @param code  机器可读错误码
     * @param error 人类可读错误信息
     * @param msg   附加提示
     * @return 响应信封
     */
    public static ApiResult<Void> error(String code, String error, String msg) {
        return new ApiResult<>(false, null, code, error, msg);
    }
}
