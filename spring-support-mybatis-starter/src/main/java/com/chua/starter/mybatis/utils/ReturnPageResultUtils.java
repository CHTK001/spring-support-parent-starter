package com.chua.starter.mybatis.utils;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.core.utils.PageUtils;
import com.chua.starter.mybatis.page.PagePreconditioning;

import java.util.List;

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

    /**
     * 预处理
     *
     * @param page 分页
     */
    public static <T> PagePreconditioning<T> preconditioning(IPage<T> page) {
        return new PagePreconditioning<>(page);
    }


    /**
     * 静态分页
     *
     * @param data 数据
     * @param page 页码
     * @param size 页大小
     * @return 分页结果
     */
    public static <T> ReturnPageResult<T> staticPage(List<T> data, int page, int size) {
        return PageResultUtils.ok(createPage(PageUtils.page(data, page, size), page, size));
    }

    /**
     * 创建分页
     *
     * @param page1 数据
     * @param page  页码
     * @param size  页大小
     * @param <T>   泛型
     * @return 分页
     */
    private static <T> IPage<T> createPage(List<T> page1, int page, int size) {
        return new Page<T>(page, size).setRecords(page1);
    }
}
