package com.chua.starter.strategy.aspect;

import com.chua.starter.strategy.annotation.DistributedLock;
import com.chua.starter.strategy.exception.LockAcquireException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁切面
 * <p>
 * 基于Redis实现分布式锁，支持：
 * - SpEL表达式解析key
 * - 可配置的等待时间和持有时间
 * - 多种失败策略（异常、降级、返回null、静默）
 * - 公平锁和非公平锁
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class DistributedLockAspect {

    private static final String LOCK_SUCCESS = "OK";

    /**
     * 解锁Lua脚本
     * <p>
     * 确保只有锁的持有者才能释放锁
     * </p>
     */
    private static final String UNLOCK_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """;

    private final ExpressionParser parser = new SpelExpressionParser();

    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        // 检查Redis是否可用
        if (stringRedisTemplate == null) {
            log.warn("StringRedisTemplate未配置，跳过分布式锁");
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 解析锁的key
        String lockKey = parseLockKey(distributedLock, method, joinPoint.getArgs());
        String lockValue = UUID.randomUUID().toString();

        boolean locked = false;
        try {
            // 尝试获取锁
            locked = tryAcquireLock(lockKey, lockValue, distributedLock);

            if (locked) {
                log.debug("获取分布式锁成功: key={}", lockKey);
                return joinPoint.proceed();
            } else {
                log.warn("获取分布式锁失败: key={}", lockKey);
                return handleLockFailure(joinPoint, distributedLock, method);
            }
        } finally {
            if (locked) {
                releaseLock(lockKey, lockValue);
            }
        }
    }

    /**
     * 解析锁的key
     *
     * @param lock   注解
     * @param method 方法
     * @param args   参数
     * @return 完整的锁key
     */
    private String parseLockKey(DistributedLock lock, Method method, Object[] args) {
        String keyExpression = lock.key();
        String prefix = lock.prefix();

        // 如果不是SpEL表达式，直接返回
        if (!keyExpression.contains("#")) {
            return prefix + keyExpression;
        }

        // 使用SpEL解析
        EvaluationContext context = new MethodBasedEvaluationContext(
                null, method, args, parameterNameDiscoverer);

        String parsedKey = parser.parseExpression(keyExpression).getValue(context, String.class);

        if (!StringUtils.hasText(parsedKey)) {
            throw new IllegalArgumentException("分布式锁key解析结果为空: " + keyExpression);
        }

        return prefix + parsedKey;
    }

    /**
     * 尝试获取锁
     *
     * @param lockKey   锁key
     * @param lockValue 锁value
     * @param lock      注解配置
     * @return 是否获取成功
     */
    private boolean tryAcquireLock(String lockKey, String lockValue, DistributedLock lock) {
        long waitTime = lock.waitTime();
        long leaseTime = lock.leaseTime();
        TimeUnit timeUnit = lock.timeUnit();

        long waitMillis = timeUnit.toMillis(waitTime);
        long leaseMillis = timeUnit.toMillis(leaseTime);

        long startTime = System.currentTimeMillis();
        long sleepTime = Math.min(100L, waitMillis / 10);

        do {
            Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(
                    lockKey, lockValue, leaseMillis, TimeUnit.MILLISECONDS);

            if (Boolean.TRUE.equals(success)) {
                return true;
            }

            if (waitMillis <= 0) {
                return false;
            }

            // 等待重试
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }

        } while (System.currentTimeMillis() - startTime < waitMillis);

        return false;
    }

    /**
     * 释放锁
     *
     * @param lockKey   锁key
     * @param lockValue 锁value
     */
    private void releaseLock(String lockKey, String lockValue) {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(UNLOCK_SCRIPT);
            script.setResultType(Long.class);

            Long result = stringRedisTemplate.execute(
                    script, Collections.singletonList(lockKey), lockValue);

            if (result != null && result > 0) {
                log.debug("释放分布式锁成功: key={}", lockKey);
            } else {
                log.warn("释放分布式锁失败（锁可能已过期）: key={}", lockKey);
            }
        } catch (Exception e) {
            log.error("释放分布式锁异常: key={}", lockKey, e);
        }
    }

    /**
     * 处理获取锁失败的情况
     *
     * @param joinPoint 切点
     * @param lock      注解配置
     * @param method    方法
     * @return 处理结果
     * @throws Throwable 异常
     */
    private Object handleLockFailure(ProceedingJoinPoint joinPoint, DistributedLock lock,
                                      Method method) throws Throwable {
        DistributedLock.LockFailStrategy strategy = lock.failStrategy();

        return switch (strategy) {
            case EXCEPTION -> throw new LockAcquireException(lock.errorMessage());
            case FALLBACK -> invokeFallback(joinPoint, lock, method);
            case RETURN_NULL -> null;
            case SILENT -> {
                log.info("分布式锁获取失败，静默处理: method={}", method.getName());
                yield null;
            }
        };
    }

    /**
     * 调用降级方法
     *
     * @param joinPoint 切点
     * @param lock      注解配置
     * @param method    原方法
     * @return 降级方法返回值
     * @throws Throwable 异常
     */
    private Object invokeFallback(ProceedingJoinPoint joinPoint, DistributedLock lock,
                                  Method method) throws Throwable {
        String fallbackMethodName = lock.fallbackMethod();

        if (!StringUtils.hasText(fallbackMethodName)) {
            throw new LockAcquireException("未配置降级方法: " + method.getName());
        }

        Object target = joinPoint.getTarget();
        Class<?> targetClass = target.getClass();

        try {
            Method fallbackMethod = targetClass.getDeclaredMethod(
                    fallbackMethodName, method.getParameterTypes());
            fallbackMethod.setAccessible(true);
            return fallbackMethod.invoke(target, joinPoint.getArgs());
        } catch (NoSuchMethodException e) {
            throw new LockAcquireException("降级方法不存在: " + fallbackMethodName, e);
        }
    }
}
