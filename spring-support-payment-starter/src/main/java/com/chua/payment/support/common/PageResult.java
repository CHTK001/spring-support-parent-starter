package com.chua.payment.support.common;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 支付模块分页结果，统一封装支付业务分页接口的返回数据。
 *
 * @param <T> 分页数据类型
 * @author CH
 * @since 4.0.0.42
 */
public class PageResult<T> implements Serializable {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 当前页数据。
     */
    private List<T> records;

    /**
     * 总记录数。
     */
    private long total;

    /**
     * 当前页码。
     */
    private long current;

    /**
     * 每页数量。
     */
    private long size;

    /**
     * 构造支付分页结果。
     *
     * <p>构造时对入参做防御性处理：记录列表为空时归一化为空列表，
     * 总数与页码分别不小于 0 与 1。</p>
     *
     * @param records 当前页数据，不能为 {@code null}
     * @param total   总记录数，不能为负数
     * @param current 当前页码，必须大于零
     * @param size    每页数量，必须大于零
     */
    public PageResult(List<T> records, long total, long current, long size) {
        this.records = records == null ? Collections.emptyList() : List.copyOf(records);
        this.total = Math.max(total, 0L);
        this.current = Math.max(current, 1L);
        this.size = Math.max(size, 1L);
    }

    /**
     * 由 MyBatis-Plus 分页对象创建支付分页结果。
     *
     * @param page MyBatis-Plus 分页对象，不能为空
     * @param <T>  分页数据类型
     * @return 支付分页结果
     */
    public static <T> PageResult<T> of(IPage<T> page) {
        if (page == null) {
            return new PageResult<>(Collections.emptyList(), 0L, 1L, 10L);
        }
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 获取当前页数据。
     *
     * @return 当前页数据，不会为 {@code null}
     */
    public List<T> getRecords() {
        return records;
    }

    /**
     * 设置当前页数据。
     *
     * @param records 当前页数据，允许为 {@code null}
     */
    public void setRecords(List<T> records) {
        this.records = records == null ? Collections.emptyList() : List.copyOf(records);
    }

    /**
     * 获取总记录数。
     *
     * @return 总记录数
     */
    public long getTotal() {
        return total;
    }

    /**
     * 设置总记录数。
     *
     * @param total 总记录数，不能为负数
     */
    public void setTotal(long total) {
        this.total = Math.max(total, 0L);
    }

    /**
     * 获取当前页码。
     *
     * @return 当前页码
     */
    public long getCurrent() {
        return current;
    }

    /**
     * 设置当前页码。
     *
     * @param current 当前页码，必须大于零
     */
    public void setCurrent(long current) {
        this.current = Math.max(current, 1L);
    }

    /**
     * 获取每页数量。
     *
     * @return 每页数量
     */
    public long getSize() {
        return size;
    }

    /**
     * 设置每页数量。
     *
     * @param size 每页数量，必须大于零
     */
    public void setSize(long size) {
        this.size = Math.max(size, 1L);
    }
}
