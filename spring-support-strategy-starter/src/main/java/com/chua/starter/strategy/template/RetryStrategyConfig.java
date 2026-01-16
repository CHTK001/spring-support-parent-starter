package com.chua.starter.strategy.template;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * 重试策略配置
 *
 * @author CH
 * @since 2025-12-25
 */
@Data
@Builder
public class RetryStrategyConfig {

    /**
     * 最大重试次数
     */
    @Builder.Default
    private int maxAttempts = 3;

    /**
     * 重试间隔
     */
    @Builder.Default
    private Duration waitDuration = Duration.ofMillis(500);

    /**
     * 需要重试的异常类型
     */
    @Builder.Default
    private List<Class<? extends Throwable>> retryExceptions = Collections.singletonList(Exception.class);

    /**
     * 忽略的异常类型（不重试）
     */
    @Builder.Default
    private List<Class<? extends Throwable>> ignoreExceptions = Collections.emptyList();

    /**
     * 是否启用指数退避
     */
    @Builder.Default
    private boolean exponentialBackoff = false;

    /**
     * 指数退避乘数
     */
    @Builder.Default
    private double exponentialBackoffMultiplier = 2.0;

    /**
     * 创建默认配置
     */
    public static RetryStrategyConfig defaults() {
        return RetryStrategyConfig.builder().build();
    }
}
