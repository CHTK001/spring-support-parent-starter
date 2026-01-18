package com.chua.starter.strategy.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 缓存配置
 * <p>
 * 支持多级缓存配置，包括本地缓存和分布式缓存。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/7/22
 */
@Data
@ConfigurationProperties(prefix = CacheProperties.PRE, ignoreInvalidFields = true)
public class CacheProperties {

    public static final String PRE = "plugin.cache";

    /**
     * 是否启用多级缓存
     */
    private boolean enable = false;

    /**
     * 缓存类型列表（按优先级排序）
     * <p>
     * 支持: default(Caffeine), redis
     * </p>
     */
    private List<String> type = List.of("default", "redis");

    /**
     * Redis 缓存配置
     */
    private RedisProperties redis = new RedisProperties();

    /**
     * Caffeine 本地缓存配置
     */
    private CaffeineProperties caffeine = new CaffeineProperties();

    /**
     * Redis 配置
     */
    @Data
    public static class RedisProperties {
        /**
         * 默认过期时间（秒）
         */
        private long ttl = 600;

        /**
         * key 前缀
         */
        private String keyPrefix = "cache:";
    }

    /**
     * Caffeine 配置
     */
    @Data
    public static class CaffeineProperties {
        /**
         * 初始容量
         */
        private int initialCapacity = 100;

        /**
         * 最大容量
         */
        private long maximumSize = 10000;

        /**
         * 写入后过期时间（秒）
         */
        private long expireAfterWrite = 300;

        /**
         * 访问后过期时间（秒）
         */
        private long expireAfterAccess = 0;
    }
}
