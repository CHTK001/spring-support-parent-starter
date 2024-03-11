package com.chua.starter.common.support.debounce;

/**
 * 防抖生成器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/10
 */
public interface DebounceLock {

    /**
     * 尝试锁定
     *
     * @param timeout 超时
     * @param key     钥匙
     * @return boolean
     */
    boolean tryLock(String key, long timeout) throws InterruptedException, Exception;

    /**
     * 解锁
     */
    void unlock();
}

