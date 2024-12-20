package com.chua.starter.common.support.lock;

import com.chua.common.support.lang.lock.FileSystemLock;
import com.chua.common.support.lang.lock.Lock;
import com.chua.common.support.lang.lock.ObjectLock;
import com.chua.common.support.spi.ServiceProvider;

import java.nio.channels.FileLock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 锁工厂
 * @author CH
 * @since 2024/12/20
 */
public class LockFactory {

    private final Map<String, Lock> lockMap = new ConcurrentHashMap<>();

    public static final LockFactory INSTANCE = new LockFactory();
    private LockFactory() {}

    /**
     * 创建锁
     * @param key key
     * @param lockType 锁类型
     * @return Lock
     */
    public Lock newLock(String key, LockType lockType) {
        return lockMap.computeIfAbsent(key, it -> createLock(key, lockType));
    }


    /**
     * 创建锁
     *
     * @param key      key
     * @param lockType 锁类型
     * @return Lock
     */
    protected Lock createLock(String key, LockType lockType) {
        return ServiceProvider.of(Lock.class).getNewExtension(lockType,  key);
    }
    /**
     * 获取实例
     * @return LockFactory
     */
    public static LockFactory getInstance() {
        return INSTANCE;
    }

    /**
     * 锁类型
     */
    public static enum LockType {

        /**
         * 无锁
         */
        NONE,
        /**
         * 对象锁
         */
        OBJECT,

        /**
         * 文件锁
         */
        FILE,

        /**
         * redis锁
         */
        REDIS,

        /**
         * redisson锁
         */
        REDISSON
    }
}
