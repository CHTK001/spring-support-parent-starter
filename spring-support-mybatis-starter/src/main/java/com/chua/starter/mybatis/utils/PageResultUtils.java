package com.chua.starter.mybatis.utils;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.lang.code.ReturnPageResult;

import java.util.List;

/**
 * 分页工具类
 *
 * @author CH
 */
public class PageResultUtils {
    /**
     * 结果
     * @param result IPage
     * @return PageResult
     * @param <T> 类型
     */
    public static <R, T> PageResult<R> transfer(IPage<T> result, Class<R> clazz) {
        if(null == result) {
            return PageResult.empty();
        }

        List<T> data = result.getRecords();
        List<R> transfer = null;
        if (null != clazz) {
            transfer = BeanUtils.copyPropertiesList(data, clazz);
        } else {
            transfer = (List<R>) data;
        }

        return PageResult.<R>builder()
                .pageNo((int) result.getCurrent())
                .totalPages((int) result.getPages())
                .pageSize((int) result.getSize())
                .total(result.getTotal())
                .data(transfer)
                .build();
    }
    /**
     * 结果
     * @param result IPage
     * @return PageResult
     * @param <T> 类型
     */
    public static <T> PageResult<T> transfer(List<T> result, int pageNo, int pageSize, int total) {
        if (null == result) {
            return PageResult.empty();
        }
        return PageResult.<T>builder()
                .pageNo(pageNo)
                .totalPages(Math.floorDiv(total, pageSize) + 1)
                .pageSize(pageSize)
                .total(total)
                .data(result)
                .build();
    }

    /**
     * 成功
     * @param result IPage
     * @return ReturnPageResult
     * @param <T> 类型
     */
    public static <T> ReturnPageResult<T> ok(IPage<T> result) {
        return ReturnPageResult.ok(transfer(result, null));
    }

    /**
     * 成功
     *
     * @param result IPage
     * @param <T>    类型
     * @return ReturnPageResult
     */
    public static <R, T> ReturnPageResult<R> ok(IPage<T> result, Class<R> clazz) {
        return ReturnPageResult.ok(transfer(result, clazz));
    }
    /**
     * 成功
     * @param result IPage
     * @return ReturnPageResult
     * @param <T> 类型
     */
    public static <T> ReturnPageResult<T> ok(List<T> result, int pageNo, int pageSize) {
        return ok(result, pageNo, pageSize, 0);
    }

    /**
     * 成功
     *
     * @param result IPage
     * @param <T>    类型
     * @return ReturnPageResult
     */
    public static <T> ReturnPageResult<T> ok(List<T> result, int pageNo, int pageSize, int total) {
        return ReturnPageResult.ok(transfer(result, pageNo, pageSize, total));
    }

    /**
     * 失败
     * @param result IPage
     * @return ReturnPageResult
     * @param <T> 类型
     */
    public static <T> ReturnPageResult<T> error(IPage<T> result) {
        return ReturnPageResult.error(transfer(result, null));
    }
}
