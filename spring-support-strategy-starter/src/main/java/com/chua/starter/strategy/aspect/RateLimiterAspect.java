package com.chua.starter.strategy.aspect;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.task.limit.RateLimiter;
import com.chua.common.support.task.limit.RateLimiterProvider;
import com.chua.common.support.task.limit.RateLimiterSetting;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.utils.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RateLimiter 注解切面
 * <p>
 * 在 Spring 环境中实现对 @RateLimiter 注解的 AOP 扫描和限流处理。
 * 支持多种限流模式：全局、IP、用户、QPS。
 * </p>
 *
 * <pre>
 * 执行流程：
 * ┌───────────────────────────────────────────────────────┐
 * │                    方法调用                          │
 * └─────────────────────────┬─────────────────────────────┘
 *                           ▼
 *                  ┌────────────────┐
 *                  │  enabled=true? │
 *                  └───────┬────────┘
 *                    ┌─────┴─────┐
 *               No   │           │  Yes
 *                    ▼           ▼
 *          ┌─────────────┐  ┌─────────────────────────────┐
 *          │ 直接执行原方法│  │      构建限流器Key            │
 *          └─────────────┘  └───────────┬─────────────────┘
 *                                       ▼
 *   ┌───────────────────────────────────────────────────┐
 *   │  Key构建规则(mode)                                    │
 *   │  - global: ratelimit:global:class#method             │
 *   │  - ip: ratelimit:ip:{clientIp}:class#method          │
 *   │  - user: ratelimit:user:{userId}:class#method        │
 *   │  - qps: ratelimit:qps:class#method                   │
 *   └─────────────────────────┬─────────────────────────┘
 *                           ▼
 * ┌───────────────────────────────────────────────────────┐
 * │        获取/创建RateLimiterProvider(缓存)            │
 * │           (SPI加载，支持多种实现)                       │
 * └─────────────────────────┬─────────────────────────────┘
 *                           ▼
 *              ┌─────────────────────────┐
 *              │  limiter.tryAcquire()    │
 *              │     尝试获取令牌          │
 *              └───────────┬─────────────┘
 *                    ┌─────┴─────┐
 *               true │           │ false
 *              (获取成功) │           │ (被限流)
 *                    ▼           ▼
 *          ┌─────────────┐  ┌─────────────────────┐
 *          │  执行目标方法 │  │抛出RateLimitException │
 *          └──────┬──────┘  └─────────────────────┘
 *                 ▼
 *          ┌─────────────┐
 *          │   返回结果    │
 *          └─────────────┘
 * </pre>
 *
 * @author CH
 * @since 2024/12/07
 * @version 1.0.0
 */
@Slf4j
@Aspect
@Component
@Order(100)
public class RateLimiterAspect {

    /**
     * 限流器缓存
     * key: 限流器唯一标识
     * value: 限流器实例
     */
    private static final Map<String, RateLimiterProvider> LIMITER_CACHE = new ConcurrentHashMap<>();

    /**
     * 切入点：所有标注 @RateLimiter 注解的方法
     */
    @Pointcut("@annotation(com.chua.common.support.task.limit.RateLimiter)")
    public void rateLimiterPointcut() {
    }

    /**
     * 环绕通知：执行限流逻辑
     *
     * @param joinPoint 切入点
     * @return 方法返回值
     * @throws Throwable 异常
     */
    @Around("rateLimiterPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimiter annotation = method.getAnnotation(RateLimiter.class);

        // 检查是否启用限流
        if (!annotation.enabled()) {
            return joinPoint.proceed();
        }

        // 生成限流器 Key
        String limiterKey = buildLimiterKey(annotation, joinPoint);

        // 获取或创建限流器
        RateLimiterProvider limiter = getLimiter(limiterKey, annotation);

        // 尝试获取许可
        if (limiter == null || limiter.tryAcquire()) {
            log.debug("[限流]通过 - Key: {}, 方法: {}", limiterKey, method.getName());
            return joinPoint.proceed();
        }

        // 限流触发
        log.warn("[限流]触发 - Key: {}, 方法: {}, 消息: {}", limiterKey, method.getName(), annotation.message());
        throw new RateLimitException(annotation.message());
    }

    /**
     * 构建限流器 Key
     *
     * @param annotation 注解
     * @param joinPoint  切入点
     * @return 限流器 Key
     */
    private String buildLimiterKey(RateLimiter annotation, ProceedingJoinPoint joinPoint) {
        StringBuilder keyBuilder = new StringBuilder("ratelimit:");

        // 根据 mode 构建 key
        String mode = annotation.mode();
        switch (mode.toLowerCase()) {
            case "ip" -> {
                keyBuilder.append("ip:");
                keyBuilder.append(getClientIp());
            }
            case "user" -> {
                keyBuilder.append("user:");
                keyBuilder.append(getUserId());
            }
            case "qps" -> keyBuilder.append("qps:");
            default -> keyBuilder.append("global:");
        }

        // 添加自定义 key 或方法标识
        if (StringUtils.isNotBlank(annotation.key())) {
            keyBuilder.append(annotation.key());
        } else {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            keyBuilder.append(signature.getDeclaringTypeName())
                    .append("#")
                    .append(signature.getName());
        }

        return keyBuilder.toString();
    }

    /**
     * 获取或创建限流器
     *
     * @param key        限流器 Key
     * @param annotation 注解配置
     * @return 限流器实例
     */
    private RateLimiterProvider getLimiter(String key, RateLimiter annotation) {
        return LIMITER_CACHE.computeIfAbsent(key, k -> {
            String type = annotation.type();
            int rate = annotation.value();

            log.info("[限流]创建限流器 - Key: {}, 类型: {}, 速率: {}/s", key, type, rate);

            return ServiceProvider.of(RateLimiterProvider.class).getNewExtension(type,
                    RateLimiterSetting.builder()
                            .permitsPerSecond(rate)
                            .build()
            );
        });
    }

    /**
     * 获取客户端 IP
     *
     * @return 客户端 IP
     */
    private String getClientIp() {
        try {
            return RequestUtils.getIpAddress();
        } catch (Exception e) {
            // 尝试从 RequestContextHolder 获取
            try {
                ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attrs != null) {
                    HttpServletRequest request = attrs.getRequest();
                    String ip = request.getHeader("X-Forwarded-For");
                    if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                        ip = request.getHeader("X-Real-IP");
                    }
                    if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                        ip = request.getRemoteAddr();
                    }
                    return ip != null ? ip.split(",")[0].trim() : "unknown";
                }
            } catch (Exception ignored) {
            }
            return "unknown";
        }
    }

    /**
     * 获取用户 ID
     *
     * @return 用户 ID
     */
    private String getUserId() {
        try {
            // 尝试从 RequestUtils 获取用户信息
            Object userInfo = RequestUtils.getUserId();
            if (userInfo != null) {
                return userInfo.toString();
            }
        } catch (Exception ignored) {
        }

        // 尝试从请求属性获取
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                Object userId = attrs.getRequest().getAttribute("userId");
                if (userId != null) {
                    return userId.toString();
                }
            }
        } catch (Exception ignored) {
        }

        return "anonymous";
    }

    /**
     * 清除指定 Key 的限流器
     *
     * @param key 限流器 Key
     */
    public static void removeLimiter(String key) {
        LIMITER_CACHE.remove(key);
        log.info("[限流]移除限流器 - Key: {}", key);
    }

    /**
     * 清除所有限流器
     */
    public static void clearAllLimiters() {
        LIMITER_CACHE.clear();
        log.info("[限流]清除所有限流器");
    }

    /**
     * 限流异常
     */
    public static class RateLimitException extends RuntimeException {
        public RateLimitException(String message) {
            super(message);
        }
    }
}
