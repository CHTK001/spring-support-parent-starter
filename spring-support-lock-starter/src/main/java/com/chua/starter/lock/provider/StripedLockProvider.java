package com.chua.starter.lock.provider;

import com.chua.common.support.concurrent.lock.AbstractLockProvider;
import com.chua.common.support.concurrent.lock.LockProvider;
import com.chua.common.support.concurrent.lock.LockSetting;
import com.google.common.util.concurrent.Striped;

import java.util.concurrent.locks.Lock;

/**
 * 基于 Guava Striped 的轻量分段锁。
 *
 * @author CH
 * @since 2026-03-28
 */
public class StripedLockProvider extends AbstractLockProvider {

    private static final Striped<Lock> STRIPED_LOCKS = Striped.lock(1024);

    public StripedLockProvider() {
        this("default");
    }

    public StripedLockProvider(String name) {
        super(name, STRIPED_LOCKS.get(name));
    }

    @Override
    public String getType() {
        return "striped";
    }

    @Override
    public LockProvider create(String name, LockSetting config) {
        return new StripedLockProvider(name);
    }
}
