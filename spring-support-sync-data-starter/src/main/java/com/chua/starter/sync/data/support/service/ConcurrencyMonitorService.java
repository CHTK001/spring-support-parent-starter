package com.chua.starter.sync.data.support.service;

import com.chua.starter.sync.data.support.sync.concurrency.PriorityTaskQueue;
import com.chua.starter.sync.data.support.sync.concurrency.RateLimiterConfig;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 并发控制监控服务
 *
 * @author System
 * @since 2026/03/09
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.sync", name = "monitoring-enabled", havingValue = "true", matchIfMissing = true)
public class ConcurrencyMonitorService {
    
    private final PriorityTaskQueue priorityTaskQueue;
    private final RateLimiterConfig rateLimiterConfig;
    
    /**
     * 定时监控并发控制状态
     * 每5分钟输出一次监控信息
     */
    @Scheduled(fixedRate = 300000)
    public void monitorConcurrency() {
        ConcurrencyStats stats = getConcurrencyStats();
        
        log.info("并发控制监控 - 优先级队列大小: {}, 限流器数量: {}", 
                stats.getQueueSize(), stats.getLimiterCount());
        
        if (stats.getQueueSize() > 50) {
            log.warn("优先级队列积压过多: {}", stats.getQueueSize());
        }
    }
    
    /**
     * 获取并发控制统计信息
     *
     * @return 统计信息
     */
    public ConcurrencyStats getConcurrencyStats() {
        ConcurrencyStats stats = new ConcurrencyStats();
        stats.setQueueSize(priorityTaskQueue.size());
        stats.setLimiterCount(rateLimiterConfig.size());
        stats.setTimestamp(System.currentTimeMillis());
        return stats;
    }
    
    /**
     * 并发控制统计信息
     */
    @Data
    public static class ConcurrencyStats {
        /**
         * 优先级队列大小
         */
        private int queueSize;
        
        /**
         * 限流器数量
         */
        private int limiterCount;
        
        /**
         * 时间戳
         */
        private long timestamp;
    }
}
