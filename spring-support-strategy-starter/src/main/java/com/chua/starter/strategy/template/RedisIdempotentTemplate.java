package com.chua.starter.strategy.template;

import com.chua.starter.strategy.support.StrategyRedisSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * Redis 分布式幂等性实现。
 * <p>
 * 基于 {@link StrategyRedisSupport#setIfAbsent} SETNX + EXPIRE 原子语义，
 * 适合多实例部署场景，需引入 spring-boot-starter-data-redis 依赖。
 * </p>
 *
 * @author CH
 * @since 2026-05-07
 */
@Slf4j
@RequiredArgsConstructor
public class RedisIdempotentTemplate implements IdempotentTemplate {

    private final StrategyRedisSupport strategyRedisSupport;

    @Override
    public boolean tryAcquire(String key, long timeout, TimeUnit unit) {
        if (!strategyRedisSupport.isAvailable()) {
            log.warn("Redis 不可用，幂等检查降级为允许通过: key={}", key);
            return true;
        }
        boolean acquired = strategyRedisSupport.setIfAbsent(
                key, String.valueOf(System.currentTimeMillis()), timeout, unit);
        if (!acquired) {
            log.info("幂等拦截重复请求: key={}", key);
        }
        return acquired;
    }

    @Override
    public void complete(String key, boolean releaseOnFinish) {
        if (releaseOnFinish) {
            release(key);
        }
    }

    @Override
    public void release(String key) {
        if (strategyRedisSupport.isAvailable()) {
            strategyRedisSupport.delete(key);
            log.debug("幂等键已释放: key={}", key);
        }
    }
}
