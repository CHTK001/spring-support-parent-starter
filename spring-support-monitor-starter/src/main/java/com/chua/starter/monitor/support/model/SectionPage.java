package com.chua.starter.monitor.support.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 画像分区分页响应：携带真实条数，替代原有的纯 List 返回。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SectionPage<T> {

    /** 当前页数据 */
    private List<T> rows;

    /** 过滤后（关键字命中的）总行数 */
    private long total;

    /** 当前页码（从 1 开始） */
    private int page;

    /** 每页条数 */
    private int size;

    /** 是否已达通道硬上限（total == MAX_SECTION_SIZE），提示前端数据被截断 */
    private boolean truncated;

    /**
     * of
     * @param page 整数
     * @param rows 参数
     * @param size 获取数量
     * @param total 长整数
     * @return 返回值
    */
    public static <T> SectionPage<T> of(List<T> rows, long total, int page, int size) {
        return new SectionPage<>(rows, total, page, size, false);
    }

    /**
     * truncated
     * @param page 整数
     * @param rows 参数
     * @param size 获取数量
     * @param total 长整数
     * @return 返回值
    */
    public static <T> SectionPage<T> truncated(List<T> rows, long total, int page, int size) {
        return new SectionPage<>(rows, total, page, size, true);
    }
}
