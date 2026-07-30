package com.chua.starter.strategy.support;

import com.chua.starter.strategy.config.StrategyProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Strategy 轻控制台页面登录拦截器。
 * <p>
 * 只保护 `/strategy-console/**` 页面入口，不干扰模块内已有业务接口。
 * </p>
 */
@RequiredArgsConstructor
public class StrategyConsoleAuthInterceptor implements HandlerInterceptor {

    private final StrategyProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!isAuthEnabled()) {
            return true;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (StrategyConsoleSessionSupport.isAuthenticated(request)) {
            return true;
        }
        if (isConsoleApi(request)) {
            writeUnauthorizedJson(response);
            return false;
        }
        redirectToLogin(request, response);
        return false;
    }

    private boolean isAuthEnabled() {
        StrategyProperties.WebAuthConfig webAuth = properties.getWebAuth();
        return webAuth != null && !"none".equalsIgnoreCase(webAuth.getMode());
    }

    private boolean isConsoleApi(HttpServletRequest request) {
        String relative = relativePath(request);
        return relative.startsWith("/v2/strategy/");
    }

    private String relativePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private void redirectToLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String target = request.getRequestURI();
        String query = request.getQueryString();
        if (query != null && !query.isEmpty()) {
            target = target + "?" + query;
        }
        String loginUrl = request.getContextPath()
                + "/strategy-console/login.html?redirect="
                + URLEncoder.encode(target, StandardCharsets.UTF_8);
        response.sendRedirect(loginUrl);
    }

    private void writeUnauthorizedJson(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"401\",\"message\":\"请先登录控制台\",\"success\":false}");
    }
}
