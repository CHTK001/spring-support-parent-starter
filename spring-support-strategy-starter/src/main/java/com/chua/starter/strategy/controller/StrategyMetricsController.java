package com.chua.starter.strategy.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.strategy.aspect.BulkheadAspect;
import com.chua.starter.strategy.actuator.StrategyMetricsEndpoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Strategy 监控指标 REST 包装接口
 * <p>
 * 统一把 actuator 指标包装到 `ReturnResult`，避免轻控制台依赖 actuator 暴露策略。
 * </p>
 *
 * @author System
 * @since 2026/03/26
 */
@RestController
@RequestMapping("/v2/strategy/metrics")
@Tag(name = "策略监控指标")
@RequiredArgsConstructor
@Slf4j
public class StrategyMetricsController {

    @Nullable
    @Autowired(required = false)
    private StrategyMetricsEndpoint strategyMetricsEndpoint;

    @GetMapping
    @Operation(summary = "获取全部策略指标")
    public ReturnResult<Object> getAllMetrics() {
        if (strategyMetricsEndpoint == null) {
            return ReturnResult.ok(emptyMetrics());
        }
        try {
            return ReturnResult.ok(toMetricsView(strategyMetricsEndpoint.getAllMetrics()));
        } catch (Exception e) {
            log.warn("构建策略指标快照失败，返回空指标视图", e);
            return ReturnResult.ok(emptyMetrics());
        }
    }

    @GetMapping("/{type}")
    @Operation(summary = "获取指定类型的策略指标")
    public ReturnResult<Object> getMetricsByType(@PathVariable String type) {
        if (strategyMetricsEndpoint == null) {
            return ReturnResult.ok("summary".equalsIgnoreCase(type) ? emptySummary() : Map.of());
        }
        try {
            return ReturnResult.ok(toMetricTypeView(type, strategyMetricsEndpoint.getMetricsByType(type)));
        } catch (Exception e) {
            log.warn("构建策略指标类型视图失败: type={}", type, e);
            return ReturnResult.ok("summary".equalsIgnoreCase(type) ? emptySummary() : Map.of());
        }
    }

    @PostMapping("/reset/{type}")
    @Operation(summary = "重置指定类型的策略指标")
    public ReturnResult<Map<String, String>> resetMetrics(@PathVariable String type) {
        if (strategyMetricsEndpoint == null) {
            return ReturnResult.ok(Map.of("status", "ignored", "message", "Strategy actuator is unavailable"));
        }
        return ReturnResult.ok(strategyMetricsEndpoint.resetMetrics(type));
    }

    private Map<String, Object> emptyMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("timestamp", null);
        metrics.put("summary", emptySummary());
        metrics.put("rateLimiter", Map.of());
        metrics.put("debounce", Map.of());
        metrics.put("circuitBreaker", Map.of());
        metrics.put("retry", Map.of());
        metrics.put("bulkhead", Map.of());
        metrics.put("cache", Map.of("enabled", false));
        return metrics;
    }

    private Map<String, Object> emptySummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRequests", 0);
        summary.put("totalSuccesses", 0);
        summary.put("totalFailures", 0);
        summary.put("successRate", 0D);
        return summary;
    }

    private Map<String, Object> toMetricsView(StrategyMetricsEndpoint.StrategyMetrics metrics) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("timestamp", metrics.timestamp() == null ? null : metrics.timestamp().toString());
        view.put("summary", toSummaryView(strategyMetricsEndpoint.getMetricsByType("summary")));
        view.put("rateLimiter", toStrategyTypeMetricsView(metrics.rateLimiter()));
        view.put("debounce", toStrategyTypeMetricsView(metrics.debounce()));
        view.put("circuitBreaker", toStrategyTypeMetricsView(metrics.circuitBreaker()));
        view.put("retry", toStrategyTypeMetricsView(metrics.retry()));
        view.put("bulkhead", toBulkheadMetricsView(metrics.bulkhead()));
        view.put("cache", toCacheMetricsView(metrics.cache()));
        return view;
    }

    private Object toMetricTypeView(String type, Object payload) {
        if (type == null) {
            return Map.of();
        }
        return switch (type.toLowerCase()) {
            case "summary" -> toSummaryView(payload);
            case "ratelimiter", "rate-limiter", "debounce", "circuitbreaker", "circuit-breaker", "retry" ->
                    toStrategyTypeMetricsView(payload);
            case "bulkhead" -> toBulkheadMetricsView(payload);
            case "cache" -> toCacheMetricsView(payload);
            default -> payload instanceof Map<?, ?> map ? copyObjectMap(map) : payload;
        };
    }

    private Map<String, Object> toSummaryView(Object summary) {
        if (summary instanceof StrategyMetricsEndpoint.SummaryMetrics metrics) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("totalRequests", metrics.totalRequests());
            view.put("totalSuccesses", metrics.totalSuccesses());
            view.put("totalFailures", metrics.totalFailures());
            view.put("successRate", metrics.successRate());
            return view;
        }
        if (summary instanceof Map<?, ?> map) {
            return copyObjectMap(map);
        }
        return emptySummary();
    }

    private Map<String, Object> toStrategyTypeMetricsView(Map<String, StrategyMetricsEndpoint.StrategyTypeMetrics> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            Map<String, Object> metric = new LinkedHashMap<>();
            metric.put("totalRequests", value.totalRequests());
            metric.put("successCount", value.successCount());
            metric.put("failureCount", value.failureCount());
            metric.put("successRate", value.successRate());
            metric.put("avgDurationMs", value.avgDurationMs());
            metric.put("lastAccessTime", value.lastAccessTime() == null ? null : value.lastAccessTime().toString());
            result.put(key, metric);
        });
        return result;
    }

    private Map<String, Object> toStrategyTypeMetricsView(Object source) {
        if (!(source instanceof Map<?, ?> map) || map.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            if (value instanceof StrategyMetricsEndpoint.StrategyTypeMetrics metrics) {
                Map<String, Object> metric = new LinkedHashMap<>();
                metric.put("totalRequests", metrics.totalRequests());
                metric.put("successCount", metrics.successCount());
                metric.put("failureCount", metrics.failureCount());
                metric.put("successRate", metrics.successRate());
                metric.put("avgDurationMs", metrics.avgDurationMs());
                metric.put("lastAccessTime", metrics.lastAccessTime() == null ? null : metrics.lastAccessTime().toString());
                result.put(String.valueOf(key), metric);
            } else if (value instanceof Map<?, ?> metricMap) {
                result.put(String.valueOf(key), copyObjectMap(metricMap));
            } else {
                result.put(String.valueOf(key), value);
            }
        });
        return result;
    }

    private Map<String, Object> toBulkheadMetricsView(Map<String, BulkheadAspect.BulkheadMetrics> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            Map<String, Object> metric = new LinkedHashMap<>();
            metric.put("availableConcurrentCalls", value.availableConcurrentCalls());
            metric.put("maxAllowedConcurrentCalls", value.maxAllowedConcurrentCalls());
            result.put(key, metric);
        });
        return result;
    }

    private Map<String, Object> toBulkheadMetricsView(Object source) {
        if (!(source instanceof Map<?, ?> map) || map.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            if (value instanceof BulkheadAspect.BulkheadMetrics metrics) {
                Map<String, Object> metric = new LinkedHashMap<>();
                metric.put("availableConcurrentCalls", metrics.availableConcurrentCalls());
                metric.put("maxAllowedConcurrentCalls", metrics.maxAllowedConcurrentCalls());
                result.put(String.valueOf(key), metric);
            } else if (value instanceof Map<?, ?> metricMap) {
                result.put(String.valueOf(key), copyObjectMap(metricMap));
            } else {
                result.put(String.valueOf(key), value);
            }
        });
        return result;
    }

    private Map<String, Object> toCacheMetricsView(Object source) {
        if (!(source instanceof Map<?, ?> map) || map.isEmpty()) {
            return Map.of("enabled", false);
        }
        Map<String, Object> view = copyObjectMap(map);
        Object caches = view.get("caches");
        if (caches instanceof Map<?, ?> cacheMap) {
            view.put("caches", copyObjectMap(cacheMap));
        }
        view.putIfAbsent("enabled", true);
        return view;
    }

    private Map<String, Object> copyObjectMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), normalizeValue(value)));
        return result;
    }

    private Object normalizeValue(Object value) {
        if (value instanceof StrategyMetricsEndpoint.SummaryMetrics) {
            return toSummaryView(value);
        }
        if (value instanceof StrategyMetricsEndpoint.StrategyTypeMetrics) {
            return toStrategyTypeMetricsView(Map.of("value", (StrategyMetricsEndpoint.StrategyTypeMetrics) value)).get("value");
        }
        if (value instanceof BulkheadAspect.BulkheadMetrics) {
            return toBulkheadMetricsView(Map.of("value", (BulkheadAspect.BulkheadMetrics) value)).get("value");
        }
        if (value instanceof Map<?, ?> map) {
            return copyObjectMap(map);
        }
        return value;
    }
}
