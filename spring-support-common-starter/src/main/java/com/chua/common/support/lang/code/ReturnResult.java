package com.chua.common.support.lang.code;

/**
 * 统一返回结果类，封装 API 响应的状态码、消息和数据
 *
 * @param <T> 数据类型
 * @author CH
 */
public class ReturnResult<T> {

    /**
     * 状态码，通常 200 表示成功，其他值表示不同类别的错误
     */
    private int code;

    /**
     * 提示消息，描述请求处理结果
     */
    private String msg;

    /**
     * 响应数据承载对象
     */
    private T data;

    /**
     * 空参数构造函数
     */
    public ReturnResult() {
    }

    /**
     * 指定状态码和消息构造返回结果
     *
     * @param code 状态码
     * @param msg  提示消息
     */
    public ReturnResult(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    /**
     * 创建成功返回结果（无数据）
     *
     * @param <T> 数据类型
     * @return 状态码为 200 的成功结果
     */
    public static <T> ReturnResult<T> ok() {
        return ok(null);
    }

    /**
     * 创建成功返回结果（带数据）
     *
     * @param <T>  数据类型
     * @param data 数据对象
     * @return 状态码为 200 的成功结果
     */
    public static <T> ReturnResult<T> ok(T data) {
        ReturnResult<T> result = new ReturnResult<>(200, "success");
        result.data = data;
        return result;
    }

    /**
     * 创建成功返回结果（带数据），与 {@link #ok(Object)} 功能相同
     *
     * @param <T>  数据类型
     * @param data 数据对象
     * @return 状态码为 200 的成功结果
     */
    public static <T> ReturnResult<T> success(T data) {
        ReturnResult<T> result = new ReturnResult<>(200, "success");
        result.data = data;
        return result;
    }

    /**
     * 创建成功返回结果（无数据）
     *
     * @param <T> 数据类型
     * @return 状态码为 200 的成功结果
     */
    public static <T> ReturnResult<T> success() {
        return success(null);
    }

    /**
     * 创建失败返回结果，使用默认状态码 500
     *
     * @param <T> 数据类型
     * @param msg 错误消息
     * @return 状态码为 500 的失败结果
     */
    public static <T> ReturnResult<T> error(String msg) {
        return new ReturnResult<>(500, msg);
    }

    /**
     * 创建失败返回结果，从 ReturnCode 中提取状态码
     *
     * @param <T>         数据类型
     * @param returnCode  状态码对象
     * @param msg         错误消息
     * @return 包含指定状态码的失败结果
     */
    public static <T> ReturnResult<T> error(ReturnCode returnCode, String msg) {
        return new ReturnResult<>(returnCode.getCode(), msg);
    }

    /**
     * 创建失败返回结果，使用自定义状态码
     *
     * @param <T>  数据类型
     * @param code 状态码
     * @param msg  错误消息
     * @return 包含指定状态码的失败结果
     */
    public static <T> ReturnResult<T> error(int code, String msg) {
        return new ReturnResult<>(code, msg);
    }

    /**
     * 创建成功返回结果（带数据和自定义消息）
     *
     * @param <R>  数据类型
     * @param data 数据对象
     * @param msg  成功消息
     * @return 状态码为 200 的成功结果
     */
    public static <R> ReturnResult<R> ok(R data, String msg) {
        ReturnResult<R> result = new ReturnResult<>(200, msg);
        result.data = data;
        return result;
    }

    /**
     * 创建非法请求返回结果，状态码为 400
     *
     * @param <T> 数据类型
     * @param msg 错误消息
     * @return 状态码为 400 的结果
     */
    public static <T> ReturnResult<T> illegal(String msg) {
        return new ReturnResult<>(400, msg);
    }

    /**
     * 创建非法请求返回结果，使用指定状态码
     *
     * @param <T>         数据类型
     * @param returnCode  状态码对象
     * @param msg         错误消息
     * @return 包含指定状态码的非法请求结果
     */
    public static <T> ReturnResult<T> illegal(ReturnCode returnCode, String msg) {
        return new ReturnResult<>(returnCode.getCode(), msg);
    }

    /**
     * 创建请求失败返回结果，状态码为 500
     *
     * @param <T> 数据类型
     * @param msg 错误消息
     * @return 状态码为 500 的结果
     */
    public static <T> ReturnResult<T> failed(String msg) {
        return new ReturnResult<>(500, msg);
    }

    /**
     * 创建成功返回结果，与 {@link #ok(Object)} 功能相同
     *
     * @param <T>  数据类型
     * @param data 数据对象
     * @return 状态码为 200 的成功结果
     */
    public static <T> ReturnResult<T> of(T data) {
        return ok(data);
    }

    /**
     * 设置响应数据
     *
     * @param data 数据对象
     */
    public void setData(T data) {
        this.data = data;
    }

    /**
     * 判断当前结果是否为成功状态（状态码为 200）
     *
     * @return 状态码为 200 时返回 true
     */
    public boolean isOk() {
        return code == 200;
    }

    /**
     * 获取响应数据
     *
     * @return 数据对象
     */
    public T getData() {
        return data;
    }

    /**
     * 获取状态码
     *
     * @return 状态码
     */
    public int getCode() {
        return code;
    }

    /**
     * 设置状态码
     *
     * @param code 状态码
     */
    public void setCode(int code) {
        this.code = code;
    }

    /**
     * 获取提示消息
     *
     * @return 提示消息
     */
    public String getMsg() {
        return msg;
    }

    /**
     * 设置提示消息
     *
     * @param msg 提示消息
     */
    public void setMsg(String msg) {
        this.msg = msg;
    }
}
