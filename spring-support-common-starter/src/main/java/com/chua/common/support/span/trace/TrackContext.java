package com.chua.common.support.span.trace;

/**
 * 链路追踪上下文
 *
 * @author CH
 * @since 4.0.0
 */
public class TrackContext {

    /**
     * 线程本地变量，用于存储当前线程的链路追踪 标识
     */
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    /**
     * 获取当前线程的链路追踪 标识
     *
     * @return traceId，未设置时返回 空
     */
    public static String getTraceId() {
        return TRACE_ID.get();
    }

    /**
     * 设置当前线程的链路追踪 标识
     *
     * @param traceId 链路追踪 标识
     */
    public static void setTraceId(String traceId) {
        TRACE_ID.set(traceId);
    }

    /**
     * 获取当前线程的链路 标识（与 获取追踪标识 功能相同）
     *
     * @return 链路 标识
     */
    public static String getLinkId() {
        return TRACE_ID.get();
    }

    /**
     * 设置当前线程的链路 标识（与 设置追踪标识 功能相同）
     *
     * @param linkId 链路 标识
     */
    public static void setLinkId(String linkId) {
        TRACE_ID.set(linkId);
    }

    /**
     * 清除当前线程的链路追踪 标识
     */
    public static void clear() {
        TRACE_ID.remove();
    }

    /**
     * 移除当前线程的链路追踪 标识（与 clear 功能相同）
     */
    public static void remove() {
        TRACE_ID.remove();
    }
}
