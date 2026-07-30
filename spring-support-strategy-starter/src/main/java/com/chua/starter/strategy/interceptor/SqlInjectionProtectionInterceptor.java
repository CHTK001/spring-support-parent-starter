package com.chua.starter.strategy.interceptor;

import com.chua.starter.strategy.config.StrategyProperties;
import com.chua.starter.strategy.event.SqlInjectionProtectionEvent;
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
import java.util.Enumeration;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * SQL注入防护拦截器
 * <p>
 * 检测并阻止SQL注入攻击，防止恶意SQL语句执行。
 * 主要检测请求参数和请求体中的SQL注入攻击模式。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Slf4j
@RequiredArgsConstructor
public class SqlInjectionProtectionInterceptor implements HandlerInterceptor {

    /**
     * SQL注入防护配置
     */
    private final StrategyProperties.SqlInjectionConfig sqlInjectionConfig;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * SQL注入特征匹配模式
     */
    private static final Pattern[] SQL_INJECTION_PATTERNS = new Pattern[]{
            // 单引号注入
            Pattern.compile("([';]+|(\\s|^)(or|and)(\\s|$))", Pattern.CASE_INSENSITIVE),
            // UNION注入
            Pattern.compile("union\\s+(all\\s+)?select", Pattern.CASE_INSENSITIVE),
            // 注释注入
            Pattern.compile("(--|#|/\\*|\\*/)", Pattern.CASE_INSENSITIVE),
            // 执行函数
            Pattern.compile("(exec|execute|sp_executesql)", Pattern.CASE_INSENSITIVE),
            // 危险函数
            Pattern.compile("(xp_cmdshell|xp_regread|xp_regwrite)", Pattern.CASE_INSENSITIVE),
            // DROP/ALTER/DELETE/TRUNCATE
            Pattern.compile("(drop|alter|delete|truncate)\\s+(table|database|index|view)", Pattern.CASE_INSENSITIVE),
            // INSERT/UPDATE
            Pattern.compile("(insert|update)\\s+into", Pattern.CASE_INSENSITIVE),
            // 条件注入
            Pattern.compile("(\\s|^)(or|and)\\s+\\d+\\s*=\\s*\\d+", Pattern.CASE_INSENSITIVE),
            // 布尔盲注
            Pattern.compile("(\\s|^)(or|and)\\s+['\"]?\\d+['\"]?\\s*=\\s*['\"]?\\d+['\"]?", Pattern.CASE_INSENSITIVE),
            // 时间盲注
            Pattern.compile("(sleep|waitfor|delay)\\s*\\(", Pattern.CASE_INSENSITIVE),
            // 信息泄露
            Pattern.compile("(version|database|user|schema)\\s*\\(", Pattern.CASE_INSENSITIVE),
            // 堆叠查询
            Pattern.compile(";\\s*(drop|alter|delete|truncate|insert|update|create)", Pattern.CASE_INSENSITIVE)
    };

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {
        if (!sqlInjectionConfig.isEnabled()) {
            return true;
        }

        String uri = request.getRequestURI();
        if (isExcluded(uri)) {
            return true;
        }

        // 检查请求参数
        SqlInjectionResult paramResult = checkParameters(request);
        if (paramResult != null) {
            log.warn("[策略模块][SQL注入防护]检测到可疑参数, uri={}, param={}, pattern={}",
                    uri, paramResult.paramName, paramResult.pattern);
            publishEvent(request, false, paramResult.pattern, "参数", paramResult.suspiciousValue);
            handleRejected(response);
            return false;
        }

        // 检查请求头
        SqlInjectionResult headerResult = checkHeaders(request);
        if (headerResult != null) {
            log.warn("[策略模块][SQL注入防护]检测到可疑请求头, uri={}, header={}, pattern={}",
                    uri, headerResult.paramName, headerResult.pattern);
            publishEvent(request, false, headerResult.pattern, "请求头", headerResult.suspiciousValue);
            handleRejected(response);
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
        for (String pattern : sqlInjectionConfig.getExcludePatterns()) {
            if (pathMatcher.match(pattern, uri)) {
                return true;
            }
        }
        if (sqlInjectionConfig.getIncludePatterns().isEmpty()) {
            return false;
        }
        for (String pattern : sqlInjectionConfig.getIncludePatterns()) {
            if (pathMatcher.match(pattern, uri)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查请求参数
     *
     * @param request HTTP请求
     * @return 检测结果，如果安全则返回null
     */
    private SqlInjectionResult checkParameters(HttpServletRequest request) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String paramName = entry.getKey();
            String[] values = entry.getValue();
            if (values == null) {
                continue;
            }

            // 检查参数值
            for (String value : values) {
                if (value == null || value.isEmpty()) {
                    continue;
                }

                // URL解码
                String decodedValue = urlDecode(value);

                // 检查所有模式
                String pattern = findSqlInjectionPattern(decodedValue);
                if (pattern != null) {
                    return new SqlInjectionResult(pattern, paramName, decodedValue);
                }
            }
        }
        return null;
    }

    /**
     * 检查请求头
     *
     * @param request HTTP请求
     * @return 检测结果，如果安全则返回null
     */
    private SqlInjectionResult checkHeaders(HttpServletRequest request) {
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            Enumeration<String> values = request.getHeaders(headerName);
            while (values.hasMoreElements()) {
                String value = values.nextElement();
                if (value == null || value.isEmpty()) {
                    continue;
                }

                // URL解码
                String decodedValue = urlDecode(value);

                // 检查所有模式
                String pattern = findSqlInjectionPattern(decodedValue);
                if (pattern != null) {
                    return new SqlInjectionResult(pattern, headerName, decodedValue);
                }
            }
        }
        return null;
    }

    /**
     * 查找SQL注入模式
     *
     * @param value 待检测内容
     * @return 命中的模式，如果没有则返回null
     */
    private String findSqlInjectionPattern(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        for (Pattern pattern : SQL_INJECTION_PATTERNS) {
            if (pattern.matcher(value).find()) {
                return pattern.pattern();
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
     * @throws IOException 写响应异常
     */
    private void handleRejected(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType("application/json;charset=UTF-8");
        String message = sqlInjectionConfig.getMessage();
        response.getWriter().write(
                "{\"code\":400,\"message\":\"" + message + "\",\"success\":false}");
    }

    /**
     * 发布事件
     *
     * @param request           请求对象
     * @param allowed           是否允许通过
     * @param matchedPattern    命中的模式
     * @param detectionLocation 检测位置
     * @param suspiciousContent 可疑内容
     */
    private void publishEvent(HttpServletRequest request, boolean allowed,
                             String matchedPattern, String detectionLocation, String suspiciousContent) {
        String clientIp = getClientIp(request);
        String userId = UserContextHelper.getUserId(request);

        SqlInjectionProtectionEvent event = new SqlInjectionProtectionEvent(
                this,
                request.getRequestURI(),
                request.getMethod(),
                clientIp,
                userId,
                "SQL注入防护",
                allowed,
                allowed ? null : "检测到SQL注入攻击",
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
     * SQL注入检测结果
     */
    private static class SqlInjectionResult {
        final String pattern;
        final String paramName;
        final String suspiciousValue;

        SqlInjectionResult(String pattern, String paramName, String suspiciousValue) {
            this.pattern = pattern;
            this.paramName = paramName;
            this.suspiciousValue = suspiciousValue;
        }
    }
}

