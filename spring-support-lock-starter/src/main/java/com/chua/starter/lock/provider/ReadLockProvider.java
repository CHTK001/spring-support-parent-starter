package com.chua.starter.lock.provider;

import com.chua.common.support.concurrent.lock.AbstractLockProvider;
import com.chua.common.support.concurrent.lock.LockProvider;
import com.chua.common.support.concurrent.lock.LockSetting;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 读锁提供者。
 *
 * @author CH
 * @since 2026-03-28
 */
public class ReadLockProvider extends AbstractLockProvider {

    private static final Map<String, ReentrantReadWriteLock> LOCKS = new ConcurrentHashMap<>();

    public ReadLockProvider() {
        this("default", false);
    }

    public ReadLockProvider(String name, boolean fair) {
        super(name, lock(name, fair).readLock());
    }

    @Override
    public String getType() {
        return "read";
    }

    @Override
    public LockProvider create(String name, LockSetting config) {
        return new ReadLockProvider(name, config.isFair());
    }

    private static ReentrantReadWriteLock lock(String name, boolean fair) {
        return LOCKS.computeIfAbsent(name + ":" + fair, key -> new ReentrantReadWriteLock(fair));
    }
}
