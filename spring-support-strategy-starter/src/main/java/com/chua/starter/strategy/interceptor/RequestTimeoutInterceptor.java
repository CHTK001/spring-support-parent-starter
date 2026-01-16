package com.chua.starter.strategy.interceptor;

import com.chua.starter.strategy.config.StrategyProperties;
import com.chua.starter.strategy.event.RequestTimeoutEvent;
import com.chua.starter.strategy.util.StrategyEventPublisher;
import com.chua.starter.strategy.util.UserContextHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 请求超时控制拦截器
 * <p>
 * 控制请求处理超时时间，支持路径级别的超时配置。
 * 超时后返回408 Request Timeout。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Slf4j
@RequiredArgsConstructor
public class RequestTimeoutInterceptor implements HandlerInterceptor {

    /**
     * 请求超时配置
     */
    private final StrategyProperties.RequestTimeoutConfig requestTimeoutConfig;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 请求开始时间记录
     */
    private final Map<String, Long> requestStartTimes = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {
        if (!requestTimeoutConfig.isEnabled()) {
            return true;
        }

        String uri = request.getRequestURI();
        if (isExcluded(uri)) {
            return true;
        }

        // 记录请求开始时间
        String requestId = getRequestId(request);
        requestStartTimes.put(requestId, System.currentTimeMillis());

        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                 @NonNull HttpServletResponse response,
                                 @NonNull Object handler,
                                 Exception ex) throws Exception {
        if (!requestTimeoutConfig.isEnabled()) {
            return;
        }

        String uri = request.getRequestURI();
        if (isExcluded(uri)) {
            return;
        }

        String requestId = getRequestId(request);
        Long startTime = requestStartTimes.remove(requestId);

        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            long timeout = getTimeoutForPath(uri);

            if (duration > timeout) {
                log.warn("[策略模块][请求超时控制]请求处理超时, uri={}, duration={}ms, timeout={}ms",
                        uri, duration, timeout);
                publishEvent(request, false, duration, timeout);
                // 注意：此时响应可能已经发送，无法修改状态码
            } else {
                publishEvent(request, true, duration, timeout);
            }
        }
    }

    /**
     * 是否在排除名单中
     *
     * @param uri 请求路径
     * @return true 表示排除
     */
    private boolean isExcluded(String uri) {
        for (String pattern : requestTimeoutConfig.getExcludePatterns()) {
            if (pathMatcher.match(pattern, uri)) {
                return true;
            }
        }
        if (requestTimeoutConfig.getIncludePatterns().isEmpty()) {
            return false;
        }
        for (String pattern : requestTimeoutConfig.getIncludePatterns()) {
            if (pathMatcher.match(pattern, uri)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取路径的超时时间
     *
     * @param uri 请求路径
     * @return 超时时间（毫秒）
     */
    private long getTimeoutForPath(String uri) {
        // 检查路径级别的配置
        var pathTimeouts = requestTimeoutConfig.getPathTimeouts();
        if (pathTimeouts != null) {
            for (var entry : pathTimeouts.entrySet()) {
                if (pathMatcher.match(entry.getKey(), uri)) {
                    return entry.getValue();
                }
            }
        }

        // 返回默认配置
        return requestTimeoutConfig.getDefaultTimeout();
    }

    /**
     * 获取请求ID
     *
     * @param request HTTP请求
     * @return 请求ID
     */
    private String getRequestId(HttpServletRequest request) {
        return request.getRequestURI() + ":" + request.getRemoteAddr() + ":" + System.currentTimeMillis();
    }

    /**
     * 发布事件
     *
     * @param request  请求对象
     * @param allowed  是否允许通过
     * @param duration 处理时长
     * @param timeout  超时时间
     */
    private void publishEvent(HttpServletRequest request, boolean allowed,
                             long duration, long timeout) {
        String clientIp = getClientIp(request);
        String userId = UserContextHelper.getUserId(request);

        RequestTimeoutEvent event = new RequestTimeoutEvent(
                this,
                request.getRequestURI(),
                request.getMethod(),
                clientIp,
                userId,
                "请求超时控制",
                allowed,
                allowed ? null : "请求处理超时",
                null,
                duration,
                timeout
        );

        StrategyEventPublisher.publishEvent(event);
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
        return ip != null ? ip.split(",")[0].trim() : "unknown";
    }
}

