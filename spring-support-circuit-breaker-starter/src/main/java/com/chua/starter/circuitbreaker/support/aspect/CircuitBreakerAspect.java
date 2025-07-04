package com.chua.starter.circuitbreaker.support.aspect;

import com.chua.starter.circuitbreaker.support.annotation.CircuitBreakerProtection;
import com.chua.starter.circuitbreaker.support.service.CircuitBreakerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 熔断降级切面处理器
 * 
 * 处理@CircuitBreakerProtection注解，提供声明式的熔断降级功能。
 * 支持同步和异步两种执行方式，以及多种容错功能的组合使用。
 * 
 * @author CH
 * @since 2024/12/20
 */
@Slf4j
@Aspect
@Component
@Order(1)
@RequiredArgsConstructor
public class CircuitBreakerAspect {

    private final CircuitBreakerService circuitBreakerService;

    /**
     * 环绕通知处理熔断降级保护
     * 
     * @param joinPoint 连接点
     * @param protection 保护注解
     * @return 执行结果
     * @throws Throwable 异常
     */
    @Around("@annotation(protection)")
    public Object around(ProceedingJoinPoint joinPoint, CircuitBreakerProtection protection) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();

        log.debug("执行熔断降级保护: {}.{}", method.getDeclaringClass().getSimpleName(), method.getName());

        // 检查是否异步执行
        if (protection.async()) {
            return executeAsync(joinPoint, protection, method, args);
        } else {
            return executeSync(joinPoint, protection, method, args);
        }
    }

    /**
     * 同步执行
     * 
     * @param joinPoint 连接点
     * @param protection 保护注解
     * @param method 方法
     * @param args 参数
     * @return 执行结果
     * @throws Throwable 异常
     */
    private Object executeSync(ProceedingJoinPoint joinPoint, CircuitBreakerProtection protection, 
                              Method method, Object[] args) throws Throwable {
        
        // 检查是否有多个保护功能
        boolean hasCircuitBreaker = StringUtils.hasText(protection.circuitBreaker());
        boolean hasRetry = StringUtils.hasText(protection.retry());
        boolean hasRateLimit = StringUtils.hasText(protection.rateLimiter());
        boolean hasBulkhead = StringUtils.hasText(protection.bulkhead());

        // 如果有多个功能，使用组合执行
        if ((hasCircuitBreaker ? 1 : 0) + (hasRetry ? 1 : 0) + (hasRateLimit ? 1 : 0) + (hasBulkhead ? 1 : 0) > 1) {
            return executeWithCombined(joinPoint, protection, method, args);
        }

        // 单个功能执行
        if (hasCircuitBreaker) {
            return circuitBreakerService.executeWithCircuitBreaker(
                    protection.circuitBreaker(),
                    () -> {
                        try {
                            return joinPoint.proceed();
                        } catch (Throwable e) {
                            throw new RuntimeException(e);
                        }
                    },
                    () -> executeFallback(joinPoint.getTarget(), method, args, protection.fallbackMethod())
            );
        }

        if (hasRetry) {
            return circuitBreakerService.executeWithRetry(
                    protection.retry(),
                    () -> {
                        try {
                            return joinPoint.proceed();
                        } catch (Throwable e) {
                            throw new RuntimeException(e);
                        }
                    }
            );
        }

        if (hasRateLimit) {
            return circuitBreakerService.executeWithRateLimit(
                    protection.rateLimiter(),
                    () -> {
                        try {
                            return joinPoint.proceed();
                        } catch (Throwable e) {
                            throw new RuntimeException(e);
                        }
                    }
            );
        }

        if (hasBulkhead) {
            return circuitBreakerService.executeWithBulkhead(
                    protection.bulkhead(),
                    () -> {
                        try {
                            return joinPoint.proceed();
                        } catch (Throwable e) {
                            throw new RuntimeException(e);
                        }
                    }
            );
        }

        // 如果没有配置任何保护功能，直接执行
        return joinPoint.proceed();
    }

    /**
     * 异步执行
     * 
     * @param joinPoint 连接点
     * @param protection 保护注解
     * @param method 方法
     * @param args 参数
     * @return CompletionStage
     */
    private CompletionStage<?> executeAsync(ProceedingJoinPoint joinPoint, CircuitBreakerProtection protection, 
                                           Method method, Object[] args) {
        
        // 检查是否有完整保护功能
        boolean hasCircuitBreaker = StringUtils.hasText(protection.circuitBreaker());
        boolean hasRetry = StringUtils.hasText(protection.retry());
        boolean hasRateLimit = StringUtils.hasText(protection.rateLimiter());
        boolean hasBulkhead = StringUtils.hasText(protection.bulkhead());
        boolean hasTimeLimit = StringUtils.hasText(protection.timeLimiter());

        if (hasCircuitBreaker && hasRetry && hasRateLimit && hasBulkhead && hasTimeLimit) {
            return circuitBreakerService.executeWithFullProtection(
                    protection.circuitBreaker(),
                    protection.retry(),
                    protection.rateLimiter(),
                    protection.bulkhead(),
                    protection.timeLimiter(),
                    () -> {
                        try {
                            Object result = joinPoint.proceed();
                            if (result instanceof CompletionStage) {
                                return (CompletionStage<?>) result;
                            } else {
                                return CompletableFuture.completedFuture(result);
                            }
                        } catch (Throwable e) {
                            return CompletableFuture.failedFuture(e);
                        }
                    },
                    () -> executeFallback(joinPoint.getTarget(), method, args, protection.fallbackMethod())
            );
        }

        // 简单异步执行
        if (hasTimeLimit) {
            return circuitBreakerService.executeWithTimeLimit(
                    protection.timeLimiter(),
                    () -> {
                        try {
                            Object result = joinPoint.proceed();
                            if (result instanceof CompletionStage) {
                                return (CompletionStage<?>) result;
                            } else {
                                return CompletableFuture.completedFuture(result);
                            }
                        } catch (Throwable e) {
                            return CompletableFuture.failedFuture(e);
                        }
                    }
            );
        }

        // 默认异步执行
        return CompletableFuture.supplyAsync(() -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 组合功能执行
     * 
     * @param joinPoint 连接点
     * @param protection 保护注解
     * @param method 方法
     * @param args 参数
     * @return 执行结果
     */
    private Object executeWithCombined(ProceedingJoinPoint joinPoint, CircuitBreakerProtection protection, 
                                      Method method, Object[] args) {
        String circuitBreakerName = StringUtils.hasText(protection.circuitBreaker()) ? protection.circuitBreaker() : "default";
        String retryName = StringUtils.hasText(protection.retry()) ? protection.retry() : "default";
        String rateLimiterName = StringUtils.hasText(protection.rateLimiter()) ? protection.rateLimiter() : "default";

        return circuitBreakerService.executeWithCombined(
                circuitBreakerName,
                retryName,
                rateLimiterName,
                () -> {
                    try {
                        return joinPoint.proceed();
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                },
                () -> executeFallback(joinPoint.getTarget(), method, args, protection.fallbackMethod())
        );
    }

    /**
     * 执行降级方法
     * 
     * @param target 目标对象
     * @param method 原方法
     * @param args 参数
     * @param fallbackMethodName 降级方法名
     * @return 降级结果
     */
    private Object executeFallback(Object target, Method method, Object[] args, String fallbackMethodName) {
        if (!StringUtils.hasText(fallbackMethodName)) {
            log.warn("未配置降级方法，返回null");
            return null;
        }

        try {
            Method fallbackMethod = target.getClass().getDeclaredMethod(fallbackMethodName, method.getParameterTypes());
            fallbackMethod.setAccessible(true);
            return fallbackMethod.invoke(target, args);
        } catch (Exception e) {
            log.error("执行降级方法失败: {}", fallbackMethodName, e);
            return null;
        }
    }
}
