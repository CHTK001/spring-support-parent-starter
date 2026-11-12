package com.chua.starter.ai.support.manage.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.Map;

/**
 * 前端统一响应信封。
 *
 * <p>前端 {@code httpRequest} 解包约定：若 JSON 响应体含 {@code data} 字段则直接返回该字段，
 * 否则返回整个 JSON 对象。因此所有响应都必须包含 {@code data} 字段（成功时为业务数据，无数据时可为 {@code null}）。</p>
 *
 * <ul>
 *     <li>成功：{@code {"success":true,"data":<T>}}</li>
 *     <li>失败：{@code {"success":false,"code":"<机器码>","error":"<人类可读>"}}
 *     （非 JSON 响应会被前端判为契约违规）</li>
 * </ul>
 *
 * @param success 是否成功
 * @param data    业务数据（成功时返回，始终输出即使为 空）
 * @param code    机器可读错误码（如 CONVERSATION_ARCHIVED / FORBIDDEN）
 * @param error   人类可读错误信息
 * @param msg     附加提示信息
 * @param detail  结构化排查信息（失败时可选：生效配置、失败节点、脱敏地址等）
 * @param <T>     业务数据类型
 * @since 4.0.0.42
 * @author CH
 */
public record ApiResult<T>(
        boolean success,
        @JsonInclude(JsonInclude.Include.ALWAYS) T data,
        @JsonInclude(JsonInclude.Include.NON_NULL) String code,
        @JsonInclude(JsonInclude.Include.NON_NULL) String error,
        @JsonInclude(JsonInclude.Include.NON_NULL) String msg,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> detail) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 构造成功响应。
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return 响应信封
     */
    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(true, data, null, null, null, null);
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
        return new ApiResult<>(false, null, code, error, null, null);
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
        return new ApiResult<>(false, null, code, error, msg, null);
    }

    /**
     * 构造失败响应（含结构化排查信息）。
     *
     * <p>给「用户必须自己定位配置问题」的失败路径用：一句中文塞不下「哪个模型、
     * 打的是哪个地址、卡在哪个节点」，硬塞会变成无法阅读的长句。这里把它拆成
     * 机器可读的明细，前端折叠展示，用户能直接照着改配置。</p>
     *
     * @param code   机器可读错误码
     * @param error  人类可读错误信息
     * @param detail 结构化排查信息（如生效模型、脱敏地址、失败节点）
     * @return 响应信封
     */
    public static ApiResult<Void> errorDetail(String code, String error, Map<String, Object> detail) {
        return new ApiResult<>(false, null, code, error, null, detail);
    }
}
