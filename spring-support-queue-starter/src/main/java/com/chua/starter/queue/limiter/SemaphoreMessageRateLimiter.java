package com.chua.starter.queue.limiter;

import com.chua.starter.queue.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 基于信号量的消息限流器
 * <p>
 * 使用信号量控制并发消息处理数量
 * </p>
 *
 * @since 2025-03-20
 * @author CH
 */
@Slf4j
@Component
public class SemaphoreMessageRateLimiter implements MessageRateLimiter {

    private final ConcurrentHashMap<String, Semaphore> semaphores = new ConcurrentHashMap<>(); // semaphores
    /**
     * 默认_许可证
    */
    private static final int DEFAULT_PERMITS = 100;

    @Override
    /**
     * 尝试获取
     * @param message 消息
     * @return 返回值
    */
    public boolean tryAcquire(Message message) {
        Semaphore semaphore = getSemaphore(message.getDestination());
        boolean acquired = semaphore.tryAcquire();

        if (!acquired) {
            log.warn("[Queue] 限流拒绝: destination={}, 当前可用许可={}",
                message.getDestination(), semaphore.availablePermits());
        }

        return acquired;
    }

    @Override
    /**
     * 获取
     * @param message 消息
     * @return 返回值
    */
    public void acquire(Message message) {
        Semaphore semaphore = getSemaphore(message.getDestination());
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            /**
             * runtime异常。
             * @param "获取限流许可被中断" "获取限流许可被中断"
             * @param e e
             * @return runtime异常的结果
             */
            throw new RuntimeException("获取限流许可被中断", e);
        }
    }

    @Override
    /**
     * 释放
     * @param message 消息
     * @return 返回值
    */
    public void release(Message message) {
        Semaphore semaphore = getSemaphore(message.getDestination());
        semaphore.release();
    }

    @Override
    /**
     * 可用许可证
     * @return 返回值
    */
    public int availablePermits() {
        return semaphores.values().stream()
            .mapToInt(Semaphore::availablePermits)
            .sum();
    }

    @Override
    /**
     * 重置
    */
    public void reset() {
        semaphores.clear();
    }

    /**
     * 获取或创建信号量
     * @param destination 目标
     * @return 获取semaphore的结果
     */
    private Semaphore getSemaphore(String destination) {
        return semaphores.computeIfAbsent(destination, k -> new Semaphore(DEFAULT_PERMITS));
    }

    /**
     * 设置目标队列的许可数
     * @param destination 目标
     * @param permits 许可证
     */
    public void setPermits(String destination, int permits) {
        semaphores.put(destination, new Semaphore(permits));
    }
}
