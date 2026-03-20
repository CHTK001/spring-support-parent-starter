package com.chua.starter.queue.retry;

import lombok.Data;

import java.time.Duration;

/**
 * 重试策略
 *
 * @author CH
 * @since 2025-03-20
 */
@Data
public class RetryPolicy {

    /**
     * 最大重试次数
     */
    private int maxRetries = 3;

    /**
     * 初始重试延迟
     */
    private Duration initialDelay = Duration.ofSeconds(1);

    /**
     * 最大重试延迟
     */
    private Duration maxDelay = Duration.ofMinutes(5);

    /**
     * 延迟倍数（指数退避）
     */
    private double multiplier = 2.0;

    /**
     * 是否启用死信队列
     */
    private boolean enableDeadLetter = true;

    /**
     * 死信队列后缀
     */
    private String deadLetterSuffix = ".dlq";

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
        return retryCount < maxRetries;
    }

    /**
     * 获取死信队列地址
     *
     * @param originalDestination 原始队列地址
     * @return 死信队列地址
     */
    public String getDeadLetterDestination(String originalDestination) {
        return originalDestination + deadLetterSuffix;
    }

    /**
     * 创建默认重试策略
     */
    public static RetryPolicy defaultPolicy() {
        return new RetryPolicy();
    }

    /**
     * 创建快速重试策略（适用于临时性错误）
     */
    public static RetryPolicy fastRetry() {
        RetryPolicy policy = new RetryPolicy();
        policy.setMaxRetries(5);
        policy.setInitialDelay(Duration.ofMillis(100));
        policy.setMaxDelay(Duration.ofSeconds(10));
        policy.setMultiplier(1.5);
        return policy;
    }

    /**
     * 创建慢速重试策略（适用于外部服务不可用）
     */
    public static RetryPolicy slowRetry() {
        RetryPolicy policy = new RetryPolicy();
        policy.setMaxRetries(10);
        policy.setInitialDelay(Duration.ofSeconds(5));
        policy.setMaxDelay(Duration.ofMinutes(30));
        policy.setMultiplier(2.0);
        return policy;
    }

    /**
     * 创建无重试策略
     */
    public static RetryPolicy noRetry() {
        RetryPolicy policy = new RetryPolicy();
        policy.setMaxRetries(0);
        policy.setEnableDeadLetter(false);
        return policy;
    }
}
