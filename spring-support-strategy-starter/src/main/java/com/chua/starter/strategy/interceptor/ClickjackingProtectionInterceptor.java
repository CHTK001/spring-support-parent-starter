package com.chua.starter.strategy.interceptor;

import com.chua.starter.strategy.config.StrategyProperties;
import com.chua.starter.strategy.event.ClickjackingProtectionEvent;
import com.chua.starter.strategy.util.StrategyEventPublisher;
import com.chua.starter.strategy.util.UserContextHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 点击劫持防护拦截器
 * <p>
 * 设置X-Frame-Options响应头，防止页面被嵌入到iframe中。
 * 支持配置X-Frame-Options值。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Slf4j
@RequiredArgsConstructor
public class ClickjackingProtectionInterceptor implements HandlerInterceptor {

    /**
     * 点击劫持防护配置
     */
    private final StrategyProperties.ClickjackingProtectionConfig clickjackingConfig;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {
        if (!clickjackingConfig.isEnabled()) {
            return true;
        }

        String uri = request.getRequestURI();
        if (isExcluded(uri)) {
            return true;
        }

        // 获取该路径的X-Frame-Options策略
        String policy = getPolicyForPath(uri);

        if (policy != null && !policy.isEmpty()) {
            // 设置X-Frame-Options响应头
            response.setHeader("X-Frame-Options", policy);

            publishEvent(request, true, policy);
        }

        return true;
    }

    /**
     * 是否在排除名单中
     *
     * @param uri 请求路径
     * @return true 表示排除
     */
    private boolean isExcluded(String uri) {
        for (String pattern : clickjackingConfig.getExemptPatterns()) {
            if (pathMatcher.match(pattern, uri)) {
                return true;
            }
        }
        if (clickjackingConfig.getIncludePatterns().isEmpty()) {
            return false;
        }
        for (String pattern : clickjackingConfig.getIncludePatterns()) {
            if (pathMatcher.match(pattern, uri)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取路径的X-Frame-Options策略
     *
     * @param uri 请求路径
     * @return X-Frame-Options策略值
     */
    private String getPolicyForPath(String uri) {
        // 检查路径级别的配置
        var pathPolicies = clickjackingConfig.getPathPolicies();
        if (pathPolicies != null) {
            for (var entry : pathPolicies.entrySet()) {
                if (pathMatcher.match(entry.getKey(), uri)) {
                    return entry.getValue();
                }
            }
        }

        // 返回默认配置
        return clickjackingConfig.getPolicy();
    }

    /**
     * 发布事件
     *
     * @param request 请求对象
     * @param allowed 是否允许通过
     * @param policy X-Frame-Options策略
     */
    private void publishEvent(HttpServletRequest request, boolean allowed, String policy) {
        String clientIp = getClientIp(request);
        String userId = UserContextHelper.getUserId(request);

        ClickjackingProtectionEvent event = new ClickjackingProtectionEvent(
                this,
                request.getRequestURI(),
                request.getMethod(),
                clientIp,
                userId,
                "点击劫持防护",
                allowed,
                null,
                null,
                policy
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

