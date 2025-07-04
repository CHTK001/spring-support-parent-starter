package com.chua.starter.circuitbreaker.support.controller;

import com.chua.starter.circuitbreaker.support.service.CircuitBreakerService;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.common.support.oauth.CurrentUser;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 熔断降级管理控制器
 *
 * 提供熔断器、重试、限流器、舱壁隔离、超时控制等功能的管理接口。
 * 支持查看状态、重置、强制开关等操作，集成用户认证服务记录操作用户信息。
 *
 * @author CH
 * @since 2024/12/20
 */
@Slf4j
@RestController
@RequestMapping("/actuator/circuit-breaker")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.circuit-breaker", name = "enable", havingValue = "true", matchIfMissing = true)
public class CircuitBreakerController {

    private final CircuitBreakerService circuitBreakerService;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;

    /**
     * 认证服务 - 用于获取当前登录用户的账号信息
     * 提供用户身份验证和权限检查功能，在熔断器管理操作中记录操作用户
     */
    @Autowired(required = false)
    private AuthService authService;

    /**
     * 获取当前用户信息的辅助方法
     *
     * @return 包含用户名和用户ID的数组，[username, userId]
     */
    private String[] getCurrentUserInfo() {
        String username = "系统";
        String userId = "unknown";

        if (authService != null) {
            CurrentUser currentUser = authService.getCurrentUser();
            if(null != currentUser) {
                try {
                    String authUsername = currentUser.getUsername();
                    String authUserId = currentUser.getUserId();
                    username = authUsername != null ? authUsername : "匿名用户";
                    userId = authUserId != null ? authUserId : "unknown";
                } catch (Exception e) {
                    log.warn("获取当前用户信息失败: {}", e.getMessage());
                }
            }
        }

        return new String[]{username, userId};
    }

    /**
     * 获取所有熔断器状态
     *
     * @return 熔断器状态信息
     */
    @GetMapping("/circuit-breakers")
    public Map<String, Object> getCircuitBreakers() {
        String[] userInfo = getCurrentUserInfo();
        log.debug("查询所有熔断器状态 - 操作用户: {} (ID: {})", userInfo[0], userInfo[1]);

        Map<String, Object> result = new HashMap<>();

        circuitBreakerRegistry.getAllCircuitBreakers().forEach(cb -> {
            Map<String, Object> details = new HashMap<>();
            details.put("state", cb.getState().name());
            details.put("metrics", Map.of(
                    "failureRate", cb.getMetrics().getFailureRate(),
                    "slowCallRate", cb.getMetrics().getSlowCallRate(),
                    "bufferedCalls", cb.getMetrics().getNumberOfBufferedCalls(),
                    "failedCalls", cb.getMetrics().getNumberOfFailedCalls(),
                    "slowCalls", cb.getMetrics().getNumberOfSlowCalls(),
                    "successfulCalls", cb.getMetrics().getNumberOfSuccessfulCalls(),
                    "notPermittedCalls", cb.getMetrics().getNumberOfNotPermittedCalls()
            ));
            result.put(cb.getName(), details);
        });

        return result;
    }

    /**
     * 获取指定熔断器状态
     *
     * @param name 熔断器名称
     * @return 熔断器状态信息
     */
    @GetMapping("/circuit-breakers/{name}")
    public Map<String, Object> getCircuitBreaker(@PathVariable String name) {
        String[] userInfo = getCurrentUserInfo();
        log.debug("查询熔断器状态 - 熔断器: {}, 操作用户: {} (ID: {})", name, userInfo[0], userInfo[1]);

        String state = circuitBreakerService.getCircuitBreakerState(name);
        return Map.of("name", name, "state", state);
    }

    /**
     * 重置熔断器
     *
     * @param name 熔断器名称
     * @return 操作结果
     */
    @PostMapping("/circuit-breakers/{name}/reset")
    public Map<String, Object> resetCircuitBreaker(@PathVariable String name) {
        String[] userInfo = getCurrentUserInfo();

        try {
            circuitBreakerService.resetCircuitBreaker(name);
            log.info("熔断器重置成功 - 熔断器: {}, 操作用户: {} (ID: {})", name, userInfo[0], userInfo[1]);
            return Map.of(
                "message", "熔断器 " + name + " 已重置",
                "success", true,
                "operator", userInfo[0]
            );
        } catch (Exception e) {
            log.error("熔断器重置失败 - 熔断器: {}, 操作用户: {} (ID: {}), 错误: {}",
                     name, userInfo[0], userInfo[1], e.getMessage(), e);
            return Map.of(
                "message", "重置失败: " + e.getMessage(),
                "success", false,
                "operator", userInfo[0]
            );
        }
    }

    /**
     * 强制打开熔断器
     *
     * @param name 熔断器名称
     * @return 操作结果
     */
    @PostMapping("/circuit-breakers/{name}/force-open")
    public Map<String, Object> forceOpenCircuitBreaker(@PathVariable String name) {
        String[] userInfo = getCurrentUserInfo();

        try {
            circuitBreakerService.forceOpenCircuitBreaker(name);
            log.info("熔断器强制打开成功 - 熔断器: {}, 操作用户: {} (ID: {})", name, userInfo[0], userInfo[1]);
            return Map.of(
                "message", "熔断器 " + name + " 已强制打开",
                "success", true,
                "operator", userInfo[0]
            );
        } catch (Exception e) {
            log.error("熔断器强制打开失败 - 熔断器: {}, 操作用户: {} (ID: {}), 错误: {}",
                     name, userInfo[0], userInfo[1], e.getMessage(), e);
            return Map.of(
                "message", "强制打开失败: " + e.getMessage(),
                "success", false,
                "operator", userInfo[0]
            );
        }
    }

    /**
     * 强制关闭熔断器
     *
     * @param name 熔断器名称
     * @return 操作结果
     */
    @PostMapping("/circuit-breakers/{name}/force-close")
    public Map<String, Object> forceCloseCircuitBreaker(@PathVariable String name) {
        String[] userInfo = getCurrentUserInfo();

        try {
            circuitBreakerService.forceCloseCircuitBreaker(name);
            log.info("熔断器强制关闭成功 - 熔断器: {}, 操作用户: {} (ID: {})", name, userInfo[0], userInfo[1]);
            return Map.of(
                "message", "熔断器 " + name + " 已强制关闭",
                "success", true,
                "operator", userInfo[0]
            );
        } catch (Exception e) {
            log.error("熔断器强制关闭失败 - 熔断器: {}, 操作用户: {} (ID: {}), 错误: {}",
                     name, userInfo[0], userInfo[1], e.getMessage(), e);
            return Map.of(
                "message", "强制关闭失败: " + e.getMessage(),
                "success", false,
                "operator", userInfo[0]
            );
        }
    }

    /**
     * 获取所有限流器状态
     * 
     * @return 限流器状态信息
     */
    @GetMapping("/rate-limiters")
    public Map<String, Object> getRateLimiters() {
        Map<String, Object> result = new HashMap<>();
        
        rateLimiterRegistry.getAllRateLimiters().forEach(rl -> {
            Map<String, Object> details = new HashMap<>();
            details.put("metrics", Map.of(
                    "availablePermissions", rl.getMetrics().getAvailablePermissions(),
                    "numberOfWaitingThreads", rl.getMetrics().getNumberOfWaitingThreads()
            ));
            result.put(rl.getName(), details);
        });
        
        return result;
    }

    /**
     * 获取指定限流器状态
     * 
     * @param name 限流器名称
     * @return 限流器状态信息
     */
    @GetMapping("/rate-limiters/{name}")
    public Map<String, Object> getRateLimiter(@PathVariable String name) {
        String state = circuitBreakerService.getRateLimiterState(name);
        return Map.of("name", name, "state", state);
    }

    /**
     * 获取所有舱壁隔离状态
     * 
     * @return 舱壁隔离状态信息
     */
    @GetMapping("/bulkheads")
    public Map<String, Object> getBulkheads() {
        Map<String, Object> result = new HashMap<>();
        
        bulkheadRegistry.getAllBulkheads().forEach(bh -> {
            Map<String, Object> details = new HashMap<>();
            details.put("metrics", Map.of(
                    "availableConcurrentCalls", bh.getMetrics().getAvailableConcurrentCalls(),
                    "maxAllowedConcurrentCalls", bh.getMetrics().getMaxAllowedConcurrentCalls()
            ));
            result.put(bh.getName(), details);
        });
        
        return result;
    }

    /**
     * 获取指定舱壁隔离状态
     * 
     * @param name 舱壁隔离名称
     * @return 舱壁隔离状态信息
     */
    @GetMapping("/bulkheads/{name}")
    public Map<String, Object> getBulkhead(@PathVariable String name) {
        String state = circuitBreakerService.getBulkheadState(name);
        return Map.of("name", name, "state", state);
    }

    /**
     * 获取所有重试器状态
     * 
     * @return 重试器状态信息
     */
    @GetMapping("/retries")
    public Map<String, Object> getRetries() {
        return retryRegistry.getAllRetries().stream()
                .collect(Collectors.toMap(
                        retry -> retry.getName(),
                        retry -> Map.of(
                                "name", retry.getName(),
                                "metrics", Map.of(
                                        "numberOfSuccessfulCallsWithoutRetryAttempt", 
                                        retry.getMetrics().getNumberOfSuccessfulCallsWithoutRetryAttempt(),
                                        "numberOfSuccessfulCallsWithRetryAttempt", 
                                        retry.getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt(),
                                        "numberOfFailedCallsWithoutRetryAttempt", 
                                        retry.getMetrics().getNumberOfFailedCallsWithoutRetryAttempt(),
                                        "numberOfFailedCallsWithRetryAttempt", 
                                        retry.getMetrics().getNumberOfFailedCallsWithRetryAttempt()
                                )
                        )
                ));
    }

    /**
     * 获取所有超时控制器状态
     * 
     * @return 超时控制器状态信息
     */
//    @GetMapping("/time-limiters")
//    public Map<String, Object> getTimeLimiters() {
//        return timeLimiterRegistry.getAllTimeLimiters().stream()
//                .collect(Collectors.toMap(
//                        timeLimiter -> timeLimiter.getName(),
//                        timeLimiter -> Map.of(
//                                "name", timeLimiter.getName(),
//                                "metrics", Map.of(
//                                        "numberOfSuccessfulCalls",
//                                        timeLimiter.getMetrics().getNumberOfSuccessfulCalls(),
//                                        "numberOfFailedCalls",
//                                        timeLimiter.getMetrics().getNumberOfFailedCalls(),
//                                        "numberOfTimeoutCalls",
//                                        timeLimiter.getMetrics().getNumberOfTimeoutCalls()
//                                )
//                        )
//                ));
//    }

    /**
     * 获取整体状态概览
     *
     * @return 整体状态信息
     */
    @GetMapping("/overview")
    public Map<String, Object> getOverview() {
        String[] userInfo = getCurrentUserInfo();
        log.debug("查询整体状态概览 - 操作用户: {} (ID: {})", userInfo[0], userInfo[1]);

        Map<String, Object> overview = new HashMap<>();

        overview.put("circuitBreakers", Map.of(
                "total", circuitBreakerRegistry.getAllCircuitBreakers().size(),
                "open", circuitBreakerRegistry.getAllCircuitBreakers().stream()
                        .mapToInt(cb -> cb.getState().name().equals("OPEN") ? 1 : 0).sum(),
                "halfOpen", circuitBreakerRegistry.getAllCircuitBreakers().stream()
                        .mapToInt(cb -> cb.getState().name().equals("HALF_OPEN") ? 1 : 0).sum(),
                "closed", circuitBreakerRegistry.getAllCircuitBreakers().stream()
                        .mapToInt(cb -> cb.getState().name().equals("CLOSED") ? 1 : 0).sum()
        ));

        overview.put("rateLimiters", Map.of(
                "total", rateLimiterRegistry.getAllRateLimiters().size()
        ));

        overview.put("bulkheads", Map.of(
                "total", bulkheadRegistry.getAllBulkheads().size()
        ));

        overview.put("retries", Map.of(
                "total", retryRegistry.getAllRetries().size()
        ));

        overview.put("timeLimiters", Map.of(
                "total", timeLimiterRegistry.getAllTimeLimiters().size()
        ));

        // 添加当前用户信息
        if (authService != null) {
            try {
                overview.put("currentUser", Map.of(
                    "username", userInfo[0],
                    "userId", userInfo[1],
                    "authenticated", authService.isAuthenticated()
                ));
            } catch (Exception e) {
                log.warn("获取用户认证状态失败: {}", e.getMessage());
                overview.put("currentUser", Map.of(
                    "username", userInfo[0],
                    "userId", userInfo[1],
                    "authenticated", false
                ));
            }
        }

        return overview;
    }
}
