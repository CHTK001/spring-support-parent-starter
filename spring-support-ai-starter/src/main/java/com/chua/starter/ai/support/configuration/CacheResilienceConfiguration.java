package com.chua.starter.ai.support.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
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
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Configuration
public class CacheResilienceConfiguration implements CachingConfigurer {

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
