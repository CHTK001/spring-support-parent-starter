package com.chua.starter.strategy.template;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;

/**
 * 限流策略配置
 *
 * @author CH
 * @since 2025-12-25
 */
@Data
@Builder
public class RateLimiterStrategyConfig {

    /**
     * 每个周期内允许的请求数
     */
    @Builder.Default
    private int limitForPeriod = 100;

    /**
     * 限流周期
     */
    @Builder.Default
    private Duration limitRefreshPeriod = Duration.ofSeconds(1);

    /**
     * 等待许可的超时时间
     */
    @Builder.Default
    private Duration timeoutDuration = Duration.ofMillis(500);

    /**
     * 创建默认配置
     */
    public static RateLimiterStrategyConfig defaults() {
        return RateLimiterStrategyConfig.builder().build();
    }
}
