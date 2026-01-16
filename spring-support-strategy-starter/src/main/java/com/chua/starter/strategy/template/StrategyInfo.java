package com.chua.starter.strategy.template;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 策略信息
 *
 * @author CH
 * @since 2025-12-25
 */
@Data
@Builder
public class StrategyInfo {

    /**
     * 策略名称
     */
    private String name;

    /**
     * 策略类型
     */
    private StrategyType type;

    /**
     * 策略配置
     */
    private Object config;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 是否启用
     */
    @Builder.Default
    private boolean enabled = true;

    /**
     * 策略类型枚举
     */
    public enum StrategyType {
        /**
         * 限流
         */
        RATE_LIMITER,
        /**
         * 熔断
         */
        CIRCUIT_BREAKER,
        /**
         * 防抖
         */
        DEBOUNCE,
        /**
         * 重试
         */
        RETRY
    }
}
