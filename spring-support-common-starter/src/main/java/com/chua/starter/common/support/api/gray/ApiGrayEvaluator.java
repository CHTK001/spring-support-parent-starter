package com.chua.starter.common.support.api.gray;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.api.annotations.ApiGray;
import com.chua.starter.common.support.utils.IpUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
* API 灰度规则评估器
* <p>
* 用于评估请求是否命中灰度规则
* </p>
*
* @since 2024/12/18
* @版本 1.0.0
* @author CH
 */
public class ApiGrayEvaluator {
    /** 日志 */
    private static final Logger log = LoggerFactory.getLogger(ApiGrayEvaluator.class);
        /** 解析器 */
        private static final ExpressionParser PARSER = new SpelExpressionParser();
    
    /**
    * spel 表达式缓存
    */
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    /**
    * 评估请求是否命中灰度
    *
    * @param apiGray  灰度注解
    * @param request  HTTP请求
    * @param userId   用户标识
    * @param username 用户名
    * @param roles    当前用户角色集合（可为 空）
    * @return 是否命中灰度
    */
    public boolean evaluate(ApiGray apiGray, HttpServletRequest request, Object userId, String username,
                            java.util.Collection<String> roles) {
        String clientIp = IpUtils.getClientIp(request);

        // 1. 检查用户白名单
        if (matchUserWhitelist(apiGray.users(), username)) {
            log.debug("灰度命中: 用户白名单匹配 - user={}", username);
            return true;
        }

        // 2. 检查角色白名单
        if (matchRoleWhitelist(apiGray.roles(), roles)) {
            log.debug("灰度命中: 角色白名单匹配 - roles={}", roles);
            return true;
        }

        // 3. 检查IP白名单
        if (matchIpWhitelist(apiGray.ips(), clientIp)) {
            log.debug("灰度命中: IP白名单匹配 - ip={}", clientIp);
            return true;
        }

        // 4. 检查请求头匹配
        if (matchHeaders(apiGray.headers(), request)) {
            log.debug("灰度命中: 请求头匹配");
            return true;
        }

 // 5. 检查spel规则
        if (StringUtils.isNotBlank(apiGray.rule())) {
            if (evaluateSpelRule(apiGray.rule(), request, userId, username, clientIp, roles)) {
                log.debug("灰度命中: SpEL规则匹配 - rule={}", apiGray.rule());
                return true;
            }
        }

        // 6. 检查百分比灰度
        if (apiGray.percentage() > 0) {
            if (matchPercentage(apiGray.percentage(), userId, username, clientIp)) {
                log.debug("灰度命中: 百分比匹配 - percentage={}%", apiGray.percentage());
                return true;
            }
        }

        return false;
    }

    /**
    * 兼容旧调用（无角色参数）
    * @param apiGray apigray
    * @param request 请求
    * @param userId 用户标识
    * @param username 用户名
    * @return 评估的结果
    */
    public boolean evaluate(ApiGray apiGray, HttpServletRequest request, Object userId, String username) {
        return evaluate(apiGray, request, userId, username, null);
    }

    /**
    * 检查用户白名单
    * @param users 用户
    * @param username 用户名
    * @return 匹配用户whitelist的结果
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
    * 检查角色白名单
    * @param roles 角色
    * @param userRoles 用户角色
    * @return 匹配角色whitelist的结果
    */
    private boolean matchRoleWhitelist(String[] roles, java.util.Collection<String> userRoles) {
        if (roles == null || roles.length == 0 || userRoles == null || userRoles.isEmpty()) {
            return false;
        }
        for (String role : roles) {
            if (userRoles.stream().anyMatch(r -> r.equalsIgnoreCase(role))) {
                return true;
            }
        }
        return false;
    }

    /**
    * 检查IP白名单
    * @param ips ips
    * @param clientIp 客户端ip
    * @return 匹配ipwhitelist的结果
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
    * @param clientIp 客户端ip
    * @param pattern 模式
    * @return 匹配ip的结果
    */
    private boolean matchIp(String clientIp, String pattern) {
        return IpUtils.matchIp(clientIp, pattern);
    }

    /**
    * 检查请求头匹配
    * @param headers 头部
    * @param request 请求
    * @return 匹配头部的结果
    */
    private boolean matchHeaders(String[] headers, HttpServletRequest request) {
        if (headers == null || headers.length == 0) {
            return false;
        }
        for (String header : headers) {
            if (header.contains("=")) {
 // 头部名称=价值观 格式
                String[] parts = header.split("=", 2);
                String headerName = parts[0].trim();
                String expectedValue = parts[1].trim();
                String actualValue = request.getHeader(headerName);
                if (expectedValue.equals(actualValue)) {
                    return true;
                }
            } else {
 // 仅检查头部是否存在
                if (StringUtils.isNotBlank(request.getHeader(header.trim()))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
    * 评估spel表达式
    */
    private boolean evaluateSpelRule(String rule, HttpServletRequest request,
                                     Object userId, String username, String clientIp,
                                     java.util.Collection<String> roles) {
        try {
            Expression expression = expressionCache.computeIfAbsent(rule, PARSER::parseExpression);
            EvaluationContext context = createEvaluationContext(request, userId, username, clientIp, roles);
            Boolean result = expression.getValue(context, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("灰度SpEL表达式评估失败: rule={}, error={}", rule, e.getMessage());
            return false;
        }
    }

    /**
    * 创建spel评估上下文
    */
    private EvaluationContext createEvaluationContext(HttpServletRequest request,
                                                       Object userId, String username, String clientIp,
                                                       java.util.Collection<String> roles) {
        StandardEvaluationContext context = new StandardEvaluationContext();

        // 设置变量
        context.setVariable("userId", userId);
        context.setVariable("username", username);
        context.setVariable("ip", clientIp);
        context.setVariable("roles", roles);
        context.setVariable("request", request);

        // 注册方法
        try {
            // 注：StandardEvaluationContext.registerFunction 要求 java.lang.reflect.Method 参数，
            //     属于 SpEL 框架 API 签名约束，无法用 ReflectUtils 替代，豁免
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
        context.setRootObject(new GrayContext(request, userId, username, clientIp, roles));

        return context;
    }

    /**
    * 百分比灰度匹配
    * <p>
    * 使用一致性哈希确保同一用户的灰度结果稳定
    * </p>
    * @param percentage percentage
    * @param userId 用户标识
    * @param username 用户名
    * @param clientIp 客户端ip
    * @return 匹配percentage的结果
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
    * @param userId 用户标识
    * @param username 用户名
    * @param clientIp 客户端ip
    * @return 构建哈希钥匙的结果
    */
    private String buildHashKey(Object userId, String username, String clientIp) {
        // 内容长度取决于实际使用的用户标识，无法预估，按需扩容
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

 // ==================== spel 辅助方法 ====================

    /**
    * 获取请求头（spel 函数）
    * @param request 请求
    * @param name 名称
    * @return 获取头部的结果
    */
    public static String getHeader(HttpServletRequest request, String name) {
        return request.getHeader(name);
    }

    /**
    * 获取请求参数（spel 函数）
    * @param request 请求
    * @param name 名称
    * @return 获取参数的结果
    */
    public static String getParam(HttpServletRequest request, String name) {
        return request.getParameter(name);
    }

    /**
    * 获取Cookie值（spel 函数）
    * @param request 请求
    * @param name 名称
    * @return 获取Cookie的结果
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
    * 灰度上下文（spel 根对象）
    */
    public record GrayContext(HttpServletRequest request, Object userId, String username, String ip,
                              java.util.Collection<String> roles) {

        /**
        * 头部
        *
        * @param name 名称
        * @return 头部的结果
        */
        public String header(String name) {
            return request.getHeader(name);
        }

        /**
        * 参数
        *
        * @param name 名称
        * @return 参数的结果
        */
        public String param(String name) {
            return request.getParameter(name);
        }

        /**
        * Cookie
        *
        * @param name 名称
        * @return Cookie的结果
        */
        public String cookie(String name) {
            return getCookie(request, name);
        }

        /**
        * 是否拥有角色
        *
        * @param role 角色
        * @return 是否包含角色的结果
        */
        public boolean hasRole(String role) {
            return roles != null && roles.stream().anyMatch(r -> r.equalsIgnoreCase(role));
        }
    }
}
