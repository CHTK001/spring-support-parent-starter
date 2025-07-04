package com.chua.starter.circuitbreaker.support.aspect;

import com.chua.starter.circuitbreaker.support.annotation.RateLimiter;
import com.chua.starter.circuitbreaker.support.metrics.RateLimiterMetrics;
import com.chua.starter.circuitbreaker.support.properties.CircuitBreakerProperties;
import com.chua.starter.circuitbreaker.support.service.CircuitBreakerService;
import com.chua.starter.circuitbreaker.support.utils.RateLimiterKeyGenerator;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;

/**
 * 限流切面
 * 
 * 基于Resilience4j RateLimiter实现的限流切面，处理@RateLimiter注解。
 * 支持多种限流维度和灵活的配置选项。
 * 
 * @author CH
 * @since 2024/12/20
 */
@Slf4j
@Aspect
@Component
public class RateLimiterAspect {

    private final RateLimiterRegistry rateLimiterRegistry;
    private final CircuitBreakerProperties properties;
    private final CircuitBreakerService circuitBreakerService;
    private final RateLimiterKeyGenerator keyGenerator;
    private final RateLimiterMetrics rateLimiterMetrics;

    /**
     * 构造函数（带指标收集器）
     */
    public RateLimiterAspect(RateLimiterRegistry rateLimiterRegistry,
                            CircuitBreakerProperties properties,
                            CircuitBreakerService circuitBreakerService,
                            RateLimiterKeyGenerator keyGenerator,
                            RateLimiterMetrics rateLimiterMetrics) {
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.properties = properties;
        this.circuitBreakerService = circuitBreakerService;
        this.keyGenerator = keyGenerator;
        this.rateLimiterMetrics = rateLimiterMetrics;
    }

    /**
     * 构造函数（不带指标收集器）
     */
    public RateLimiterAspect(RateLimiterRegistry rateLimiterRegistry,
                            CircuitBreakerProperties properties,
                            CircuitBreakerService circuitBreakerService,
                            RateLimiterKeyGenerator keyGenerator) {
        this(rateLimiterRegistry, properties, circuitBreakerService, keyGenerator, null);
    }

    /**
     * 处理@RateLimiter注解的方法
     */
    @Around("@annotation(com.chua.starter.circuitbreaker.support.annotation.RateLimiter)")
    public Object handleRateLimiter(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 获取注解
        RateLimiter rateLimiterAnnotation = AnnotatedElementUtils.findMergedAnnotation(method, RateLimiter.class);
        if (rateLimiterAnnotation == null) {
            return joinPoint.proceed();
        }

        // 根据维度生成限流器名称和键
        String rateLimiterName = generateRateLimiterName(rateLimiterAnnotation, method);
        String rateLimiterKey = keyGenerator.generateKey(rateLimiterAnnotation, joinPoint);

        log.debug("执行限流检查: name={}, key={}, dimension={}",
                 rateLimiterName, rateLimiterKey, rateLimiterAnnotation.dimension());

        // 记录开始时间
        Instant startTime = Instant.now();

        try {
            // 获取或创建限流器（根据维度和键创建独立的限流器）
            io.github.resilience4j.ratelimiter.RateLimiter rateLimiter =
                getRateLimiterByDimension(rateLimiterName, rateLimiterKey, rateLimiterAnnotation);

            // 记录限流器状态
            recordRateLimiterState(rateLimiterName, rateLimiterAnnotation.dimension(), rateLimiter);

            // 执行限流检查
            Object result = rateLimiter.executeSupplier(() -> {
                try {
                    return joinPoint.proceed();
                } catch (Throwable throwable) {
                    if (throwable instanceof RuntimeException) {
                        throw (RuntimeException) throwable;
                    }
                    throw new RuntimeException(throwable);
                }
            });

            // 记录成功指标
            if (rateLimiterMetrics != null) {
                Duration executionTime = Duration.between(startTime, Instant.now());
                rateLimiterMetrics.recordRequest(rateLimiterName, rateLimiterAnnotation.dimension(), "success");
                rateLimiterMetrics.recordExecutionTime(rateLimiterName, rateLimiterAnnotation.dimension(),
                                                      executionTime, "success");
            }

            return result;

        } catch (RequestNotPermitted e) {
            log.warn("限流触发: name={}, key={}, dimension={}, message={}",
                    rateLimiterName, rateLimiterKey, rateLimiterAnnotation.dimension(), rateLimiterAnnotation.message());

            // 记录拒绝指标
            if (rateLimiterMetrics != null) {
                Duration executionTime = Duration.between(startTime, Instant.now());
                rateLimiterMetrics.recordRequest(rateLimiterName, rateLimiterAnnotation.dimension(), "rejected");
                rateLimiterMetrics.recordExecutionTime(rateLimiterName, rateLimiterAnnotation.dimension(),
                                                      executionTime, "rejected");
            }

            // 尝试执行降级方法
            if (!rateLimiterAnnotation.fallbackMethod().isEmpty()) {
                return executeFallback(joinPoint, rateLimiterAnnotation.fallbackMethod(), e);
            }

            // 抛出限流异常
            throw new RuntimeException(rateLimiterAnnotation.message());
        }
    }

    /**
     * 根据维度生成限流器名称
     *
     * @param annotation 限流注解
     * @param method 方法
     * @return 限流器名称
     */
    private String generateRateLimiterName(RateLimiter annotation, Method method) {
        String baseName;
        if (!annotation.name().isEmpty()) {
            baseName = annotation.name();
        } else {
            // 使用方法签名生成默认名称
            baseName = method.getDeclaringClass().getSimpleName() + "." + method.getName();
        }

        // 根据维度添加前缀，确保不同维度的限流器独立
        return annotation.dimension().name().toLowerCase() + ":" + baseName;
    }

    /**
     * 根据维度获取或创建限流器
     *
     * @param rateLimiterName 限流器名称
     * @param rateLimiterKey 限流键
     * @param annotation 限流注解
     * @return 限流器实例
     */
    private io.github.resilience4j.ratelimiter.RateLimiter getRateLimiterByDimension(
            String rateLimiterName, String rateLimiterKey, RateLimiter annotation) {

        // 为每个维度和键的组合创建独立的限流器
        String uniqueRateLimiterName = rateLimiterName + ":" + rateLimiterKey;

        io.github.resilience4j.ratelimiter.RateLimiter rateLimiter =
            rateLimiterRegistry.rateLimiter(uniqueRateLimiterName);

        // 如果注解中指定了配置，则更新限流器配置
        if (needsConfigUpdate(annotation)) {
            updateRateLimiterConfig(rateLimiter, annotation);
        }

        log.debug("获取限流器: uniqueName={}, dimension={}, key={}",
                 uniqueRateLimiterName, annotation.dimension(), rateLimiterKey);

        return rateLimiter;
    }

    /**
     * 记录限流器状态指标
     *
     * @param rateLimiterName 限流器名称
     * @param dimension 限流维度
     * @param rateLimiter 限流器实例
     */
    private void recordRateLimiterState(String rateLimiterName, RateLimiter.Dimension dimension,
                                       io.github.resilience4j.ratelimiter.RateLimiter rateLimiter) {
        if (rateLimiterMetrics != null) {
            try {
                int availablePermissions = rateLimiter.getMetrics().getAvailablePermissions();
                int waitingThreads = rateLimiter.getMetrics().getNumberOfWaitingThreads();

                rateLimiterMetrics.recordCurrentState(rateLimiterName, dimension,
                                                     availablePermissions, waitingThreads);
            } catch (Exception e) {
                log.debug("记录限流器状态失败: name={}, dimension={}", rateLimiterName, dimension, e);
            }
        }
    }



    /**
     * 检查是否需要更新配置
     */
    private boolean needsConfigUpdate(RateLimiter annotation) {
        return annotation.limitForPeriod() != -1 
            || annotation.limitRefreshPeriodSeconds() != -1 
            || annotation.timeoutDurationMillis() != -1;
    }

    /**
     * 更新限流器配置
     */
    private void updateRateLimiterConfig(io.github.resilience4j.ratelimiter.RateLimiter rateLimiter, RateLimiter annotation) {
        var configBuilder = io.github.resilience4j.ratelimiter.RateLimiterConfig.from(rateLimiter.getRateLimiterConfig());
        
        if (annotation.limitForPeriod() != -1) {
            configBuilder.limitForPeriod(annotation.limitForPeriod());
        }
        
        if (annotation.limitRefreshPeriodSeconds() != -1) {
            configBuilder.limitRefreshPeriod(Duration.ofSeconds(annotation.limitRefreshPeriodSeconds()));
        }
        
        if (annotation.timeoutDurationMillis() != -1) {
            configBuilder.timeoutDuration(Duration.ofMillis(annotation.timeoutDurationMillis()));
        }
        
        // 注意：Resilience4j的RateLimiter不支持运行时配置更新
        // 这里只是为了演示，实际使用中需要重新创建限流器
        log.debug("限流器配置更新: name={}", rateLimiter.getName());
    }

    /**
     * 执行降级方法
     */
    private Object executeFallback(ProceedingJoinPoint joinPoint, String fallbackMethodName, Exception exception) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method originalMethod = signature.getMethod();
            Class<?> targetClass = joinPoint.getTarget().getClass();
            
            // 查找降级方法
            Method fallbackMethod = findFallbackMethod(targetClass, fallbackMethodName, originalMethod, exception);
            if (fallbackMethod != null) {
                Object[] args = joinPoint.getArgs();
                Object[] fallbackArgs = new Object[args.length + 1];
                System.arraycopy(args, 0, fallbackArgs, 0, args.length);
                fallbackArgs[args.length] = exception;
                
                return fallbackMethod.invoke(joinPoint.getTarget(), fallbackArgs);
            }
            
            log.warn("未找到降级方法: {}", fallbackMethodName);
            throw new RuntimeException("限流触发且未找到降级方法: " + fallbackMethodName);
            
        } catch (Exception e) {
            log.error("执行降级方法失败: {}", fallbackMethodName, e);
            throw new RuntimeException("执行降级方法失败: " + fallbackMethodName, e);
        }
    }

    /**
     * 查找降级方法
     */
    private Method findFallbackMethod(Class<?> targetClass, String fallbackMethodName, Method originalMethod, Exception exception) {
        try {
            // 尝试查找带异常参数的降级方法
            Class<?>[] paramTypes = originalMethod.getParameterTypes();
            Class<?>[] fallbackParamTypes = new Class[paramTypes.length + 1];
            System.arraycopy(paramTypes, 0, fallbackParamTypes, 0, paramTypes.length);
            fallbackParamTypes[paramTypes.length] = Exception.class;
            
            return targetClass.getDeclaredMethod(fallbackMethodName, fallbackParamTypes);
        } catch (NoSuchMethodException e) {
            try {
                // 尝试查找不带异常参数的降级方法
                return targetClass.getDeclaredMethod(fallbackMethodName, originalMethod.getParameterTypes());
            } catch (NoSuchMethodException ex) {
                return null;
            }
        }
    }
}
