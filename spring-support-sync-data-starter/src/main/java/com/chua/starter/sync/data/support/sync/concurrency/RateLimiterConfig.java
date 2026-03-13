package com.chua.starter.sync.data.support.sync.concurrency;

import com.chua.starter.sync.data.support.properties.SyncProperties;
import com.google.common.util.concurrent.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 限流配置
 * 为每个任务提供独立的限流器
 *
 * @author System
 * @since 2026/03/09
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.sync", name = "rate-limit-enabled", havingValue = "true", matchIfMissing = true)
public class RateLimiterConfig {
    
    private final SyncProperties syncProperties;
    private final Map<Long, RateLimiter> limiters = new ConcurrentHashMap<>();
    
    /**
     * 获取任务的限流器
     *
     * @param taskId 任务ID
     * @return 限流器
     */
    public RateLimiter getLimiter(Long taskId) {
        return getLimiter(taskId, syncProperties.getDefaultRateLimit());
    }
    
    /**
     * 获取任务的限流器（指定速率）
     *
     * @param taskId 任务ID
     * @param permitsPerSecond 每秒允许的请求数
     * @return 限流器
     */
    public RateLimiter getLimiter(Long taskId, double permitsPerSecond) {
        return limiters.computeIfAbsent(taskId, k -> {
            RateLimiter limiter = RateLimiter.create(permitsPerSecond);
            log.info("为任务创建限流器: taskId={}, rate={}/s", taskId, permitsPerSecond);
            return limiter;
        });
    }
    
    /**
     * 尝试获取许可（非阻塞）
     *
     * @param taskId 任务ID
     * @return 是否获取成功
     */
    public boolean tryAcquire(Long taskId) {
        RateLimiter limiter = getLimiter(taskId);
        return limiter.tryAcquire();
    }
    
    /**
     * 尝试获取许可（超时）
     *
     * @param taskId 任务ID
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return 是否获取成功
     */
    public boolean tryAcquire(Long taskId, long timeout, TimeUnit unit) {
        RateLimiter limiter = getLimiter(taskId);
        return limiter.tryAcquire(timeout, unit);
    }
    
    /**
     * 获取许可（阻塞）
     *
     * @param taskId 任务ID
     */
    public void acquire(Long taskId) {
        RateLimiter limiter = getLimiter(taskId);
        limiter.acquire();
    }
    
    /**
     * 获取多个许可（阻塞）
     *
     * @param taskId 任务ID
     * @param permits 许可数量
     */
    public void acquire(Long taskId, int permits) {
        RateLimiter limiter = getLimiter(taskId);
        limiter.acquire(permits);
    }
    
    /**
     * 更新限流速率
     *
     * @param taskId 任务ID
     * @param permitsPerSecond 每秒允许的请求数
     */
    public void updateRate(Long taskId, double permitsPerSecond) {
        RateLimiter limiter = limiters.get(taskId);
        if (limiter != null) {
            limiter.setRate(permitsPerSecond);
            log.info("更新任务限流速率: taskId={}, newRate={}/s", taskId, permitsPerSecond);
        } else {
            getLimiter(taskId, permitsPerSecond);
        }
    }
    
    /**
     * 移除任务的限流器
     *
     * @param taskId 任务ID
     */
    public void removeLimiter(Long taskId) {
        RateLimiter removed = limiters.remove(taskId);
        if (removed != null) {
            log.info("移除任务限流器: taskId={}", taskId);
        }
    }
    
    /**
     * 清空所有限流器
     */
    public void clear() {
        limiters.clear();
        log.info("已清空所有限流器");
    }
    
    /**
     * 获取限流器数量
     *
     * @return 限流器数量
     */
    public int size() {
        return limiters.size();
    }
}
