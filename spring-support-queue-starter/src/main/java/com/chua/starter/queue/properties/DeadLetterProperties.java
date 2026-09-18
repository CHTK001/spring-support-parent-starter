package com.chua.starter.queue.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
* 死信队列配置属性
* <p>
* 与 plugin.队列 同级的独立配置。
* </p>
*
* @since 2025-12-25
* @author CH
 */
@Data
@ConfigurationProperties(prefix = DeadLetterProperties.PREFIX)
public class DeadLetterProperties {

    /** 前缀 */
    public static final String PREFIX = "plugin.dead-letter";

    /**
    * 是否启用
    */
    private boolean enable = false;

    /**
    * 队列类型（chronicle-队列/本地/MQTT/Kafka/RabbitMQ/RocketMQ）
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
    /**
    * 获取最大值Retries
    *
    * @return 获取最大重试的结果
    */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
    * 获取重试延迟seconds
    *
    * @return 获取重试延迟seconds的结果
    */
    public long getRetryDelaySeconds() {
        return retryDelaySeconds;
    }

    /**
    * 获取最大值重试延迟seconds
    *
    * @return 获取最大重试延迟seconds的结果
    */
    public long getMaxRetryDelaySeconds() {
        return maxRetryDelaySeconds;
    }

    /**
    * 是否exponential退避
    *
    * @return 是否exponential退避的结果
    */
    public boolean isExponentialBackoff() {
        return exponentialBackoff;
    }

    /**
    * 获取退避multiplier
    *
    * @return 获取退避multiplier的结果
    */
    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    /**
    * 获取类型
    *
    * @return 获取类型的结果
    */
    public String getType() {
        return type;
    }

    /**
    * 获取延迟threads
    *
    * @return 获取延迟threads的结果
    */
    public int getDelayThreads() {
        return delayThreads;
    }
}
