package com.chua.starter.strategy.template;

import com.chua.common.support.annotations.Extension;
import com.chua.common.support.core.spi.ServiceProvider;
import com.chua.starter.strategy.distributed.StrategyRateLimiter;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * 默认限流模板实现
 * <p>
 * 通过 SPI 机制获取限流器实现，支持本地和分布式限流。
 * 默认使用 local 类型的限流器，可通过配置切换。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Slf4j
@Extension("default")
public class DefaultLimitTemplate implements LimitTemplate {

    private static final String DEFAULT_LIMITER_TYPE = "local";

    /**
     * 获取限流器实例
     *
     * @param type 限流器类型
     * @return 限流器实例
     */
    private StrategyRateLimiter getRateLimiter(String type) {
        var provider = ServiceProvider.of(StrategyRateLimiter.class);
        var limiter = provider.getExtension(type != null ? type : DEFAULT_LIMITER_TYPE);
        if (limiter == null) {
            log.warn("限流器类型 {} 不存在，使用默认类型 {}", type, DEFAULT_LIMITER_TYPE);
            limiter = provider.getExtension(DEFAULT_LIMITER_TYPE);
        }
        if (limiter == null) {
            throw new IllegalStateException("无法获取限流器实例，请检查限流器实现是否已注册");
        }
        return limiter;
    }

    @Override
    public <T> T tryAcquire(String key, int limitForPeriod, int periodSeconds, Supplier<T> supplier) {
        return tryAcquire(key, limitForPeriod, periodSeconds, 0, supplier);
    }

    @Override
    public <T> T tryAcquire(String key, int limitForPeriod, int periodSeconds, long timeoutMillis, Supplier<T> supplier) {
        var limiter = getRateLimiter(null);
        boolean acquired = timeoutMillis > 0
                ? limiter.tryAcquire(key, limitForPeriod, periodSeconds, timeoutMillis)
                : limiter.tryAcquire(key, limitForPeriod, periodSeconds);

        if (acquired) {
            log.debug("获取限流许可成功: key={}, limit={}, period={}s", key, limitForPeriod, periodSeconds);
            return supplier.get();
        } else {
            log.warn("获取限流许可失败（被限流）: key={}, limit={}, period={}s", key, limitForPeriod, periodSeconds);
            return null;
        }
    }

    @Override
    public <T> T tryAcquire(String key, int limitForPeriod, int periodSeconds, Supplier<T> supplier, Supplier<T> fallback) {
        return tryAcquire(key, limitForPeriod, periodSeconds, 0, supplier, fallback);
    }

    @Override
    public <T> T tryAcquire(String key, int limitForPeriod, int periodSeconds, long timeoutMillis, Supplier<T> supplier, Supplier<T> fallback) {
        var limiter = getRateLimiter(null);
        boolean acquired = timeoutMillis > 0
                ? limiter.tryAcquire(key, limitForPeriod, periodSeconds, timeoutMillis)
                : limiter.tryAcquire(key, limitForPeriod, periodSeconds);

        if (acquired) {
            log.debug("获取限流许可成功: key={}, limit={}, period={}s", key, limitForPeriod, periodSeconds);
            return supplier.get();
        } else {
            log.warn("获取限流许可失败，执行降级逻辑: key={}, limit={}, period={}s", key, limitForPeriod, periodSeconds);
            return fallback.get();
        }
    }

    @Override
    public boolean tryAcquire(String key, int limitForPeriod, int periodSeconds, Runnable action) {
        return tryAcquire(key, limitForPeriod, periodSeconds, 0, action);
    }

    @Override
    public boolean tryAcquire(String key, int limitForPeriod, int periodSeconds, long timeoutMillis, Runnable action) {
        var limiter = getRateLimiter(null);
        boolean acquired = timeoutMillis > 0
                ? limiter.tryAcquire(key, limitForPeriod, periodSeconds, timeoutMillis)
                : limiter.tryAcquire(key, limitForPeriod, periodSeconds);

        if (acquired) {
            log.debug("获取限流许可成功: key={}, limit={}, period={}s", key, limitForPeriod, periodSeconds);
            action.run();
            return true;
        } else {
            log.warn("获取限流许可失败（被限流）: key={}, limit={}, period={}s", key, limitForPeriod, periodSeconds);
            return false;
        }
    }

    @Override
    public long getAvailablePermits(String key, int limitForPeriod, int periodSeconds) {
        var limiter = getRateLimiter(null);
        return limiter.getAvailablePermits(key, limitForPeriod, periodSeconds);
    }

    @Override
    public void reset(String key) {
        var limiter = getRateLimiter(null);
        limiter.reset(key);
        log.debug("重置限流计数: key={}", key);
    }

    @Override
    public boolean isLimited(String key, int limitForPeriod, int periodSeconds) {
        return !getRateLimiter(null).tryAcquire(key, limitForPeriod, periodSeconds);
    }
}

