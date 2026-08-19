package com.chua.starter.queue.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 死信队列配置属性
 * <p>
 * 与 plugin.queue 同级的独立配置。
 * </p>
 *
 * @since 2025-12-25
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = DeadLetterProperties.PREFIX)
public class DeadLetterProperties {

    /** Prefix */
    public static final String PREFIX = "plugin.dead-letter";

    /**
     * 是否启用
     */
    private boolean enable = false;

    /**
     * 队列类型（chronicle-queue/local/mqtt/kafka/rabbitmq/rocketmq）
     */
    private String type = "chronicle-queue";

    /**
     * 延迟消息调度线程数
     */
    private int delayThreads = 2;

    /**
     * 最大重试次数
     */
    private int maxRetries = 3;

    /**
     * 重试延迟（秒）
     */
    private long retryDelaySeconds = 5;

    /**
     * 最大重试延迟（秒）
     */
    private long maxRetryDelaySeconds = 300;

    /**
     * 是否启用指数退避
     */
    private boolean exponentialBackoff = true;

    /**
     * 退避乘数
     */
    private double backoffMultiplier = 2.0;

    // Lombok 注解处理器未运行时的手动 getter 方法
    /** 获取最大值Retries */
    public int getMaxRetries() {
        return maxRetries;
    }

    /** 获取RetryDelaySeconds */
    public long getRetryDelaySeconds() {
        return retryDelaySeconds;
    }

    /** 获取最大值RetryDelaySeconds */
    public long getMaxRetryDelaySeconds() {
        return maxRetryDelaySeconds;
    }

    /** 是否ExponentialBackoff */
    public boolean isExponentialBackoff() {
        return exponentialBackoff;
    }

    /** 获取BackoffMultiplier */
    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    /** 获取Type */
    public String getType() {
        return type;
    }

    /** 获取DelayThreads */
    public int getDelayThreads() {
        return delayThreads;
    }
}
