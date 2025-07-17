package com.chua.starter.plugin.store;

import lombok.Data;

import java.util.List;

/**
 * 分页结果
 * 
 * @author CH
 * @since 2025/1/16
 */
@Data
public class PageResult<T> {

    /**
     * 当前页数据
     */
    private List<T> content;

    /**
     * 当前页码（从1开始）
     */
    private int page;

    /**
     * 每页大小
     */
    private int size;

    /**
     * 总记录数
     */
    private long total;

    /**
     * 总页数
     */
    private int totalPages;

    /**
     * 是否有下一页
     */
    private boolean hasNext;

    /**
     * 是否有上一页
     */
    private boolean hasPrevious;

    /**
     * 是否为第一页
     */
    private boolean isFirst;

    /**
     * 是否为最后一页
     */
    private boolean isLast;

    /**
     * 构造函数
     */
    public PageResult() {
    }

    /**
     * 构造函数
     * 
     * @param content 当前页数据
     * @param page 当前页码
     * @param size 每页大小
     * @param total 总记录数
     */
    public PageResult(List<T> content, int page, int size, long total) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.total = total;
        
        // 计算总页数
        this.totalPages = (int) Math.ceil((double) total / size);
        
        // 计算分页状态
        this.hasNext = page < totalPages;
        this.hasPrevious = page > 1;
        this.isFirst = page == 1;
        this.isLast = page == totalPages || totalPages == 0;
    }

    /**
     * 创建分页结果
     * 
     * @param content 当前页数据
     * @param page 当前页码
     * @param size 每页大小
     * @param total 总记录数
     * @return 分页结果
     */
    public static <T> PageResult<T> of(List<T> content, int page, int size, long total) {
        return new PageResult<>(content, page, size, total);
    }

    /**
     * 创建空分页结果
     * 
     * @param page 当前页码
     * @param size 每页大小
     * @return 空分页结果
     */
    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(List.of(), page, size, 0);
    }

    /**
     * 获取当前页记录数
     * 
     * @return 当前页记录数
     */
    public int getNumberOfElements() {
        return content != null ? content.size() : 0;
    }

    /**
     * 检查是否为空
     * 
     * @return 是否为空
     */
    public boolean isEmpty() {
        return content == null || content.isEmpty();
    }
}
