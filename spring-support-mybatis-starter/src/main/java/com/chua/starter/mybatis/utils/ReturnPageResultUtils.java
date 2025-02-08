package com.chua.starter.mybatis.utils;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 分页工具类
 *
 * @author CH
 */
public class ReturnPageResultUtils extends PageResultUtils{

    static final IPage<?> EMPTY = new Page<>();

    private ReturnPageResultUtils() {
    }


    /**
     * 获取空分页
     *
     * @return IPage
     */
    @SuppressWarnings("ALL")
    public static IPage empty() {
        return EMPTY;
    }

    /**
     * 获取空分页
     *
     * @return IPage
     */
    @SuppressWarnings("ALL")
    public static <T> IPage<T> empty(Class<T> type) {
        return (IPage<T>) EMPTY;
    }
}
