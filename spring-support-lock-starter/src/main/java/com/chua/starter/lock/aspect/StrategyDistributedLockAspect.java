package com.chua.starter.lock.aspect;

import com.chua.common.support.concurrent.lock.LockProvider;
import com.chua.common.support.concurrent.lock.LockSetting;
import com.chua.starter.lock.exception.LockAcquireException;
import com.chua.starter.lock.properties.LockProperties;
import com.chua.starter.lock.support.AnnotationAttributeSupport;
import com.chua.starter.lock.support.LockProviderFactory;
import com.chua.starter.lock.support.MethodInvocationSupport;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 兼容 strategy 模块旧版 {@code @DistributedLock} 注解。
 *
 * @author CH
 * @since 2026-03-28
 */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 90)
public class StrategyDistributedLockAspect {

    private static final String ANNOTATION_NAME = "com.chua.starter.strategy.annotation.DistributedLock";

    private final LockProviderFactory lockProviderFactory;
    private final LockProperties lockProperties;

    public StrategyDistributedLockAspect(LockProviderFactory lockProviderFactory, LockProperties lockProperties) {
        this.lockProviderFactory = lockProviderFactory;
        this.lockProperties = lockProperties;
    }

    @Around("@annotation(com.chua.starter.strategy.annotation.DistributedLock)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = MethodInvocationSupport.resolveMethod(joinPoint);
        Annotation annotation = AnnotationAttributeSupport.findAnnotation(method, ANNOTATION_NAME);
        if (annotation == null) {
            return joinPoint.proceed();
        }

        Map<String, Object> attributes = AnnotationAttributeSupport.attributes(annotation);
        TimeUnit timeUnit = (TimeUnit) attributes.getOrDefault("timeUnit", TimeUnit.SECONDS);
        boolean fair = AnnotationAttributeSupport.getBoolean(attributes, "fair", false);
        String key = AnnotationAttributeSupport.getString(attributes, "key", "");
        String prefix = AnnotationAttributeSupport.getString(attributes, "prefix", "");
        String fallbackMethod = AnnotationAttributeSupport.getString(attributes, "fallbackMethod", "");
        String errorMessage = AnnotationAttributeSupport.getString(attributes, "errorMessage", "获取分布式锁失败，请稍后重试");
        String failStrategy = AnnotationAttributeSupport.getEnumName(attributes, "failStrategy", "EXCEPTION");

        String lockName = resolveLockName(joinPoint, method, prefix, key);
        LockSetting lockSetting = LockSetting.builder()
                .name(lockName)
                .lockType(resolveLockType(fair))
                .fair(fair)
                .waitTime(timeUnit.toMillis(AnnotationAttributeSupport.getLong(attributes, "waitTime", 0L)))
                .leaseTime(timeUnit.toMillis(AnnotationAttributeSupport.getLong(attributes, "leaseTime", -1L)))
                .reentrant(true)
                .build();

        LockProvider lockProvider = lockProviderFactory.createLock(lockName, lockSetting);
        boolean lockedState = false;
        try {
            lockedState = lockProvider.tryLock(toTimeout(lockSetting.getWaitTime()), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!lockedState) {
                return handleFailure(joinPoint, method, fallbackMethod, errorMessage, failStrategy);
            }
            return joinPoint.proceed();
        } finally {
            if (lockedState) {
                unlockQuietly(lockProvider);
            }
        }
    }

    private String resolveLockName(ProceedingJoinPoint joinPoint, Method method, String prefix, String keyExpression) {
        String key = MethodInvocationSupport.evaluateToString(keyExpression, joinPoint, method);
        if (!StringUtils.hasText(key)) {
            key = MethodInvocationSupport.buildMethodSignature(method);
        }
        return prefix + key;
    }

    private Object handleFailure(ProceedingJoinPoint joinPoint, Method method, String fallbackMethod,
                                 String errorMessage, String failStrategy) throws Throwable {
        return switch (failStrategy) {
            case "FALLBACK" -> MethodInvocationSupport.invokeFallback(joinPoint, method, fallbackMethod);
            case "RETURN_NULL", "SILENT" -> null;
            default -> throw new LockAcquireException(errorMessage);
        };
    }

    private String resolveLockType(boolean fair) {
        if (fair) {
            return "fair";
        }
        return lockProperties.getCompatibility().getStrategyLockType();
    }

    private int toTimeout(long waitTime) {
        if (waitTime <= 0) {
            return 0;
        }
        return (int) Math.min(waitTime, Integer.MAX_VALUE);
    }

    private void unlockQuietly(LockProvider lockProvider) {
        try {
            lockProvider.unlock();
        } catch (IllegalMonitorStateException ignored) {
        }
    }
}
