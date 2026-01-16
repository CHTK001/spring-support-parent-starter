package com.chua.starter.strategy.interceptor;

import com.chua.starter.strategy.entity.SysCircuitBreakerConfiguration;
import com.chua.starter.strategy.entity.SysCircuitBreakerRecord;
import com.chua.starter.strategy.event.CircuitBreakerEvent;
import com.chua.starter.strategy.service.SysCircuitBreakerConfigurationService;
import com.chua.starter.strategy.service.SysCircuitBreakerRecordService;
import com.chua.starter.strategy.util.StrategyEventPublisher;
import com.chua.starter.strategy.util.UserContextHelper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 熔断HTTP拦截器
 * <p>
 * 基于URL路径匹配的熔断拦截器，当请求失败率超过阈值时触发熔断。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Slf4j
@RequiredArgsConstructor
public class CircuitBreakerInterceptor implements HandlerInterceptor {

    private static final String CIRCUIT_BREAKER_START_TIME = "circuitBreaker.startTime";

    private final SysCircuitBreakerConfigurationService configService;
    private final SysCircuitBreakerRecordService recordService;

    private final Map<String, CircuitBreaker> circuitBreakerCache = new ConcurrentHashMap<>();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, 
                            @NonNull HttpServletResponse response, 
                            @NonNull Object handler) throws Exception {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        // 查找匹配的熔断配置
        SysCircuitBreakerConfiguration config = findMatchingConfig(uri, method);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return true;
        }

        // 获取或创建熔断器
        CircuitBreaker circuitBreaker = getCircuitBreaker(config);
        
        // 检查熔断器状态
        if (!circuitBreaker.tryAcquirePermission()) {
            log.warn("熔断器打开: uri={}, name={}", uri, config.getName());
            
            // 记录熔断
            recordCircuitBreak(config, request, "CIRCUIT_OPEN");
            
            // 发布事件
            publishEvent(request, config, circuitBreaker.getState().name(), false, "熔断器打开");
            
            // 返回熔断响应
            handleCircuitOpen(response, config);
            return false;
        }

        // 发布允许事件
        publishEvent(request, config, circuitBreaker.getState().name(), true, null);

        // 记录开始时间
        request.setAttribute(CIRCUIT_BREAKER_START_TIME, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, 
                               @NonNull HttpServletResponse response,
                               @NonNull Object handler, Exception ex) {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        SysCircuitBreakerConfiguration config = findMatchingConfig(uri, method);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return;
        }

        CircuitBreaker circuitBreaker = getCircuitBreaker(config);
        Long startTime = (Long) request.getAttribute(CIRCUIT_BREAKER_START_TIME);
        long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;

        if (ex != null || response.getStatus() >= 500) {
            // 记录失败
            circuitBreaker.onError(duration, java.util.concurrent.TimeUnit.MILLISECONDS, 
                    ex != null ? ex : new RuntimeException("HTTP " + response.getStatus()));
            log.debug("熔断器记录失败: uri={}, duration={}ms", uri, duration);
            
            // 发布失败事件
            publishEvent(request, config, circuitBreaker.getState().name(), true, 
                    "请求失败: " + (ex != null ? ex.getMessage() : "HTTP " + response.getStatus()));
        } else {
            // 记录成功
            circuitBreaker.onSuccess(duration, java.util.concurrent.TimeUnit.MILLISECONDS);
            log.debug("熔断器记录成功: uri={}, duration={}ms", uri, duration);
        }
    }

    private SysCircuitBreakerConfiguration findMatchingConfig(String uri, String method) {
        List<SysCircuitBreakerConfiguration> configs = configService.list();
        
        for (SysCircuitBreakerConfiguration config : configs) {
            String pattern = config.getUrlPattern();
            String configMethod = config.getHttpMethod();
            
            if (pattern != null && pathMatcher.match(pattern, uri)) {
                if (configMethod == null || "*".equals(configMethod) || configMethod.equalsIgnoreCase(method)) {
                    return config;
                }
            }
        }
        
        return null;
    }

    private CircuitBreaker getCircuitBreaker(SysCircuitBreakerConfiguration config) {
        return circuitBreakerCache.computeIfAbsent(config.getName(), name -> {
            CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                    .failureRateThreshold(config.getFailureRateThreshold() != null ? 
                            config.getFailureRateThreshold().floatValue() : 50f)
                    .slowCallRateThreshold(config.getSlowCallRateThreshold() != null ? 
                            config.getSlowCallRateThreshold().floatValue() : 100f)
                    .slowCallDurationThreshold(Duration.ofMillis(config.getSlowCallDurationThreshold() != null ? 
                            config.getSlowCallDurationThreshold() : 60000))
                    .waitDurationInOpenState(Duration.ofSeconds(config.getWaitDurationInOpenState() != null ? 
                            config.getWaitDurationInOpenState() : 60))
                    .slidingWindowSize(config.getSlidingWindowSize() != null ? 
                            config.getSlidingWindowSize() : 100)
                    .minimumNumberOfCalls(config.getMinimumNumberOfCalls() != null ? 
                            config.getMinimumNumberOfCalls() : 10)
                    .permittedNumberOfCallsInHalfOpenState(config.getPermittedNumberOfCallsInHalfOpenState() != null ? 
                            config.getPermittedNumberOfCallsInHalfOpenState() : 10)
                    .build();

            CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(cbConfig);
            CircuitBreaker cb = registry.circuitBreaker(name);
            
            // 添加事件监听
            cb.getEventPublisher()
                    .onStateTransition(event -> 
                            log.info("熔断器状态变更: name={}, from={}, to={}", 
                                    name, event.getStateTransition().getFromState(), 
                                    event.getStateTransition().getToState()));
            
            log.info("创建熔断器: name={}, failureRate={}", name, config.getFailureRateThreshold());
            return cb;
        });
    }

    private void handleCircuitOpen(HttpServletResponse response, SysCircuitBreakerConfiguration config) 
            throws IOException {
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setContentType("application/json;charset=UTF-8");
        
        String message = config.getFallbackMessage() != null ? 
                config.getFallbackMessage() : "服务暂时不可用，请稍后重试";
        
        response.getWriter().write(String.format(
                "{\"code\":503,\"message\":\"%s\",\"success\":false}", message));
    }

    private void recordCircuitBreak(SysCircuitBreakerConfiguration config, 
                                   HttpServletRequest request, String reason) {
        try {
            SysCircuitBreakerRecord record = new SysCircuitBreakerRecord();
            record.setConfigId(config.getId());
            record.setConfigName(config.getName());
            record.setRequestUri(request.getRequestURI());
            record.setRequestMethod(request.getMethod());
            record.setTriggerReason(reason);
            record.setTriggerTime(LocalDateTime.now());
            record.setClientIp(getClientIp(request));
            
            recordService.save(record);
        } catch (Exception e) {
            log.warn("记录熔断事件失败", e);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip != null ? ip.split(",")[0].trim() : "unknown";
    }

    /**
     * 刷新熔断器配置
     */
    public void refreshCircuitBreaker(String name) {
        circuitBreakerCache.remove(name);
        log.info("刷新熔断器配置: name={}", name);
    }

    /**
     * 重置熔断器状态
     */
    public void resetCircuitBreaker(String name) {
        CircuitBreaker cb = circuitBreakerCache.get(name);
        if (cb != null) {
            cb.reset();
            log.info("重置熔断器状态: name={}", name);
        }
    }

    /**
     * 发布熔断事件
     *
     * @param request               HTTP请求
     * @param config                熔断配置
     * @param state                 熔断器状态
     * @param allowed               是否允许通过
     * @param reason                拒绝原因
     */
    private void publishEvent(HttpServletRequest request, SysCircuitBreakerConfiguration config,
                             String state, boolean allowed, String reason) {
        String clientIp = getClientIp(request);
        String userId = UserContextHelper.getUserId(request);

        CircuitBreakerEvent event = new CircuitBreakerEvent(
                this,
                request.getRequestURI(),
                request.getMethod(),
                clientIp,
                userId,
                config.getName(),
                allowed,
                reason,
                null,
                config.getId(),
                state,
                config.getFailureRateThreshold() != null ? config.getFailureRateThreshold().floatValue() : null
        );

        StrategyEventPublisher.publishEvent(event);
    }
}
