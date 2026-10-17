package com.chua.starter.ai.support.configuration;

import lombok.extern.slf4j.Slf4j;
import com.chua.starter.common.support.cache.CacheManagerResolver;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.interceptor.SimpleCacheResolver;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * 缓存故障容错。
 *
 * <p>本应用的设置读写大量使用 {@code @Cacheable / @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS)}。
 * 当 Redis 不可用（本地开发常见：未起 Redis、连接被拒）时，默认错误处理会把异常抛给调用方，
 * 结果是「业务已经写库成功，却因为清缓存失败返回 500」——例如
 * {@code PUT /v2/ai/bot-binding/default-model} 实际已写入 {@code sys_ai_user_setting}，
 * 接口却报「数据操作失败」。同理所有只读设置接口也会 500，与真实数据状态完全不符。</p>
 *
 * <p>这里把缓存层面的异常统一降级为 WARN：读缓存失败 → 回源（走 DB），
 * 写/清缓存失败 → 忽略（下次读到的是库里的最新值，DB 才是唯一事实来源）。
 * 作用域仅限本应用上下文，不影响其它引入本 starter 的宿主。</p>
 *
 * <p>本类同时是本应用<b>唯一</b>的 {@link CachingConfigurer}（Spring 只允许一个），
 * 因此一并负责给出主缓存管理器与解析器：见 {@link #cacheManager()}。默认实现返回 {@code null}，
 * 会把 {@code CacheAspectSupport} 推向 {@code beanFactory.getBean(CacheManager.class)} 的
 * 唯一性兜底——上下文里存在多个 {@code CacheManager} 而没有可解析的主管理器时，应用会直接
 * 启动失败（{@code no CacheResolver specified and expected single matching CacheManager but found N}）。
 * 由本类显式给值即可彻底绕开该脆弱路径。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Configuration
public class CacheResilienceConfiguration implements CachingConfigurer {

    /**
     * 应用上下文，用于解析主缓存管理器。
     */
    private final ApplicationContext applicationContext;

    /**
     * 构造缓存容错配置。
     *
     * @param applicationContext 应用上下文
     */
    public CacheResilienceConfiguration(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 主缓存管理器（确定性解析）。
     *
     * <p>能唯一解析（单候选或 {@code @Primary}）时结果与 Spring 原生兜底完全一致；
     * 多候选冲突时按框架约定顺序（多级管理器优先）确定选择，避免启动失败。</p>
     *
     * @return 主缓存管理器
     */
    @Override
    public CacheManager cacheManager() {
        return CacheManagerResolver.resolve(applicationContext);
    }

    /**
     * 缓存解析器：直接用 {@link #cacheManager()} 解析出的管理器构造，
     * 使 {@code CacheAspectSupport} 不再走「唯一性」兜底探测。
     *
     * @return 缓存解析器；无可用缓存管理器时返回 {@code null}
     */
    @Override
    public CacheResolver cacheResolver() {
        CacheManager manager = cacheManager();
        return manager == null ? null : new SimpleCacheResolver(manager);
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {

            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("[Cache] 读取失败已降级回源 cache={} key={}: {}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("[Cache] 写入失败已忽略 cache={} key={}: {}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("[Cache] 失效失败已忽略 cache={} key={}: {}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("[Cache] 清空失败已忽略 cache={}: {}", cache.getName(), exception.getMessage());
            }
        };
    }
}
