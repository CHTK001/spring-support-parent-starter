package com.chua.starter.common.support.result;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 分頁結果
 * @author CH
 */
@Data
@Builder
public class PageResult<T> {
    /**
     * 页码
     */
    private int page;
    /**
     * 每页数量
     */
    private int pageSize;
    /**
     * 总页数
     */
    private int totalPages;
    /**
     * 总数
     */
    private long total;
    /**
     * 數據
     */
    private List<T> data;

    private static final PageResult EMPTY = PageResult.builder().build();

    public static <T> PageResult<T> empty() {
        return EMPTY;
    }
}
