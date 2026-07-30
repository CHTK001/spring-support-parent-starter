package com.chua.starter.strategy.aspect;

import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
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
import java.util.concurrent.*;

/**
 * 超时控制切面
 * <p>
 * 在 Spring 环境中实现对 @TimeLimit 注解的 AOP 扫描和超时控制。
 * 当方法执行超过指定时间时自动取消。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Slf4j
@Aspect
@Component
@Order(97)
public class TimeLimiterAspect {

    /**
     * 超时控制器缓存
     */
    private static final Map<String, TimeLimiter> TIME_LIMITER_CACHE = new ConcurrentHashMap<>();

    /**
     * 执行线程池
     */
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "time-limiter");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 切入点：所有标注 @TimeLimit 注解的方法
     */
    @Pointcut("@annotation(com.chua.starter.strategy.aspect.TimeLimiterAspect.TimeLimit)")
    public void timeLimitPointcut() {
    }

    /**
     * 环绕通知：执行超时控制逻辑
     */
    @Around("timeLimitPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        TimeLimit annotation = method.getAnnotation(TimeLimit.class);

        // 检查是否启用超时控制
        if (!annotation.enabled()) {
            return joinPoint.proceed();
        }

        // 生成超时控制器Key
        String limiterKey = buildLimiterKey(annotation, joinPoint);

        // 获取或创建超时控制器
        TimeLimiter timeLimiter = getTimeLimiter(limiterKey, annotation);

        // 创建异步任务
        CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable e) {
                throw new CompletionException(e);
            }
        }, EXECUTOR);

        try {
            // 执行带超时控制的方法
            Object result = timeLimiter.executeFutureSupplier(() -> future);
            log.debug("[超时控制]执行成功 - Key: {}, 方法: {}", limiterKey, method.getName());
            return result;
        } catch (TimeoutException e) {
            log.warn("[超时控制]触发超时 - Key: {}, 方法: {}, 超时: {}ms", 
                    limiterKey, method.getName(), annotation.timeout());
            
            // 取消任务
            future.cancel(annotation.cancelRunningFuture());
            
            // 调用降级方法
            if (!annotation.fallbackMethod().isEmpty()) {
                return invokeFallback(joinPoint, annotation.fallbackMethod());
            }
            
            throw new TimeLimitException(annotation.message());
        } catch (CompletionException e) {
            throw e.getCause();
        }
    }

    /**
     * 构建超时控制器 Key
     */
    private String buildLimiterKey(TimeLimit annotation, ProceedingJoinPoint joinPoint) {
        if (annotation.name() != null && !annotation.name().isEmpty()) {
            return "timelimit:" + annotation.name();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return "timelimit:" + signature.getDeclaringTypeName() + "#" + signature.getName();
    }

    /**
     * 获取或创建超时控制器
     */
    private TimeLimiter getTimeLimiter(String key, TimeLimit annotation) {
        return TIME_LIMITER_CACHE.computeIfAbsent(key, k -> {
            TimeLimiterConfig config = TimeLimiterConfig.custom()
                    .timeoutDuration(Duration.ofMillis(annotation.timeout()))
                    .cancelRunningFuture(annotation.cancelRunningFuture())
                    .build();

            TimeLimiterRegistry registry = TimeLimiterRegistry.of(config);
            TimeLimiter limiter = registry.timeLimiter(key);

            // 添加事件监听
            limiter.getEventPublisher()
                    .onTimeout(event -> log.warn("[超时控制]超时事件 - Key: {}", key))
                    .onSuccess(event -> log.debug("[超时控制]成功事件 - Key: {}", key))
                    .onError(event -> log.warn("[超时控制]错误事件 - Key: {}", key));

            log.info("[超时控制]创建超时控制器 - Key: {}, 超时: {}ms", key, annotation.timeout());
            return limiter;
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
            log.warn("[超时控制]降级方法不存在: {}", fallbackMethod);
        } catch (Exception e) {
            log.error("[超时控制]调用降级方法失败: {}", fallbackMethod, e);
        }
        return null;
    }

    /**
     * 清除指定Key的超时控制器
     */
    public static void removeTimeLimiter(String key) {
        TIME_LIMITER_CACHE.remove(key);
        log.info("[超时控制]移除超时控制器 - Key: {}", key);
    }

    /**
     * 超时控制注解
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
     *          │ 直接执行原方法│  │获取/创建TimeLimiter(缓存)   │
     *          └─────────────┘  └───────────┬─────────────────┘
     *                                       ▼
     * ┌───────────────────────────────────────────────────────┐
     * │     创建CompletableFuture异步任务                     │
     * │          (使用独立线程池执行)                         │
     * └─────────────────────────┬─────────────────────────────┘
     *                           ▼
     * ┌───────────────────────────────────────────────────────┐
     * │   timeLimiter.executeFutureSupplier()               │
     * │        开始计时，执行异步任务                         │
     * └─────────────────────────┬─────────────────────────────┘
     *                           ▼
     *                  ┌────────────────────┐
     *                  │在timeout内完成？    │
     *                  └───────┬────────────┘
     *                    ┌─────┴─────┐
     *               Yes  │           │  No (超时)
     *                    ▼           ▼
     *          ┌─────────────┐  ┌──────────────────────────────┐
     *          │  返回结果    │  │cancelRunningFuture=true?    │
     *          └─────────────┘  └───────────┬──────────────────┘
     *                                 ┌─────┴─────┐
     *                            Yes  │           │  No
     *                                 ▼           ▼
     *                        ┌───────────┐  ┌───────────┐
     *                        │取消运行任务│  │任务继续执行│
     *                        └─────┬─────┘  └─────┬─────┘
     *                              └───────┬──────┘
     *                                      ▼
     *                           ┌─────────────────┐
     *                           │fallbackMethod   │
     *                           │   是否配置？     │
     *                           └───────┬─────────┘
     *                             ┌─────┴─────┐
     *                        Yes  │           │ No
     *                             ▼           ▼
     *                     ┌───────────┐ ┌─────────────────┐
     *                     │执行降级方法│ │抛出TimeLimitException│
     *                     └───────────┘ └─────────────────┘
     * </pre>
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface TimeLimit {

        /**
         * 超时控制器名称
         */
        String name() default "";

        /**
         * 是否启用
         */
        boolean enabled() default true;

        /**
         * 超时时间（毫秒）
         */
        long timeout() default 5000;

        /**
         * 超时时是否取消正在执行的任务
         */
        boolean cancelRunningFuture() default true;

        /**
         * 超时提示消息
         */
        String message() default "操作超时，请稍后重试";

        /**
         * 降级方法名
         */
        String fallbackMethod() default "";
    }

    /**
     * 超时异常
     */
    public static class TimeLimitException extends RuntimeException {
        public TimeLimitException(String message) {
            super(message);
        }
    }
}
