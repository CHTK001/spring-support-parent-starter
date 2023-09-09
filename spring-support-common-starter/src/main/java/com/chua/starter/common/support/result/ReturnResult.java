package com.chua.starter.common.support.result;

import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.lang.code.ResultCode;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.chua.starter.common.support.result.ReturnCode.*;

/**
 * 返回结果
 *
 * @author CH
 */
@Data
@NoArgsConstructor
public class ReturnResult<T> {
    /**
     * http状态码
     */
    protected String code;

    /**
     * 结果
     */
    protected T data;
    /**
     * 信息
     */
    protected String msg;
    private long timestamp = System.currentTimeMillis();

    public ReturnResult(String code, T data, String msg) {
        this.code = code;
        this.data = data;
        this.msg = msg;
    }

    /**
     * 构造器
     *
     * @param <T> 类型
     * @return 构造器
     */
    public static <T> ReturnResultBuilder<T> newBuilder() {
        return new ReturnResultBuilder<>();
    }


    /**
     * 构造器
     *
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
        return new ReturnResult<>(SUCCESS.getCode(), data, msg);
    }

    /**
     * 初始化
     *
     * @param <T> 类型
     * @return 结果
     */
    public static <T> ReturnResult<T> noAuth() {
        return new ReturnResult<>(RESULT_ACCESS_UNAUTHORIZED.getCode(), null, RESULT_ACCESS_UNAUTHORIZED.getMsg());
    }

    /**
     * 初始化
     *
     * @param msg 消息
     * @param <T> 类型
     * @return 结果
     */
    public static <T> ReturnResult<T> noAuth(String msg) {
        return new ReturnResult<>(RESULT_ACCESS_UNAUTHORIZED.getCode(), null, msg);
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
     * @param <T> 类型
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
        return new ReturnResult<>(SYSTEM_SERVER_BUSINESS.getCode(), data, msg);
    }

    /**
     * 初始化
     *
     * @param data 数据
     * @param code 错误码
     * @param msg  消息
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnResult<T> of(String code, T data, String msg) {
        return new ReturnResult<>(code, data, msg);
    }

    /**
     * 初始化
     *
     * @param data 数据
     * @param code 错误码
     * @param msg  消息
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnResult<T> of(ResultCode code, T data, String msg) {
        return new ReturnResult<>(code.getCode(), data, msg);
    }

    /**
     * 初始化
     *
     * @param data 数据
     * @param code 错误码
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnResult<T> of(ResultCode code, T data) {
        return new ReturnResult<>(code.getCode(), data, code.getMsg());
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
     * @param <T> 类型
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
        return new ReturnResult<>(PARAM_ERROR.getCode(), data, msg);
    }
    /**
     * 初始化
     *
     * @param msg  消息
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnResult<T> illegal(String msg) {
        return new ReturnResult<>(PARAM_ERROR.getCode(), null, msg);
    }
    /**
     * 初始化
     *
     * @param data 数据
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnResult<T> illegalWith(T data) {
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
        return illegal(null, "操作失败");
    }

    /**
     * 属于
     *
     * @param body 消息体
     * @return {@link ReturnResult}<{@link T}>
     */
    public static <T> ReturnResult<T> of(Object body) {
        return BeanUtils.copyProperties(body, ReturnResult.class);
    }

    @Override
    public String toString() {
        return null == data ? "Empty" : data.toString();
    }
}
