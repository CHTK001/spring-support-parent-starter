package com.chua.starter.common.support.debounce;

import org.springframework.util.ConcurrentReferenceHashMap;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 防抖生成器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/10
 */
public class DefaultDebounceLock implements DebounceLock{
    private static final ConcurrentMap<String, Lock> LOCK_MAP = new ConcurrentReferenceHashMap<>(1024);
    private Lock lock;

    @Override
    public boolean tryLock(String key, long timeout) throws InterruptedException {
        this.lock = LOCK_MAP.computeIfAbsent(key, k -> new ReentrantLock());
        return lock.tryLock(timeout, TimeUnit.SECONDS);
    }

    @Override
    public void unlock() {
        lock.unlock();
    }
}

