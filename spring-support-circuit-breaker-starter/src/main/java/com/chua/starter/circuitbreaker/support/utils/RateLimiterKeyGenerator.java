package com.chua.starter.circuitbreaker.support.utils;

import com.chua.starter.circuitbreaker.support.annotation.RateLimiter;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.common.support.oauth.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

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

    @Autowired
    private AuthService authService;

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
                return baseKey + ":login:" + userId;
                
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
            if (attributes == null) {
                log.debug("无法获取ServletRequestAttributes，可能不在Web环境中");
                return "anonymous";
            }

            HttpServletRequest request = attributes.getRequest();
            if (request == null) {
                log.debug("无法获取HttpServletRequest");
                return "anonymous";
            }

            CurrentUser currentUser = authService.getCurrentUser();
            return null == currentUser ? "anonymous" : currentUser.getUserId();
        } catch (Exception e) {
            log.debug("获取当前用户ID失败", e);
        }

        log.debug("所有策略均失败，返回默认用户ID: anonymous");
        return "anonymous";
    }

    /**
     * 验证用户ID的有效性
     */
    private boolean isValidUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return false;
        }

        // 过滤掉一些明显无效的用户ID
        String lowerUserId = userId.toLowerCase();
        return !lowerUserId.equals("anonymous")
                && !lowerUserId.equals("guest")
                && !lowerUserId.equals("unknown")
                && !lowerUserId.equals("null")
                && !lowerUserId.equals("undefined")
                && userId.length() <= 100; // 防止过长的ID
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


}
