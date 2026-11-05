// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.payment.support.common;

import java.io.Serializable;

/**
 * 统一返回结果类。
 *
 * @param code      状态码
 * @param message   消息
 * @param data      数据
 * @param timestamp 时间戳
 * @param <T>       数据类型
 * @since 2026-03-18
 * @author CH
 */
public record Result<T>(Integer code, String message, T data, Long timestamp) implements Serializable {

    private static final long serialVersionUID = 1L; // 序列化版本 uid

    /**
     * 成功返回。
     *
     * @param <T> 数据类型
     * @return 成功的结果
     */
    public static <T> Result<T> success() {
        return new Result<>(200, "操作成功", null, System.currentTimeMillis());
    }

    /**
     * 成功返回（携带数据）。
     *
     * @param data 数据
     * @param <T>  数据类型
     * @return 成功的结果
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data, System.currentTimeMillis());
    }

    /**
     * 成功返回（携带消息和数据）。
     *
     * @param message 消息
     * @param data    数据
     * @param <T>     数据类型
     * @return 成功的结果
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data, System.currentTimeMillis());
    }

    /**
     * 失败返回。
     *
     * @param message 消息
     * @param <T>     数据类型
     * @return 错误的结果
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null, System.currentTimeMillis());
    }

    /**
     * 失败返回（携带状态码）。
     *
     * @param code    状态码
     * @param message 消息
     * @param <T>     数据类型
     * @return 错误的结果
     */
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null, System.currentTimeMillis());
    }

    /**
     * 自定义返回。
     *
     * @param code    状态码
     * @param message 消息
     * @param data    数据
     * @param <T>     数据类型
     * @return 构建的结果
     */
    public static <T> Result<T> build(Integer code, String message, T data) {
        return new Result<>(code, message, data, System.currentTimeMillis());
    }
}