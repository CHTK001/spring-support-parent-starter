package com.chua.starter.mybatis.utils;

import com.baomidou.mybatisplus.core.metadata.IPage;
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
    public static <T> PageResult<T> transfer(IPage<T> result) {
        if(null == result) {
            return PageResult.empty();
        }
        return PageResult.<T>builder()
                .pageNo((int) result.getCurrent())
                .totalPages((int) result.getPages())
                .pageSize((int) result.getSize())
                .total(result.getTotal())
                .data(result.getRecords())
                .build();
    }
    /**
     * 结果
     * @param result IPage
     * @return PageResult
     * @param <T> 类型
     */
    public static <T> PageResult<T> transfer(List<T> result, int pageNo, int pageSize, int pages) {
        if (null == result) {
            return PageResult.empty();
        }
        return PageResult.<T>builder()
                .pageNo(pageNo)
                .totalPages(pages)
                .pageSize(pageSize)
                .total(result.size())
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
        return ReturnPageResult.ok(transfer(result));
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
    public static <T> ReturnPageResult<T> ok(List<T> result, int pageNo, int pageSize, int pages) {
        return ReturnPageResult.ok(transfer(result, pageNo, pageSize, pages));
    }

    /**
     * 失败
     * @param result IPage
     * @return ReturnPageResult
     * @param <T> 类型
     */
    public static <T> ReturnPageResult<T> error(IPage<T> result) {
        return ReturnPageResult.error(transfer(result));
    }
}
