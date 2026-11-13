package com.chua.starter.common.support.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 缓存配置
 * <p>
 * 支持多级缓存配置，涵盖本地缓存、分布式缓存以及按名称细化的过期时间。
 * 通过 {@code plugin.cache} 前缀绑定配置。
 * </p>
 *
 * <h3>典型配置示例：</h3>
 * <pre>
 * plugin:
 *   cache:
 *     enabled: true
 *     # 多级缓存层级（按读取顺序），可选: caffeine / redis
 *     levels:
 *       - caffeine
 *       - redis
 *     caffeine:
 *       initial-capacity: 256
 *       maximum-size: 50000
 *       expire-after-write: 300
 *       expire-after-access: 60
 *       record-stats: true
 *     redis:
 *       ttl: 1800
 *       key-prefix: "cache:"
 *       cache-null-values: false
 *       serializer: jackson
 *     # 自定义每级 CacheManager 的 Bean 名称（可选）
 *     caffeine-bean: caffeineCacheManager
 *     redis-bean: redisCacheManager
 *     # 按缓存名定制 TTL（秒）
 *     expires:
 *       user: 600
 *       order: 60
 *     # 缓存键策略
 *     key:
 *       prefix: "cache:"
 *       separator: ":"
 *       with-tenant: true
 *       with-application: true
 * </pre>
 *
 * @since 2024/7/22
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = CacheProperties.PRE, ignoreInvalidFields = true)
public class CacheProperties {

    /**
     * 配置前缀
     */
    public static final String PRE = "plugin.cache";

    /**
     * Caffeine 缓存管理器 Bean 名称
     */
    public static final String CAFFEINE_BEAN_NAME = "caffeineCacheManager";

    /**
     * Redis 缓存管理器 Bean 名称
     */
    public static final String REDIS_BEAN_NAME = "redisCacheManager";

    /**
     * 是否启用多级缓存
     */
    private boolean enabled = false;

    /**
     * 多级缓存层级（按读取顺序，越靠前越接近调用方）
     * <p>支持：caffeine、redis</p>
     */
    private List<String> levels = List.of("caffeine", "redis");

    /**
     * 本地 Caffeine 缓存配置
     */
    private CaffeineProperties caffeine = new CaffeineProperties();

    /**
     * Redis 分布式缓存配置
     */
    private RedisProperties redis = new RedisProperties();

    /**
     * 缓存键策略
     */
    private KeyProperties key = new KeyProperties();

    /**
     * Caffeine 缓存管理器 Bean 名称（用于多级缓存中按名称解析）
     */
    private String caffeineBean = CAFFEINE_BEAN_NAME;

    /**
     * Redis 缓存管理器 Bean 名称（用于多级缓存中按名称解析）
     */
    private String redisBean = REDIS_BEAN_NAME;

    /**
     * 按缓存名称细化的过期时间（秒）
     * <p>未配置的缓存名使用 {@link RedisProperties#getTtl()} 默认值</p>
     */
    private Map<String, Long> expires = new LinkedHashMap<>();

    /**
     * 是否允许缓存空值（防缓存穿透）
     */
    private boolean cacheNullValues = false;

    /**
     * 空值占位符过期时间（秒），仅在 {@link #cacheNullValues} 为 true 时生效
     */
    // [P3C A20 豁免]
    private long nullValueTtl = 60;

    /**
     * 多级缓存中是否启用命中率统计
     */
    private boolean recordStats = true;

    /**
     * Caffeine 本地缓存配置
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class CaffeineProperties {
        /**
         * 初始容量
         */
        // [P3C A20 豁免]
        private int initialCapacity = 256;

        /**
         * 最大容量
         */
        private long maximumSize = 10_000L;

        /**
         * 写入后过期时间（秒），小于等于 0 表示不设置
         */
        // [P3C A20 豁免]
        private long expireAfterWrite = 300;

        /**
         * 访问后过期时间（秒），小于等于 0 表示不设置
         */
        private long expireAfterAccess = 0;

        /**
         * 是否启用命中率统计
         */
        private boolean recordStats = true;

        /**
         * 弱引用 Key
         */
        private boolean weakKeys = false;

        /**
         * 弱引用 值
         */
        private boolean weakValues = false;
    }

    /**
     * Redis 缓存配置
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class RedisProperties {
        /**
         * 默认过期时间（秒），小于等于 0 表示永不过期
         */
        // [P3C A20 豁免]
        private long ttl = 600;

        /**
         * Key 前缀
         */
        private String keyPrefix = "cache:";

        /**
         * 值序列化方式：jackson / jdk
         */
        private String serializer = "jackson";

        /**
         * 是否允许缓存空值
         */
        private boolean cacheNullValues = false;

        /**
         * Key 与 值 是否使用相同分隔符
         */
        private String separator = ":";

        /**
         * 是否在 Key 中携带应用名前缀
         */
        private boolean useKeyPrefix = true;
    }

    /**
     * 缓存键策略
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class KeyProperties {
        /**
         * 缓存键统一前缀
         */
        private String prefix = "cache:";

        /**
         * 缓存键分隔符
         */
        private String separator = ":";

        /**
         * 是否在 Key 中追加租户标识
         */
        private boolean withTenant = true;

        /**
         * 是否在 Key 中追加应用名
         */
        private boolean withApplication = true;

        /**
         * 是否在 Key 中追加平台标识
         */
        private boolean withPlatform = true;
    }
}
