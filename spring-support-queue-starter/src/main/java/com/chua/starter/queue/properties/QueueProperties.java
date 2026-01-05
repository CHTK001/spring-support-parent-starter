package com.chua.starter.queue.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 消息队列配置属性
 *
 * @author CH
 * @since 2025-12-25
 */
@Data
@ConfigurationProperties(prefix = QueueProperties.PREFIX)
public class QueueProperties {

    public static final String PREFIX = "plugin.queue";

    /**
     * 是否启用
     */
    private boolean enable = false;

    /**
     * 消息队列类型（local/mqtt/kafka/rabbitmq/rocketmq）
     */
    private String type = "local";

    /**
     * 默认目标地址
     */
    private String defaultDestination;

    /**
     * 本地队列配置（基于Guava EventBus）
     */
    private LocalConfig local = new LocalConfig();

    /**
     * MQTT配置
     */
    private MqttConfig mqtt = new MqttConfig();

    /**
     * Kafka配置
     */
    private KafkaConfig kafka = new KafkaConfig();

    /**
     * RabbitMQ配置
     */
    private RabbitMQConfig rabbitmq = new RabbitMQConfig();

    /**
     * RocketMQ配置
     */
    private RocketMQConfig rocketmq = new RocketMQConfig();

    /**
     * 本地队列配置（基于Guava EventBus）
     */
    @Data
    public static class LocalConfig {
        /**
         * 延迟消息调度线程数
         */
        private int delayThreads = 2;
    }

    /**
     * MQTT配置
     */
    @Data
    public static class MqttConfig {
        /**
         * 服务器地址
         */
        private String address = "tcp://localhost:1883";

        /**
         * 客户端ID
         */
        private String clientId;

        /**
         * 用户名
         */
        private String username;

        /**
         * 密码
         */
        private String password;

        /**
         * QoS级别
         */
        private int qos = 1;

        /**
         * 连接超时（秒）
         */
        private int connectionTimeout = 30;

        /**
         * 心跳间隔（秒）
         */
        private int keepAliveInterval = 60;
    }

    /**
     * Kafka配置
     */
    @Data
    public static class KafkaConfig {
        /**
         * Bootstrap服务器
         */
        private String bootstrapServers = "localhost:9092";

        /**
         * 消费者组ID
         */
        private String groupId = "default-group";

        /**
         * 自动偏移重置策略
         */
        private String autoOffsetReset = "earliest";

        /**
         * 是否自动提交
         */
        private boolean enableAutoCommit = true;

        /**
         * 自动提交间隔（毫秒）
         */
        private int autoCommitIntervalMs = 5000;

        /**
         * 生产者确认机制
         */
        private String acks = "all";
    }

    /**
     * RabbitMQ配置
     */
    @Data
    public static class RabbitMQConfig {
        /**
         * 主机地址
         */
        private String host = "localhost";

        /**
         * 端口
         */
        private int port = 5672;

        /**
         * 用户名
         */
        private String username = "guest";

        /**
         * 密码
         */
        private String password = "guest";

        /**
         * 虚拟主机
         */
        private String virtualHost = "/";

        /**
         * 连接超时（毫秒）
         */
        private int connectionTimeout = 60000;
    }

    /**
     * RocketMQ配置
     */
    @Data
    public static class RocketMQConfig {
        /**
         * NameServer地址
         */
        private String nameServer = "localhost:9876";

        /**
         * 生产者组
         */
        private String producerGroup = "default-producer";

        /**
         * 消费者组
         */
        private String consumerGroup = "default-consumer";

        /**
         * 发送超时（毫秒）
         */
        private int sendMsgTimeout = 3000;

        /**
         * 最大消息大小（字节）
         */
        private int maxMessageSize = 4194304;
    }
}
