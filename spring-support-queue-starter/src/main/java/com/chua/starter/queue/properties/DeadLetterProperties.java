package com.chua.starter.queue.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 死信队列配置属性
 * <p>
 * 与 plugin.queue 同级的独立配置。
 * </p>
 *
 * @author CH
 * @since 2025-12-25
 */
@Data
@ConfigurationProperties(prefix = DeadLetterProperties.PREFIX)
public class DeadLetterProperties {

    public static final String PREFIX = "plugin.dead-letter";

    /**
     * 是否启用
     */
    private boolean enable = false;

    /**
     * 队列类型（local/mqtt/kafka/rabbitmq/rocketmq）
     */
    private String type = "local";

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
}
