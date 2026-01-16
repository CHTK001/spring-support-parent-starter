package com.chua.starter.strategy.template;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;

/**
 * 熔断策略配置
 *
 * @author CH
 * @since 2025-12-25
 */
@Data
@Builder
public class CircuitBreakerStrategyConfig {

    /**
     * 失败率阈值（百分比）
     */
    @Builder.Default
    private float failureRateThreshold = 50.0f;

    /**
     * 慢调用率阈值（百分比）
     */
    @Builder.Default
    private float slowCallRateThreshold = 100.0f;

    /**
     * 慢调用时间阈值
     */
    @Builder.Default
    private Duration slowCallDurationThreshold = Duration.ofSeconds(60);

    /**
     * 半开状态下允许的调用数
     */
    @Builder.Default
    private int permittedNumberOfCallsInHalfOpenState = 10;

    /**
     * 滑动窗口大小
     */
    @Builder.Default
    private int slidingWindowSize = 100;

    /**
     * 最小调用次数
     */
    @Builder.Default
    private int minimumNumberOfCalls = 10;

    /**
     * 等待开放持续时间
     */
    @Builder.Default
    private Duration waitDurationInOpenState = Duration.ofSeconds(60);

    /**
     * 创建默认配置
     */
    public static CircuitBreakerStrategyConfig defaults() {
        return CircuitBreakerStrategyConfig.builder().build();
    }
}
