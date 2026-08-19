package com.chua.common.support.span.trace;

/**
 * 链路追踪上下文
 *
 * @author CH
 */
public class TrackContext {

    /**
     * 线程本地变量，用于存储当前线程的链路追踪 ID
     */
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    /**
     * 获取当前线程的链路追踪 ID
     *
     * @return traceId，未设置时返回 null
     */
    public static String getTraceId() {
        return TRACE_ID.get();
    }

    /**
     * 设置当前线程的链路追踪 ID
     *
     * @param traceId 链路追踪 ID
     */
    public static void setTraceId(String traceId) {
        TRACE_ID.set(traceId);
    }

    /**
     * 获取当前线程的链路 ID（与 getTraceId 功能相同）
     *
     * @return 链路 ID
     */
    public static String getLinkId() {
        return TRACE_ID.get();
    }

    /**
     * 设置当前线程的链路 ID（与 setTraceId 功能相同）
     *
     * @param linkId 链路 ID
     */
    public static void setLinkId(String linkId) {
        TRACE_ID.set(linkId);
    }

    /**
     * 清除当前线程的链路追踪 ID
     */
    public static void clear() {
        TRACE_ID.remove();
    }

    /**
     * 移除当前线程的链路追踪 ID（与 clear 功能相同）
     */
    public static void remove() {
        TRACE_ID.remove();
    }
}
