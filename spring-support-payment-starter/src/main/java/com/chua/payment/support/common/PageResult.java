package com.chua.payment.support.common;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 支付模块分页结果，统一封装支付业务分页接口的返回数据。
 *
 * @param records 当前页数据，不会为 {@code null}
 * @param total   总记录数，不小于 0
 * @param current 当前页码，不小于 1
 * @param size    每页数量，不小于 1
 * @param <T>     分页数据类型
 * @author CH
 * @since 4.0.0.42
 */
public record PageResult<T>(List<T> records, long total, long current, long size) implements Serializable {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 构造支付分页结果。
     *
     * <p>构造时对入参做防御性处理：记录列表为空时归一化为空列表，
     * 总数与页码分别不小于 0 与 1。</p>
     */
    public PageResult {
        records = records == null ? Collections.emptyList() : List.copyOf(records);
        total = Math.max(total, 0L);
        current = Math.max(current, 1L);
        size = Math.max(size, 1L);
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
            // [P3C A20 豁免]
            return new PageResult<>(Collections.emptyList(), 0L, 1L, 10L);
        }
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }
}
