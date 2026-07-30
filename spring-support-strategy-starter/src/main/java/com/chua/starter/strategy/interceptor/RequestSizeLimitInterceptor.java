package com.chua.starter.strategy.interceptor;

import com.chua.starter.strategy.config.StrategyProperties;
import com.chua.starter.strategy.event.RequestSizeLimitEvent;
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

/**
 * 请求大小限制拦截器
 * <p>
 * 限制请求体大小，防止DoS攻击。
 * 支持全局和路径级别的限制配置。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Slf4j
@RequiredArgsConstructor
public class RequestSizeLimitInterceptor implements HandlerInterceptor {

    /**
     * 请求大小限制配置
     */
    private final StrategyProperties.RequestSizeLimitConfig requestSizeLimitConfig;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {
        if (!requestSizeLimitConfig.isEnabled()) {
            return true;
        }

        String uri = request.getRequestURI();
        if (isExcluded(uri)) {
            return true;
        }

        // 获取该路径的最大请求大小
        long maxSize = getMaxSizeForPath(uri);

        // 检查Content-Length
        String contentLengthHeader = request.getHeader("Content-Length");
        if (contentLengthHeader != null && !contentLengthHeader.isEmpty()) {
            try {
                long contentLength = Long.parseLong(contentLengthHeader);
                if (contentLength > maxSize) {
                    log.warn("[策略模块][请求大小限制]请求体大小超过限制, uri={}, size={}, max={}",
                            uri, contentLength, maxSize);
                    publishEvent(request, false, contentLength, maxSize, "Content-Length超过限制");
                    handleRejected(response, contentLength, maxSize);
                    return false;
                }
            } catch (NumberFormatException e) {
                log.debug("Content-Length格式错误: {}", contentLengthHeader);
            }
        }

        // 验证通过
        publishEvent(request, true, 0L, maxSize, null);
        return true;
    }

    /**
     * 是否在排除名单中
     *
     * @param uri 请求路径
     * @return true 表示排除
     */
    private boolean isExcluded(String uri) {
        for (String pattern : requestSizeLimitConfig.getExcludePatterns()) {
            if (pathMatcher.match(pattern, uri)) {
                return true;
            }
        }
        if (requestSizeLimitConfig.getIncludePatterns().isEmpty()) {
            return false;
        }
        for (String pattern : requestSizeLimitConfig.getIncludePatterns()) {
            if (pathMatcher.match(pattern, uri)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取路径的最大请求大小
     *
     * @param uri 请求路径
     * @return 最大请求大小（字节）
     */
    private long getMaxSizeForPath(String uri) {
        // 检查路径级别的配置
        var pathTimeouts = requestSizeLimitConfig.getPathMaxSizes();
        if (pathTimeouts != null) {
            for (var entry : pathTimeouts.entrySet()) {
                if (pathMatcher.match(entry.getKey(), uri)) {
                    return entry.getValue();
                }
            }
        }

        // 返回默认配置
        return requestSizeLimitConfig.getMaxSize();
    }

    /**
     * 拒绝请求
     *
     * @param response     响应对象
     * @param actualSize   实际大小
     * @param maxSize     最大大小
     * @throws IOException 写响应异常
     */
    private void handleRejected(HttpServletResponse response, long actualSize, long maxSize) throws IOException {
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType("application/json;charset=UTF-8");
        String message = requestSizeLimitConfig.getMessage();
        response.getWriter().write(
                String.format("{\"code\":413,\"message\":\"%s\",\"success\":false,\"actualSize\":%d,\"maxSize\":%d}",
                        message, actualSize, maxSize));
    }

    /**
     * 发布事件
     *
     * @param request     请求对象
     * @param allowed     是否允许通过
     * @param actualSize  实际大小
     * @param maxSize     最大大小
     * @param reason      拒绝原因
     */
    private void publishEvent(HttpServletRequest request, boolean allowed,
                             long actualSize, long maxSize, String reason) {
        String clientIp = getClientIp(request);
        String userId = UserContextHelper.getUserId(request);

        RequestSizeLimitEvent event = new RequestSizeLimitEvent(
                this,
                request.getRequestURI(),
                request.getMethod(),
                clientIp,
                userId,
                "请求大小限制",
                allowed,
                allowed ? null : reason,
                null,
                actualSize,
                maxSize
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

