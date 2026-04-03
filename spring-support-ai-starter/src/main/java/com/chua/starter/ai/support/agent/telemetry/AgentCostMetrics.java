package com.chua.starter.ai.support.agent.telemetry;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Agent 单次请求费用指标。
 *
 * @author CH
 * @since 2026/04/03
 */
@Getter
@Builder(toBuilder = true)
public class AgentCostMetrics {

    /**
     * 输入成本。
     */
    private final BigDecimal inputCost;

    /**
     * 输出成本。
     */
    private final BigDecimal outputCost;

    /**
     * 缓存成本。
     */
    private final BigDecimal cacheCost;

    /**
     * 总成本。
     */
    private final BigDecimal totalCost;

    /**
     * 输入单价。
     */
    private final BigDecimal inputUnitPrice;

    /**
     * 输出单价。
     */
    private final BigDecimal outputUnitPrice;

    /**
     * 价格倍率。
     */
    private final BigDecimal multiplier;

    /**
     * 货币单位。
     */
    private final String currency;
}
