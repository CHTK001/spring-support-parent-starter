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
     * 消息队列类型（chronicle-queue/local/mqtt/kafka/rabbitmq/rocketmq）
     */
    private String type = "chronicle-queue";

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

        // Lombok 注解处理器未运行时的手动 getter/setter 方法
        public int getDelayThreads() {
            return delayThreads;
        }

        public void setDelayThreads(int delayThreads) {
            this.delayThreads = delayThreads;
        }
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

        // Lombok 注解处理器未运行时的手动 getter 方法
        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getQos() {
            return qos;
        }

        public void setQos(int qos) {
            this.qos = qos;
        }

        public int getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        public int getKeepAliveInterval() {
            return keepAliveInterval;
        }

        public void setKeepAliveInterval(int keepAliveInterval) {
            this.keepAliveInterval = keepAliveInterval;
        }
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

        // Lombok 注解处理器未运行时的手动 getter 方法
        public String getBootstrapServers() {
            return bootstrapServers;
        }

        public void setBootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
        }

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        public String getAutoOffsetReset() {
            return autoOffsetReset;
        }

        public void setAutoOffsetReset(String autoOffsetReset) {
            this.autoOffsetReset = autoOffsetReset;
        }

        public boolean isEnableAutoCommit() {
            return enableAutoCommit;
        }

        public void setEnableAutoCommit(boolean enableAutoCommit) {
            this.enableAutoCommit = enableAutoCommit;
        }

        public int getAutoCommitIntervalMs() {
            return autoCommitIntervalMs;
        }

        public void setAutoCommitIntervalMs(int autoCommitIntervalMs) {
            this.autoCommitIntervalMs = autoCommitIntervalMs;
        }

        public String getAcks() {
            return acks;
        }

        public void setAcks(String acks) {
            this.acks = acks;
        }
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

        // Lombok 注解处理器未运行时的手动 getter/setter 方法
        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getVirtualHost() {
            return virtualHost;
        }

        public void setVirtualHost(String virtualHost) {
            this.virtualHost = virtualHost;
        }

        public int getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }
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

        // Lombok 注解处理器未运行时的手动 getter/setter 方法
        public String getNameServer() {
            return nameServer;
        }

        public void setNameServer(String nameServer) {
            this.nameServer = nameServer;
        }

        public String getProducerGroup() {
            return producerGroup;
        }

        public void setProducerGroup(String producerGroup) {
            this.producerGroup = producerGroup;
        }

        public String getConsumerGroup() {
            return consumerGroup;
        }

        public void setConsumerGroup(String consumerGroup) {
            this.consumerGroup = consumerGroup;
        }

        public int getSendMsgTimeout() {
            return sendMsgTimeout;
        }

        public void setSendMsgTimeout(int sendMsgTimeout) {
            this.sendMsgTimeout = sendMsgTimeout;
        }

        public int getMaxMessageSize() {
            return maxMessageSize;
        }

        public void setMaxMessageSize(int maxMessageSize) {
            this.maxMessageSize = maxMessageSize;
        }
    }

    // Lombok 注解处理器未运行时的手动 getter 方法
    public String getType() {
        return type;
    }

    public MqttConfig getMqtt() {
        return mqtt;
    }

    public void setMqtt(MqttConfig mqtt) {
        this.mqtt = mqtt;
    }

    public KafkaConfig getKafka() {
        return kafka;
    }

    public void setKafka(KafkaConfig kafka) {
        this.kafka = kafka;
    }

    public RabbitMQConfig getRabbitmq() {
        return rabbitmq;
    }

    public void setRabbitmq(RabbitMQConfig rabbitmq) {
        this.rabbitmq = rabbitmq;
    }

    public RocketMQConfig getRocketmq() {
        return rocketmq;
    }

    public void setRocketmq(RocketMQConfig rocketmq) {
        this.rocketmq = rocketmq;
    }

    public LocalConfig getLocal() {
        return local;
    }
}
