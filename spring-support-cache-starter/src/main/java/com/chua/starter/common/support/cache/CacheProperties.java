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
 * <h3>完整配置示例：</h3>
 * <pre>
 * plugin:
 *   cache:
 *     enabled: true                        # 总开关，默认 false，必须显式开启
 *     levels:                              # 层级顺序，越靠前越接近调用方（L1 在前）
 *       - caffeine
 *       - redis
 *     caffeine-bean: caffeineCacheManager  # 本地层 CacheManager Bean 名
 *     redis-bean: redisCacheManager        # 分布式层 CacheManager Bean 名
 *     cache-null-values: false             # 是否缓存空值占位符（防穿透），默认 false
 *     caffeine:
 *       initial-capacity: 256              # 初始容量
 *       maximum-size: 10000                # 最大条目数
 *       expire-after-write: 300            # 写入后过期（秒），&lt;=0 不设置
 *       expire-after-access: 0             # 访问后过期（秒），&lt;=0 不设置
 *       record-stats: true                 # Caffeine 命中率统计
 *       weak-keys: false                   # 弱引用 Key
 *       weak-values: false                 # 弱引用 Value
 *     redis:
 *       ttl: 600                           # 默认 TTL（秒）；-1 永不过期；其它 &lt;=0 跟随 Spring Data Redis 默认
 *       key-prefix: "cache:"               # Redis 键前缀
 *       separator: ":"                     # 缓存名与键之间的分隔符
 *       use-key-prefix: true               # 是否启用键前缀策略
 *     key:
 *       prefix: "cache:"                   # 业务键统一前缀（TenantedKeyGenerator 拼键用）
 *       with-application: true             # 键中追加应用名（spring.application.name，缺省 app）
 *       with-platform: true                # 键中追加平台名（plugin.api.platform.platform-name，缺省 default）
 *       with-tenant: true                  # 键中追加租户 ID（TenantIdProvider 提供，缺省 0）
 * </pre>
 *
 * <h3>声明项（当前版本字段存在但无消费方，配置不生效）：</h3>
 * <ul>
 *   <li>{@code plugin.cache.expires}（按缓存名细化 TTL）</li>
 *   <li>{@code plugin.cache.null-value-ttl}</li>
 *   <li>{@code plugin.cache.record-stats}（顶层；Caffeine 层请配 {@code plugin.cache.caffeine.record-stats}，
 *       多级缓存命中/未命中计数器始终开启）</li>
 *   <li>{@code plugin.cache.redis.serializer}（实现固定为 Jackson：GenericJackson2JsonRedisSerializer）</li>
 *   <li>{@code plugin.cache.redis.cache-null-values}（以顶层 {@code plugin.cache.cache-null-values} 为准）</li>
 *   <li>{@code plugin.cache.key.separator}（键拼接固定使用 {@code ":"}）</li>
 * </ul>
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
     * 是否启用多级缓存（总开关）。
     * <p>默认值：{@code false}。关闭时 {@code caffeineCacheManager} / {@code redisCacheManager} /
     * {@code multiLevelCacheManager} / {@code cacheTemplate} 均不注册，仅键生成器与注解解析器在位。</p>
     * <pre>
     * plugin:
     *   cache:
     *     enabled: true
     * </pre>
     */
    private boolean enabled = false;

    /**
     * 多级缓存层级（按读取顺序，越靠前越接近调用方）。
     * <p>支持：{@code caffeine}、{@code redis}；二者分别映射到 {@code caffeine-bean} /
     * {@code redis-bean} 指定的 CacheManager Bean，其余值按 Bean 名大写匹配容器中的 CacheManager。</p>
     * <p>默认值：{@code [caffeine, redis]}。</p>
     * <pre>
     * plugin:
     *   cache:
     *     levels:
     *       - caffeine
     *       - redis
     * </pre>
     */
    private List<String> levels = List.of("caffeine", "redis");

    /**
     * 本地 Caffeine 缓存配置。
     * <p>默认值：见 {@link CaffeineProperties} 各字段。</p>
     * <pre>
     * plugin:
     *   cache:
     *     caffeine:
     *       initial-capacity: 256
     *       maximum-size: 10000
     *       expire-after-write: 300
     * </pre>
     */
    private CaffeineProperties caffeine = new CaffeineProperties();

    /**
     * Redis 分布式缓存配置。
     * <p>默认值：见 {@link RedisProperties} 各字段。</p>
     * <pre>
     * plugin:
     *   cache:
     *     redis:
     *       ttl: 600
     *       key-prefix: "cache:"
     * </pre>
     */
    private RedisProperties redis = new RedisProperties();

    /**
     * 缓存键策略。
     * <p>默认值：见 {@link KeyProperties} 各字段。</p>
     * <pre>
     * plugin:
     *   cache:
     *     key:
     *       prefix: "cache:"
     *       with-tenant: true
     * </pre>
     */
    private KeyProperties key = new KeyProperties();

    /**
     * 本地 Caffeine 缓存管理器 Bean 名称（用于多级缓存中按名称解析）。
     * <p>默认值：{@code caffeineCacheManager}。{@code levels} 含 {@code caffeine} 时按该 Bean 名查找。</p>
     * <pre>
     * plugin:
     *   cache:
     *     caffeine-bean: caffeineCacheManager
     * </pre>
     */
    private String caffeineBean = CAFFEINE_BEAN_NAME;

    /**
     * Redis 缓存管理器 Bean 名称（用于多级缓存中按名称解析）。
     * <p>默认值：{@code redisCacheManager}。{@code levels} 含 {@code redis} 时按该 Bean 名查找。</p>
     * <pre>
     * plugin:
     *   cache:
     *     redis-bean: redisCacheManager
     * </pre>
     */
    private String redisBean = REDIS_BEAN_NAME;

    /**
     * 按缓存名称细化的过期时间（秒）。
     * <p><b>声明项：</b>当前版本无消费方，配置不生效；未配置的缓存名本应回落到
     * {@link RedisProperties#getTtl()} 的默认值，实际以各层自身 TTL 为准。</p>
     * <p>默认值：空 Map。</p>
     * <pre>
     * plugin:
     *   cache:
     *     expires:
     *       user: 600
     *       order: 60
     * </pre>
     */
    private Map<String, Long> expires = new LinkedHashMap<>();

    /**
     * 是否允许缓存空值（防缓存穿透）。
     * <p>默认值：{@code false}。同时作用于 Caffeine 的 {@code allowNullValues}、
     * Redis 的 {@code disableCachingNullValues} 与 {@link MultiLevelCache} 的空值占位符；
     * 关闭时查询结果为 {@code null} 不落缓存。</p>
     * <pre>
     * plugin:
     *   cache:
     *     cache-null-values: false
     * </pre>
     */
    private boolean cacheNullValues = false;

    /**
     * 空值占位符过期时间（秒），仅在 {@link #cacheNullValues} 为 true 时本应生效。
     * <p><b>声明项：</b>当前版本无消费方，配置不生效；空值占位符实际跟随各缓存层级自身 TTL。</p>
     * <p>默认值：{@code 60}。</p>
     * <pre>
     * plugin:
     *   cache:
     *     null-value-ttl: 60
     * </pre>
     */
    // [P3C A20 豁免]
    private long nullValueTtl = 60;

    /**
     * 多级缓存中是否启用命中率统计。
     * <p><b>声明项：</b>当前版本无消费方；{@link MultiLevelCache} 的命中/未命中计数器始终开启。
     * Caffeine 层的统计请配置 {@code plugin.cache.caffeine.record-stats}。</p>
     * <p>默认值：{@code true}。</p>
     * <pre>
     * plugin:
     *   cache:
     *     record-stats: true
     * </pre>
     */
    private boolean recordStats = true;

    /**
     * Caffeine 本地缓存配置
     *
     * <h3>配置示例：</h3>
     * <pre>
     * plugin:
     *   cache:
     *     caffeine:
     *       initial-capacity: 256
     *       maximum-size: 10000
     *       expire-after-write: 300
     *       expire-after-access: 0
     *       record-stats: true
     *       weak-keys: false
     *       weak-values: false
     * </pre>
     *
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class CaffeineProperties {
        /**
         * 初始容量。
         * <p>默认值：{@code 256}。</p>
         * <pre>
         * plugin:
         *   cache:
         *     caffeine:
         *       initial-capacity: 256
         * </pre>
         */
        // [P3C A20 豁免]
        private int initialCapacity = 256;

        /**
         * 最大容量（最大条目数）。
         * <p>默认值：{@code 10000}；按 Caffeine {@code maximumSize} 生效。</p>
         * <pre>
         * plugin:
         *   cache:
         *     caffeine:
         *       maximum-size: 10000
         * </pre>
         */
        private long maximumSize = 10_000L;

        /**
         * 写入后过期时间（秒）。
         * <p>默认值：{@code 300}；{@code <=0} 时不设置（不调用 {@code expireAfterWrite}）。</p>
         * <pre>
         * plugin:
         *   cache:
         *     caffeine:
         *       expire-after-write: 300
         * </pre>
         */
        // [P3C A20 豁免]
        private long expireAfterWrite = 300;

        /**
         * 访问后过期时间（秒）。
         * <p>默认值：{@code 0}；{@code <=0} 时不设置（不调用 {@code expireAfterAccess}），即不按访问时间过期。</p>
         * <pre>
         * plugin:
         *   cache:
         *     caffeine:
         *       expire-after-access: 0
         * </pre>
         */
        private long expireAfterAccess = 0;

        /**
         * 是否启用命中率统计。
         * <p>默认值：{@code true}；为 true 时对 Caffeine 调用 {@code recordStats()}。</p>
         * <pre>
         * plugin:
         *   cache:
         *     caffeine:
         *       record-stats: true
         * </pre>
         */
        private boolean recordStats = true;

        /**
         * 弱引用 Key。
         * <p>默认值：{@code false}；为 true 时对 Caffeine 调用 {@code weakKeys()}。</p>
         * <pre>
         * plugin:
         *   cache:
         *     caffeine:
         *       weak-keys: false
         * </pre>
         */
        private boolean weakKeys = false;

        /**
         * 弱引用 Value。
         * <p>默认值：{@code false}；为 true 时对 Caffeine 调用 {@code weakValues()}。</p>
         * <pre>
         * plugin:
         *   cache:
         *     caffeine:
         *       weak-values: false
         * </pre>
         */
        private boolean weakValues = false;
    }

    /**
     * Redis 缓存配置
     *
     * <h3>配置示例：</h3>
     * <pre>
     * plugin:
     *   cache:
     *     redis:
     *       ttl: 600
     *       key-prefix: "cache:"
     *       separator: ":"
     *       use-key-prefix: true
     * </pre>
     *
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class RedisProperties {
        /**
         * 默认过期时间（秒）。
         * <p>默认值：{@code 600}。语义：{@code >0} 按 {@code entryTtl(秒)} 生效；
         * {@code =-1}（{@code TTL_NEVER_EXPIRE}）按 {@code entryTtl(Duration.ZERO)} 永不过期；
         * 其它 {@code <=0} 不设置 TTL，跟随 Spring Data Redis 默认行为。</p>
         * <pre>
         * plugin:
         *   cache:
         *     redis:
         *       ttl: 600
         * </pre>
         */
        // [P3C A20 豁免]
        private long ttl = 600;

        /**
         * Redis Key 前缀。
         * <p>默认值：{@code cache:}。仅在 {@link #useKeyPrefix} 为 true 时参与
         * {@code prefixCacheNameWith(keyPrefix)}；随后被 {@code computePrefixWith(缓存名 + separator)} 覆盖，
         * 最终键前缀为「缓存名 + separator」。</p>
         * <pre>
         * plugin:
         *   cache:
         *     redis:
         *       key-prefix: "cache:"
         * </pre>
         */
        private String keyPrefix = "cache:";

        /**
         * 值序列化方式：jackson / jdk。
         * <p><b>声明项：</b>当前版本无消费方；实现固定为
         * {@code GenericJackson2JsonRedisSerializer(ObjectMapper)}。</p>
         * <p>默认值：{@code jackson}。</p>
         * <pre>
         * plugin:
         *   cache:
         *     redis:
         *       serializer: jackson
         * </pre>
         */
        private String serializer = "jackson";

        /**
         * 是否允许缓存空值。
         * <p><b>声明项：</b>当前版本无消费方；Redis 层实际读取顶层
         * {@code plugin.cache.cache-null-values}。</p>
         * <p>默认值：{@code false}。</p>
         * <pre>
         * plugin:
         *   cache:
         *     redis:
         *       cache-null-values: false
         * </pre>
         */
        private boolean cacheNullValues = false;

        /**
         * Redis Key 与缓存名之间的分隔符。
         * <p>默认值：{@code ":"}。仅在 {@link #useKeyPrefix} 为 true 时参与
         * {@code computePrefixWith(缓存名 + separator)}。</p>
         * <pre>
         * plugin:
         *   cache:
         *     redis:
         *       separator: ":"
         * </pre>
         */
        private String separator = ":";

        /**
         * 是否启用 Redis Key 前缀策略。
         * <p>默认值：{@code true}；为 true 时先 {@code prefixCacheNameWith(keyPrefix)}，
         * 再以 {@code computePrefixWith(缓存名 + separator)} 生效。</p>
         * <pre>
         * plugin:
         *   cache:
         *     redis:
         *       use-key-prefix: true
         * </pre>
         */
        private boolean useKeyPrefix = true;
    }

    /**
     * 缓存键策略
     * <p>由 {@link TenantedKeyGenerator} 消费，完整键形如
     * {@code prefix + 应用名:平台名:租户ID:参数简单键}，各段是否拼接由 with-* 开关控制。</p>
     *
     * <h3>配置示例：</h3>
     * <pre>
     * plugin:
     *   cache:
     *     key:
     *       prefix: "cache:"
     *       with-application: true
     *       with-platform: true
     *       with-tenant: true
     * </pre>
     *
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class KeyProperties {
        /**
         * 缓存键统一前缀。
         * <p>默认值：{@code cache:}。</p>
         * <pre>
         * plugin:
         *   cache:
         *     key:
         *       prefix: "cache:"
         * </pre>
         */
        private String prefix = "cache:";

        /**
         * 缓存键分隔符。
         * <p><b>声明项：</b>当前版本无消费方；键拼接固定使用 {@code ":"}。</p>
         * <p>默认值：{@code ":"}。</p>
         * <pre>
         * plugin:
         *   cache:
         *     key:
         *       separator: ":"
         * </pre>
         */
        private String separator = ":";

        /**
         * 是否在 Key 中追加租户标识。
         * <p>默认值：{@code true}；租户 ID 由 {@link TenantIdProvider} 提供，未声明时回落 {@code "0"}。</p>
         * <pre>
         * plugin:
         *   cache:
         *     key:
         *       with-tenant: true
         * </pre>
         */
        private boolean withTenant = true;

        /**
         * 是否在 Key 中追加应用名。
         * <p>默认值：{@code true}；应用名取 {@code spring.application.name}，缺省 {@code app}。</p>
         * <pre>
         * plugin:
         *   cache:
         *     key:
         *       with-application: true
         * </pre>
         */
        private boolean withApplication = true;

        /**
         * 是否在 Key 中追加平台标识。
         * <p>默认值：{@code true}；平台名取 {@code plugin.api.platform.platform-name}，缺省 {@code default}。</p>
         * <pre>
         * plugin:
         *   cache:
         *     key:
         *       with-platform: true
         * </pre>
         */
        private boolean withPlatform = true;
    }
}
