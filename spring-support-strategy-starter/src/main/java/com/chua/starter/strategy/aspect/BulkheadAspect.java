package com.chua.starter.strategy.aspect;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 舱壁隔离切面
 * <p>
 * 在 Spring 环境中实现对 @BulkheadLimit 注解的 AOP 扫描和并发控制。
 * 限制同时执行的请求数量，防止资源耗尽。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Slf4j
@Aspect
@Component
@Order(96)
public class BulkheadAspect {

    /**
     * 舱壁隔离器缓存
     */
    private static final Map<String, Bulkhead> BULKHEAD_CACHE = new ConcurrentHashMap<>();

    /**
     * 切入点：所有标注 @BulkheadLimit 注解的方法
     */
    @Pointcut("@annotation(com.chua.starter.strategy.aspect.BulkheadAspect.BulkheadLimit)")
    public void bulkheadPointcut() {
    }

    /**
     * 环绕通知：执行舱壁隔离逻辑
     */
    @Around("bulkheadPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        BulkheadLimit annotation = method.getAnnotation(BulkheadLimit.class);

        // 检查是否启用舱壁隔离
        if (!annotation.enabled()) {
            return joinPoint.proceed();
        }

        // 生成舱壁隔离器Key
        String bulkheadKey = buildBulkheadKey(annotation, joinPoint);

        // 获取或创建舱壁隔离器
        Bulkhead bulkhead = getBulkhead(bulkheadKey, annotation);

        try {
            // 尝试获取许可
            bulkhead.acquirePermission();
            
            try {
                Object result = joinPoint.proceed();
                log.debug("[舱壁隔离]执行成功 - Key: {}, 方法: {}", bulkheadKey, method.getName());
                return result;
            } finally {
                // 释放许可
                bulkhead.releasePermission();
            }
        } catch (BulkheadFullException e) {
            log.warn("[舱壁隔离]触发 - Key: {}, 方法: {}, 当前并发: {}/{}", 
                    bulkheadKey, method.getName(), 
                    bulkhead.getMetrics().getAvailableConcurrentCalls(),
                    annotation.maxConcurrentCalls());
            
            // 调用降级方法
            if (!annotation.fallbackMethod().isEmpty()) {
                return invokeFallback(joinPoint, annotation.fallbackMethod());
            }
            
            throw new BulkheadException(annotation.message());
        }
    }

    /**
     * 构建舱壁隔离器 Key
     */
    private String buildBulkheadKey(BulkheadLimit annotation, ProceedingJoinPoint joinPoint) {
        if (annotation.name() != null && !annotation.name().isEmpty()) {
            return "bulkhead:" + annotation.name();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return "bulkhead:" + signature.getDeclaringTypeName() + "#" + signature.getName();
    }

    /**
     * 获取或创建舱壁隔离器
     */
    private Bulkhead getBulkhead(String key, BulkheadLimit annotation) {
        return BULKHEAD_CACHE.computeIfAbsent(key, k -> {
            BulkheadConfig config = BulkheadConfig.custom()
                    .maxConcurrentCalls(annotation.maxConcurrentCalls())
                    .maxWaitDuration(Duration.ofMillis(annotation.maxWaitDuration()))
                    .build();

            BulkheadRegistry registry = BulkheadRegistry.of(config);
            Bulkhead bulkhead = registry.bulkhead(key);

            // 添加事件监听
            bulkhead.getEventPublisher()
                    .onCallPermitted(event -> log.debug("[舱壁隔离]获取许可 - Key: {}", key))
                    .onCallRejected(event -> log.warn("[舱壁隔离]拒绝请求 - Key: {}", key))
                    .onCallFinished(event -> log.debug("[舱壁隔离]释放许可 - Key: {}", key));

            log.info("[舱壁隔离]创建舱壁隔离器 - Key: {}, 最大并发: {}", key, annotation.maxConcurrentCalls());
            return bulkhead;
        });
    }

    /**
     * 调用降级方法
     */
    private Object invokeFallback(ProceedingJoinPoint joinPoint, String fallbackMethod) {
        try {
            Object target = joinPoint.getTarget();
            Method method = target.getClass().getDeclaredMethod(fallbackMethod,
                    ((MethodSignature) joinPoint.getSignature()).getParameterTypes());
            method.setAccessible(true);
            return method.invoke(target, joinPoint.getArgs());
        } catch (NoSuchMethodException e) {
            log.warn("[舱壁隔离]降级方法不存在: {}", fallbackMethod);
        } catch (Exception e) {
            log.error("[舱壁隔离]调用降级方法失败: {}", fallbackMethod, e);
        }
        return null;
    }

    /**
     * 获取舱壁隔离器指标
     */
    public static Map<String, BulkheadMetrics> getMetrics() {
        Map<String, BulkheadMetrics> metrics = new ConcurrentHashMap<>();
        
        BULKHEAD_CACHE.forEach((key, bulkhead) -> {
            Bulkhead.Metrics m = bulkhead.getMetrics();
            metrics.put(key, new BulkheadMetrics(
                    m.getAvailableConcurrentCalls(),
                    m.getMaxAllowedConcurrentCalls()
            ));
        });
        
        return metrics;
    }

    /**
     * 清除指定Key的舱壁隔离器
     */
    public static void removeBulkhead(String key) {
        BULKHEAD_CACHE.remove(key);
        log.info("[舱壁隔离]移除舱壁隔离器 - Key: {}", key);
    }

    /**
     * 舱壁隔离注解
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
     *          │ 直接执行原方法│  │ 获取/创建Bulkhead隔离器(缓存)│
     *          └─────────────┘  └───────────┬─────────────────┘
     *                                       ▼
     * ┌───────────────────────────────────────────────────────┐
     * │         bulkhead.acquirePermission()                 │
     * │              尝试获取执行许可                          │
     * └─────────────────────────┬─────────────────────────────┘
     *                           ▼
     *              ┌─────────────────────────┐
     *              │当前并发数<maxConcurrentCalls?│
     *              └───────────┬─────────────┘
     *                    ┌─────┴─────┐
     *               Yes  │           │  No
     *                    ▼           ▼
     *       ┌───────────────────┐  ┌───────────────────────────┐
     *       │   获取许可成功     │  │等待时间<maxWaitDuration?  │
     *       └─────────┬─────────┘  └───────────┬───────────────┘
     *                 ▼                  ┌─────┴─────┐
     *       ┌─────────────────┐     Yes  │           │ No(等待超时)
     *       │  执行目标方法    │          ▼           ▼
     *       └────────┬────────┘    [继续等待]  ┌─────────────────┐
     *                ▼                        │BulkheadFullException│
     *       ┌─────────────────┐               └────────┬────────┘
     *       │释放许可(finally) │                       ▼
     *       │releasePermission│              ┌──────────────────┐
     *       └────────┬────────┘              │fallbackMethod    │
     *                ▼                       │   是否配置？      │
     *       ┌─────────────────┐              └───────┬──────────┘
     *       │    返回结果      │                ┌────┴────┐
     *       └─────────────────┘           Yes  │         │ No
     *                                          ▼         ▼
     *                                  ┌───────────┐ ┌─────────────────┐
     *                                  │执行降级方法│ │抛出BulkheadException│
     *                                  └───────────┘ └─────────────────┘
     * </pre>
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface BulkheadLimit {

        /**
         * 舱壁隔离器名称
         */
        String name() default "";

        /**
         * 是否启用
         */
        boolean enabled() default true;

        /**
         * 最大并发调用数
         */
        int maxConcurrentCalls() default 25;

        /**
         * 最大等待时间（毫秒）
         */
        long maxWaitDuration() default 0;

        /**
         * 触发隔离时的提示消息
         */
        String message() default "系统繁忙，请稍后重试";

        /**
         * 降级方法名
         */
        String fallbackMethod() default "";
    }

    /**
     * 舱壁隔离异常
     */
    public static class BulkheadException extends RuntimeException {
        public BulkheadException(String message) {
            super(message);
        }
    }

    /**
     * 舱壁隔离指标
     */
    public record BulkheadMetrics(int availableConcurrentCalls, int maxAllowedConcurrentCalls) {
    }
}
