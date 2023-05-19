package com.chua.starter.common.support.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 返回结果
 *
 * @author CH
 */
@Data
@AllArgsConstructor(staticName = "of")
@NoArgsConstructor(staticName = "of")
public class ReturnResult<T> {
    /**
     * http状态码
     */
    private Integer code;

    /**
     * 结果
     */
    private T data;
    /**
     * 信息
     */
    private String msg;


    /**
     * 构造器
     * @param <T> 类型
     * @return 构造器
     */
    public static <T> ReturnResultBuilder<T> newBuilder() {
        return new ReturnResultBuilder<>();
    }


    /**
     * 构造器
     * @param <T> 类型
     * @return 构造器
     */
    public static <T> ReturnResultsBuilder<T> newsBuilder() {
        return new ReturnResultsBuilder<>();
    }
    /**
     * 初始化
     *
     * @param data 数据
     * @param msg  消息
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnResult<T> ok(T data, String msg) {
        return new ReturnResult<>(200, data, msg);
    }

    /**
     * 初始化
     *
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnResult<T> noAuth() {
        return new ReturnResult<>(403, null, null);
    }
    /**
     * 初始化
     *@param msg  消息
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnResult<T> noAuth(String msg) {
        return new ReturnResult<>(403, null, msg);
    }

    /**
     * 初始化
     *
     * @param data 数据
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnResult<T> ok(T data) {
        return ok(data, "");
    }

    /**
     * 初始化
     *
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnResult<T> ok() {
        return ok(null);
    }

    /**
     * 初始化
     *
     * @param data 数据
     * @param msg  消息
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnResult<T> error(T data, String msg) {
        return new ReturnResult<>(500, null, msg);
    }

    /**
     * 初始化
     *
     * @param data 数据
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnResult<T> error(T data) {
        return error(data, "");
    }

    /**
     * 初始化
     *
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnResult<T> error() {
        return error(null);
    }

    /**
     * 初始化
     *
     * @param data 数据
     * @param msg  消息
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnResult<T> illegal(T data, String msg) {
        return new ReturnResult<>(400, null, msg);
    }

    /**
     * 初始化
     *
     * @param data 数据
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnResult<T> illegal(T data) {
        return illegal(data, "");
    }

    /**
     * 初始化
     *
     * @param throwable 数据
     * @param <T>       类型
     * @return 结果
     */
    public static <T> ReturnResult<T> illegal(Throwable throwable) {
        return illegal(null, throwable.getMessage());
    }

    /**
     * 初始化
     *
     * @param <T> 类型
     * @return 结果
     */
    public static <T> ReturnResult<T> illegal() {
        return illegal(null);
    }

    @Override
    public String toString() {
        return null == data ? "Empty" : data.toString();
    }
}
