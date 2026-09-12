package com.chua.starter.common.support.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

import static com.chua.starter.common.support.constant.CacheConstant.*;

/**
 * 缓存自动配置
 * <p>
 * 根据 {@link CacheProperties} 配置动态创建 Caffeine、Redis 以及多级缓存管理器。
   * 支持自定义缓存管理器 Bean 名称，便于 multi水平仪缓存管理器 按名称查找。
 * </p>
 *
 * @since 2024/7/22
 * @author CH
 */
@Configuration
@EnableConfigurationProperties(CacheProperties.class)
@AutoConfigureAfter(name = "com.chua.starter.redis.support.CacheConfiguration")
public class CacheConfiguration {

    /**
     * Caffeine 缓存管理器
     * <p>
      * 当未显式定义 caffeine缓存管理器 Bean 时，根据 缓存属性 创建。
     * </p>
     * @param properties 属性
     * @return caffeine缓存管理器的结果
     */
    @Bean(name = CacheProperties.CAFFEINE_BEAN_NAME)
    @ConditionalOnMissingBean(name = CacheProperties.CAFFEINE_BEAN_NAME)
    @ConditionalOnClass(Caffeine.class)
    @ConditionalOnProperty(prefix = CacheProperties.PRE, name = "enabled", havingValue = "true")
    public CacheManager caffeineCacheManager(CacheProperties properties) {
        CacheProperties.CaffeineProperties caffeineProps = properties.getCaffeine();
        Caffeine<Object, Object> builder = Caffeine.newBuilder()
                .initialCapacity(caffeineProps.getInitialCapacity())
                .maximumSize(caffeineProps.getMaximumSize());

        if (caffeineProps.isRecordStats()) {
            builder.recordStats();
        }

        if (caffeineProps.getExpireAfterWrite() > 0) {
            builder.expireAfterWrite(caffeineProps.getExpireAfterWrite(), TimeUnit.SECONDS);
        }
        if (caffeineProps.getExpireAfterAccess() > 0) {
            builder.expireAfterAccess(caffeineProps.getExpireAfterAccess(), TimeUnit.SECONDS);
        }
        if (caffeineProps.isWeakKeys()) {
            builder.weakKeys();
        }
        if (caffeineProps.isWeakValues()) {
            builder.weakValues();
        }

        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(builder);
        manager.setAllowNullValues(properties.isCacheNullValues());
        return manager;
    }

    /**
     * 多级缓存管理器
     * <p>
     * 作为主缓存管理器，按配置的层级顺序组合 Caffeine、Redis 等。
      * 当未显式定义 multi水平仪缓存管理器 Bean 时创建。
     * </p>
     * @param applicationContext application上下文
     * @return multi水平仪缓存管理器的结果
     */
    @Primary
    @Bean(name = CACHE_MANAGER_FOR_MULTI)
    @ConditionalOnMissingBean(name = CACHE_MANAGER_FOR_MULTI)
    @ConditionalOnProperty(prefix = CacheProperties.PRE, name = "enabled", havingValue = "true")
    public MultiLevelCacheManager multiLevelCacheManager(ApplicationContext applicationContext) {
        MultiLevelCacheManager manager = new MultiLevelCacheManager();
        manager.setApplicationContext(applicationContext);
        manager.afterPropertiesSet();
        return manager;
    }

    /**
     * 租户缓存键生成器
     * <p>
     * 用于生成包含租户、应用、平台等上下文的缓存键。
     * </p>
     */
    @Bean("tenantedKeyGenerator")
    @ConditionalOnMissingBean(name = "tenantedKeyGenerator")
    public TenantedKeyGenerator tenantedKeyGenerator(
            ApplicationContext applicationContext,
            CacheProperties properties,
            java.util.Optional<TenantIdProvider> tenantIdProvider) {
        TenantedKeyGenerator generator = new TenantedKeyGenerator();
        generator.setApplicationContext(applicationContext);
        generator.setKeyProperties(properties.getKey());
        tenantIdProvider.ifPresent(generator::setTenantIdProvider);
        return generator;
    }

    /**
      * 兼容旧版的自定义缓存键生成器 Bean（返回 简单钥匙生成器）。
     *
     * <p>保留供既有 @Cacheable(keyGenerator = "customTenantedKeyGenerator") 引用兼容。</p>
     * @return 习俗tenanted钥匙生成器的结果
     */
    @Bean("customTenantedKeyGenerator")
    @ConditionalOnMissingBean(name = "customTenantedKeyGenerator")
    public org.springframework.cache.interceptor.KeyGenerator customTenantedKeyGenerator() {
        return new org.springframework.cache.interceptor.SimpleKeyGenerator();
    }

    /**
     * 自定义缓存注解解析器
     * <p>
      * 支持 钥匙 和 钥匙生成器 同时使用，默认使用 tenanted钥匙生成器。
     * </p>
     * @return 缓存习俗注解parser的结果
     */
    @Bean
    @ConditionalOnMissingBean(CacheCustomAnnotationParser.class)
    public CacheCustomAnnotationParser cacheCustomAnnotationParser() {
        /**
         * 缓存习俗注解parser。
         * @return 缓存习俗注解parser的结果
         */
        return new CacheCustomAnnotationParser();
    }

/**
 * 缓存操作模板
 * <p>
   * 提供手动缓存操作方法，内部自动使用 tenanted钥匙生成器 拼接完整缓存键，
   * 保证手动操作与 {@code @Cacheable} 等注解生成的 钥匙 一致。
 * </p>
 */
@Bean
@ConditionalOnMissingBean(CacheTemplate.class)
@ConditionalOnProperty(prefix = CacheProperties.PRE, name = "enabled", havingValue = "true")
public CacheTemplate cacheTemplate(
        MultiLevelCacheManager multiLevelCacheManager,
        TenantedKeyGenerator tenantedKeyGenerator) {
    /**
     * 缓存template。
     * @param multiLevelCacheManager multi水平仪缓存管理器
     * @param tenantedKeyGenerator tenanted钥匙生成器
     * @return 缓存template的结果
     */
    return new CacheTemplate(multiLevelCacheManager, tenantedKeyGenerator);
}

/**
 * 缓存操作源（覆盖默认，使用自定义解析器）
     * <p>无条件创建并标记 @Primary，确保 CacheInterceptor 始终使用
      * 支持 钥匙 与 钥匙生成器 共用的 缓存习俗注解parser；
      * 若依赖 @条件onmissingBean 会被 @enable缓存 默认的标准解析器抢注，
      * 导致 钥匙+钥匙生成器 同时使用时报 illegal状态异常。</p>
     */
    @Bean
    @Primary
    public org.springframework.cache.interceptor.CacheOperationSource customCacheOperationSource(
            CacheCustomAnnotationParser parser) {
        return new org.springframework.cache.annotation.AnnotationCacheOperationSource(parser);
    }

    /**
     * 注册缓存模块服务信息。
     *
     * @return 服务信息贡献器
     */
    @Bean
    public com.chua.starter.common.support.serviceinfo.ServiceInfoContributor cacheServiceInfoContributor() {
        return registry -> registry.registerFlag("cache", "缓存", true, "缓存模块");
    }
}