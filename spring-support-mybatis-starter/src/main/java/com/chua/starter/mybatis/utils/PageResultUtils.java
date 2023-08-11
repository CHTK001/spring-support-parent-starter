package com.chua.starter.mybatis.utils;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chua.starter.common.support.result.PageResult;
import com.chua.starter.common.support.result.ReturnPageResult;

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
        return PageResult.<T>builder()
                .pageSize((int) result.getSize())
                .total(result.getTotal())
                .data(result.getRecords())
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
     * 失败
     * @param result IPage
     * @return ReturnPageResult
     * @param <T> 类型
     */
    public static <T> ReturnPageResult<T> error(IPage<T> result) {
        return ReturnPageResult.error(transfer(result));
    }
}
