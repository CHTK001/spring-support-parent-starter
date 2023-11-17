package com.chua.starter.common.support.result;

import com.chua.common.support.lang.code.ResultCode;
import com.chua.common.support.lang.page.Page;
import com.chua.common.support.objects.definition.element.TypeDescribe;
import com.chua.common.support.utils.ClassUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
     * 好啊
     * 初始化
     *
     * @param data 数据
     * @return 结果
     */
    public static <T> ReturnPageResult<T> ok(Collection<T> data) {
        return ok(PageResult.<T>builder()
                .data(data)
                .total(data.size())
                .build());
    }

    /**
     * 好啊
     * 初始化
     *
     * @param data  数据
     * @param total 全部
     * @return 结果
     */
    public static <T> ReturnPageResult<T> ok(Collection<T> data, int total) {
        return ok(PageResult.<T>builder()
                .data(data)
                .total(total)
                .build());
    }
    /**
     * 初始化
     *
     * @param data 数据
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnPageResult<T> ok(Object data) {
        if (data instanceof Page) {
            return ok(PageResult.<T>builder()
                    .page(((Page<?>) data).getPageNum())
                    .pageSize(((Page<?>) data).getPageSize())
                    .data((List<T>) ((Page<?>) data).getData())
                    .total(((Page<?>) data).getTotal())
                    .totalPages(((Page<?>) data).getPages())
                    .build());
        }

        if (ClassUtils.isAssignableFrom(data, "com.baomidou.mybatisplus.core.metadata.IPage")) {
            TypeDescribe typeDescribe = TypeDescribe.create(data);
            return ok(PageResult.<T>builder()
                    .page(Optional.ofNullable(typeDescribe.getMethodDescribe("getCurrent").executeSelf(int.class)).orElse(1))
                    .pageSize(Optional.ofNullable(typeDescribe.getMethodDescribe("getSize").executeSelf(int.class)).orElse(10))
                    .data((List<T>) typeDescribe.getMethodDescribe("getRecords").executeSelf())
                    .total(Optional.ofNullable(typeDescribe.getMethodDescribe("getTotal").executeSelf(long.class)).orElse(0L))
                    .totalPages(Optional.ofNullable(typeDescribe.getMethodDescribe("getPages").executeSelf(int.class)).orElse(0))
                    .build());
        }
        return ok(null, "");
    }

    /**
     * 初始化
     *
     * @param <T> 类型
     * @return 结果
     */
    public static <T> ReturnPageResult<T> ok() {
        return ok(Collections.emptyList());
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
     * 错误
     * 初始化
     *
     * @param msg 消息
     * @return 结果
     */
    public static <T> ReturnPageResult<T> error(String msg) {
        return error(null, msg);
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
        return error(null, "");
    }

    /**
     * 初始化
     *
     * @param data 数据
     * @param msg  消息
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnPageResult<T> illegal(ResultCode data, String msg) {
        return new ReturnPageResult<>(data.getCode(), null, msg);
    }

    /**
     * 初始化
     *
     * @param data 数据
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnPageResult<T> illegal(PageResult<T> data) {
        return illegal(PARAM_ERROR, "");
    }

    /**
     * 初始化
     *
     * @param message 数据
     * @param <T>     类型
     * @return 结果
     */
    public static <T> ReturnPageResult<T> illegal(String message) {
        return new ReturnPageResult<>(PARAM_ERROR.getCode(), null, message);
    }

    /**
     * 初始化
     *
     * @param <T> 类型
     * @return 结果
     */
    public static <T> ReturnPageResult<T> illegal() {
        return new ReturnPageResult<>(PARAM_ERROR.getCode(), null, "");
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
