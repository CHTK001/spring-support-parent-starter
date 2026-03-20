package com.chua.starter.queue.limiter;

import com.chua.starter.queue.Message;

/**
 * 消息限流器接口
 * <p>
 * 控制消息处理速率，防止系统过载
 * </p>
 *
 * @author CH
 * @since 2025-03-20
 */
public interface MessageRateLimiter {

    /**
     * 尝试获取许可
     * <p>
     * 如果获取成功返回true，否则返回false
     * </p>
     *
     * @param message 消息
     * @return 是否获取成功
     */
    boolean tryAcquire(Message message);

    /**
     * 获取许可（阻塞）
     * <p>
     * 如果没有可用许可，会阻塞直到获取成功
     * </p>
     *
     * @param message 消息
     */
    void acquire(Message message);

    /**
     * 释放许可
     *
     * @param message 消息
     */
    default void release(Message message) {
        // 默认不需要释放
    }

    /**
     * 获取当前可用许可数
     *
     * @return 可用许可数
     */
    int availablePermits();

    /**
     * 重置限流器
     */
    void reset();
}
