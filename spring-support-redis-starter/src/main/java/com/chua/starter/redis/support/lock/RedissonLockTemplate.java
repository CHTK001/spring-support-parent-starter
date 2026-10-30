package com.chua.starter.redis.support.lock;

import com.chua.starter.redis.support.template.LockTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 基于 Redisson 的分布式锁模板实现
 * <p>
 * 使用 Redis 实现分布式锁，适用于分布式环境。
 * </p>
 *
 * @since 2025-12-25
 * @author CH
 */
@Slf4j
@RequiredArgsConstructor
public class RedissonLockTemplate implements LockTemplate {

    /**
     * 锁_前缀
    */
    private static final String LOCK_PREFIX = "lock:";
    /**
     * 默认_wait_时间
    */
    private static final Duration DEFAULT_WAIT_TIME = Duration.ofSeconds(10);
    /**
     * 默认_lease_时间
    */
    private static final Duration DEFAULT_LEASE_TIME = Duration.ofSeconds(30);

    /**
     * Redisson客户端
    */
    private final RedissonClient redissonClient;

    /**
     * 获取锁的完整Key
     *
     * @param lockKey 锁的唯一标识
     * @return 完整的锁key
     */
    private String getLockKey(String lockKey) {
        return LOCK_PREFIX + lockKey;
    }

    /**
     * 获取 Redisson 锁对象
     *
     * @param lockKey 锁的唯一标识
     * @return RLock 对象
     */
    private RLock getLock(String lockKey) {
        return redissonClient.getLock(getLockKey(lockKey));
    }

    @Override
    /**
     * 锁
    */
    public <T> T lock(String lockKey, Supplier<T> supplier) {
        return lock(lockKey, DEFAULT_LEASE_TIME, supplier);
    }

    @Override
    /**
     * 锁
    */
    public <T> T lock(String lockKey, Duration leaseTime, Supplier<T> supplier) {
        RLock lock = getLock(lockKey);
        try {
            lock.lock(leaseTime.toMillis(), TimeUnit.MILLISECONDS);
            log.debug("Acquired distributed lock: {}", lockKey);
            return supplier.get();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Released distributed lock: {}", lockKey);
            }
        }
    }

    @Override
    /**
     * 尝试锁
    */
    public <T> T tryLock(String lockKey, Duration waitTime, Supplier<T> supplier) {
        return tryLock(lockKey, waitTime, DEFAULT_LEASE_TIME, supplier);
    }

    @Override
    /**
     * 尝试锁
    */
    public <T> T tryLock(String lockKey, Duration waitTime, Duration leaseTime, Supplier<T> supplier) {
        RLock lock = getLock(lockKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(waitTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS);
            if (acquired) {
                log.debug("Acquired distributed lock: {}", lockKey);
                return supplier.get();
            } else {
                log.warn("Failed to acquire distributed lock: {} within {} ms", lockKey, waitTime.toMillis());
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for distributed lock: {}", lockKey);
            return null;
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Released distributed lock: {}", lockKey);
            }
        }
    }

    @Override
    /**
     * 尝试锁
    */
    public <T> T tryLock(String lockKey, Duration waitTime, Supplier<T> supplier, Supplier<T> fallback) {
        return tryLock(lockKey, waitTime, DEFAULT_LEASE_TIME, supplier, fallback);
    }

    @Override
    /**
     * 尝试锁
    */
    public <T> T tryLock(String lockKey, Duration waitTime, Duration leaseTime, Supplier<T> supplier, Supplier<T> fallback) {
        RLock lock = getLock(lockKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(waitTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS);
            if (acquired) {
                log.debug("Acquired distributed lock: {}", lockKey);
                return supplier.get();
            } else {
                log.warn("Failed to acquire distributed lock: {}, executing fallback", lockKey);
                return fallback.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for distributed lock: {}, executing fallback", lockKey);
            return fallback.get();
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Released distributed lock: {}", lockKey);
            }
        }
    }

    @Override
    /**
     * 锁
     * @param action 可运行任务
     * @param lockKey 字符串
     * @return 返回值
    */
    public void lock(String lockKey, Runnable action) {
        /**
         * 锁。
         * @param lockKey 锁Key
         * @param DEFAULT_LEASE_TIME 默认lease时间
         * @param action 动作
         */
        lock(lockKey, DEFAULT_LEASE_TIME, action);
    }

    @Override
    /**
     * 锁
     * @param action 可运行任务
     * @param leaseTime 参数
     * @param lockKey 字符串
     * @return 返回值
    */
    public void lock(String lockKey, Duration leaseTime, Runnable action) {
        RLock lock = getLock(lockKey);
        try {
            lock.lock(leaseTime.toMillis(), TimeUnit.MILLISECONDS);
            log.debug("Acquired distributed lock: {}", lockKey);
            action.run();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Released distributed lock: {}", lockKey);
            }
        }
    }

    @Override
    /**
     * 尝试锁
     * @param action 可运行任务
     * @param lockKey 字符串
     * @param waitTime 参数
     * @return 返回值
    */
    public boolean tryLock(String lockKey, Duration waitTime, Runnable action) {
        return tryLock(lockKey, waitTime, DEFAULT_LEASE_TIME, action);
    }

    @Override
    /**
     * 尝试锁
     * @param action 可运行任务
     * @param leaseTime 参数
     * @param lockKey 字符串
     * @param waitTime 参数
     * @return 返回值
    */
    public boolean tryLock(String lockKey, Duration waitTime, Duration leaseTime, Runnable action) {
        RLock lock = getLock(lockKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(waitTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS);
            if (acquired) {
                log.debug("Acquired distributed lock: {}", lockKey);
                action.run();
                return true;
            } else {
                log.warn("Failed to acquire distributed lock: {} within {} ms", lockKey, waitTime.toMillis());
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for distributed lock: {}", lockKey);
            return false;
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Released distributed lock: {}", lockKey);
            }
        }
    }

    @Override
    /**
     * 获取
     * @param lockKey 字符串
     * @return 返回值
    */
    public boolean acquire(String lockKey) {
        return acquire(lockKey, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME);
    }

    @Override
    /**
     * 获取
     * @param leaseTime 参数
     * @param lockKey 字符串
     * @param waitTime 参数
     * @return 返回值
    */
    public boolean acquire(String lockKey, Duration waitTime, Duration leaseTime) {
        RLock lock = getLock(lockKey);
        try {
            boolean acquired = lock.tryLock(waitTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS);
            if (acquired) {
                log.debug("Manually acquired distributed lock: {}", lockKey);
            } else {
                log.warn("Failed to manually acquire distributed lock: {}", lockKey);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while manually acquiring distributed lock: {}", lockKey);
            return false;
        }
    }

    @Override
    /**
     * 释放
     * @param lockKey 字符串
     * @return 返回值
    */
    public void release(String lockKey) {
        RLock lock = getLock(lockKey);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("Manually released distributed lock: {}", lockKey);
        }
    }

    @Override
    /**
     * 是否锁
     * @param lockKey 字符串
     * @return 返回值
    */
    public boolean isLocked(String lockKey) {
        RLock lock = getLock(lockKey);
        return lock.isLocked();
    }

    @Override
    /**
     * 是否heldby当前thread
     * @param lockKey 字符串
     * @return 返回值
    */
    public boolean isHeldByCurrentThread(String lockKey) {
        RLock lock = getLock(lockKey);
        return lock.isHeldByCurrentThread();
    }
}
