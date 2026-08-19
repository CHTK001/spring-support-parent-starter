package com.chua.starter.common.support.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 多级缓存管理器
 * <p>
 * 根据 {@link CacheProperties} 中配置的层级，按顺序组织多级缓存。
 * 实际缓存命中时优先查找低层级，未命中时查找高层级并回填。
 * </p>
 *
 * <h3>特性：</h3>
 * <ul>
 *   <li>支持 caffeine、redis 等多级缓存层级，可自由组合。</li>
 *   <li>支持按名称配置自定义 CacheManager Bean。</li>
 *   <li>支持缓存统计、预热、按模式清除。</li>
 * </ul>
 *
 * @since 2024/7/22
 * @author CH
 */
public class MultiLevelCacheManager implements CacheManager, InitializingBean, ApplicationContextAware {

    /** 日志 */
    private static final Logger log = LoggerFactory.getLogger(MultiLevelCacheManager.class);

    /**
     * 缓存键前缀
     */
    private static final String CACHE_KEY_PREFIX = "plugin.cache.";

    private final Map<String, Cache> cacheMap = new ConcurrentHashMap<>();

    /** 缓存managers */
    private final List<CacheManager> cacheManagers = new ArrayList<>();
    private final Map<String, CacheManager> cacheManagerMap = new ConcurrentHashMap<>();

    /** Application上下文 */
    private ApplicationContext applicationContext;
    /** 环境 */
    private Environment environment;
    /** 缓存属性 */
    private CacheProperties cacheProperties;

    @Override
    /** 设置ApplicationContext */
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        this.environment = applicationContext.getEnvironment();
    }

    @Override
    /** AfterProperties设置 */
    public void afterPropertiesSet() {
        // 加载配置
        cacheProperties = Binder.get(environment)
                .bindOrCreate(CacheProperties.PRE, CacheProperties.class);

        // 通过 ApplicationContext 获取所有 CacheManager
        Map<String, CacheManager> beansOfType = applicationContext.getBeansOfType(CacheManager.class);
        for (Map.Entry<String, CacheManager> entry : beansOfType.entrySet()) {
            cacheManagerMap.put(entry.getKey().toUpperCase(), entry.getValue());
        }

        // 按配置顺序添加缓存管理器
        for (String level : cacheProperties.getLevels()) {
            String key = resolveLevelKey(level, cacheProperties);
            CacheManager manager = cacheManagerMap.get(key);
            if (manager == null) {
                log.warn("未找到层级对应的 CacheManager: level={}, key={}", level, key);
                continue;
            }
            if (manager == this) {
                log.debug("跳过自身: level={}", level);
                continue;
            }
            cacheManagers.add(manager);
            log.info("添加缓存层级: level={}, manager={}", level, manager.getClass().getSimpleName());
        }

        log.info("多级缓存管理器初始化完成: levels={}, enabled={}",
                cacheManagers.size(), cacheProperties.isEnabled());
    }

    /**
     * 将层级名称解析为 CacheManager 的 Bean 名称（默认转大写）。
     *
     * @param level      层级名称
     * @param properties 缓存配置
     * @return 规范化的 Bean 名称
     */
    private String resolveLevelKey(String level, CacheProperties properties) {
        String normalized = level == null ? "" : level.trim().toUpperCase();
        if ("CAFFEINE".equals(normalized)) {
            String bean = properties.getCaffeineBean();
            if (bean != null && !bean.isEmpty()) {
                return bean.toUpperCase();
            }
        }
        if ("REDIS".equals(normalized)) {
            String bean = properties.getRedisBean();
            if (bean != null && !bean.isEmpty()) {
                return bean.toUpperCase();
            }
        }
        return normalized;
    }

    @Override
    /** 获取Cache */
    public Cache getCache(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        return cacheMap.computeIfAbsent(name, this::buildCache);
    }

    /** 构建Cache */
    private Cache buildCache(String name) {
        if (cacheManagers.isEmpty()) {
            log.warn("无法创建多级缓存: name={}, 没有可用的缓存管理器", name);
            return null;
        }
        List<Cache> caches = new ArrayList<>(cacheManagers.size());
        for (CacheManager manager : cacheManagers) {
            if (manager instanceof MultiLevelCacheManager) {
                continue;
            }
            Cache cache;
            try {
                cache = manager.getCache(name);
            } catch (Exception e) {
                log.warn("获取底层缓存失败: manager={}, cache={}",
                        manager.getClass().getSimpleName(), name, e);
                continue;
            }
            if (cache != null) {
                caches.add(cache);
            }
        }

        if (caches.isEmpty()) {
            log.warn("无法创建多级缓存: name={}, 所有层级均未提供 Cache", name);
            return null;
        }

        log.debug("创建多级缓存: name={}, levels={}", name, caches.size());
        return new MultiLevelCache(name, caches, cacheProperties != null && cacheProperties.isCacheNullValues());
    }

    @Override
    /** 获取CacheNames */
    public Collection<String> getCacheNames() {
        if (cacheManagers.isEmpty()) {
            return List.of();
        }
        return cacheManagers.stream()
                .filter(it -> !(it instanceof MultiLevelCacheManager))
                .map(CacheManager::getCacheNames)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 获取缓存统计信息
     *
     * @return 缓存名到统计的映射
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
                        mlc.getBackfillCount(),
                        mlc.getLevelCount()
                ));
            }
        }
        return stats;
    }

    /**
     * 预热缓存
     *
     * @param cacheName 缓存名
     * @param data      待预热数据
     */
    public void warmUp(String cacheName, Map<Object, Object> data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        Cache cache = getCache(cacheName);
        if (cache != null) {
            data.forEach(cache::put);
            log.info("缓存预热完成: name={}, count={}", cacheName, data.size());
        }
    }

    /**
     * 按模式清除缓存
     *
     * @param cacheName 缓存名
     * @param pattern   模式
     * @return 是否执行成功
     */
    public boolean evictByPattern(String cacheName, String pattern) {
        Cache cache = getCache(cacheName);
        if (cache instanceof MultiLevelCache mlc) {
            int affected = mlc.evictByPattern(pattern);
            log.info("按模式清除缓存: name={}, pattern={}, affectedLevels={}", cacheName, pattern, affected);
            return affected > 0;
        }
        if (cache != null) {
            cache.clear();
            log.info("按模式清除缓存（已 fallback 到 clear）: name={}, pattern={}", cacheName, pattern);
            return true;
        }
        return false;
    }

    /**
     * 清除所有多级缓存
     */
    public void clearAll() {
        cacheMap.values().forEach(Cache::clear);
        log.info("清除所有多级缓存: count={}", cacheMap.size());
    }

    /**
     * 重置所有统计
     */
    public void resetAllStats() {
        cacheMap.values().stream()
                .filter(MultiLevelCache.class::isInstance)
                .map(MultiLevelCache.class::cast)
                .forEach(MultiLevelCache::resetStats);
    }

    /**
     * 获取底层缓存管理器列表（用于高级管理）。
     *
     * @return 缓存管理器列表
     */
    public List<CacheManager> getCacheManagers() {
        return List.copyOf(cacheManagers);
    }

    /**
     * 获取当前生效的配置。
     *
     * @return 缓存配置
     */
    public CacheProperties getCacheProperties() {
        return cacheProperties;
    }

    /**
     * 缓存统计信息
     *
     * @param hitCount      命中次数
     * @param missCount     未命中次数
     * @param hitRate       命中率
     * @param backfillCount 回填次数
     * @param levelCount    缓存层级数
     */
    public record CacheStats(long hitCount, long missCount, double hitRate, long backfillCount, int levelCount) {
    }
}
