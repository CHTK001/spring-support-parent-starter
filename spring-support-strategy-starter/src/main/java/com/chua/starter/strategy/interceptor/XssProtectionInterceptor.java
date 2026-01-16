package com.chua.starter.strategy.interceptor;

import com.chua.starter.strategy.config.StrategyProperties;
import com.chua.starter.strategy.event.XssProtectionEvent;
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
import java.util.Enumeration;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * XSS 防护拦截器
 *
 * 通过简单规则检测常见脚本注入内容，命中后直接拒绝请求。
 * 主要针对查询参数与请求头，避免引入复杂的请求包装逻辑。
 *
 * @author CH
 */
@Slf4j
@RequiredArgsConstructor
public class XssProtectionInterceptor implements HandlerInterceptor {

    /**
     * XSS 规则配置
     */
    private final StrategyProperties.XssConfig xssConfig;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 常见 XSS 特征匹配
     */
    private static final Pattern[] XSS_PATTERNS = new Pattern[]{
            // script 标签
            Pattern.compile("<\\s*script", Pattern.CASE_INSENSITIVE),
            Pattern.compile("</\\s*script", Pattern.CASE_INSENSITIVE),
            // javascript: 协议
            Pattern.compile("javascript\\s*:", Pattern.CASE_INSENSITIVE),
            // onXXX 事件
            Pattern.compile("on[a-zA-Z]+\\s*=", Pattern.CASE_INSENSITIVE),
            // iframe、img 等常见载体
            Pattern.compile("<\\s*iframe", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<\\s*img", Pattern.CASE_INSENSITIVE),
            // 危险函数
            Pattern.compile("eval\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("alert\\s*\\(", Pattern.CASE_INSENSITIVE)
    };

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {
        if (!xssConfig.isEnabled()) {
            return true;
        }

        String uri = request.getRequestURI();
        if (isExcluded(uri)) {
            return true;
        }

        XssDetectionResult result = detectXss(request);
        if (result != null) {
            log.warn("[策略模块][XSS防护]检测到可疑请求, uri={}, pattern={}, location={}", 
                    uri, result.pattern, result.location);
            publishEvent(request, false, result.pattern, result.location, result.content);
            handleRejected(response);
            return false;
        }

        publishEvent(request, true, null, null, null);
        return true;
    }

    /**
     * 是否在排除名单中
     *
     * @param uri 请求路径
     * @return true 表示排除
     */
    private boolean isExcluded(String uri) {
        for (String pattern : xssConfig.getExcludePatterns()) {
            if (pathMatcher.match(pattern, uri)) {
                return true;
            }
        }
        if (xssConfig.getIncludePatterns().isEmpty()) {
            return false;
        }
        for (String pattern : xssConfig.getIncludePatterns()) {
            if (pathMatcher.match(pattern, uri)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检测XSS内容
     *
     * @param request HTTP请求
     * @return 检测结果，如果安全则返回null
     */
    private XssDetectionResult detectXss(HttpServletRequest request) {
        // 检查请求参数
        Map<String, String[]> parameterMap = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String[] values = entry.getValue();
            if (values == null) {
                continue;
            }
            for (String value : values) {
                String pattern = findXssPattern(value);
                if (pattern != null) {
                    return new XssDetectionResult(pattern, "参数", value);
                }
            }
        }

        // 检查请求头
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            Enumeration<String> values = request.getHeaders(headerName);
            while (values.hasMoreElements()) {
                String value = values.nextElement();
                String pattern = findXssPattern(value);
                if (pattern != null) {
                    return new XssDetectionResult(pattern, "请求头", value);
                }
            }
        }

        return null;
    }

    /**
     * 查找XSS模式
     *
     * @param value 待检测内容
     * @return 命中的模式，如果没有则返回null
     */
    private String findXssPattern(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        for (Pattern pattern : XSS_PATTERNS) {
            if (pattern.matcher(value).find()) {
                return pattern.pattern();
            }
        }
        return null;
    }

    /**
     * 发布XSS防护事件
     *
     * @param request           请求对象
     * @param allowed           是否允许通过
     * @param matchedPattern   命中的模式
     * @param detectionLocation 检测位置
     * @param suspiciousContent 可疑内容
     */
    private void publishEvent(HttpServletRequest request, boolean allowed,
                             String matchedPattern, String detectionLocation, String suspiciousContent) {
        String clientIp = getClientIp(request);
        String userId = UserContextHelper.getUserId(request);

        XssProtectionEvent event = new XssProtectionEvent(
                this,
                request.getRequestURI(),
                request.getMethod(),
                clientIp,
                userId,
                "XSS防护",
                allowed,
                allowed ? null : "检测到XSS攻击",
                null,
                matchedPattern,
                detectionLocation,
                suspiciousContent
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

    /**
     * XSS检测结果
     */
    private static class XssDetectionResult {
        final String pattern;
        final String location;
        final String content;

        XssDetectionResult(String pattern, String location, String content) {
            this.pattern = pattern;
            this.location = location;
            this.content = content;
        }
    }

    /**
     * 拒绝请求
     *
     * @param response 响应对象
     * @throws IOException 写响应异常
     */
    private void handleRejected(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType("application/json;charset=UTF-8");
        String message = xssConfig.getMessage();
        response.getWriter().write(
                "{\"code\":400,\"message\":\"" + message + "\",\"success\":false}");
    }
}


