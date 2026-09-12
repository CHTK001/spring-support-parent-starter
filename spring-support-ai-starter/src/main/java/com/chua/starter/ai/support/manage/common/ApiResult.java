package com.chua.starter.ai.support.manage.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;

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
* @param <T> 业务数据类型
* @since 4.0.0.42
* @author CH
 */
@Data
public class ApiResult<T> implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
    * 是否成功
     */
    private boolean success;

    /**
    * 业务数据（成功时返回，始终输出即使为 空）
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private T data; // 数据

    /**
    * 机器可读错误码（如 CONVERSATION_ARCHIVED / FORBIDDEN）
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String code; // 编码

    /**
    * 人类可读错误信息
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String error; // 错误

    /**
    * 附加提示信息
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String msg; // msg

    /**
    * 构造成功响应。
    *
    * @param data 业务数据
    * @param <T>  数据类型
    * @return 响应信封
     */
    public static <T> ApiResult<T> ok(T data) {
        ApiResult<T> result = new ApiResult<>();
        result.setSuccess(true);
        result.setData(data);
        return result;
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
        ApiResult<Void> result = new ApiResult<>();
        result.setSuccess(false);
        result.setCode(code);
        result.setError(error);
        return result;
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
        ApiResult<Void> result = new ApiResult<>();
        result.setSuccess(false);
        result.setCode(code);
        result.setError(error);
        result.setMsg(msg);
        return result;
    }
}