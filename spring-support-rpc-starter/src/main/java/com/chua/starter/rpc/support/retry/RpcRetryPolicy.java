package com.chua.starter.rpc.support.retry;

import lombok.Data;

import java.time.Duration;

/**
 * RPC重试策略
 * <p>
 * 配置RPC调用失败时的重试行为
 * </p>
 *
 * @author CH
 * @since 2025-03-20
 */
@Data
public class RpcRetryPolicy {

    /**
     * 最大重试次数
     */
    private int maxRetries = 3;

    /**
     * 初始重试延迟
     */
    private Duration initialDelay = Duration.ofMillis(100);

    /**
     * 最大重试延迟
     */
    private Duration maxDelay = Duration.ofSeconds(10);

    /**
     * 延迟倍数（指数退避）
     */
    private double multiplier = 2.0;

    /**
     * 是否启用重试
     */
    private boolean enabled = true;

    /**
     * 可重试的异常类型
     */
    private Class<? extends Throwable>[] retryableExceptions = new Class[]{
        java.net.SocketTimeoutException.class,
        java.net.ConnectException.class,
        java.io.IOException.class
    };

    /**
     * 计算重试延迟
     *
     * @param retryCount 当前重试次数（从0开始）
     * @return 延迟时间
     */
    public Duration calculateDelay(int retryCount) {
        if (retryCount <= 0) {
            return initialDelay;
        }

        long delayMillis = (long) (initialDelay.toMillis() * Math.pow(multiplier, retryCount));
        Duration delay = Duration.ofMillis(delayMillis);

        return delay.compareTo(maxDelay) > 0 ? maxDelay : delay;
    }

    /**
     * 是否应该重试
     *
     * @param retryCount 当前重试次数
     * @return 是否应该重试
     */
    public boolean shouldRetry(int retryCount) {
        return enabled && retryCount < maxRetries;
    }

    /**
     * 是否为可重试异常
     *
     * @param throwable 异常
     * @return 是否可重试
     */
    public boolean isRetryableException(Throwable throwable) {
        if (!enabled || retryableExceptions == null) {
            return false;
        }

        for (Class<? extends Throwable> exceptionClass : retryableExceptions) {
            if (exceptionClass.isInstance(throwable)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 创建默认重试策略
     */
    public static RpcRetryPolicy defaultPolicy() {
        return new RpcRetryPolicy();
    }

    /**
     * 创建快速重试策略
     */
    public static RpcRetryPolicy fastRetry() {
        RpcRetryPolicy policy = new RpcRetryPolicy();
        policy.setMaxRetries(5);
        policy.setInitialDelay(Duration.ofMillis(50));
        policy.setMaxDelay(Duration.ofSeconds(2));
        policy.setMultiplier(1.5);
        return policy;
    }

    /**
     * 创建慢速重试策略
     */
    public static RpcRetryPolicy slowRetry() {
        RpcRetryPolicy policy = new RpcRetryPolicy();
        policy.setMaxRetries(10);
        policy.setInitialDelay(Duration.ofSeconds(1));
        policy.setMaxDelay(Duration.ofMinutes(1));
        policy.setMultiplier(2.0);
        return policy;
    }

    /**
     * 创建无重试策略
     */
    public static RpcRetryPolicy noRetry() {
        RpcRetryPolicy policy = new RpcRetryPolicy();
        policy.setEnabled(false);
        policy.setMaxRetries(0);
        return policy;
    }
}
