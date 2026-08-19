package com.chua.common.support.lang.code;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页结果类，封装分页查询结果
 *
 * @param <T> 数据类型
 * @author CH
 */
public class PageResult<T> implements Serializable {

    /** Serial版本UID */
    private static final long serialVersionUID = 1L;

    /**
     * 当前页码
     */
    private long current;

    /**
     * 每页大小
     */
    private long size;

    /**
     * 总记录数
     */
    private long total;

    /**
     * 数据列表
     */
    private List<T> records;

    /**
     * 创建分页结果
     *
     * @param <T> 数据类型
     * @return 分页结果实例
     */
    public static <T> PageResult<T> of() {
        return new PageResult<>();
    }

    /**
     * 获取当前页码
     *
     * @return 当前页码
     */
    public long getCurrent() {
        return current;
    }

    /**
     * 设置当前页码
     *
     * @param current 当前页码
     * @return 当前实例
     */
    public PageResult<T> setCurrent(long current) {
        this.current = current;
        return this;
    }

    /**
     * 获取每页大小
     *
     * @return 每页大小
     */
    public long getSize() {
        return size;
    }

    /**
     * 设置每页大小
     *
     * @param size 每页大小
     * @return 当前实例
     */
    public PageResult<T> setSize(long size) {
        this.size = size;
        return this;
    }

    /**
     * 获取总记录数
     *
     * @return 总记录数
     */
    public long getTotal() {
        return total;
    }

    /**
     * 设置总记录数
     *
     * @param total 总记录数
     * @return 当前实例
     */
    public PageResult<T> setTotal(long total) {
        this.total = total;
        return this;
    }

    /**
     * 获取数据列表
     *
     * @return 数据列表
     */
    public List<T> getRecords() {
        return records;
    }

    /**
     * 设置数据列表
     *
     * @param records 数据列表
     * @return 当前实例
     */
    public PageResult<T> setRecords(List<T> records) {
        this.records = records;
        return this;
    }

    public static <T> PageResult<T> builder() {
        return of();
    }

    public static <T> PageResult<T> empty() {
        PageResult<T> result = new PageResult<>();
        result.records = Collections.emptyList();
        return result;
    }

    public PageResult<T> pageNo(long pageNo) {
        this.current = pageNo;
        return this;
    }

    public PageResult<T> totalPages(long totalPages) {
        return this;
    }

    public PageResult<T> pageSize(long pageSize) {
        this.size = pageSize;
        return this;
    }

    public PageResult<T> total(long total) {
        this.total = total;
        return this;
    }

    public PageResult<T> data(List<T> records) {
        this.records = records;
        return this;
    }

    public T build() {
        return (T) this;
    }
}
