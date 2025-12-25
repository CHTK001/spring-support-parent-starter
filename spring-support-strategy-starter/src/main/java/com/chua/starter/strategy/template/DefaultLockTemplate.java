package com.chua.starter.strategy.template;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 默认锁模板实现
 * <p>
 * 基于 JVM 内存的 ReentrantLock 实现，适用于单机环境。
 * 分布式环境请使用 Redis 或 Zookeeper 等实现。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-25
 */
@Slf4j
public class DefaultLockTemplate implements LockTemplate {

    private static final Duration DEFAULT_WAIT_TIME = Duration.ofSeconds(10);
    private static final Duration DEFAULT_LEASE_TIME = Duration.ofSeconds(30);

    private final Map<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    /**
     * 获取或创建锁
     *
     * @param lockKey 锁的唯一标识
     * @return 锁对象
     */
    private ReentrantLock getLock(String lockKey) {
        return lockMap.computeIfAbsent(lockKey, k -> new ReentrantLock());
    }

    @Override
    public <T> T lock(String lockKey, Supplier<T> supplier) {
        return lock(lockKey, DEFAULT_LEASE_TIME, supplier);
    }

    @Override
    public <T> T lock(String lockKey, Duration leaseTime, Supplier<T> supplier) {
        ReentrantLock lock = getLock(lockKey);
        lock.lock();
        try {
            log.debug("Acquired lock: {}", lockKey);
            return supplier.get();
        } finally {
            lock.unlock();
            log.debug("Released lock: {}", lockKey);
        }
    }

    @Override
    public <T> T tryLock(String lockKey, Duration waitTime, Supplier<T> supplier) {
        return tryLock(lockKey, waitTime, DEFAULT_LEASE_TIME, supplier);
    }

    @Override
    public <T> T tryLock(String lockKey, Duration waitTime, Duration leaseTime, Supplier<T> supplier) {
        ReentrantLock lock = getLock(lockKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(waitTime.toMillis(), TimeUnit.MILLISECONDS);
            if (acquired) {
                log.debug("Acquired lock: {}", lockKey);
                return supplier.get();
            } else {
                log.warn("Failed to acquire lock: {} within {} ms", lockKey, waitTime.toMillis());
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for lock: {}", lockKey);
            return null;
        } finally {
            if (acquired) {
                lock.unlock();
                log.debug("Released lock: {}", lockKey);
            }
        }
    }

    @Override
    public <T> T tryLock(String lockKey, Duration waitTime, Supplier<T> supplier, Supplier<T> fallback) {
        return tryLock(lockKey, waitTime, DEFAULT_LEASE_TIME, supplier, fallback);
    }

    @Override
    public <T> T tryLock(String lockKey, Duration waitTime, Duration leaseTime, Supplier<T> supplier, Supplier<T> fallback) {
        ReentrantLock lock = getLock(lockKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(waitTime.toMillis(), TimeUnit.MILLISECONDS);
            if (acquired) {
                log.debug("Acquired lock: {}", lockKey);
                return supplier.get();
            } else {
                log.warn("Failed to acquire lock: {}, executing fallback", lockKey);
                return fallback.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for lock: {}, executing fallback", lockKey);
            return fallback.get();
        } finally {
            if (acquired) {
                lock.unlock();
                log.debug("Released lock: {}", lockKey);
            }
        }
    }

    @Override
    public void lock(String lockKey, Runnable action) {
        lock(lockKey, DEFAULT_LEASE_TIME, action);
    }

    @Override
    public void lock(String lockKey, Duration leaseTime, Runnable action) {
        ReentrantLock lock = getLock(lockKey);
        lock.lock();
        try {
            log.debug("Acquired lock: {}", lockKey);
            action.run();
        } finally {
            lock.unlock();
            log.debug("Released lock: {}", lockKey);
        }
    }

    @Override
    public boolean tryLock(String lockKey, Duration waitTime, Runnable action) {
        return tryLock(lockKey, waitTime, DEFAULT_LEASE_TIME, action);
    }

    @Override
    public boolean tryLock(String lockKey, Duration waitTime, Duration leaseTime, Runnable action) {
        ReentrantLock lock = getLock(lockKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(waitTime.toMillis(), TimeUnit.MILLISECONDS);
            if (acquired) {
                log.debug("Acquired lock: {}", lockKey);
                action.run();
                return true;
            } else {
                log.warn("Failed to acquire lock: {} within {} ms", lockKey, waitTime.toMillis());
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for lock: {}", lockKey);
            return false;
        } finally {
            if (acquired) {
                lock.unlock();
                log.debug("Released lock: {}", lockKey);
            }
        }
    }

    @Override
    public boolean acquire(String lockKey) {
        return acquire(lockKey, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME);
    }

    @Override
    public boolean acquire(String lockKey, Duration waitTime, Duration leaseTime) {
        ReentrantLock lock = getLock(lockKey);
        try {
            boolean acquired = lock.tryLock(waitTime.toMillis(), TimeUnit.MILLISECONDS);
            if (acquired) {
                log.debug("Manually acquired lock: {}", lockKey);
            } else {
                log.warn("Failed to manually acquire lock: {}", lockKey);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while manually acquiring lock: {}", lockKey);
            return false;
        }
    }

    @Override
    public void release(String lockKey) {
        ReentrantLock lock = lockMap.get(lockKey);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("Manually released lock: {}", lockKey);
        }
    }

    @Override
    public boolean isLocked(String lockKey) {
        ReentrantLock lock = lockMap.get(lockKey);
        return lock != null && lock.isLocked();
    }

    @Override
    public boolean isHeldByCurrentThread(String lockKey) {
        ReentrantLock lock = lockMap.get(lockKey);
        return lock != null && lock.isHeldByCurrentThread();
    }
}
