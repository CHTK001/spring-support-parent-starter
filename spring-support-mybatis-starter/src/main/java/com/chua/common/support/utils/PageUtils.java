package com.chua.common.support.utils;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

/**
 * MyBatis Plus 分页工具类
 * <p>
 * 提供将 List 集合转换为 IPage 对象的快捷方法，
 * 便于在服务层手动构造分页结果返回给前端。
 * </p>
 *
 * @author CH
 */
public class PageUtils {

    /**
     * 将 List 数据包装为 MyBatis Plus 的 IPage 对象
     *
     * @param data 数据列表
     * @param page 页码
     * @param size 页大小
     * @param <T>  数据元素类型
     * @return 包含数据的 IPage 实例
     */
    public static <T> IPage<T> page(List<T> data, int page, int size) {
        Page<T> pageResult = new Page<>(page, size);
        pageResult.setRecords(data);
        pageResult.setTotal(data == null ? 0L : data.size());
        return pageResult;
    }
}