package com.chua.starter.lock.aspect;

import com.chua.common.support.concurrent.lock.LockProvider;
import com.chua.common.support.concurrent.lock.LockSetting;
import com.chua.common.support.concurrent.lock.Locked;
import com.chua.common.support.math.unit.TimeUnit;
import com.chua.starter.lock.exception.LockAcquireException;
import com.chua.starter.lock.support.LockProviderFactory;
import com.chua.starter.lock.support.MethodInvocationSupport;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 锁切面，复用 utils-support 的锁能力并补齐 Spring AOP 适配。
 *
 * @author CH
 * @since 2026-03-28
 */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class LockedAspect {

    private final LockProviderFactory lockProviderFactory;

    public LockedAspect(LockProviderFactory lockProviderFactory) {
        this.lockProviderFactory = lockProviderFactory;
    }

    @Around("@annotation(locked)")
    public Object around(ProceedingJoinPoint joinPoint, Locked locked) throws Throwable {
        Method method = MethodInvocationSupport.resolveMethod(joinPoint);
        String lockName = resolveLockName(joinPoint, method, locked);
        long waitTime = parseTime(locked.waitTime());
        long leaseTime = parseTime(locked.leaseTime());

        LockSetting lockSetting = LockSetting.builder()
                .name(lockName)
                .lockType(resolveLockType(locked))
                .fair(locked.type() == Locked.LockType.FAIR)
                .waitTime(waitTime)
                .leaseTime(leaseTime)
                .reentrant(true)
                .build();

        LockProvider lockProvider = lockProviderFactory.createLock(lockName, lockSetting);
        boolean lockedState = false;
        try {
            lockedState = lockProvider.tryLock(toTimeout(waitTime), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!lockedState) {
                if (locked.throwException()) {
                    throw new LockAcquireException("无法获取锁: " + lockName);
                }
                if (StringUtils.hasText(locked.fallbackMethod())) {
                    return MethodInvocationSupport.invokeFallback(joinPoint, method, locked.fallbackMethod());
                }
                return null;
            }
            return joinPoint.proceed();
        } finally {
            if (lockedState) {
                unlockQuietly(lockProvider);
            }
        }
    }

    private String resolveLockName(ProceedingJoinPoint joinPoint, Method method, Locked locked) {
        String baseName = StringUtils.hasText(locked.name())
                ? MethodInvocationSupport.evaluateToString(locked.name(), joinPoint, method)
                : MethodInvocationSupport.buildMethodSignature(method);

        if (!StringUtils.hasText(baseName)) {
            baseName = MethodInvocationSupport.buildMethodSignature(method);
        }

        List<String> parts = new ArrayList<>();
        parts.add(baseName);
        for (String key : locked.keys()) {
            String value = MethodInvocationSupport.evaluateToString(key, joinPoint, method);
            if (StringUtils.hasText(value)) {
                parts.add(value);
            }
        }

        return String.join(":", parts);
    }

    private String resolveLockType(Locked locked) {
        return switch (locked.type()) {
            case FAIR -> "fair";
            case READ -> "read";
            case WRITE -> "write";
            case STRIPED, CACHE -> "striped";
            default -> locked.type().name().toLowerCase(Locale.ROOT);
        };
    }

    private long parseTime(String value) {
        if (!StringUtils.hasText(value)) {
            return 0L;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return TimeUnit.parse(value).toMillis();
        }
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
