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
 * @author CH
 * @since 2025-03-20
 */
@Slf4j
@Component
public class SemaphoreMessageRateLimiter implements MessageRateLimiter {

    private final ConcurrentHashMap<String, Semaphore> semaphores = new ConcurrentHashMap<>();
    private static final int DEFAULT_PERMITS = 100;

    @Override
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
    public void acquire(Message message) {
        Semaphore semaphore = getSemaphore(message.getDestination());
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取限流许可被中断", e);
        }
    }

    @Override
    public void release(Message message) {
        Semaphore semaphore = getSemaphore(message.getDestination());
        semaphore.release();
    }

    @Override
    public int availablePermits() {
        return semaphores.values().stream()
            .mapToInt(Semaphore::availablePermits)
            .sum();
    }

    @Override
    public void reset() {
        semaphores.clear();
    }

    /**
     * 获取或创建信号量
     */
    private Semaphore getSemaphore(String destination) {
        return semaphores.computeIfAbsent(destination, k -> new Semaphore(DEFAULT_PERMITS));
    }

    /**
     * 设置目标队列的许可数
     */
    public void setPermits(String destination, int permits) {
        semaphores.put(destination, new Semaphore(permits));
    }
}
