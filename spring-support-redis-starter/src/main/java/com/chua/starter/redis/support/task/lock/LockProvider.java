package com.chua.starter.redis.support.task.lock;

import java.util.concurrent.TimeUnit;

/**
 * 锁提供者接口
 *
 * @author CH
 * @since 2024/12/25
 */
public interface LockProvider {

    /**
     * 尝试获取锁
     *
     * @param timeout  超时时间
     * @param timeUnit 时间单位
     * @return 是否成功获取锁
     */
    boolean tryLock(int timeout, TimeUnit timeUnit);

    /**
     * 释放锁
     */
    void unlock();

    /**
     * 获取锁提供者名称
     *
     * @return 名称
     */
    String getName();

    /**
     * 获取锁提供者类型
     *
     * @return 类型
     */
    String getType();
}

