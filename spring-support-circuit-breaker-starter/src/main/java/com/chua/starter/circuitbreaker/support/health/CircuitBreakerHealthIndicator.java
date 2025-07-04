package com.chua.starter.circuitbreaker.support.health;

import com.chua.starter.circuitbreaker.support.service.CircuitBreakerService;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.util.HashMap;
import java.util.Map;

/**
 * 熔断降级健康检查指示器
 * 
 * 提供熔断器、限流器、舱壁隔离等功能的健康状态检查。
 * 集成到Spring Boot Actuator的健康检查端点中。
 * 
 * @author CH
 * @since 2024/12/20
 */
@Slf4j
@RequiredArgsConstructor
public class CircuitBreakerHealthIndicator implements HealthIndicator {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final CircuitBreakerService circuitBreakerService;

    @Override
    public Health health() {
        try {
            Health.Builder builder = Health.up();
            
            // 检查熔断器状态
            Map<String, Object> circuitBreakerDetails = checkCircuitBreakers();
            builder.withDetail("circuitBreakers", circuitBreakerDetails);
            
            // 检查限流器状态
            Map<String, Object> rateLimiterDetails = checkRateLimiters();
            builder.withDetail("rateLimiters", rateLimiterDetails);
            
            // 检查舱壁隔离状态
            Map<String, Object> bulkheadDetails = checkBulkheads();
            builder.withDetail("bulkheads", bulkheadDetails);
            
            // 检查整体健康状态
            boolean isHealthy = isOverallHealthy(circuitBreakerDetails, rateLimiterDetails, bulkheadDetails);
            
            if (isHealthy) {
                return builder.build();
            } else {
                return builder.down().build();
            }
            
        } catch (Exception e) {
            log.error("熔断降级健康检查失败", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }

    /**
     * 检查熔断器状态
     * 
     * @return 熔断器状态详情
     */
    private Map<String, Object> checkCircuitBreakers() {
        Map<String, Object> details = new HashMap<>();
        
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(circuitBreaker -> {
            String name = circuitBreaker.getName();
            CircuitBreaker.State state = circuitBreaker.getState();
            CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
            
            Map<String, Object> cbDetails = new HashMap<>();
            cbDetails.put("state", state.name());
            cbDetails.put("failureRate", String.format("%.2f%%", metrics.getFailureRate()));
            cbDetails.put("slowCallRate", String.format("%.2f%%", metrics.getSlowCallRate()));
            cbDetails.put("bufferedCalls", metrics.getNumberOfBufferedCalls());
            cbDetails.put("failedCalls", metrics.getNumberOfFailedCalls());
            cbDetails.put("slowCalls", metrics.getNumberOfSlowCalls());
            cbDetails.put("successfulCalls", metrics.getNumberOfSuccessfulCalls());
            cbDetails.put("notPermittedCalls", metrics.getNumberOfNotPermittedCalls());
            
            details.put(name, cbDetails);
        });
        
        return details;
    }

    /**
     * 检查限流器状态
     * 
     * @return 限流器状态详情
     */
    private Map<String, Object> checkRateLimiters() {
        Map<String, Object> details = new HashMap<>();
        
        rateLimiterRegistry.getAllRateLimiters().forEach(rateLimiter -> {
            String name = rateLimiter.getName();
            RateLimiter.Metrics metrics = rateLimiter.getMetrics();
            
            Map<String, Object> rlDetails = new HashMap<>();
            rlDetails.put("availablePermissions", metrics.getAvailablePermissions());
            rlDetails.put("numberOfWaitingThreads", metrics.getNumberOfWaitingThreads());
            
            details.put(name, rlDetails);
        });
        
        return details;
    }

    /**
     * 检查舱壁隔离状态
     * 
     * @return 舱壁隔离状态详情
     */
    private Map<String, Object> checkBulkheads() {
        Map<String, Object> details = new HashMap<>();
        
        bulkheadRegistry.getAllBulkheads().forEach(bulkhead -> {
            String name = bulkhead.getName();
            Bulkhead.Metrics metrics = bulkhead.getMetrics();
            
            Map<String, Object> bhDetails = new HashMap<>();
            bhDetails.put("availableConcurrentCalls", metrics.getAvailableConcurrentCalls());
            bhDetails.put("maxAllowedConcurrentCalls", metrics.getMaxAllowedConcurrentCalls());
            
            details.put(name, bhDetails);
        });
        
        return details;
    }

    /**
     * 检查整体健康状态
     * 
     * @param circuitBreakerDetails 熔断器详情
     * @param rateLimiterDetails 限流器详情
     * @param bulkheadDetails 舱壁隔离详情
     * @return 是否健康
     */
    private boolean isOverallHealthy(Map<String, Object> circuitBreakerDetails,
                                   Map<String, Object> rateLimiterDetails,
                                   Map<String, Object> bulkheadDetails) {
        
        // 检查是否有熔断器处于OPEN状态
        for (Object value : circuitBreakerDetails.values()) {
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cbDetail = (Map<String, Object>) value;
                String state = (String) cbDetail.get("state");
                if ("OPEN".equals(state)) {
                    log.warn("发现熔断器处于OPEN状态，整体健康状态为DOWN");
                    return false;
                }
            }
        }
        
        // 检查是否有限流器等待线程过多
        for (Object value : rateLimiterDetails.values()) {
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> rlDetail = (Map<String, Object>) value;
                Integer waitingThreads = (Integer) rlDetail.get("numberOfWaitingThreads");
                if (waitingThreads != null && waitingThreads > 10) {
                    log.warn("发现限流器等待线程过多: {}, 整体健康状态为DOWN", waitingThreads);
                    return false;
                }
            }
        }
        
        // 检查是否有舱壁隔离资源耗尽
        for (Object value : bulkheadDetails.values()) {
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> bhDetail = (Map<String, Object>) value;
                Integer availableCalls = (Integer) bhDetail.get("availableConcurrentCalls");
                Integer maxCalls = (Integer) bhDetail.get("maxAllowedConcurrentCalls");
                if (availableCalls != null && maxCalls != null && availableCalls == 0 && maxCalls > 0) {
                    log.warn("发现舱壁隔离资源耗尽，整体健康状态为DOWN");
                    return false;
                }
            }
        }
        
        return true;
    }
}
