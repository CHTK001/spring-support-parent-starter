package com.chua.starter.lock.provider;

import com.chua.common.support.concurrent.lock.AbstractLockProvider;
import com.chua.common.support.concurrent.lock.LockProvider;
import com.chua.common.support.concurrent.lock.LockSetting;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 写锁提供者。
 *
 * @author CH
 * @since 2026-03-28
 */
public class WriteLockProvider extends AbstractLockProvider {

    private static final Map<String, ReentrantReadWriteLock> LOCKS = new ConcurrentHashMap<>();

    public WriteLockProvider() {
        this("default", false);
    }

    public WriteLockProvider(String name, boolean fair) {
        super(name, lock(name, fair).writeLock());
    }

    @Override
    public String getType() {
        return "write";
    }

    @Override
    public LockProvider create(String name, LockSetting config) {
        return new WriteLockProvider(name, config.isFair());
    }

    private static ReentrantReadWriteLock lock(String name, boolean fair) {
        return LOCKS.computeIfAbsent(name + ":" + fair, key -> new ReentrantReadWriteLock(fair));
    }
}
