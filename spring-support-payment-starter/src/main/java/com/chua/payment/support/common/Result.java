package com.chua.payment.support.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一返回结果类。
 *
 * @since 2026-03-18
 * @author CH
 */
@Data
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L; // 序列化版本 uid

    /**
     * 状态码。
     */
    private Integer code;

    /**
     * 消息。
     */
    private String message;

    /**
     * 数据。
     */
    private T data;

    /**
     * 时间戳。
     */
    private Long timestamp;

    /**
     * 使用当前时间戳构造结果对象。
     */
    public Result() {
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 构造指定状态码、消息与数据的结果对象。
     *
     * @param code    状态码
     * @param message 消息
     * @param data    数据
     */
    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 成功返回。
     *
     * @return 成功的结果
     */
    public static <T> Result<T> success() {
        return new Result<>(200, "操作成功", null);
    }

    /**
     * 成功返回（携带数据）。
     *
     * @param data 数据
     * @return 成功的结果
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data);
    }

    /**
     * 成功返回（携带消息和数据）。
     *
     * @param message 消息
     * @param data    数据
     * @return 成功的结果
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data);
    }

    /**
     * 失败返回。
     *
     * @param message 消息
     * @return 错误的结果
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }

    /**
     * 失败返回（携带状态码）。
     *
     * @param code    状态码
     * @param message 消息
     * @return 错误的结果
     */
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null);
    }

    /**
     * 自定义返回。
     *
     * @param code    状态码
     * @param message 消息
     * @param data    数据
     * @return 构建的结果
     */
    public static <T> Result<T> build(Integer code, String message, T data) {
        return new Result<>(code, message, data);
    }
}
