package com.chua.starter.monitor.support.model;

import java.util.List;

/**
 * 画像分区分页响应：携带真实条数，替代原有的纯 List 返回。
 *
 * @param <T> 行类型
 * @author CH
 * @since 4.0.0.42
 */
public record SectionPage<T>(List<T> rows, long total, int page, int size, boolean truncated) {

    /**
     * of
     * @param rows 行数据
     * @param total 总条数
     * @param page 当前页码
     * @param size 每页条数
     * @return section page
     */
    public static <T> SectionPage<T> of(List<T> rows, long total, int page, int size) {
        return new SectionPage<>(rows, total, page, size, false);
    }

    /**
     * truncated
     * @param rows 行数据
     * @param total 总条数
     * @param page 当前页码
     * @param size 每页条数
     * @return section page
     */
    public static <T> SectionPage<T> truncated(List<T> rows, long total, int page, int size) {
        return new SectionPage<>(rows, total, page, size, true);
    }
}
