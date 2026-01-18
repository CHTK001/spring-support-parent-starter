package com.chua.starter.strategy.interceptor;

import com.chua.starter.strategy.entity.SysLimitConfiguration;
import com.chua.starter.strategy.entity.SysLimitRecord;
import com.chua.starter.strategy.event.RateLimitEvent;
import com.chua.starter.strategy.service.SysLimitConfigurationService;
import com.chua.starter.strategy.service.SysLimitRecordService;
import com.chua.starter.strategy.util.StrategyEventPublisher;
import com.chua.starter.strategy.util.UserContextHelper;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流配置拦截器
 *
 * 处理数据库配置的限流策略，拦截HTTP请求
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SysLimitConfigurationInterceptor implements HandlerInterceptor {

    private final SysLimitConfigurationService sysLimitConfigurationService;
    private final RateLimiterRegistry rateLimiterRegistry;

    @Autowired(required = false)
    private SysLimitRecordService sysLimitRecordService;

    /**
     * 限流器缓存
     * key: 配置ID + 维度值（如：1_192.168.1.1）
     */
    private final Map<String, RateLimiter> rateLimiterCache = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String requestPath = request.getRequestURI();

        // 从内存缓存中匹配限流配置
        SysLimitConfiguration config = sysLimitConfigurationService.getByPath(requestPath);

        if (config == null) {
            // 没有匹配的配置，放行
            return true;
        }

        // 生成限流器键
        String rateLimiterKey = generateRateLimiterKey(config, request);

        // 获取或创建限流器
        RateLimiter rateLimiter = getRateLimiter(rateLimiterKey, config);

        try {
            // 尝试获取许可
            rateLimiter.acquirePermission();
            log.debug("限流检查通过: {} - {}", config.getSysLimitName(), rateLimiterKey);
            
            // 发布允许事件
            publishEvent(request, config, rateLimiterKey, true, null);
            return true;
        } catch (RequestNotPermitted e) {
            // 限流触发，记录日志
            log.warn("限流触发: {} - {}", config.getSysLimitName(), rateLimiterKey);

            // 异步保存限流记录
            saveRateLimitRecord(config, request);

            // 发布拒绝事件
            publishEvent(request, config, rateLimiterKey, false, config.getSysLimitMessage());

            // 返回限流错误
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(String.format("{\"code\":429,\"message\":\"%s\"}",
                    config.getSysLimitMessage()));
            return false;
        }
    }

    /**
     * 获取或创建限流器
     *
     * @param key    限流器键
     * @param config 限流配置
     * @return 限流器实例
     */
    private RateLimiter getRateLimiter(String key, SysLimitConfiguration config) {
        return rateLimiterCache.computeIfAbsent(key, k -> {
            RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
                    .limitForPeriod(config.getSysLimitForPeriod())
                    .limitRefreshPeriod(Duration.ofSeconds(config.getSysLimitRefreshPeriodSeconds()))
                    .timeoutDuration(Duration.ofMillis(config.getSysLimitTimeoutDurationMillis()))
                    .build();

            RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(k, rateLimiterConfig);
            log.debug("创建限流器: {} - 配置: {}/{} 秒", k,
                    config.getSysLimitForPeriod(),
                    config.getSysLimitRefreshPeriodSeconds());
            return rateLimiter;
        });
    }

    /**
     * 异步保存限流记录
     *
     * @param config  限流配置
     * @param request HTTP请求
     */
    private void saveRateLimitRecord(SysLimitConfiguration config, HttpServletRequest request) {
        if (sysLimitRecordService == null) {
            return;
        }

        try {
            SysLimitRecord record = new SysLimitRecord();
            record.setSysLimitConfigurationId(config.getSysLimitConfigurationId());
            record.setSysLimitPath(request.getRequestURI());
            record.setClientIp(getClientIp(request));
            record.setRequestMethod(request.getMethod());
            record.setSysLimitTime(LocalDateTime.now());

            // TODO: 获取用户信息
            // record.setSysUserId(...);
            // record.setSysUserName(...);

            sysLimitRecordService.saveAsync(record);
        } catch (Exception e) {
            log.error("保存限流记录失败", e);
        }
    }

    /**
     * 生成限流器键
     *
     * @param config  限流配置
     * @param request HTTP请求
     * @return 限流器键
     */
    private String generateRateLimiterKey(SysLimitConfiguration config, HttpServletRequest request) {
        StringBuilder keyBuilder = new StringBuilder("ratelimit:");
        keyBuilder.append(config.getSysLimitConfigurationId()).append(":");

        // 根据维度生成键
        String dimension = config.getSysLimitDimension();
        switch (dimension) {
            case "IP" -> keyBuilder.append(getClientIp(request));
            case "USER" -> keyBuilder.append(getUserId(request));
            case "API" -> keyBuilder.append(config.getSysLimitPath());
            default -> keyBuilder.append("global");
        }

        return keyBuilder.toString();
    }

    /**
     * 获取客户端IP
     *
     * @param request HTTP请求
     * @return 客户端IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip != null ? ip : "unknown";
    }

    /**
     * 获取用户ID
     *
     * @param request HTTP请求
     * @return 用户ID
     */
    private String getUserId(HttpServletRequest request) {
        String userId = UserContextHelper.getUserId(request);
        return userId != null ? userId : "anonymous";
    }

    /**
     * 发布限流事件
     *
     * @param request        HTTP请求
     * @param config         限流配置
     * @param rateLimitKey   限流键
     * @param allowed        是否允许通过
     * @param reason         拒绝原因
     */
    private void publishEvent(HttpServletRequest request, SysLimitConfiguration config,
                             String rateLimitKey, boolean allowed, String reason) {
        String clientIp = getClientIp(request);
        String userId = UserContextHelper.getUserId(request);

        RateLimitEvent event = new RateLimitEvent(
                this,
                request.getRequestURI(),
                request.getMethod(),
                clientIp,
                userId,
                config.getSysLimitName(),
                allowed,
                reason,
                null,
                config.getSysLimitConfigurationId(),
                config.getSysLimitDimension(),
                rateLimitKey,
                config.getSysLimitForPeriod(),
                config.getSysLimitRefreshPeriodSeconds()
        );

        StrategyEventPublisher.publishEvent(event);
    }
}
