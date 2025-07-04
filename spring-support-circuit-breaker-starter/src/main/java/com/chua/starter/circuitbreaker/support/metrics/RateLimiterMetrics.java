package com.chua.starter.circuitbreaker.support.metrics;

import com.chua.starter.circuitbreaker.support.annotation.RateLimiter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 限流器指标收集器
 * 
 * 负责收集和记录限流器的各种指标，包括QPS、成功率、拒绝率等。
 * 集成Micrometer指标系统，支持Prometheus等监控系统。
 * 
 * @author CH
 * @since 2024/12/20
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(MeterRegistry.class)
public class RateLimiterMetrics {

    private final MeterRegistry meterRegistry;
    
    /**
     * 计数器缓存，避免重复创建
     */
    private final ConcurrentMap<String, Counter> counterCache = new ConcurrentHashMap<>();
    
    /**
     * 计时器缓存，避免重复创建
     */
    private final ConcurrentMap<String, Timer> timerCache = new ConcurrentHashMap<>();

    /**
     * 记录限流请求总数
     * 
     * @param limiterName 限流器名称
     * @param dimension 限流维度
     * @param status 状态（success/rejected）
     */
    public void recordRequest(String limiterName, RateLimiter.Dimension dimension, String status) {
        try {
            // 总体QPS指标
            getOrCreateCounter("rate_limiter_qps_total", 
                "limiter_name", limiterName,
                "dimension", dimension.name().toLowerCase(),
                "status", status)
                .increment();

            // 按维度的QPS指标
            String dimensionMetricName = "rate_limiter_qps_" + dimension.name().toLowerCase();
            getOrCreateCounter(dimensionMetricName,
                "limiter_name", limiterName,
                "status", status)
                .increment();
                
            log.debug("记录限流指标: limiter={}, dimension={}, status={}", 
                     limiterName, dimension, status);
                     
        } catch (Exception e) {
            log.warn("记录限流指标失败: limiter={}, dimension={}, status={}", 
                    limiterName, dimension, status, e);
        }
    }

    /**
     * 记录限流请求执行时间
     * 
     * @param limiterName 限流器名称
     * @param dimension 限流维度
     * @param duration 执行时间
     * @param status 状态
     */
    public void recordExecutionTime(String limiterName, RateLimiter.Dimension dimension, 
                                   Duration duration, String status) {
        try {
            String timerName = "rate_limiter_execution_time";
            getOrCreateTimer(timerName,
                "limiter_name", limiterName,
                "dimension", dimension.name().toLowerCase(),
                "status", status)
                .record(duration);
                
            log.debug("记录限流执行时间: limiter={}, dimension={}, duration={}ms, status={}", 
                     limiterName, dimension, duration.toMillis(), status);
                     
        } catch (Exception e) {
            log.warn("记录限流执行时间失败: limiter={}, dimension={}, status={}", 
                    limiterName, dimension, status, e);
        }
    }

    /**
     * 记录限流器等待时间
     * 
     * @param limiterName 限流器名称
     * @param dimension 限流维度
     * @param waitTime 等待时间
     */
    public void recordWaitTime(String limiterName, RateLimiter.Dimension dimension, Duration waitTime) {
        try {
            String timerName = "rate_limiter_wait_time";
            getOrCreateTimer(timerName,
                "limiter_name", limiterName,
                "dimension", dimension.name().toLowerCase())
                .record(waitTime);
                
            log.debug("记录限流等待时间: limiter={}, dimension={}, waitTime={}ms", 
                     limiterName, dimension, waitTime.toMillis());
                     
        } catch (Exception e) {
            log.warn("记录限流等待时间失败: limiter={}, dimension={}", 
                    limiterName, dimension, e);
        }
    }

    /**
     * 记录限流器当前状态
     * 
     * @param limiterName 限流器名称
     * @param dimension 限流维度
     * @param availablePermissions 可用许可数
     * @param waitingThreads 等待线程数
     */
    public void recordCurrentState(String limiterName, RateLimiter.Dimension dimension, 
                                  int availablePermissions, int waitingThreads) {
        try {
            // 可用许可数指标
            meterRegistry.gauge("rate_limiter_available_permissions",
                io.micrometer.core.instrument.Tags.of(
                    "limiter_name", limiterName,
                    "dimension", dimension.name().toLowerCase()
                ), availablePermissions);

            // 等待线程数指标
            meterRegistry.gauge("rate_limiter_waiting_threads",
                io.micrometer.core.instrument.Tags.of(
                    "limiter_name", limiterName,
                    "dimension", dimension.name().toLowerCase()
                ), waitingThreads);
                
            log.debug("记录限流器状态: limiter={}, dimension={}, available={}, waiting={}", 
                     limiterName, dimension, availablePermissions, waitingThreads);
                     
        } catch (Exception e) {
            log.warn("记录限流器状态失败: limiter={}, dimension={}", 
                    limiterName, dimension, e);
        }
    }

    /**
     * 获取或创建计数器
     */
    private Counter getOrCreateCounter(String name, String... tags) {
        String cacheKey = name + ":" + String.join(":", tags);
        return counterCache.computeIfAbsent(cacheKey, k -> 
            Counter.builder(name)
                .description("Rate limiter counter metric")
                .tags(tags)
                .register(meterRegistry)
        );
    }

    /**
     * 获取或创建计时器
     */
    private Timer getOrCreateTimer(String name, String... tags) {
        String cacheKey = name + ":" + String.join(":", tags);
        return timerCache.computeIfAbsent(cacheKey, k -> 
            Timer.builder(name)
                .description("Rate limiter timer metric")
                .tags(tags)
                .register(meterRegistry)
        );
    }

    /**
     * 清理指标缓存
     * 
     * @param limiterName 限流器名称
     */
    public void clearMetrics(String limiterName) {
        try {
            // 移除相关的计数器
            counterCache.entrySet().removeIf(entry -> 
                entry.getKey().contains("limiter_name:" + limiterName));
            
            // 移除相关的计时器
            timerCache.entrySet().removeIf(entry -> 
                entry.getKey().contains("limiter_name:" + limiterName));
                
            log.info("清理限流器指标: limiter={}", limiterName);
            
        } catch (Exception e) {
            log.warn("清理限流器指标失败: limiter={}", limiterName, e);
        }
    }

    /**
     * 获取指标统计信息
     * 
     * @return 指标统计信息
     */
    public MetricsStatistics getStatistics() {
        try {
            int totalCounters = counterCache.size();
            int totalTimers = timerCache.size();
            
            // 计算总请求数
            long totalRequests = counterCache.values().stream()
                .filter(counter -> counter.getId().getName().equals("rate_limiter_qps_total"))
                .mapToLong(counter -> (long) counter.count())
                .sum();
            
            // 计算成功请求数
            long successRequests = counterCache.values().stream()
                .filter(counter -> counter.getId().getName().equals("rate_limiter_qps_total") 
                    && "success".equals(counter.getId().getTag("status")))
                .mapToLong(counter -> (long) counter.count())
                .sum();
            
            // 计算拒绝请求数
            long rejectedRequests = totalRequests - successRequests;
            
            return MetricsStatistics.builder()
                .totalCounters(totalCounters)
                .totalTimers(totalTimers)
                .totalRequests(totalRequests)
                .successRequests(successRequests)
                .rejectedRequests(rejectedRequests)
                .successRate(totalRequests > 0 ? (double) successRequests / totalRequests : 0.0)
                .build();
                
        } catch (Exception e) {
            log.warn("获取指标统计信息失败", e);
            return MetricsStatistics.builder().build();
        }
    }

    /**
     * 指标统计信息
     */
    @lombok.Builder
    @lombok.Data
    public static class MetricsStatistics {
        private int totalCounters;
        private int totalTimers;
        private long totalRequests;
        private long successRequests;
        private long rejectedRequests;
        private double successRate;
    }
}
