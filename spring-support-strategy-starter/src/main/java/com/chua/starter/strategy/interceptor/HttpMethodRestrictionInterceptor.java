package com.chua.starter.strategy.interceptor;

import com.chua.starter.strategy.config.StrategyProperties;
import com.chua.starter.strategy.event.HttpMethodRestrictionEvent;
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
import java.util.List;
import java.util.Set;

/**
 * HTTP方法限制拦截器
 * <p>
 * 限制允许的HTTP方法，防止不必要的HTTP方法访问。
 * 支持路径级别的HTTP方法配置。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Slf4j
@RequiredArgsConstructor
public class HttpMethodRestrictionInterceptor implements HandlerInterceptor {

    /**
     * HTTP方法限制配置
     */
    private final StrategyProperties.HttpMethodRestrictionConfig httpMethodRestrictionConfig;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {
        if (!httpMethodRestrictionConfig.isEnabled()) {
            return true;
        }

        String uri = request.getRequestURI();
        String method = request.getMethod();

        // 获取该路径允许的HTTP方法
        Set<String> allowedMethods = getAllowedMethodsForPath(uri);

        // 检查方法是否允许
        if (!allowedMethods.contains(method.toUpperCase())) {
            log.warn("[策略模块][HTTP方法限制]不允许的HTTP方法, uri={}, method={}, allowed={}",
                    uri, method, allowedMethods);
            publishEvent(request, false, method, allowedMethods);
            handleRejected(response, method, allowedMethods);
            return false;
        }

        // 验证通过
        publishEvent(request, true, method, allowedMethods);
        return true;
    }

    /**
     * 获取路径允许的HTTP方法
     *
     * @param uri 请求路径
     * @return 允许的HTTP方法集合
     */
    private Set<String> getAllowedMethodsForPath(String uri) {
        // 检查路径级别的配置
        var pathMethods = httpMethodRestrictionConfig.getPathMethods();
        if (pathMethods != null) {
            for (var entry : pathMethods.entrySet()) {
                if (pathMatcher.match(entry.getKey(), uri)) {
                    return Set.copyOf(entry.getValue());
                }
            }
        }

        // 返回默认配置
        List<String> defaultMethods = httpMethodRestrictionConfig.getDefaultAllowedMethods();
        return Set.copyOf(defaultMethods);
    }

    /**
     * 拒绝请求
     *
     * @param response       响应对象
     * @param method         请求方法
     * @param allowedMethods 允许的方法
     * @throws IOException 写响应异常
     */
    private void handleRejected(HttpServletResponse response, String method, Set<String> allowedMethods) throws IOException {
        response.setStatus(HttpStatus.METHOD_NOT_ALLOWED.value());
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Allow", String.join(", ", allowedMethods));
        String message = httpMethodRestrictionConfig.getMessage();
        response.getWriter().write(
                String.format("{\"code\":405,\"message\":\"%s\",\"success\":false,\"method\":\"%s\",\"allowedMethods\":%s}",
                        message, method, allowedMethods));
    }

    /**
     * 发布事件
     *
     * @param request       请求对象
     * @param allowed       是否允许通过
     * @param method        请求方法
     * @param allowedMethods 允许的方法
     */
    private void publishEvent(HttpServletRequest request, boolean allowed,
                             String method, Set<String> allowedMethods) {
        String clientIp = getClientIp(request);
        String userId = UserContextHelper.getUserId(request);

        HttpMethodRestrictionEvent event = new HttpMethodRestrictionEvent(
                this,
                request.getRequestURI(),
                request.getMethod(),
                clientIp,
                userId,
                "HTTP方法限制",
                allowed,
                allowed ? null : "不允许的HTTP方法: " + method,
                null,
                method,
                allowedMethods
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

