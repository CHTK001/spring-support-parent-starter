package com.chua.starter.strategy.aspect;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
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
import java.util.function.Supplier;

/**
 * 重试注解切面
 * <p>
 * 在 Spring 环境中实现对 @Retryable 注解的 AOP 扫描和重试处理。
 * 支持配置重试次数、间隔、异常类型等。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Slf4j
@Aspect
@Component
@Order(98)
public class RetryAspect {

    /**
     * 重试器缓存
     */
    private static final Map<String, Retry> RETRY_CACHE = new ConcurrentHashMap<>();

    /**
     * 切入点：所有标注 @Retryable 注解的方法
     */
    @Pointcut("@annotation(com.chua.starter.strategy.aspect.RetryAspect.Retryable)")
    public void retryPointcut() {
    }

    /**
     * 环绕通知：执行重试逻辑
     */
    @Around("retryPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Retryable annotation = method.getAnnotation(Retryable.class);

        // 检查是否启用重试
        if (!annotation.enabled()) {
            return joinPoint.proceed();
        }

        // 生成重试器Key
        String retryKey = buildRetryKey(annotation, joinPoint);

        // 获取或创建重试器
        Retry retry = getRetry(retryKey, annotation);

        // 执行带重试的方法
        Supplier<Object> retryableSupplier = Retry.decorateSupplier(retry, () -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });

        try {
            Object result = retryableSupplier.get();
            log.debug("[重试]执行成功 - Key: {}, 方法: {}", retryKey, method.getName());
            return result;
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            
            // 尝试调用降级方法
            String fallbackMethod = annotation.fallbackMethod();
            if (fallbackMethod != null && !fallbackMethod.isEmpty()) {
                log.info("[重试]所有重试失败，执行降级方法: {}", fallbackMethod);
                return invokeFallback(joinPoint, fallbackMethod);
            }
            
            if (cause != null) {
                throw cause;
            }
            throw e;
        }
    }

    /**
     * 构建重试器 Key
     */
    private String buildRetryKey(Retryable annotation, ProceedingJoinPoint joinPoint) {
        if (annotation.name() != null && !annotation.name().isEmpty()) {
            return "retry:" + annotation.name();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return "retry:" + signature.getDeclaringTypeName() + "#" + signature.getName();
    }

    /**
     * 获取或创建重试器
     */
    private Retry getRetry(String key, Retryable annotation) {
        return RETRY_CACHE.computeIfAbsent(key, k -> {
            RetryConfig.Builder<Object> builder = RetryConfig.custom()
                    .maxAttempts(annotation.maxAttempts())
                    .waitDuration(Duration.ofMillis(annotation.waitDuration()));

            // 配置重试异常
            if (annotation.retryExceptions().length > 0) {
                builder.retryExceptions(annotation.retryExceptions());
            }

            // 配置忽略异常
            if (annotation.ignoreExceptions().length > 0) {
                builder.ignoreExceptions(annotation.ignoreExceptions());
            }

            // 配置指数退避
            if (annotation.enableExponentialBackoff()) {
                builder.intervalFunction(attempt -> 
                        (long) (annotation.waitDuration() * Math.pow(annotation.multiplier(), attempt - 1)));
            }

            RetryConfig config = builder.build();
            RetryRegistry registry = RetryRegistry.of(config);
            Retry retry = registry.retry(key);

            // 添加事件监听
            retry.getEventPublisher()
                    .onRetry(event -> log.info("[重试]第{}次重试 - Key: {}, 异常: {}", 
                            event.getNumberOfRetryAttempts(), key, 
                            event.getLastThrowable().getMessage()))
                    .onSuccess(event -> log.debug("[重试]成功 - Key: {}, 重试次数: {}", 
                            key, event.getNumberOfRetryAttempts()))
                    .onError(event -> log.warn("[重试]失败 - Key: {}, 重试次数: {}", 
                            key, event.getNumberOfRetryAttempts()));

            log.info("[重试]创建重试器 - Key: {}, 最大次数: {}, 间隔: {}ms", 
                    key, annotation.maxAttempts(), annotation.waitDuration());
            return retry;
        });
    }

    /**
     * 清除指定Key的重试器
     */
    public static void removeRetry(String key) {
        RETRY_CACHE.remove(key);
        log.info("[重试]移除重试器 - Key: {}", key);
    }

    /**
     * 清除所有重试器
     */
    public static void clearAllRetries() {
        RETRY_CACHE.clear();
        log.info("[重试]清除所有重试器");
    }

    /**
     * 调用降级方法
     *
     * @param joinPoint      切点
     * @param fallbackMethod 降级方法名
     * @return 降级方法返回值
     */
    private Object invokeFallback(ProceedingJoinPoint joinPoint, String fallbackMethod) {
        try {
            Object target = joinPoint.getTarget();
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = target.getClass().getDeclaredMethod(fallbackMethod, signature.getParameterTypes());
            method.setAccessible(true);
            return method.invoke(target, joinPoint.getArgs());
        } catch (NoSuchMethodException e) {
            log.warn("[重试]降级方法不存在: {}", fallbackMethod);
            throw new RuntimeException("降级方法不存在: " + fallbackMethod, e);
        } catch (Exception e) {
            log.error("[重试]调用降级方法失败: {}", fallbackMethod, e);
            throw new RuntimeException("调用降级方法失败: " + fallbackMethod, e);
        }
    }

    /**
     * 重试注解
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
     *          │ 直接执行原方法│  │获取/创建Retry重试器(缓存)   │
     *          └─────────────┘  └───────────┬─────────────────┘
     *                                       ▼
     * ┌───────────────────────────────────────────────────────┐
     * │                   执行目标方法                        │
     * └─────────────────────────┬─────────────────────────────┘
     *                           ▼
     *                  ┌────────────────┐
     *                  │   执行成功？    │
     *                  └───────┬────────┘
     *                    ┌─────┴─────┐
     *               Yes  │           │  No
     *                    ▼           ▼
     *          ┌─────────────┐  ┌──────────────────────────┐
     *          │  返回结果    │  │ 异常是否在retryExceptions │
     *          └─────────────┘  │ 且不在ignoreExceptions中？│
     *                           └───────────┬──────────────┘
     *                                 ┌─────┴─────┐
     *                            No   │           │  Yes
     *                                 ▼           ▼
     *                        ┌──────────┐  ┌──────────────────┐
     *                        │  抛出异常 │  │重试次数<maxAttempts?│
     *                        └──────────┘  └────────┬─────────┘
     *                                         ┌─────┴─────┐
     *                                    No   │           │  Yes
     *                                         ▼           ▼
     *                            ┌───────────────┐  ┌───────────────────┐
     *                            │fallbackMethod │  │等待waitDuration   │
     *                            │  是否配置？    │  │(支持指数退避)      │
     *                            └───────┬───────┘  └─────────┬─────────┘
     *                              ┌─────┴─────┐              │
     *                         Yes  │           │ No           │
     *                              ▼           ▼              │
     *                      ┌───────────┐ ┌─────────┐          │
     *                      │执行降级方法│ │ 抛出异常│          │
     *                      └───────────┘ └─────────┘          │
     *                                                         │
     *                           ┌─────────────────────────────┘
     *                           ▼
     *                   [返回循环：执行目标方法]
     * </pre>
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface Retryable {

        /**
         * 重试器名称
         */
        String name() default "";

        /**
         * 是否启用
         */
        boolean enabled() default true;

        /**
         * 最大重试次数（包括首次调用）
         */
        int maxAttempts() default 3;

        /**
         * 重试间隔（毫秒）
         */
        long waitDuration() default 500;

        /**
         * 需要重试的异常类型
         */
        Class<? extends Throwable>[] retryExceptions() default {Exception.class};

        /**
         * 不需要重试的异常类型
         */
        Class<? extends Throwable>[] ignoreExceptions() default {};

        /**
         * 是否启用指数退避
         */
        boolean enableExponentialBackoff() default false;

        /**
         * 指数退避乘数
         */
        double multiplier() default 2.0;

        /**
         * 降级方法名
         */
        String fallbackMethod() default "";
    }
}
