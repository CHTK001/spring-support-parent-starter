package com.chua.starter.datasource.entity;

import lombok.Data;

/**
 * 熔断器配置实体，持久化到 sqlite 的 并发_熔断_中断 表。
 * <p>
 * 字段与注解 {@code @CircuitBreaker} 属性对应，通过 actuator 端点增删查改后
 * 清除 {@link com.chua.common.support.concurrent.circuitbreaker.CircuitBreakerFlow} 缓存，
 * 下次调用时按新配置重建。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class ConcurrentCircuitBreakerConfig {

    /**
     * 熔断器名称，对应 @熔断中断(名称 = "...")，也是缓存键
    */
    private String name;

    /**
     * 连续失败次数达到此阈值后熔断器打开，默认 5
    */
    private int failureThreshold;

    /**
     * 半开状态下连续成功次数达到此阈值后熔断器关闭，默认 2
    */
    private int successThreshold;

    /**
     * 熔断打开后等待时间（毫秒），到期后进入半开状态试探，默认 60000
    */
    private long waitDurationMs;

    /**
     * 是否启用；false 时该熔断器不加载到运行时缓存
    */
    private boolean enabled;

    /**
     * 记录创建时间（sqlite datetime('now')）
    */
    private long createdAt;

    /**
     * 记录更新时间（sqlite datetime('now')）
    */
    private long updatedAt;
}
