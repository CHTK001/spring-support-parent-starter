package com.chua.starter.redis.support.lock;

import com.chua.starter.redis.support.template.LockTemplate;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 无 Redis 环境下的本地锁模板兜底实现。
 * @author CH
 */
@Slf4j
public class NoopLockTemplate implements LockTemplate {

    @Override
    /** 锁 */
    public <T> T lock(String lockKey, Supplier<T> supplier) {
        return supplier.get();
    }

    @Override
    /** 锁 */
    public <T> T lock(String lockKey, Duration leaseTime, Supplier<T> supplier) {
        return supplier.get();
    }

    @Override
    /** Try锁 */
    public <T> T tryLock(String lockKey, Duration waitTime, Supplier<T> supplier) {
        return supplier.get();
    }

    @Override
    /** Try锁 */
    public <T> T tryLock(String lockKey, Duration waitTime, Duration leaseTime, Supplier<T> supplier) {
        return supplier.get();
    }

    @Override
    /** Try锁 */
    public <T> T tryLock(String lockKey, Duration waitTime, Supplier<T> supplier, Supplier<T> fallback) {
        return supplier.get();
    }

    @Override
    /** Try锁 */
    public <T> T tryLock(String lockKey, Duration waitTime, Duration leaseTime, Supplier<T> supplier, Supplier<T> fallback) {
        return supplier.get();
    }

    @Override
    /** 锁 */
    public void lock(String lockKey, Runnable action) {
        action.run();
    }

    @Override
    /** 锁 */
    public void lock(String lockKey, Duration leaseTime, Runnable action) {
        action.run();
    }

    @Override
    /** Try锁 */
    public boolean tryLock(String lockKey, Duration waitTime, Runnable action) {
        action.run();
        return true;
    }

    @Override
    /** Try锁 */
    public boolean tryLock(String lockKey, Duration waitTime, Duration leaseTime, Runnable action) {
        action.run();
        return true;
    }

    @Override
    /** 获取 */
    public boolean acquire(String lockKey) {
        return true;
    }

    @Override
    /** 获取 */
    public boolean acquire(String lockKey, Duration waitTime, Duration leaseTime) {
        return true;
    }

    @Override
    /** 释放 */
    public void release(String lockKey) {
        log.debug("Skip releasing local noop lock: {}", lockKey);
    }

    @Override
    /** 是否Locked */
    public boolean isLocked(String lockKey) {
        return false;
    }

    @Override
    /** 是否HeldByCurrentThread */
    public boolean isHeldByCurrentThread(String lockKey) {
        return false;
    }
}

