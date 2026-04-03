package com.chua.starter.ai.support.agent.telemetry;

import lombok.Builder;
import lombok.Getter;

/**
 * Agent 单次请求 token 指标。
 *
 * @author CH
 * @since 2026/04/03
 */
@Getter
@Builder(toBuilder = true)
public class AgentTokenMetrics {

    /**
     * 输入 token。
     */
    private final Integer inputTokens;

    /**
     * 输出 token。
     */
    private final Integer outputTokens;

    /**
     * 缓存 token。
     */
    private final Integer cacheTokens;
}
