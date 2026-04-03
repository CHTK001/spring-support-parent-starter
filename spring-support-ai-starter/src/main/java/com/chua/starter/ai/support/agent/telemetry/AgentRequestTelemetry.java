package com.chua.starter.ai.support.agent.telemetry;

import lombok.Builder;
import lombok.Getter;

/**
 * Agent 单次请求遥测记录。
 *
 * @author CH
 * @since 2026/04/03
 */
@Getter
@Builder(toBuilder = true)
public class AgentRequestTelemetry {

    /**
     * 请求标识。
     */
    private final String requestId;

    /**
     * 会话标识。
     */
    private final String sessionId;

    /**
     * Agent 名称。
     */
    private final String agentName;

    /**
     * 请求类型。
     */
    private final String requestType;

    /**
     * 提供商名称。
     */
    private final String provider;

    /**
     * 实际模型。
     */
    private final String model;

    /**
     * 推理强度。
     */
    private final String reasoningEffort;

    /**
     * 接口端点。
     */
    private final String endpoint;

    /**
     * 客户端类型。
     */
    private final String clientType;

    /**
     * User-Agent。
     */
    private final String userAgent;

    /**
     * token 指标。
     */
    private final AgentTokenMetrics tokens;

    /**
     * 费用指标。
     */
    private final AgentCostMetrics costs;

    /**
     * 首 token 延迟。
     */
    private final Long firstTokenLatencyMillis;

    /**
     * 总耗时。
     */
    private final Long durationMillis;

    /**
     * 请求开始时间。
     */
    private final Long startedAt;

    /**
     * 请求完成时间。
     */
    private final Long completedAt;

    /**
     * 是否成功。
     */
    private final boolean success;

    /**
     * 错误信息。
     */
    private final String error;
}
