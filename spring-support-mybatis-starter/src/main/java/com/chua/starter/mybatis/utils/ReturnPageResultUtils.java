package com.chua.starter.mybatis.utils;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.utils.PageUtils;
import com.chua.starter.common.support.lang.ReturnPageResult;

import java.util.List;

/**
 * 高级分页工具类，继承 {@link PageResultUtils} 并扩展静态分页功能。
 *
 * @author CH
 * @since 4.0.0
 */
public class ReturnPageResultUtils {

    /**
     * 空分页实例
     */
    private static final IPage<?> EMPTY = new Page<>();

    /** 创建 申报page结果工具 实例 */
    private ReturnPageResultUtils() {
    }

    /**
    * 获取空分页对象
    *
    * @return 空分页实例
    */
    @SuppressWarnings("ALL")
    public static IPage<?> empty() {
        return EMPTY;
    }

    /**
     * 获取类型安全的空分页对象
     *
     * @param type 元素类型
     * @param <T>  元素类型
     * @return 空分页实例
     */
    @SuppressWarnings("all")
    public static <T> IPage<T> empty(Class<T> type) {
        return (IPage<T>) EMPTY;
    }

    /**
     * 创建静态分页结果（将 列表 数据及分页参数封装为 申报page结果）
     *
     * @param data  数据列表
     * @param page 页码
     * @param size  页大小
     * @param <T>   数据类型
     * @return 包装后的分页结果
     */
    public static <T> ReturnPageResult<T> staticPage(List<T> data, int page, int size) {
        IPage<T> ipage = com.chua.common.support.utils.PageUtils.page(data, page, size);
        PageResult<T> pageResult = PageResult.<T>builder()
                .pageNo((int) ipage.getCurrent())
                .pageSize((int) ipage.getSize())
                .totalPages((int) ipage.getPages())
                .total(ipage.getTotal())
                .data(ipage.getRecords())
                .build();
        return ReturnPageResult.ok(pageResult);
    }

    /**
     * 创建分页（内部使用）
     *
     * @param list 数据列表
     * @param page 页码
     * @param size  页大小
     * @param <T>   数据类型
     * @return 填充数据的 ipage
     */
    public static <T> IPage<T> page(List<T> list, int page, int size) {
        return com.chua.common.support.utils.PageUtils.page(list, page, size);
    }
}
