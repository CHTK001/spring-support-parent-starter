package com.chua.starter.oauth.client.support.trace;

import lombok.Getter;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * o认证 链路追踪上下文
 * <p>
 * 管理整个请求的追踪信息，包括 追踪标识、spanid 和各节点耗时
 * </p>
 *
 * @since 2024/12/28
 * @author CH
 */
public class TraceContext {

    /** Mdc_追踪_标识 */
    private static final String MDC_TRACE_ID = "traceId";
    /** Mdc_span_标识 */
    private static final String MDC_SPAN_ID = "spanId";

    /**
     * 线程本地存储
     */
    private static final ThreadLocal<TraceContext> CONTEXT_HOLDER = new ThreadLocal<>();

    /**
     * 追踪标识（全局唯一）
     */
    @Getter
    /** 跟踪标识 */
    private final String traceId;

    /**
     * 当前 Span 标识 计数器
     */
    private final AtomicInteger spanCounter = new AtomicInteger(0);

    /**
     * 请求开始时间戳
     */
    @Getter
    /** 开始时间戳 */
    private final long startTimestamp;

    /**
     * 追踪的 Span 列表
     */
    @Getter
    /** Spans */
    private final List<TraceSpan> spans = new ArrayList<>();

    /**
     * 当前活跃的 Span
     */
    private TraceSpan currentSpan;

    /**
     * 客户端应用名称
     */
    @Getter
    /** 客户端APP名称 */
    private String clientAppName;

    /**
     * 客户端IP
     */
    @Getter
    /** 客户端IP */
    private String clientIp;

    /**
     * 私有构造
     * @param traceId 追踪标识
     */
    private TraceContext(String traceId) {
        this.traceId = traceId != null ? traceId : UUID.randomUUID().toString().replace("-", "");
        this.startTimestamp = System.currentTimeMillis();
    }

    /**
     * 创建新的追踪上下文
     *
     * @return 追踪上下文
     */
    public static TraceContext create() {
        TraceContext context = new TraceContext(null);
        CONTEXT_HOLDER.set(context);
        MDC.put(MDC_TRACE_ID, context.getTraceId());
        return context;
    }

    /**
     * 创建追踪上下文（使用指定的 追踪标识）
     *
     * @param traceId 追踪标识
     * @return 追踪上下文
     */
    public static TraceContext create(String traceId) {
        TraceContext context = new TraceContext(traceId);
        CONTEXT_HOLDER.set(context);
        MDC.put(MDC_TRACE_ID, context.getTraceId());
        return context;
    }

    /**
     * 获取当前追踪上下文
     *
     * @return 追踪上下文，可能为 空
     */
    public static TraceContext current() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 清除当前追踪上下文
     */
    public static void clear() {
        CONTEXT_HOLDER.remove();
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_SPAN_ID);
    }

    /**
     * 设置客户端信息
     *
     * @param appName 应用名称
     * @param ip      客户端IP
     * @return this
     */
    public TraceContext clientInfo(String appName, String ip) {
        this.clientAppName = appName;
        this.clientIp = ip;
        return this;
    }

    /**
     * 生成新的 Span 标识
     *
     * @return Span 标识
     */
    public String nextSpanId() {
        return traceId.substring(0, 8) + "-" + spanCounter.incrementAndGet();
    }

    /**
     * 开始一个新的 Span
     *
     * @param spanName Span 名称
     * @return TraceSpan
     */
    public TraceSpan startSpan(String spanName) {
        String spanId = nextSpanId();
        String parentSpanId = currentSpan != null ? currentSpan.getSpanId() : null;
        TraceSpan span = new TraceSpan(spanId, parentSpanId, spanName);
        spans.add(span);
        currentSpan = span;
        MDC.put(MDC_SPAN_ID, spanId);
        return span;
    }

    /**
     * 结束当前 Span
     */
    public void endSpan() {
        if (currentSpan != null) {
            currentSpan.end();
            // 回退到父 Span
            String parentSpanId = currentSpan.getParentSpanId();
            if (parentSpanId != null) {
                currentSpan = spans.stream()
                        .filter(s -> s.getSpanId().equals(parentSpanId))
                        .findFirst()
                        .orElse(null);
            } else {
                currentSpan = null;
            }
            MDC.put(MDC_SPAN_ID, currentSpan != null ? currentSpan.getSpanId() : "");
        }
    }

    /**
     * 获取总耗时（毫秒）
     *
     * @return 总耗时
     */
    public long getTotalCostMs() {
        return System.currentTimeMillis() - startTimestamp;
    }

    /**
     * 获取所有 Span 的耗时摘要
     *
     * @return 耗时摘要 JSON 字符串
     */
    public String getSpansSummary() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < spans.size(); i++) {
            TraceSpan span = spans.get(i);
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{\"name\":\"").append(span.getSpanName())
                    .append("\",\"cost\":").append(span.getCostMs()).append("}");
        }
        sb.append("]");
        return sb.toString();
    }
}
