package com.chua.starter.common.support.lang;

import com.chua.common.support.lang.code.PageResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分页返回结果封装类
 *
 * <p>统一 API 响应格式，包含返回码、消息以及分页数据。</p>
 *
 * @param <T> 数据类型
 * @author CH
 * @since 4.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnPageResult<T> {

    /**
     * 返回状态码，0 表示成功
     */
    private int code;

    /**
     * 返回消息描述
     */
    private String msg;

    /**
     * 分页数据对象
     */
    private PageResult<T> data;

    /**
     * 构造一个空的成功分页结果
     *
     * @param <T> 数据类型
     * @return 空结果对象
     */
    public static <T> ReturnPageResult<T> empty() {
        return ReturnPageResult.<T>builder()
                .code(0)
                .msg("success")
                .data(PageResult.empty())
                .build();
    }

    /**
     * 将分页结果封装为返回对象
     *
     * @param result 分页结果
     * @param <T>    数据类型
     * @return 返回对象
     */
    public static <T> ReturnPageResult<T> of(PageResult<T> result) {
        return ReturnPageResult.<T>builder()
                .code(0)
                .msg("success")
                .data(result)
                .build();
    }

    /**
     * 快捷方式：构造成功返回（等价于 的 方法）
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
        return ReturnPageResult.<T>builder()
                .code(-1)
                .msg(message)
                .data(PageResult.empty())
                .build();
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
        return ReturnPageResult.<T>builder()
                .code(-1)
                .msg(message)
                .data(data)
                .build();
    }
}
