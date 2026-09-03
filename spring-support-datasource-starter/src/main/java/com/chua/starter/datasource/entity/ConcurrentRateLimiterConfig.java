package com.chua.starter.datasource.entity;

import lombok.Data;

/**
 * 限流器配置实体，持久化到 SQLite 的 concurrent_rate_limiter 表。
 * <p>
 * 字段与注解 {@code @RateLimiter} 属性对应，通过 actuator 端点增删查改后
 * 自动加载到 {@link com.chua.common.support.concurrent.rate.RateLimiterFlow} 缓存。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class ConcurrentRateLimiterConfig {

    /** 限流器名称，对应 @RateLimiter(name = "...")，也是 Map<String, RateLimiterProvider> 的缓存键 */
    private String name;

    /** 每秒许可数（QPS 上限），对应 permitsPerSecond 属性 */
    private double permitsPerSecond;

    /** 预热时间（毫秒），0 表示不预热；从启动到低速阈值的平滑过渡时长 */
    private long warmupPeriodMs;

    /** 获取许可的超时时间（毫秒），0 表示不超时（立即阻塞） */
    private long timeoutMs;

    /** 是否启用；false 时该限流器不加载到运行时缓存 */
    private boolean enabled;

    /** 记录创建时间（SQLite datetime('now')） */
    private long createdAt;

    /** 记录更新时间（SQLite datetime('now')） */
    private long updatedAt;
}
