package com.chua.starter.eduai.support.util;

/**
 * 分页边界收敛。
 *
 * <p>分页参数直接来自请求，此前只做了「小于 1 时回落默认值」，
 * <b>没有上限</b>：{@code pageSize=1000000} 会被原样接受并尝试把整表读进内存，
 * 数据量上去就是内存与耗时风险（生产级测试记录：全仓 0 处 {@code maxLimit}）。</p>
 *
 * <p>这里统一收敛：页码小于 1 视为第 1 页；每页条数小于 1 用默认值、
 * 超过上限则封顶。所有分页入口都应经由此处，避免各写一遍。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class PageBounds {

    /**
     * 每页条数上限。
     *
     * <p>取 500：业务页面最大只提供 100 条/页，导出类场景也用不到更大；
     * 再大只会把整表拖进内存。</p>
     */
    public static final int MAX_PAGE_SIZE = 500;

    /**
     * 每页条数缺省值。
     */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * 工具类禁止实例化。
     */
    private PageBounds() {
    }

    /**
     * 收敛页码：小于 1 或为空时视为第 1 页。
     *
     * @param pageNum 原始页码，可为空
     * @return 合法页码（&gt;= 1）
     */
    public static long pageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1L : pageNum;
    }

    /**
     * 收敛页码（long 入参）。
     *
     * @param pageNum 原始页码
     * @return 合法页码（&gt;= 1）
     */
    public static long pageNum(Long pageNum) {
        return pageNum == null || pageNum < 1 ? 1L : pageNum;
    }

    /**
     * 收敛页码（long 原始值）。
     *
     * @param pageNum 原始页码
     * @return 合法页码（&gt;= 1）
     */
    public static long pageNum(long pageNum) {
        return pageNum < 1 ? 1L : pageNum;
    }

    /**
     * 收敛每页条数：小于 1 用默认值，超过上限则封顶。
     *
     * @param pageSize 原始每页条数，可为空
     * @return 合法每页条数（1 ~ {@link #MAX_PAGE_SIZE}）
     */
    public static long pageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /**
     * 收敛每页条数（long 入参）。
     *
     * @param pageSize 原始每页条数
     * @return 合法每页条数（1 ~ {@link #MAX_PAGE_SIZE}）
     */
    public static long pageSize(Long pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /**
     * 收敛每页条数（long 原始值）。
     *
     * @param pageSize 原始每页条数
     * @return 合法每页条数（1 ~ {@link #MAX_PAGE_SIZE}）
     */
    public static long pageSize(long pageSize) {
        if (pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
