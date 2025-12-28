package com.chua.starter.strategy.distributed;

import com.chua.common.support.annotations.Extension;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 基于内存的本地限流器
 * <p>
 * 使用滑动窗口算法实现本地限流，适用于单机场景。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Slf4j
@Extension("local")
public class LocalRateLimiter implements StrategyRateLimiter {

    /**
     * 滑动窗口数据结构
     */
    private static class SlidingWindow {
        private final int limitForPeriod;
        private final long windowSizeMillis;
        private final AtomicLong windowStart;
        private final AtomicLong counter;
        private final ReentrantLock lock = new ReentrantLock();

        SlidingWindow(int limitForPeriod, int periodSeconds) {
            this.limitForPeriod = limitForPeriod;
            this.windowSizeMillis = periodSeconds * 1000L;
            this.windowStart = new AtomicLong(System.currentTimeMillis());
            this.counter = new AtomicLong(0);
        }

        boolean tryAcquire() {
            long now = System.currentTimeMillis();
            lock.lock();
            try {
                // 检查是否需要滑动窗口
                if (now - windowStart.get() >= windowSizeMillis) {
                    windowStart.set(now);
                    counter.set(1);
                    return true;
                }
                
                // 检查是否超过限制
                if (counter.get() < limitForPeriod) {
                    counter.incrementAndGet();
                    return true;
                }
                return false;
            } finally {
                lock.unlock();
            }
        }

        long getAvailablePermits() {
            long now = System.currentTimeMillis();
            if (now - windowStart.get() >= windowSizeMillis) {
                return limitForPeriod;
            }
            return Math.max(0, limitForPeriod - counter.get());
        }

        void reset() {
            windowStart.set(System.currentTimeMillis());
            counter.set(0);
        }
    }

    /**
     * 限流器缓存
     */
    private static final Map<String, SlidingWindow> WINDOW_CACHE = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key, int limitForPeriod, int periodSeconds) {
        SlidingWindow window = WINDOW_CACHE.computeIfAbsent(key, 
                k -> new SlidingWindow(limitForPeriod, periodSeconds));
        
        boolean acquired = window.tryAcquire();
        
        if (acquired) {
            log.debug("本地限流通过: key={}, limit={}/{}", key, limitForPeriod, periodSeconds);
        } else {
            log.debug("本地限流触发: key={}, limit={}/{}", key, limitForPeriod, periodSeconds);
        }
        
        return acquired;
    }

    @Override
    public boolean tryAcquire(String key, int limitForPeriod, int periodSeconds, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        
        while (System.currentTimeMillis() < deadline) {
            if (tryAcquire(key, limitForPeriod, periodSeconds)) {
                return true;
            }
            
            try {
                Thread.sleep(Math.min(50, timeoutMillis / 10));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        return false;
    }

    @Override
    public long getAvailablePermits(String key, int limitForPeriod, int periodSeconds) {
        SlidingWindow window = WINDOW_CACHE.get(key);
        if (window == null) {
            return limitForPeriod;
        }
        return window.getAvailablePermits();
    }

    @Override
    public void reset(String key) {
        SlidingWindow window = WINDOW_CACHE.get(key);
        if (window != null) {
            window.reset();
            log.debug("重置本地限流: key={}", key);
        }
    }

    @Override
    public String getType() {
        return "LOCAL_SLIDING_WINDOW";
    }

    /**
     * 清除指定key的限流器
     */
    public static void remove(String key) {
        WINDOW_CACHE.remove(key);
    }

    /**
     * 清除所有限流器
     */
    public static void clear() {
        WINDOW_CACHE.clear();
    }
}
