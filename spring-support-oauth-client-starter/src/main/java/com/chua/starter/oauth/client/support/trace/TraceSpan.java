package com.chua.starter.oauth.client.support.trace;

import lombok.Getter;

/**
 * OAuth 链路追踪 Span
 * <p>
 * 表示追踪链路中的一个节点，记录该节点的耗时信息
 * </p>
 *
 * @author CH
 * @since 2024/12/28
 */
@Getter
public class TraceSpan {

    /**
     * Span ID
     */
    private final String spanId;

    /**
     * 父 Span ID
     */
    private final String parentSpanId;

    /**
     * Span 名称
     */
    private final String spanName;

    /**
     * 开始时间（毫秒）
     */
    private final long startTime;

    /**
     * 结束时间（毫秒）
     */
    private long endTime;

    /**
     * 是否已结束
     */
    private boolean finished;

    /**
     * 附加信息
     */
    private String extra;

    /**
     * 构造函数
     *
     * @param spanId       Span ID
     * @param parentSpanId 父 Span ID
     * @param spanName     Span 名称
     */
    public TraceSpan(String spanId, String parentSpanId, String spanName) {
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.spanName = spanName;
        this.startTime = System.currentTimeMillis();
        this.finished = false;
    }

    /**
     * 结束 Span
     */
    public void end() {
        if (!finished) {
            this.endTime = System.currentTimeMillis();
            this.finished = true;
        }
    }

    /**
     * 设置附加信息
     *
     * @param extra 附加信息
     * @return this
     */
    public TraceSpan extra(String extra) {
        this.extra = extra;
        return this;
    }

    /**
     * 获取耗时（毫秒）
     *
     * @return 耗时
     */
    public long getCostMs() {
        if (finished) {
            return endTime - startTime;
        }
        return System.currentTimeMillis() - startTime;
    }

    /**
     * 转换为 JSON 字符串
     *
     * @return JSON 字符串
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"spanId\":\"").append(spanId).append("\"");
        if (parentSpanId != null) {
            sb.append(",\"parentSpanId\":\"").append(parentSpanId).append("\"");
        }
        sb.append(",\"name\":\"").append(spanName).append("\"");
        sb.append(",\"startTime\":").append(startTime);
        sb.append(",\"endTime\":").append(endTime);
        sb.append(",\"costMs\":").append(getCostMs());
        if (extra != null) {
            sb.append(",\"extra\":\"").append(extra.replace("\"", "\\\"")).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %dms", spanId, spanName, getCostMs());
    }
}
