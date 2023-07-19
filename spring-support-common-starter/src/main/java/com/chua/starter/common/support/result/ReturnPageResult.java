package com.chua.starter.common.support.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.chua.starter.common.support.result.ReturnCode.*;

/**
 * 返回结果
 *
 * @author CH
 */
@Data
@AllArgsConstructor(staticName = "of")
@NoArgsConstructor(staticName = "of")
public class ReturnPageResult<T> {
    /**
     * http状态码
     */
    protected String code;

    /**
     * 结果
     */
    private PageResult<T> data;
    /**
     * 信息
     */
    private String msg;

    /**
     * 初始化
     *
     * @param data 数据
     * @param msg  消息
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnPageResult<T> ok(PageResult<T> data, String msg) {
        return new ReturnPageResult<>(SUCCESS.getCode(), data, msg);
    }

    /**
     * 初始化
     *
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnPageResult<T> noAuth() {
        return new ReturnPageResult<>(RESULT_ACCESS_UNAUTHORIZED.getCode(), null, null);
    }
    /**
     * 初始化
     *@param msg  消息
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnPageResult<T> noAuth(String msg) {
        return new ReturnPageResult<>(RESULT_ACCESS_UNAUTHORIZED.getCode(), null, msg);
    }

    /**
     * 初始化
     *
     * @param data 数据
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnPageResult<T> ok(PageResult<T> data) {
        return ok(data, "");
    }

    /**
     * 初始化
     *
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnPageResult<T> ok() {
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
    public static <T> ReturnPageResult<T> error(PageResult<T> data, String msg) {
        return new ReturnPageResult<>(SYSTEM_SERVER_BUSINESS.getCode(), null, msg);
    }

    /**
     * 初始化
     *
     * @param data 数据
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnPageResult<T> error(PageResult<T> data) {
        return error(data, "");
    }

    /**
     * 初始化
     *
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnPageResult<T> error() {
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
    public static <T> ReturnPageResult<T> illegal(PageResult<T> data, String msg) {
        return new ReturnPageResult<>(PARAM_ERROR.getCode(), null, msg);
    }

    /**
     * 初始化
     *
     * @param data 数据
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnPageResult<T> illegal(PageResult<T> data) {
        return illegal(data, "");
    }

    /**
     * 初始化
     *
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnPageResult<T> illegal() {
        return illegal(null);
    }

    /**
     * 初始化数据
     * @param <T> 类型
     * @return 构造器
     */
    public static <T> PageResult.PageResultBuilder<T> newBuilder() {
        return PageResult.builder();
    }

}
