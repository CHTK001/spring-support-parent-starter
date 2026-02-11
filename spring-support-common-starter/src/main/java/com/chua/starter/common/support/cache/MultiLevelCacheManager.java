package com.chua.starter.common.support.cache;

import com.chua.spring.support.configuration.SpringBeanUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 多级缓存管理器
 * <p>
 * 整合多个 CacheManager，实现多级缓存策略。
 * 支持缓存统计、预热等功能。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/7/22
 */
@Slf4j
@SuppressWarnings("ALL")
public class MultiLevelCacheManager implements CacheManager, InitializingBean {

    private final Map<String, CacheManager> cacheManagerMap = new ConcurrentHashMap<>();
    private final List<CacheManager> cacheManagers = new LinkedList<>();
    private final Map<String, Cache> cacheMap = new ConcurrentHashMap<>();
    
    private CacheProperties cacheProperties;

    @Override
    public Cache getCache(String name) {
        return cacheMap.computeIfAbsent(name, k -> {
            List<Cache> caches = cacheManagers.stream()
                    .filter(it -> !(it instanceof MultiLevelCacheManager))
                    .map(it -> it.getCache(name))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            
            if (caches.isEmpty()) {
                log.warn("无法创建缓存: name={}, 没有可用的缓存管理器", name);
                return null;
            }
            
            log.debug("创建多级缓存: name={}, levels={}", name, caches.size());
            return new MultiLevelCache(name, caches);
        });
    }

    @Override
    public Collection<String> getCacheNames() {
        return cacheManagers.stream()
                .filter(it -> !(it instanceof MultiLevelCacheManager))
                .map(CacheManager::getCacheNames)
                .flatMap(Collection::stream)
                .distinct()
                .toList();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // 获取所有 CacheManager
        Map<String, CacheManager> beansOfType = SpringBeanUtils.getApplicationContext()
                .getBeansOfType(CacheManager.class);
        
        for (Map.Entry<String, CacheManager> entry : beansOfType.entrySet()) {
            cacheManagerMap.put(entry.getKey().toUpperCase(), entry.getValue());
        }

        // 加载配置
        cacheProperties = Binder.get(SpringBeanUtils.getEnvironment())
                .bindOrCreate(CacheProperties.PRE, CacheProperties.class);

        // 按配置顺序添加缓存管理器
        for (String type : cacheProperties.getType()) {
            String key = type.toUpperCase();
            if (cacheManagerMap.containsKey(key)) {
                cacheManagers.add(cacheManagerMap.get(key));
                log.info("添加缓存层级: type={}", type);
            }
        }

        log.info("多级缓存管理器初始化完成: levels={}", cacheManagers.size());
    }

    /**
     * 获取缓存统计信息
     */
    public Map<String, CacheStats> getStats() {
        Map<String, CacheStats> stats = new HashMap<>();
        
        for (Map.Entry<String, Cache> entry : cacheMap.entrySet()) {
            Cache cache = entry.getValue();
            if (cache instanceof MultiLevelCache mlc) {
                stats.put(entry.getKey(), new CacheStats(
                        mlc.getHitCount(),
                        mlc.getMissCount(),
                        mlc.getHitRate(),
                        mlc.getLevelCount()
                ));
            }
        }
        
        return stats;
    }

    /**
     * 预热缓存
     */
    public void warmUp(String cacheName, Map<Object, Object> data) {
        Cache cache = getCache(cacheName);
        if (cache != null) {
            data.forEach(cache::put);
            log.info("缓存预热完成: name={}, count={}", cacheName, data.size());
        }
    }

    /**
     * 按模式清除缓存
     */
    public void evictByPattern(String cacheName, String pattern) {
        // 多级缓存不支持按模式清除，只能清空整个缓存
        Cache cache = getCache(cacheName);
        if (cache != null) {
            cache.clear();
            log.info("按模式清除缓存: name={}, pattern={}", cacheName, pattern);
        }
    }

    /**
     * 清除所有缓存
     */
    public void clearAll() {
        cacheMap.values().forEach(Cache::clear);
        log.info("清除所有缓存");
    }

    /**
     * 重置统计信息
     */
    public void resetAllStats() {
        cacheMap.values().stream()
                .filter(MultiLevelCache.class::isInstance)
                .map(MultiLevelCache.class::cast)
                .forEach(MultiLevelCache::resetStats);
    }

    /**
     * 缓存统计信息
     */
    public record CacheStats(long hitCount, long missCount, double hitRate, int levelCount) {
    }
}
