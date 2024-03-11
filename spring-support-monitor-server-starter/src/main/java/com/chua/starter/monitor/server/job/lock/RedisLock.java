package com.chua.starter.monitor.server.job.lock;

import org.redisson.api.RLock;

/**
 * redis锁
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 */
public class RedisLock implements JobLock{
    private final RLock lock;

    public RedisLock(RLock lock) {
        this.lock = lock;
    }

    @Override
    public void close() throws Exception {
        lock.unlock();
    }

    @Override
    public void lock() {
        lock.lock();
    }

    @Override
    public void unlock() {
        try {
            close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
