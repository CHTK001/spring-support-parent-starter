package com.chua.starter.spider.dto;

import lombok.Data;

import java.util.List;

/**
 * 分页结果对象。
 *
 * <p>字段命名对齐前端 {@code PageResult} 类型：records/total/totalPages 等。</p>
 *
 * @param <T> 数据项类型
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class PageResultVO<T> {
    /**
     * 当前页码
     */
    private long pageNo;
    /**
     * 每页条数
     */
    private long pageSize;
    /**
     * 总条数
     */
    private long total;
    /**
     * 总页数
     */
    private long totalPages;
    /**
     * 当前页数据
     */
    private List<T> records;
    /**
     * 数据列表（兼容别名）
     */
    private List<T> data;
}