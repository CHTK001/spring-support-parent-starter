package com.chua.starter.circuitbreaker.support.utils;

import com.chua.starter.circuitbreaker.support.annotation.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Base64;
import java.util.List;

/**
 * 限流键生成器
 * 
 * 根据限流维度和配置生成限流键，支持SpEL表达式。
 * 
 * @author CH
 * @since 2024/12/20
 */
@Slf4j
@Component
public class RateLimiterKeyGenerator {

    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * 用户ID获取策略列表，按优先级排序
     */
    private final List<UserIdStrategy> userIdStrategies = List.of(
        new JwtTokenUserIdStrategy(),
        new SpringSecurityUserIdStrategy(),
        new CustomHeaderUserIdStrategy(),
        new SessionUserIdStrategy(),
        new PrincipalUserIdStrategy()
    );

    /**
     * 生成限流键
     * 
     * @param annotation 限流注解
     * @param joinPoint 切点
     * @return 限流键
     */
    public String generateKey(RateLimiter annotation, ProceedingJoinPoint joinPoint) {
        // 如果指定了自定义键，优先使用
        if (StringUtils.hasText(annotation.key())) {
            return evaluateSpelExpression(annotation.key(), joinPoint);
        }

        // 根据限流维度生成键
        String baseKey = getBaseKey(joinPoint);
        
        switch (annotation.dimension()) {
            case GLOBAL:
                return baseKey;
                
            case IP:
                String ipAddress = getClientIpAddress();
                return baseKey + ":ip:" + ipAddress;
                
            case USER:
                String userId = getCurrentUserId();
                return baseKey + ":user:" + userId;
                
            case API:
                String apiPath = getApiPath();
                return baseKey + ":api:" + apiPath;
                
            default:
                return baseKey;
        }
    }

    /**
     * 获取基础键
     */
    private String getBaseKey(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }

    /**
     * 评估SpEL表达式
     */
    private String evaluateSpelExpression(String expression, ProceedingJoinPoint joinPoint) {
        try {
            Expression spelExpression = parser.parseExpression(expression);
            EvaluationContext context = createEvaluationContext(joinPoint);
            Object result = spelExpression.getValue(context);
            return result != null ? result.toString() : "";
        } catch (Exception e) {
            log.warn("SpEL表达式评估失败: {}", expression, e);
            return expression; // 返回原始表达式作为键
        }
    }

    /**
     * 创建SpEL评估上下文
     */
    private EvaluationContext createEvaluationContext(ProceedingJoinPoint joinPoint) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        
        // 添加方法参数
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        
        if (paramNames != null && args != null) {
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }
        
        // 添加常用变量
        context.setVariable("ip", getClientIpAddress());
        context.setVariable("user", getCurrentUserId());
        context.setVariable("api", getApiPath());
        context.setVariable("method", signature.getMethod().getName());
        context.setVariable("class", signature.getDeclaringType().getSimpleName());
        
        return context;
    }

    /**
     * 获取客户端IP地址
     */
    private String getClientIpAddress() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return getIpAddress(request);
            }
        } catch (Exception e) {
            log.debug("获取客户端IP地址失败", e);
        }
        return "unknown";
    }

    /**
     * 从请求中获取真实IP地址
     */
    private String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        // 处理多个IP的情况，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        
        return ip != null ? ip : "unknown";
    }

    /**
     * 获取当前用户ID
     *
     * 按优先级尝试多种策略获取用户ID：
     * 1. JWT Token解析
     * 2. Spring Security上下文
     * 3. 自定义请求头
     * 4. Session中的用户信息
     * 5. Principal用户信息
     *
     * @return 用户ID，获取失败时返回"anonymous"
     */
    private String getCurrentUserId() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                if (request != null) {
                    // 按优先级尝试各种策略
                    for (UserIdStrategy strategy : userIdStrategies) {
                        if (strategy != null) {
                            try {
                                String userId = strategy.getUserId(request);
                                if (StringUtils.hasText(userId) && !"anonymous".equals(userId)) {
                                    log.debug("通过策略 {} 获取到用户ID: {}", strategy.getClass().getSimpleName(), userId);
                                    return userId;
                                }
                            } catch (Exception e) {
                                log.debug("策略 {} 获取用户ID失败: {}", strategy.getClass().getSimpleName(), e.getMessage());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("获取当前用户ID失败", e);
        }

        log.debug("所有策略均失败，返回默认用户ID: anonymous");
        return "anonymous";
    }

    /**
     * 获取API路径
     */
    private String getApiPath() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String requestURI = request.getRequestURI();
                String contextPath = request.getContextPath();
                
                if (StringUtils.hasText(contextPath) && requestURI.startsWith(contextPath)) {
                    return requestURI.substring(contextPath.length());
                }
                
                return requestURI;
            }
        } catch (Exception e) {
            log.debug("获取API路径失败", e);
        }
        return "unknown";
    }

    /**
     * 用户ID获取策略接口
     */
    private interface UserIdStrategy {
        /**
         * 从请求中获取用户ID
         *
         * @param request HTTP请求
         * @return 用户ID，获取失败返回null或空字符串
         */
        String getUserId(HttpServletRequest request);
    }

    /**
     * JWT Token用户ID获取策略
     */
    private static class JwtTokenUserIdStrategy implements UserIdStrategy {
        @Override
        public String getUserId(HttpServletRequest request) {
            String authHeader = request.getHeader("Authorization");
            if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                return parseUserIdFromJwtToken(token);
            }
            return null;
        }

        /**
         * 从JWT Token中解析用户ID
         *
         * @param token JWT Token
         * @return 用户ID
         */
        private String parseUserIdFromJwtToken(String token) {
            try {
                // 简单的JWT解析（仅解析payload部分）
                String[] parts = token.split("\\.");
                if (parts.length >= 2) {
                    String payload = new String(Base64.getUrlDecoder().decode(parts[1]));

                    // 简单的JSON解析，查找用户ID字段
                    if (payload.contains("\"sub\"")) {
                        String sub = extractJsonValue(payload, "sub");
                        if (StringUtils.hasText(sub)) {
                            return sub;
                        }
                    }

                    if (payload.contains("\"userId\"")) {
                        String userId = extractJsonValue(payload, "userId");
                        if (StringUtils.hasText(userId)) {
                            return userId;
                        }
                    }

                    if (payload.contains("\"user_id\"")) {
                        String userId = extractJsonValue(payload, "user_id");
                        if (StringUtils.hasText(userId)) {
                            return userId;
                        }
                    }
                }
            } catch (Exception e) {
                // JWT解析失败，忽略
            }
            return null;
        }

        /**
         * 从JSON字符串中提取指定字段的值
         */
        private String extractJsonValue(String json, String key) {
            String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
            return null;
        }
    }

    /**
     * Spring Security用户ID获取策略
     */
    private static class SpringSecurityUserIdStrategy implements UserIdStrategy {
        @Override
        public String getUserId(HttpServletRequest request) {
            try {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication != null && authentication.isAuthenticated()
                    && !"anonymousUser".equals(authentication.getName())) {
                    return authentication.getName();
                }
            } catch (Exception e) {
                // Spring Security不可用或未配置
            }
            return null;
        }
    }

    /**
     * 自定义请求头用户ID获取策略
     */
    private static class CustomHeaderUserIdStrategy implements UserIdStrategy {
        private final String[] headerNames = {"X-User-Id", "X-UserId", "User-Id", "UserId"};

        @Override
        public String getUserId(HttpServletRequest request) {
            for (String headerName : headerNames) {
                String userId = request.getHeader(headerName);
                if (StringUtils.hasText(userId)) {
                    return userId;
                }
            }

            // 尝试从请求参数获取
            String userId = request.getParameter("userId");
            if (StringUtils.hasText(userId)) {
                return userId;
            }

            return null;
        }
    }

    /**
     * Session用户ID获取策略
     */
    private static class SessionUserIdStrategy implements UserIdStrategy {
        private final String[] sessionKeys = {"userId", "user_id", "USER_ID", "currentUserId"};

        @Override
        public String getUserId(HttpServletRequest request) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                for (String key : sessionKeys) {
                    Object userId = session.getAttribute(key);
                    if (userId != null) {
                        return userId.toString();
                    }
                }

                // 尝试获取用户对象
                Object user = session.getAttribute("user");
                if (user != null) {
                    return extractUserIdFromUserObject(user);
                }
            }
            return null;
        }

        /**
         * 从用户对象中提取用户ID
         */
        private String extractUserIdFromUserObject(Object user) {
            if (user == null) {
                return null;
            }

            try {
                // 尝试通过反射获取ID字段
                Class<?> userClass = user.getClass();

                // 尝试常见的ID字段名
                String[] idFields = {"id", "userId", "user_id", "getId", "getUserId"};
                for (String fieldName : idFields) {
                    if (fieldName == null || fieldName.isEmpty()) {
                        continue;
                    }

                    try {
                        if (fieldName.startsWith("get")) {
                            // 尝试调用getter方法
                            Method method = userClass.getMethod(fieldName);
                            if (method != null) {
                                Object result = method.invoke(user);
                                if (result != null && StringUtils.hasText(result.toString())) {
                                    return result.toString();
                                }
                            }
                        } else {
                            // 尝试访问字段
                            Field field = userClass.getDeclaredField(fieldName);
                            if (field != null) {
                                field.setAccessible(true);
                                Object result = field.get(user);
                                if (result != null && StringUtils.hasText(result.toString())) {
                                    return result.toString();
                                }
                            }
                        }
                    } catch (Exception e) {
                        // 忽略单个字段的获取失败
                        log.trace("获取字段 {} 失败: {}", fieldName, e.getMessage());
                    }
                }
            } catch (Exception e) {
                // 反射操作失败
                log.debug("反射获取用户ID失败: {}", e.getMessage());
            }
            return null;
        }
    }

    /**
     * Principal用户ID获取策略
     */
    private static class PrincipalUserIdStrategy implements UserIdStrategy {
        @Override
        public String getUserId(HttpServletRequest request) {
            Principal principal = request.getUserPrincipal();
            if (principal != null && StringUtils.hasText(principal.getName())) {
                return principal.getName();
            }
            return null;
        }
    }
}
