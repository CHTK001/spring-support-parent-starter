package com.chua.starter.ai.support.configuration.actuator;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeansException;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 缓存端点：{@code GET /actuator/ai-cache}。
 *
 * <p>只读 {@code aiCacheManager}（Caffeine 本地缓存）的统计：条目数、命中/未命中、
 * 淘汰数。用于确认缓存是否真的生效、有没有被配置变更正确失效。Redis 缓存管理器
 * 只列 Bean 名，不主动遍历（避免对线上 Redis 做全量 SCAN）。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Endpoint(id = "ai-cache")
@RequiredArgsConstructor
public class AiCacheEndpoint {

    /**
     * 本地缓存管理器 Bean 名
     */
    private static final String LOCAL_CACHE_MANAGER = "aiCacheManager";

    /**
     * 上下文（缓存可被关闭，按名查找并在运行时兜底）
     */
    private final ApplicationContext applicationContext;

    /**
     * 读取缓存摘要。
     *
     * @return enabled / manager / caches / managers
     */
    @ReadOperation
    public Map<String, Object> read() {
        Map<String, Object> result = new LinkedHashMap<>();
        CacheManager manager = localCacheManager();
        result.put("enabled", manager != null);
        if (manager == null) {
            return result;
        }
        result.put("manager", manager.getClass().getSimpleName());
        result.put("caches", caches(manager));
        result.put("managers", applicationContext.getBeansOfType(CacheManager.class).keySet());
        return result;
    }

    /**
     * 按名取本地缓存管理器；未启用缓存时返回 null。
     *
     * @return 缓存管理器或 null
     */
    private CacheManager localCacheManager() {
        try {
            return applicationContext.getBean(LOCAL_CACHE_MANAGER, CacheManager.class);
        } catch (BeansException ex) {
            return null;
        }
    }

    /**
     * 渲染每个缓存的统计。
     *
     * @param manager 缓存管理器
     * @return cacheName → 统计
     */
    private Map<String, Object> caches(CacheManager manager) {
        Map<String, Object> caches = new LinkedHashMap<>();
        for (String name : manager.getCacheNames()) {
            org.springframework.cache.Cache cache = manager.getCache(name);
            if (cache == null) {
                continue;
            }
            Map<String, Object> node = new LinkedHashMap<>();
            Object nativeCache = cache.getNativeCache();
            node.put("type", nativeCache == null ? null : nativeCache.getClass().getSimpleName());
            if (nativeCache instanceof Cache<?, ?> caffeine) {
                CacheStats stats = caffeine.stats();
                node.put("estimatedSize", caffeine.estimatedSize());
                node.put("hitCount", stats.hitCount());
                node.put("missCount", stats.missCount());
                node.put("evictionCount", stats.evictionCount());
                node.put("hitRate", stats.hitRate());
            }
            caches.put(name, node);
        }
        return caches;
    }
}
