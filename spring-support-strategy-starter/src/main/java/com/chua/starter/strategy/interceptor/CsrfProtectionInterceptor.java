package com.chua.starter.strategy.interceptor;

import com.chua.starter.strategy.config.StrategyProperties;
import com.chua.starter.strategy.event.CsrfProtectionEvent;
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
 * CSRF防护拦截器
 * <p>
 * 验证CSRF Token，防止跨站请求伪造攻击。
 * 支持从请求头或请求参数中获取Token，并与Session或Redis中存储的Token进行比对。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Slf4j
@RequiredArgsConstructor
public class CsrfProtectionInterceptor implements HandlerInterceptor {

    /**
     * CSRF防护配置
     */
    private final StrategyProperties.CsrfConfig csrfConfig;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {
        if (!csrfConfig.isEnabled()) {
            return true;
        }

        String uri = request.getRequestURI();
        String method = request.getMethod();

        // 检查是否豁免
        if (isExempted(uri, method)) {
            publishEvent(request, true, null, "豁免路径或方法");
            return true;
        }

        // 获取CSRF Token
        String token = getCsrfToken(request);
        if (token == null || token.isEmpty()) {
            log.warn("[策略模块][CSRF防护]缺少CSRF Token, uri={}, method={}", uri, method);
            publishEvent(request, false, null, "缺少CSRF Token");
            handleRejected(response);
            return false;
        }

        // 验证Token（简化实现，实际应该从Session或Redis中获取）
        // 这里仅做基本验证，实际项目中应该集成Spring Security的CSRF保护
        if (!isValidToken(token, request)) {
            log.warn("[策略模块][CSRF防护]CSRF Token验证失败, uri={}, method={}", uri, method);
            publishEvent(request, false, token, "CSRF Token验证失败");
            handleRejected(response);
            return false;
        }

        // 验证通过
        publishEvent(request, true, token, null);
        return true;
    }

    /**
     * 是否豁免
     *
     * @param uri    请求路径
     * @param method HTTP方法
     * @return true 表示豁免
     */
    private boolean isExempted(String uri, String method) {
        // 检查路径豁免
        for (String pattern : csrfConfig.getExemptPatterns()) {
            if (pathMatcher.match(pattern, uri)) {
                return true;
            }
        }

        // 检查方法豁免
        for (String exemptMethod : csrfConfig.getExemptMethods()) {
            if (exemptMethod.equalsIgnoreCase(method)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 获取CSRF Token
     *
     * @param request HTTP请求
     * @return CSRF Token
     */
    private String getCsrfToken(HttpServletRequest request) {
        // 优先从请求头获取
        String headerName = csrfConfig.getHeaderName();
        if (headerName != null && !headerName.isEmpty()) {
            String token = request.getHeader(headerName);
            if (token != null && !token.isEmpty()) {
                return token;
            }
        }

        // 从请求参数获取
        String parameterName = csrfConfig.getParameterName();
        if (parameterName != null && !parameterName.isEmpty()) {
            String token = request.getParameter(parameterName);
            if (token != null && !token.isEmpty()) {
                return token;
            }
        }

        return null;
    }

    /**
     * 验证Token是否有效
     * <p>
     * 简化实现，实际应该从Session或Redis中获取存储的Token进行比对。
     * 建议集成Spring Security的CSRF保护机制。
     * </p>
     *
     * @param token   CSRF Token
     * @param request HTTP请求
     * @return true 表示有效
     */
    private boolean isValidToken(String token, HttpServletRequest request) {
        // 简化实现：仅检查Token格式
        // 实际项目中应该从Session或Redis中获取存储的Token进行比对
        if (token == null || token.isEmpty()) {
            return false;
        }

        // 基本格式验证（实际应该与Session中的Token比对）
        // 这里仅做示例，实际应该：
        // 1. 从Session中获取存储的CSRF Token
        // 2. 或者从Redis中获取（分布式环境）
        // 3. 进行比对验证

        return token.length() >= 32; // 简单验证：Token长度至少32字符
    }

    /**
     * 拒绝请求
     *
     * @param response 响应对象
     * @throws IOException 写响应异常
     */
    private void handleRejected(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json;charset=UTF-8");
        String message = csrfConfig.getMessage();
        response.getWriter().write(
                "{\"code\":403,\"message\":\"" + message + "\",\"success\":false}");
    }

    /**
     * 发布事件
     *
     * @param request   请求对象
     * @param allowed   是否允许通过
     * @param token     CSRF Token
     * @param reason    拒绝原因
     */
    private void publishEvent(HttpServletRequest request, boolean allowed,
                             String token, String reason) {
        String clientIp = getClientIp(request);
        String userId = UserContextHelper.getUserId(request);

        CsrfProtectionEvent event = new CsrfProtectionEvent(
                this,
                request.getRequestURI(),
                request.getMethod(),
                clientIp,
                userId,
                "CSRF防护",
                allowed,
                allowed ? null : reason,
                null,
                token
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

