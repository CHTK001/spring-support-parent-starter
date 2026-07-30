package com.chua.starter.strategy.interceptor;

import com.chua.starter.strategy.config.StrategyProperties;
import com.chua.starter.strategy.event.PathTraversalEvent;
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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 路径穿透防护拦截器
 * <p>
 * 检测并阻止路径穿透攻击（Path Traversal），防止访问系统敏感文件。
 * 主要检测请求参数和路径中的 "../"、"..\\" 等危险模式。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Slf4j
@RequiredArgsConstructor
public class PathTraversalInterceptor implements HandlerInterceptor {

    /**
     * 路径穿透配置
     */
    private final StrategyProperties.PathTraversalConfig pathTraversalConfig;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 路径穿透特征匹配模式
     */
    private static final Pattern[] PATH_TRAVERSAL_PATTERNS = new Pattern[]{
            // 相对路径模式
            Pattern.compile("\\.\\./", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\.\\.\\\\", Pattern.CASE_INSENSITIVE),
            // URL编码的相对路径
            Pattern.compile("%2e%2e%2f", Pattern.CASE_INSENSITIVE),
            Pattern.compile("%2e%2e%5c", Pattern.CASE_INSENSITIVE),
            Pattern.compile("%252e%252e%252f", Pattern.CASE_INSENSITIVE),
            Pattern.compile("%252e%252e%255c", Pattern.CASE_INSENSITIVE),
            // 双编码
            Pattern.compile("%c0%ae%c0%ae%c0%af", Pattern.CASE_INSENSITIVE),
            Pattern.compile("%c0%ae%c0%ae%c1%9c", Pattern.CASE_INSENSITIVE),
            // 绝对路径（Windows）
            Pattern.compile("^[a-zA-Z]:\\\\", Pattern.CASE_INSENSITIVE),
            // 绝对路径（Unix）
            Pattern.compile("^/etc/", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^/proc/", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^/sys/", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^/boot/", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^/root/", Pattern.CASE_INSENSITIVE),
            // 危险文件名
            Pattern.compile("(^|/)passwd", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|/)shadow", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|/)hosts", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|/)\\.\\.", Pattern.CASE_INSENSITIVE)
    };

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {
        if (!pathTraversalConfig.isEnabled()) {
            return true;
        }

        String uri = request.getRequestURI();
        if (isExcluded(uri)) {
            return true;
        }

        // 检查请求路径
        PathTraversalResult pathResult = checkPath(uri);
        if (pathResult != null) {
            log.warn("[策略模块][路径穿透防护]检测到可疑路径, uri={}, pattern={}", uri, pathResult.pattern);
            publishEvent(request, false, pathResult.pattern, "路径", uri);
            handleRejected(response, pathResult.pattern);
            return false;
        }

        // 检查请求参数
        PathTraversalResult paramResult = checkParameters(request);
        if (paramResult != null) {
            log.warn("[策略模块][路径穿透防护]检测到可疑参数, uri={}, param={}, pattern={}",
                    uri, paramResult.paramName, paramResult.pattern);
            publishEvent(request, false, paramResult.pattern, "参数", paramResult.suspiciousValue);
            handleRejected(response, paramResult.pattern);
            return false;
        }

        // 检查通过
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
        for (String pattern : pathTraversalConfig.getExcludePatterns()) {
            if (pathMatcher.match(pattern, uri)) {
                return true;
            }
        }
        if (pathTraversalConfig.getIncludePatterns().isEmpty()) {
            return false;
        }
        for (String pattern : pathTraversalConfig.getIncludePatterns()) {
            if (pathMatcher.match(pattern, uri)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查请求路径
     *
     * @param path 请求路径
     * @return 检测结果，如果安全则返回null
     */
    private PathTraversalResult checkPath(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        // URL解码
        String decodedPath = urlDecode(path);

        // 检查所有模式
        for (Pattern pattern : PATH_TRAVERSAL_PATTERNS) {
            if (pattern.matcher(decodedPath).find()) {
                return new PathTraversalResult(pattern.pattern(), null, decodedPath);
            }
        }

        return null;
    }

    /**
     * 检查请求参数
     *
     * @param request HTTP请求
     * @return 检测结果，如果安全则返回null
     */
    private PathTraversalResult checkParameters(HttpServletRequest request) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String paramName = entry.getKey();
            String[] values = entry.getValue();
            if (values == null) {
                continue;
            }

            // 检查参数名
            PathTraversalResult nameResult = checkPath(paramName);
            if (nameResult != null) {
                return new PathTraversalResult(nameResult.pattern, paramName, paramName);
            }

            // 检查参数值
            for (String value : values) {
                if (value == null || value.isEmpty()) {
                    continue;
                }

                // URL解码
                String decodedValue = urlDecode(value);

                // 检查所有模式
                for (Pattern pattern : PATH_TRAVERSAL_PATTERNS) {
                    if (pattern.matcher(decodedValue).find()) {
                        return new PathTraversalResult(pattern.pattern(), paramName, decodedValue);
                    }
                }
            }
        }
        return null;
    }

    /**
     * URL解码
     *
     * @param value 待解码的值
     * @return 解码后的值
     */
    private String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.debug("URL解码失败: {}", value, e);
            return value;
        }
    }

    /**
     * 拒绝请求
     *
     * @param response 响应对象
     * @param pattern  命中的模式
     * @throws IOException 写响应异常
     */
    private void handleRejected(HttpServletResponse response, String pattern) throws IOException {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType("application/json;charset=UTF-8");
        String message = pathTraversalConfig.getMessage();
        response.getWriter().write(
                "{\"code\":400,\"message\":\"" + message + "\",\"success\":false}");
    }

    /**
     * 发布事件
     *
     * @param request           请求对象
     * @param allowed           是否允许通过
     * @param matchedPattern   命中的模式
     * @param detectionLocation 检测位置
     * @param suspiciousPath   可疑路径
     */
    private void publishEvent(HttpServletRequest request, boolean allowed,
                             String matchedPattern, String detectionLocation, String suspiciousPath) {
        String clientIp = getClientIp(request);
        String userId = UserContextHelper.getUserId(request);

        PathTraversalEvent event = new PathTraversalEvent(
                this,
                request.getRequestURI(),
                request.getMethod(),
                clientIp,
                userId,
                "路径穿透防护",
                allowed,
                allowed ? null : "检测到路径穿透攻击",
                null,
                matchedPattern,
                detectionLocation,
                suspiciousPath
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
     * 路径穿透检测结果
     */
    private static class PathTraversalResult {
        final String pattern;
        final String paramName;
        final String suspiciousValue;

        PathTraversalResult(String pattern, String paramName, String suspiciousValue) {
            this.pattern = pattern;
            this.paramName = paramName;
            this.suspiciousValue = suspiciousValue;
        }
    }
}

