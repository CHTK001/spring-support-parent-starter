package com.chua.starter.common.support.lang;

import com.chua.common.support.lang.code.PageResult;

/**
 * 分页返回结果封装类
 *
 * <p>统一 API 响应格式，包含返回码、消息以及分页数据。</p>
 *
 * @param code 返回状态码，0 表示成功
 * @param msg  返回消息描述
 * @param data 分页数据对象
 * @param <T>  数据类型
 * @author CH
 * @since 4.0.0
 */
public record ReturnPageResult<T>(int code, String msg, PageResult<T> data) {

    /**
     * 构造一个空的成功分页结果
     *
     * @param <T> 数据类型
     * @return 空结果对象
     */
    public static <T> ReturnPageResult<T> empty() {
        return new ReturnPageResult<>(0, "success", PageResult.empty());
    }

    /**
     * 将分页结果封装为返回对象
     *
     * @param result 分页结果
     * @param <T>    数据类型
     * @return 返回对象
     */
    public static <T> ReturnPageResult<T> of(PageResult<T> result) {
        return new ReturnPageResult<>(0, "success", result);
    }

    /**
     * 快捷方式：构造成功返回
     *
     * @param result 分页结果
     * @param <T>    数据类型
     * @return 返回对象
     */
    public static <T> ReturnPageResult<T> ok(PageResult<T> result) {
        return of(result);
    }

    /**
     * 快捷方式：构造失败返回，数据部分为空分页结果
     *
     * @param message 错误消息
     * @param <T>     数据类型
     * @return 失败返回对象
     */
    public static <T> ReturnPageResult<T> error(String message) {
        return new ReturnPageResult<>(-1, message, PageResult.empty());
    }

    /**
     * 构造失败返回，可指定错误数据和消息
     *
     * @param data    错误时的分页数据（可为空）
     * @param message 错误消息
     * @param <T>     数据类型
     * @return 失败返回对象
     */
    public static <T> ReturnPageResult<T> error(PageResult<T> data, String message) {
        return new ReturnPageResult<>(-1, message, data);
    }
}
