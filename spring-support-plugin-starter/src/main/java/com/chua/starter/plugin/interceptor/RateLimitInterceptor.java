package com.chua.starter.plugin.interceptor;

import com.chua.starter.plugin.entity.PluginRateLimitConfig;
import com.chua.starter.plugin.service.BlackWhiteListService;
import com.chua.starter.plugin.service.RateLimitCacheManager;
import com.chua.starter.plugin.service.RateLimitConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 限流拦截器 在请求处理前进行限流检查
 * 
 * @author CH
 * @since 2025/1/16
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitCacheManager cacheManager;
    private final RateLimitConfigService configService;
    private final BlackWhiteListService blackWhiteListService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String requestPath = request.getRequestURI();
        String clientIp = getClientIp(request);

        // 1. 检查黑白名单（IP）
        if (!checkBlackWhiteList(clientIp, response, "IP")) {
            return false;
        }

        // 2. 检查黑白名单（API路径）
        if (!checkBlackWhiteList(requestPath, response, "API")) {
            return false;
        }

        // 3. 检查全局QPS限流
        if (!checkGlobalQpsLimit(response)) {
            return false;
        }

        // 4. 检查API限流
        if (!checkApiRateLimit(requestPath, response)) {
            return false;
        }

        // 5. 检查IP限流
        return checkIpRateLimit(clientIp, response);
    }

    /**
     * 检查黑白名单
     *
     * @param value    要检查的值
     * @param response 响应对象
     * @param type     类型描述
     * @return 是否通过检查
     */
    private boolean checkBlackWhiteList(String value, HttpServletResponse response, String type) throws IOException {
        // 检查是否允许访问（白名单优先级高于黑名单）
        if (!blackWhiteListService.isAccessAllowed(value)) {
            String message = String.format("%s访问被拒绝: %s", type, value);
            sendRateLimitResponse(response, 403, message, type + "_BLOCKED");
            log.warn("{} blocked by black/white list: {}", type, value);
            return false;
        }
        return true;
    }

    /**
     * 检查全局QPS限流
     *
     * @param response 响应对象
     * @return 是否通过限流检查
     */
    private boolean checkGlobalQpsLimit(HttpServletResponse response) throws IOException {
        // 检查全局QPS限流
        boolean acquired = cacheManager.tryAcquire(PluginRateLimitConfig.LimitType.QPS, "GLOBAL");

        if (!acquired) {
            Optional<PluginRateLimitConfig> configOpt = configService.getConfig(PluginRateLimitConfig.LimitType.QPS, "GLOBAL");
            String message = "全局QPS超出限制，请稍后再试";

            if (configOpt.isPresent()) {
                PluginRateLimitConfig config = configOpt.get();
                message = String.format("全局QPS超出限制，当前限制: %d QPS", config.getQps());
            }

            sendRateLimitResponse(response, 429, message, "GLOBAL_QPS_LIMIT");
            log.warn("Global QPS limit exceeded");
            return false;
        }

        return true;
    }

    /**
     * 检查API限流
     *
     * @param requestPath 请求路径
     * @param response    响应对象
     * @return 是否通过限流检查
     */
    private boolean checkApiRateLimit(String requestPath, HttpServletResponse response) throws IOException {
        // 尝试获取许可
        boolean acquired = cacheManager.tryAcquire(PluginRateLimitConfig.LimitType.API, requestPath);

        if (!acquired) {
            // 获取配置信息用于错误响应
            Optional<PluginRateLimitConfig> configOpt = configService.getConfig(PluginRateLimitConfig.LimitType.API, requestPath);
            String message = "API请求过于频繁，请稍后再试";
            int errorCode = 429;

            if (configOpt.isPresent()) {
                PluginRateLimitConfig config = configOpt.get();
                message = String.format("API请求超出限制，当前限制: %d QPS", config.getQps());
            }

            sendRateLimitResponse(response, errorCode, message, "API_RATE_LIMIT");
            log.warn("API rate limit exceeded for path: {}", requestPath);
            return false;
        }

        return true;
    }

    /**
     * 检查IP限流
     * 
     * @param clientIp 客户端IP
     * @param response 响应对象
     * @return 是否通过限流检查
     */
    private boolean checkIpRateLimit(String clientIp, HttpServletResponse response) throws IOException {
        // 先检查具体IP的限流配置
        boolean acquired = cacheManager.tryAcquire(PluginRateLimitConfig.LimitType.IP, clientIp);

        if (!acquired) {
            // 检查通配符IP限流配置
            acquired = cacheManager.tryAcquire(PluginRateLimitConfig.LimitType.IP, "*");
        }

        if (!acquired) {
            // 获取配置信息用于错误响应
            Optional<PluginRateLimitConfig> configOpt = configService.getConfig(PluginRateLimitConfig.LimitType.IP, clientIp);
            if (!configOpt.isPresent()) {
                configOpt = configService.getConfig(PluginRateLimitConfig.LimitType.IP, "*");
            }

            String message = "IP请求过于频繁，请稍后再试";
            int errorCode = 429;

            if (configOpt.isPresent()) {
                PluginRateLimitConfig config = configOpt.get();
                message = String.format("IP请求超出限制，当前限制: %d QPS", config.getQps());
            }

            sendRateLimitResponse(response, errorCode, message, "IP_RATE_LIMIT");
            log.warn("IP rate limit exceeded for IP: {}", clientIp);
            return false;
        }

        return true;
    }

    /**
     * 发送限流响应
     * 
     * @param response  响应对象
     * @param errorCode 错误代码
     * @param message   错误消息
     * @param limitType 限流类型
     */
    private void sendRateLimitResponse(HttpServletResponse response, int errorCode, String message, String limitType)
            throws IOException {
        response.setStatus(errorCode);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("code", errorCode);
        errorResponse.put("message", message);
        errorResponse.put("limitType", limitType);
        errorResponse.put("timestamp", System.currentTimeMillis());

        String jsonResponse = objectMapper.writeValueAsString(errorResponse);
        response.getWriter().write(jsonResponse);
    }

    /**
     * 获取客户端真实IP地址
     * 
     * @param request 请求对象
     * @return 客户端IP
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            // 多个IP时取第一个
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }

        String proxyClientIp = request.getHeader("Proxy-Client-IP");
        if (proxyClientIp != null && !proxyClientIp.isEmpty() && !"unknown".equalsIgnoreCase(proxyClientIp)) {
            return proxyClientIp;
        }

        String wlProxyClientIp = request.getHeader("WL-Proxy-Client-IP");
        if (wlProxyClientIp != null && !wlProxyClientIp.isEmpty() && !"unknown".equalsIgnoreCase(wlProxyClientIp)) {
            return wlProxyClientIp;
        }

        String httpClientIp = request.getHeader("HTTP_CLIENT_IP");
        if (httpClientIp != null && !httpClientIp.isEmpty() && !"unknown".equalsIgnoreCase(httpClientIp)) {
            return httpClientIp;
        }

        String httpXForwardedFor = request.getHeader("HTTP_X_FORWARDED_FOR");
        if (httpXForwardedFor != null && !httpXForwardedFor.isEmpty()
                && !"unknown".equalsIgnoreCase(httpXForwardedFor)) {
            return httpXForwardedFor;
        }

        return request.getRemoteAddr();
    }
}
