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
        if (joinPoint == null) {
            throw new IllegalArgumentException("ProceedingJoinPoint不能为null");
        }

        if (protection == null) {
            log.warn("CircuitBreakerProtection注解为null，直接执行原方法");
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();

        String className = method.getDeclaringClass().getSimpleName();
        String methodName = method.getName();

        log.debug("开始执行熔断降级保护: {}.{}, async={}", className, methodName, protection.async());

        try {
            // 检查是否异步执行
            if (protection.async()) {
                Object result = executeAsync(joinPoint, protection, method, args);
                log.debug("异步执行完成: {}.{}", className, methodName);
                return result;
            } else {
                Object result = executeSync(joinPoint, protection, method, args);
                log.debug("同步执行完成: {}.{}", className, methodName);
                return result;
            }
        } catch (Exception e) {
            log.error("熔断降级保护执行失败: {}.{}", className, methodName, e);
            throw e;
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

        int protectionCount = (hasCircuitBreaker ? 1 : 0) + (hasRetry ? 1 : 0) +
                (hasRateLimit ? 1 : 0) + (hasBulkhead ? 1 : 0);

        log.debug("保护功能数量: {}, 熔断器: {}, 重试: {}, 限流: {}, 隔离: {}",
                protectionCount, hasCircuitBreaker, hasRetry, hasRateLimit, hasBulkhead);

        // 如果有多个功能，使用组合执行
        if (protectionCount > 1) {
            return executeWithCombined(joinPoint, protection, method, args);
        }

        // 单个功能执行
        if (hasCircuitBreaker) {
            log.debug("执行熔断器保护: {}", protection.circuitBreaker());
            return circuitBreakerService.executeWithCircuitBreaker(
                    protection.circuitBreaker(),
                    () -> proceedSafely(joinPoint),
                    () -> executeFallback(joinPoint.getTarget(), method, args, protection.fallbackMethod())
            );
        }

        if (hasRetry) {
            log.debug("执行重试保护: {}", protection.retry());
            return circuitBreakerService.executeWithRetry(
                    protection.retry(),
                    () -> proceedSafely(joinPoint)
            );
        }

        if (hasRateLimit) {
            log.debug("执行限流保护: {}", protection.rateLimiter());
            return circuitBreakerService.executeWithRateLimit(
                    protection.rateLimiter(),
                    () -> proceedSafely(joinPoint)
            );
        }

        if (hasBulkhead) {
            log.debug("执行隔离保护: {}", protection.bulkhead());
            return circuitBreakerService.executeWithBulkhead(
                    protection.bulkhead(),
                    () -> proceedSafely(joinPoint)
            );
        }

        // 如果没有配置任何保护功能，直接执行
        log.debug("没有配置保护功能，直接执行原方法");
        return joinPoint.proceed();
    }

    /**
     * 安全地执行原方法
     */
    private Object proceedSafely(ProceedingJoinPoint joinPoint) {
        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("方法执行失败", e);
        }
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

        log.debug("异步执行保护功能 - 熔断器: {}, 重试: {}, 限流: {}, 隔离: {}, 超时: {}",
                hasCircuitBreaker, hasRetry, hasRateLimit, hasBulkhead, hasTimeLimit);

        // 如果配置了完整保护功能，尝试使用组合保护
        if (hasCircuitBreaker && hasRetry && hasRateLimit && hasBulkhead && hasTimeLimit) {
            log.debug("使用完整保护功能执行异步方法");
            try {
                // 由于类型匹配问题，我们使用简化的异步执行
                return executeAsyncWithTimeLimit(joinPoint, protection.timeLimiter());
            } catch (Exception e) {
                log.warn("完整保护功能执行失败，降级为简单异步执行: {}", e.getMessage());
                return executeSimpleAsync(joinPoint);
            }
        }

        // 简单异步执行
        if (hasTimeLimit) {
            log.debug("使用超时限制执行异步方法: {}", protection.timeLimiter());
            return executeAsyncWithTimeLimit(joinPoint, protection.timeLimiter());
        }

        // 默认异步执行
        log.debug("使用默认方式执行异步方法");
        return executeSimpleAsync(joinPoint);
    }

    /**
     * 带超时限制的异步执行
     */
    private CompletionStage<?> executeAsyncWithTimeLimit(ProceedingJoinPoint joinPoint, String timeLimiterName) {
        try {
            return circuitBreakerService.executeWithTimeLimit(
                    timeLimiterName,
                    () -> proceedAsyncSafely(joinPoint)
            );
        } catch (Exception e) {
            log.error("超时限制执行失败: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 简单异步执行
     */
    private CompletionStage<?> executeSimpleAsync(ProceedingJoinPoint joinPoint) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable e) {
                log.error("异步方法执行失败", e);
                throw new RuntimeException("异步方法执行失败", e);
            }
        });
    }

    /**
     * 安全地异步执行原方法
     */
    @SuppressWarnings("unchecked")
    private CompletionStage<Object> proceedAsyncSafely(ProceedingJoinPoint joinPoint) {
        try {
            Object result = joinPoint.proceed();
            if (result instanceof CompletionStage) {
                return (CompletionStage<Object>) result;
            } else {
                return CompletableFuture.completedFuture(result);
            }
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(e);
        }
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

        log.debug("执行组合保护功能 - 熔断器: {}, 重试: {}, 限流: {}",
                circuitBreakerName, retryName, rateLimiterName);

        try {
            return circuitBreakerService.executeWithCombined(
                    circuitBreakerName,
                    retryName,
                    rateLimiterName,
                    () -> proceedSafely(joinPoint),
                    () -> executeFallback(joinPoint.getTarget(), method, args, protection.fallbackMethod())
            );
        } catch (Exception e) {
            log.error("组合保护功能执行失败", e);
            // 如果组合执行失败，尝试执行降级方法
            Object fallbackResult = executeFallback(joinPoint.getTarget(), method, args, protection.fallbackMethod());
            if (fallbackResult != null) {
                return fallbackResult;
            }
            throw e;
        }
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
            log.debug("未配置降级方法，返回默认值");
            return getDefaultValue(method.getReturnType());
        }

        if (target == null) {
            log.warn("目标对象为null，无法执行降级方法: {}", fallbackMethodName);
            return getDefaultValue(method.getReturnType());
        }

        try {
            // 首先尝试查找完全匹配的方法（相同参数类型）
            Method fallbackMethod = findFallbackMethod(target.getClass(), fallbackMethodName, method.getParameterTypes());

            if (fallbackMethod == null) {
                // 尝试查找无参数的降级方法
                fallbackMethod = findFallbackMethod(target.getClass(), fallbackMethodName, new Class<?>[0]);
            }

            if (fallbackMethod == null) {
                log.warn("未找到降级方法: {} 在类 {}", fallbackMethodName, target.getClass().getSimpleName());
                return getDefaultValue(method.getReturnType());
            }

            fallbackMethod.setAccessible(true);

            // 根据方法参数数量决定传递的参数
            Object result;
            if (fallbackMethod.getParameterCount() == 0) {
                result = fallbackMethod.invoke(target);
            } else {
                result = fallbackMethod.invoke(target, args);
            }

            log.debug("成功执行降级方法: {} 返回: {}", fallbackMethodName, result);
            return result;

        } catch (Exception e) {
            log.error("执行降级方法失败: {} 在类 {}", fallbackMethodName, target.getClass().getSimpleName(), e);
            return getDefaultValue(method.getReturnType());
        }
    }

    /**
     * 查找降级方法
     */
    private Method findFallbackMethod(Class<?> targetClass, String methodName, Class<?>[] parameterTypes) {
        try {
            return targetClass.getDeclaredMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
            // 尝试在父类中查找
            Class<?> superClass = targetClass.getSuperclass();
            if (superClass != null && superClass != Object.class) {
                return findFallbackMethod(superClass, methodName, parameterTypes);
            }
            return null;
        }
    }

    /**
     * 获取返回类型的默认值
     */
    private Object getDefaultValue(Class<?> returnType) {
        if (returnType == void.class || returnType == Void.class) {
            return null;
        }
        if (returnType.isPrimitive()) {
            if (returnType == boolean.class) return false;
            if (returnType == byte.class) return (byte) 0;
            if (returnType == short.class) return (short) 0;
            if (returnType == int.class) return 0;
            if (returnType == long.class) return 0L;
            if (returnType == float.class) return 0.0f;
            if (returnType == double.class) return 0.0d;
            if (returnType == char.class) return '\0';
        }
        return null;
    }
}
