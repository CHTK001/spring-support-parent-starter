package com.chua.starter.common.support.api.gray;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.api.annotations.ApiGray;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * API 灰度规则评估器
 * <p>
 * 用于评估请求是否命中灰度规则
 * </p>
 *
 * @author CH
 * @since 2024/12/18
 * @version 1.0.0
 */
@Slf4j
public class ApiGrayEvaluator {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    
    /**
     * SpEL 表达式缓存
     */
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    /**
     * 评估请求是否命中灰度
     *
     * @param apiGray 灰度注解
     * @param request HTTP请求
     * @param userId  用户ID
     * @param username 用户名
     * @return 是否命中灰度
     */
    public boolean evaluate(ApiGray apiGray, HttpServletRequest request, Object userId, String username) {
        String clientIp = getClientIp(request);
        
        // 1. 检查用户白名单
        if (matchUserWhitelist(apiGray.users(), username)) {
            log.debug("灰度命中: 用户白名单匹配 - user={}", username);
            return true;
        }
        
        // 2. 检查IP白名单
        if (matchIpWhitelist(apiGray.ips(), clientIp)) {
            log.debug("灰度命中: IP白名单匹配 - ip={}", clientIp);
            return true;
        }
        
        // 3. 检查请求头匹配
        if (matchHeaders(apiGray.headers(), request)) {
            log.debug("灰度命中: 请求头匹配");
            return true;
        }
        
        // 4. 检查SpEL规则
        if (StringUtils.isNotBlank(apiGray.rule())) {
            if (evaluateSpelRule(apiGray.rule(), request, userId, username, clientIp)) {
                log.debug("灰度命中: SpEL规则匹配 - rule={}", apiGray.rule());
                return true;
            }
        }
        
        // 5. 检查百分比灰度
        if (apiGray.percentage() > 0) {
            if (matchPercentage(apiGray.percentage(), userId, username, clientIp)) {
                log.debug("灰度命中: 百分比匹配 - percentage={}%", apiGray.percentage());
                return true;
            }
        }
        
        return false;
    }

    /**
     * 检查用户白名单
     */
    private boolean matchUserWhitelist(String[] users, String username) {
        if (users == null || users.length == 0 || StringUtils.isBlank(username)) {
            return false;
        }
        for (String user : users) {
            if (user.equalsIgnoreCase(username)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查IP白名单
     */
    private boolean matchIpWhitelist(String[] ips, String clientIp) {
        if (ips == null || ips.length == 0 || StringUtils.isBlank(clientIp)) {
            return false;
        }
        for (String ip : ips) {
            if (matchIp(clientIp, ip)) {
                return true;
            }
        }
        return false;
    }

    /**
     * IP匹配（支持通配符）
     */
    private boolean matchIp(String clientIp, String pattern) {
        if (StringUtils.isBlank(clientIp) || StringUtils.isBlank(pattern)) {
            return false;
        }
        // 精确匹配
        if (clientIp.equals(pattern)) {
            return true;
        }
        // 通配符匹配
        if (pattern.contains("*")) {
            String regex = pattern.replace(".", "\\.").replace("*", ".*");
            return clientIp.matches(regex);
        }
        return false;
    }

    /**
     * 检查请求头匹配
     */
    private boolean matchHeaders(String[] headers, HttpServletRequest request) {
        if (headers == null || headers.length == 0) {
            return false;
        }
        for (String header : headers) {
            if (header.contains("=")) {
                // headerName=value 格式
                String[] parts = header.split("=", 2);
                String headerName = parts[0].trim();
                String expectedValue = parts[1].trim();
                String actualValue = request.getHeader(headerName);
                if (expectedValue.equals(actualValue)) {
                    return true;
                }
            } else {
                // 仅检查header是否存在
                if (StringUtils.isNotBlank(request.getHeader(header.trim()))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 评估SpEL表达式
     */
    private boolean evaluateSpelRule(String rule, HttpServletRequest request, 
                                     Object userId, String username, String clientIp) {
        try {
            Expression expression = expressionCache.computeIfAbsent(rule, PARSER::parseExpression);
            EvaluationContext context = createEvaluationContext(request, userId, username, clientIp);
            Boolean result = expression.getValue(context, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("灰度SpEL表达式评估失败: rule={}, error={}", rule, e.getMessage());
            return false;
        }
    }

    /**
     * 创建SpEL评估上下文
     */
    private EvaluationContext createEvaluationContext(HttpServletRequest request, 
                                                       Object userId, String username, String clientIp) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        
        // 设置变量
        context.setVariable("userId", userId);
        context.setVariable("username", username);
        context.setVariable("ip", clientIp);
        context.setVariable("request", request);
        
        // 注册方法
        try {
            context.registerFunction("header", 
                    ApiGrayEvaluator.class.getDeclaredMethod("getHeader", HttpServletRequest.class, String.class));
            context.registerFunction("param", 
                    ApiGrayEvaluator.class.getDeclaredMethod("getParam", HttpServletRequest.class, String.class));
            context.registerFunction("cookie", 
                    ApiGrayEvaluator.class.getDeclaredMethod("getCookie", HttpServletRequest.class, String.class));
        } catch (NoSuchMethodException e) {
            log.warn("注册SpEL函数失败", e);
        }
        
        // 设置根对象为请求，方便直接访问
        context.setRootObject(new GrayContext(request, userId, username, clientIp));
        
        return context;
    }

    /**
     * 百分比灰度匹配
     * <p>
     * 使用一致性哈希确保同一用户的灰度结果稳定
     * </p>
     */
    private boolean matchPercentage(int percentage, Object userId, String username, String clientIp) {
        if (percentage <= 0) {
            return false;
        }
        if (percentage >= 100) {
            return true;
        }
        
        // 生成稳定的哈希值
        String key = buildHashKey(userId, username, clientIp);
        int hash = Math.abs(key.hashCode() % 100);
        
        return hash < percentage;
    }

    /**
     * 构建哈希键
     */
    private String buildHashKey(Object userId, String username, String clientIp) {
        StringBuilder sb = new StringBuilder();
        if (userId != null) {
            sb.append(userId);
        } else if (StringUtils.isNotBlank(username)) {
            sb.append(username);
        } else if (StringUtils.isNotBlank(clientIp)) {
            sb.append(clientIp);
        } else {
            // 无法确定用户身份，使用随机数
            sb.append(ThreadLocalRandom.current().nextInt(100));
        }
        return sb.toString();
    }

    /**
     * 获取客户端真实IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    // ==================== SpEL 辅助方法 ====================

    /**
     * 获取请求头（SpEL 函数）
     */
    public static String getHeader(HttpServletRequest request, String name) {
        return request.getHeader(name);
    }

    /**
     * 获取请求参数（SpEL 函数）
     */
    public static String getParam(HttpServletRequest request, String name) {
        return request.getParameter(name);
    }

    /**
     * 获取Cookie值（SpEL 函数）
     */
    public static String getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(name)) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * 灰度上下文（SpEL 根对象）
     */
    public record GrayContext(HttpServletRequest request, Object userId, String username, String ip) {
        
        public String header(String name) {
            return request.getHeader(name);
        }
        
        public String param(String name) {
            return request.getParameter(name);
        }
        
        public String cookie(String name) {
            return getCookie(request, name);
        }
    }
}
