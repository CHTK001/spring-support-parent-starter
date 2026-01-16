package com.chua.starter.strategy.interceptor;

import com.chua.starter.strategy.config.StrategyProperties;
import com.chua.starter.strategy.event.ParameterCountLimitEvent;
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

/**
 * 参数数量限制拦截器
 * <p>
 * 限制请求参数数量，防止参数炸弹攻击。
 * 支持配置不同路径的参数数量限制。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Slf4j
@RequiredArgsConstructor
public class ParameterCountLimitInterceptor implements HandlerInterceptor {

    /**
     * 参数数量限制配置
     */
    private final StrategyProperties.ParameterCountLimitConfig parameterCountLimitConfig;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {
        if (!parameterCountLimitConfig.isEnabled()) {
            return true;
        }

        String uri = request.getRequestURI();
        if (isExcluded(uri)) {
            return true;
        }

        // 统计参数数量
        int parameterCount = countParameters(request);

        // 获取该路径的最大参数数量
        int maxParameters = getMaxParametersForPath(uri);

        if (parameterCount > maxParameters) {
            log.warn("[策略模块][参数数量限制]请求参数数量超过限制, uri={}, count={}, max={}",
                    uri, parameterCount, maxParameters);
            publishEvent(request, false, parameterCount, maxParameters);
            handleRejected(response, parameterCount, maxParameters);
            return false;
        }

        // 验证通过
        publishEvent(request, true, parameterCount, maxParameters);
        return true;
    }

    /**
     * 是否在排除名单中
     *
     * @param uri 请求路径
     * @return true 表示排除
     */
    private boolean isExcluded(String uri) {
        for (String pattern : parameterCountLimitConfig.getExcludePatterns()) {
            if (pathMatcher.match(pattern, uri)) {
                return true;
            }
        }
        if (parameterCountLimitConfig.getIncludePatterns().isEmpty()) {
            return false;
        }
        for (String pattern : parameterCountLimitConfig.getIncludePatterns()) {
            if (pathMatcher.match(pattern, uri)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 统计参数数量
     *
     * @param request HTTP请求
     * @return 参数数量
     */
    private int countParameters(HttpServletRequest request) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        int count = 0;
        for (String[] values : parameterMap.values()) {
            if (values != null) {
                count += values.length;
            }
        }
        return count;
    }

    /**
     * 获取路径的最大参数数量
     *
     * @param uri 请求路径
     * @return 最大参数数量
     */
    private int getMaxParametersForPath(String uri) {
        // 检查路径级别的配置
        var pathLimits = parameterCountLimitConfig.getPathLimits();
        if (pathLimits != null) {
            for (var entry : pathLimits.entrySet()) {
                if (pathMatcher.match(entry.getKey(), uri)) {
                    return entry.getValue();
                }
            }
        }

        // 返回默认配置
        return parameterCountLimitConfig.getMaxParameters();
    }

    /**
     * 拒绝请求
     *
     * @param response        响应对象
     * @param parameterCount  实际参数数量
     * @param maxParameters   最大参数数量
     * @throws IOException 写响应异常
     */
    private void handleRejected(HttpServletResponse response, int parameterCount, int maxParameters) throws IOException {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType("application/json;charset=UTF-8");
        String message = parameterCountLimitConfig.getMessage();
        response.getWriter().write(
                String.format("{\"code\":400,\"message\":\"%s\",\"success\":false,\"parameterCount\":%d,\"maxParameters\":%d}",
                        message, parameterCount, maxParameters));
    }

    /**
     * 发布事件
     *
     * @param request        请求对象
     * @param allowed        是否允许通过
     * @param parameterCount 实际参数数量
     * @param maxParameters  最大参数数量
     */
    private void publishEvent(HttpServletRequest request, boolean allowed,
                             int parameterCount, int maxParameters) {
        String clientIp = getClientIp(request);
        String userId = UserContextHelper.getUserId(request);

        ParameterCountLimitEvent event = new ParameterCountLimitEvent(
                this,
                request.getRequestURI(),
                request.getMethod(),
                clientIp,
                userId,
                "参数数量限制",
                allowed,
                allowed ? null : "请求参数数量超过限制",
                null,
                parameterCount,
                maxParameters
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

