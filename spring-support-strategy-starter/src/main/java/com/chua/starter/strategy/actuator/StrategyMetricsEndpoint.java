package com.chua.starter.strategy.actuator;

import com.chua.starter.strategy.aspect.BulkheadAspect;
import com.chua.starter.strategy.cache.MultiLevelCacheManager;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 策略模块监控指标Actuator端点
 * <p>
 * 暴露策略执行指标到 /actuator/strategy
 * 使用Java 21特性（records、sealed classes、pattern matching）
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Component
@Endpoint(id = "strategy")
@ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
public class StrategyMetricsEndpoint {

    private final MetricsCollector metricsCollector = new MetricsCollector();
    
    @Nullable
    private final MultiLevelCacheManager cacheManager;

    public StrategyMetricsEndpoint(@Nullable MultiLevelCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * 获取所有策略指标
     */
    @ReadOperation
    public StrategyMetrics getAllMetrics() {
        return new StrategyMetrics(
                LocalDateTime.now(),
                getRateLimiterMetrics(),
                getDebounceMetrics(),
                getCircuitBreakerMetrics(),
                getRetryMetrics(),
                getBulkheadMetrics(),
                getCacheMetrics()
        );
    }

    /**
     * 获取指定类型的指标
     */
    @ReadOperation
    public Object getMetricsByType(@Selector String type) {
        return switch (type.toLowerCase()) {
            case "ratelimiter", "rate-limiter" -> getRateLimiterMetrics();
            case "debounce" -> getDebounceMetrics();
            case "circuitbreaker", "circuit-breaker" -> getCircuitBreakerMetrics();
            case "retry" -> getRetryMetrics();
            case "bulkhead" -> getBulkheadMetrics();
            case "cache" -> getCacheMetrics();
            case "summary" -> getSummary();
            default -> Map.of("error", "Unknown metric type: " + type);
        };
    }

    /**
     * 重置指标
     */
    @WriteOperation
    public Map<String, String> resetMetrics(@Selector String type) {
        return switch (type.toLowerCase()) {
            case "all" -> {
                metricsCollector.resetAll();
                yield Map.of("status", "success", "message", "All metrics reset");
            }
            case "ratelimiter" -> {
                metricsCollector.reset(StrategyType.RATE_LIMITER);
                yield Map.of("status", "success", "message", "Rate limiter metrics reset");
            }
            case "debounce" -> {
                metricsCollector.reset(StrategyType.DEBOUNCE);
                yield Map.of("status", "success", "message", "Debounce metrics reset");
            }
            default -> Map.of("status", "error", "message", "Unknown type: " + type);
        };
    }

    // ==================== 各类型指标获取 ====================

    private Map<String, StrategyTypeMetrics> getRateLimiterMetrics() {
        return metricsCollector.getMetrics(StrategyType.RATE_LIMITER);
    }

    private Map<String, StrategyTypeMetrics> getDebounceMetrics() {
        return metricsCollector.getMetrics(StrategyType.DEBOUNCE);
    }

    private Map<String, StrategyTypeMetrics> getCircuitBreakerMetrics() {
        return metricsCollector.getMetrics(StrategyType.CIRCUIT_BREAKER);
    }

    private Map<String, StrategyTypeMetrics> getRetryMetrics() {
        return metricsCollector.getMetrics(StrategyType.RETRY);
    }

    private Map<String, BulkheadAspect.BulkheadMetrics> getBulkheadMetrics() {
        return BulkheadAspect.getMetrics();
    }

    private Map<String, Object> getCacheMetrics() {
        if (cacheManager == null) {
            return Map.of("enabled", false);
        }
        
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("enabled", true);
        metrics.put("caches", cacheManager.getStats());
        return metrics;
    }

    private SummaryMetrics getSummary() {
        return new SummaryMetrics(
                metricsCollector.getTotalRequests(),
                metricsCollector.getTotalSuccesses(),
                metricsCollector.getTotalFailures(),
                metricsCollector.getSuccessRate()
        );
    }

    // ==================== 供切面调用的记录方法 ====================

    /**
     * 记录策略执行结果
     */
    public void record(StrategyType type, String key, boolean success) {
        metricsCollector.record(type, key, success);
    }

    /**
     * 记录策略执行结果（带耗时）
     */
    public void record(StrategyType type, String key, boolean success, long durationMs) {
        metricsCollector.record(type, key, success, durationMs);
    }

    // ==================== Java 21 Records & Sealed Classes ====================

    /**
     * 总体指标
     */
    public record StrategyMetrics(
            LocalDateTime timestamp,
            Map<String, StrategyTypeMetrics> rateLimiter,
            Map<String, StrategyTypeMetrics> debounce,
            Map<String, StrategyTypeMetrics> circuitBreaker,
            Map<String, StrategyTypeMetrics> retry,
            Map<String, BulkheadAspect.BulkheadMetrics> bulkhead,
            Map<String, Object> cache
    ) {}

    /**
     * 各策略类型指标
     */
    public record StrategyTypeMetrics(
            long totalRequests,
            long successCount,
            long failureCount,
            double successRate,
            long avgDurationMs,
            LocalDateTime lastAccessTime
    ) {
        public static StrategyTypeMetrics from(MetricData data) {
            long total = data.totalRequests.sum();
            long success = data.successCount.sum();
            long failure = data.failureCount.sum();
            long totalDuration = data.totalDuration.sum();
            
            return new StrategyTypeMetrics(
                    total,
                    success,
                    failure,
                    total == 0 ? 0 : (double) success / total,
                    total == 0 ? 0 : totalDuration / total,
                    data.lastAccessTime
            );
        }
    }

    /**
     * 摘要指标
     */
    public record SummaryMetrics(
            long totalRequests,
            long totalSuccesses,
            long totalFailures,
            double successRate
    ) {}

    /**
     * 策略类型枚举
     */
    public enum StrategyType {
        RATE_LIMITER, DEBOUNCE, CIRCUIT_BREAKER, RETRY, TIME_LIMITER, BULKHEAD
    }

    // ==================== 内部指标收集器 ====================

    private static class MetricsCollector {
        private final Map<StrategyType, Map<String, MetricData>> metricsMap = new ConcurrentHashMap<>();

        void record(StrategyType type, String key, boolean success) {
            record(type, key, success, 0);
        }

        void record(StrategyType type, String key, boolean success, long durationMs) {
            metricsMap.computeIfAbsent(type, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(key, k -> new MetricData())
                    .record(success, durationMs);
        }

        Map<String, StrategyTypeMetrics> getMetrics(StrategyType type) {
            Map<String, MetricData> data = metricsMap.get(type);
            if (data == null) return Map.of();
            
            Map<String, StrategyTypeMetrics> result = new HashMap<>();
            data.forEach((key, metric) -> result.put(key, StrategyTypeMetrics.from(metric)));
            return result;
        }

        void reset(StrategyType type) {
            metricsMap.remove(type);
        }

        void resetAll() {
            metricsMap.clear();
        }

        long getTotalRequests() {
            return metricsMap.values().stream()
                    .flatMap(m -> m.values().stream())
                    .mapToLong(d -> d.totalRequests.sum())
                    .sum();
        }

        long getTotalSuccesses() {
            return metricsMap.values().stream()
                    .flatMap(m -> m.values().stream())
                    .mapToLong(d -> d.successCount.sum())
                    .sum();
        }

        long getTotalFailures() {
            return metricsMap.values().stream()
                    .flatMap(m -> m.values().stream())
                    .mapToLong(d -> d.failureCount.sum())
                    .sum();
        }

        double getSuccessRate() {
            long total = getTotalRequests();
            return total == 0 ? 0 : (double) getTotalSuccesses() / total;
        }
    }

    private static class MetricData {
        final LongAdder totalRequests = new LongAdder();
        final LongAdder successCount = new LongAdder();
        final LongAdder failureCount = new LongAdder();
        final LongAdder totalDuration = new LongAdder();
        volatile LocalDateTime lastAccessTime = LocalDateTime.now();

        void record(boolean success, long durationMs) {
            totalRequests.increment();
            if (success) {
                successCount.increment();
            } else {
                failureCount.increment();
            }
            totalDuration.add(durationMs);
            lastAccessTime = LocalDateTime.now();
        }
    }
}
