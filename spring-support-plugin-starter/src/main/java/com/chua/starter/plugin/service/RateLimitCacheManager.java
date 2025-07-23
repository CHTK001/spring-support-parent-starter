package com.chua.starter.plugin.service;

import com.chua.starter.plugin.entity.PluginRateLimitConfig;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 限流缓存管理器
 * 负责管理内存中的限流配置和限流器实例
 *
 * @author CH
 * @since 2025/1/16
 */
@Slf4j
@Component
public class RateLimitCacheManager {

    /**
     * 内存中的限流配置缓存
     * Key: limitType:limitKey
     * Value: PluginRateLimitConfig
     */
    private final ConcurrentMap<String, PluginRateLimitConfig> configCache = new ConcurrentHashMap<>();

    /**
     * 限流器实例缓存
     * Key: limitType:limitKey
     * Value: RateLimiter
     */
    private final ConcurrentMap<String, RateLimiter> rateLimiterCache = new ConcurrentHashMap<>();

    /**
     * 添加或更新限流配置到内存
     *
     * @param config 限流配置
     */
    public void putConfig(PluginRateLimitConfig config) {
        if (config == null || !config.isValid()) {
            log.warn("Invalid rate limit config: {}", config);
            return;
        }

        String key = config.getUniqueKey();
        
        // 更新配置缓存
        configCache.put(key, config);
        
        // 更新或创建限流器
        if (config.getEnabled()) {
            updateRateLimiter(key, config);
        } else {
            // 如果配置被禁用，移除限流器
            rateLimiterCache.remove(key);
        }
        
        log.debug("Updated rate limit config in cache: {}", key);
    }

    /**
     * 从内存中获取限流配置
     *
     * @param limitType 限流类型
     * @param limitKey 限流键
     * @return 限流配置
     */
    public PluginRateLimitConfig getConfig(PluginRateLimitConfig.LimitType limitType, String limitKey) {
        String key = limitType + ":" + limitKey;
        return configCache.get(key);
    }

    /**
     * 获取限流器
     *
     * @param limitType 限流类型
     * @param limitKey 限流键
     * @return 限流器
     */
    public RateLimiter getRateLimiter(PluginRateLimitConfig.LimitType limitType, String limitKey) {
        String key = limitType + ":" + limitKey;
        return rateLimiterCache.get(key);
    }

    /**
     * 移除限流配置
     *
     * @param limitType 限流类型
     * @param limitKey 限流键
     */
    public void removeConfig(PluginRateLimitConfig.LimitType limitType, String limitKey) {
        String key = limitType + ":" + limitKey;
        configCache.remove(key);
        rateLimiterCache.remove(key);
        log.debug("Removed rate limit config from cache: {}", key);
    }

    /**
     * 清空所有缓存
     */
    public void clearAll() {
        configCache.clear();
        rateLimiterCache.clear();
        log.info("Cleared all rate limit cache");
    }

    /**
     * 获取所有配置
     *
     * @return 所有配置
     */
    public ConcurrentMap<String, PluginRateLimitConfig> getAllConfigs() {
        return new ConcurrentHashMap<>(configCache);
    }

    /**
     * 检查是否存在配置
     *
     * @param limitType 限流类型
     * @param limitKey 限流键
     * @return 是否存在
     */
    public boolean hasConfig(PluginRateLimitConfig.LimitType limitType, String limitKey) {
        String key = limitType + ":" + limitKey;
        return configCache.containsKey(key);
    }

    /**
     * 获取配置数量
     * 
     * @return 配置数量
     */
    public int getConfigCount() {
        return configCache.size();
    }

    /**
     * 获取限流器数量
     * 
     * @return 限流器数量
     */
    public int getRateLimiterCount() {
        return rateLimiterCache.size();
    }

    /**
     * 更新或创建限流器
     * 
     * @param key 缓存键
     * @param config 限流配置
     */
    private void updateRateLimiter(String key, PluginRateLimitConfig config) {
        try {
            RateLimiter rateLimiter = rateLimiterCache.get(key);
            
            if (rateLimiter == null) {
                // 创建新的限流器
                rateLimiter = createRateLimiter(config);
                rateLimiterCache.put(key, rateLimiter);
                log.debug("Created new rate limiter for key: {}, qps: {}", key, config.getQps());
            } else {
                // 更新现有限流器的速率
                rateLimiter.setRate(config.getQps());
                log.debug("Updated rate limiter for key: {}, qps: {}", key, config.getQps());
            }
        } catch (Exception e) {
            log.error("Failed to update rate limiter for key: {}", key, e);
        }
    }

    /**
     * 创建限流器
     * 
     * @param config 限流配置
     * @return 限流器
     */
    private RateLimiter createRateLimiter(PluginRateLimitConfig config) {
        switch (config.getAlgorithmType()) {
            case TOKEN_BUCKET:
                // 使用Guava的RateLimiter（基于令牌桶算法）
                return RateLimiter.create(config.getQps());
            case LEAKY_BUCKET:
            case FIXED_WINDOW:
            case SLIDING_WINDOW:
            default:
                // 默认使用令牌桶算法
                return RateLimiter.create(config.getQps());
        }
    }

    /**
     * 尝试获取许可
     * 
     * @param limitType 限流类型
     * @param limitKey 限流键
     * @return 是否获取成功
     */
    public boolean tryAcquire(PluginRateLimitConfig.LimitType limitType, String limitKey) {
        RateLimiter rateLimiter = getRateLimiter(limitType, limitKey);
        if (rateLimiter == null) {
            // 如果没有限流器，默认允许通过
            return true;
        }
        
        return rateLimiter.tryAcquire();
    }

    /**
     * 尝试获取指定数量的许可
     * 
     * @param limitType 限流类型
     * @param limitKey 限流键
     * @param permits 许可数量
     * @return 是否获取成功
     */
    public boolean tryAcquire(PluginRateLimitConfig.LimitType limitType, String limitKey, int permits) {
        RateLimiter rateLimiter = getRateLimiter(limitType, limitKey);
        if (rateLimiter == null) {
            return true;
        }
        
        return rateLimiter.tryAcquire(permits);
    }

    /**
     * 获取当前QPS
     * 
     * @param limitType 限流类型
     * @param limitKey 限流键
     * @return 当前QPS
     */
    public double getCurrentQps(PluginRateLimitConfig.LimitType limitType, String limitKey) {
        RateLimiter rateLimiter = getRateLimiter(limitType, limitKey);
        if (rateLimiter == null) {
            return 0.0;
        }
        
        return rateLimiter.getRate();
    }
}
