package com.chua.starter.queue.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 消息队列配置属性
 *
 * @since 2025-12-25
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = QueueProperties.PREFIX)
public class QueueProperties {

    /** Prefix */
    public static final String PREFIX = "plugin.queue";

    /**
     * 是否启用
     */
    private boolean enable = false;

    /**
     * 消息队列类型（chronicle-queue/local/mqtt/kafka/rabbitmq/rocketmq/nats）
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
     * NATS配置
     */
    private NatsConfig nats = new NatsConfig();

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
        /** 获取DelayThreads */
        public int getDelayThreads() {
            return delayThreads;
        }

        /** 设置DelayThreads */
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
        /** 获取Address */
        public String getAddress() {
            return address;
        }

        /** 设置Address */
        public void setAddress(String address) {
            this.address = address;
        }

        /** 获取ClientId */
        public String getClientId() {
            return clientId;
        }

        /** 设置ClientId */
        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        /** 获取Username */
        public String getUsername() {
            return username;
        }

        /** 设置Username */
        public void setUsername(String username) {
            this.username = username;
        }

        /** 获取Password */
        public String getPassword() {
            return password;
        }

        /** 设置Password */
        public void setPassword(String password) {
            this.password = password;
        }

        /** 获取Qos */
        public int getQos() {
            return qos;
        }

        /** 设置Qos */
        public void setQos(int qos) {
            this.qos = qos;
        }

        /** 获取ConnectionTimeout */
        public int getConnectionTimeout() {
            return connectionTimeout;
        }

        /** 设置ConnectionTimeout */
        public void setConnectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        /** 获取KeepAliveInterval */
        public int getKeepAliveInterval() {
            return keepAliveInterval;
        }

        /** 设置KeepAliveInterval */
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
        /** 获取BootstrapServers */
        public String getBootstrapServers() {
            return bootstrapServers;
        }

        /** 设置BootstrapServers */
        public void setBootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
        }

        /** 获取分组Id */
        public String getGroupId() {
            return groupId;
        }

        /** 设置分组Id */
        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        /** 获取AutoOffset重置 */
        public String getAutoOffsetReset() {
            return autoOffsetReset;
        }

        /** 设置AutoOffset重置 */
        public void setAutoOffsetReset(String autoOffsetReset) {
            this.autoOffsetReset = autoOffsetReset;
        }

        /** 是否启用Auto提交 */
        public boolean isEnableAutoCommit() {
            return enableAutoCommit;
        }

        /** 设置启用Auto提交 */
        public void setEnableAutoCommit(boolean enableAutoCommit) {
            this.enableAutoCommit = enableAutoCommit;
        }

        /** 获取Auto提交IntervalMs */
        public int getAutoCommitIntervalMs() {
            return autoCommitIntervalMs;
        }

        /** 设置Auto提交IntervalMs */
        public void setAutoCommitIntervalMs(int autoCommitIntervalMs) {
            this.autoCommitIntervalMs = autoCommitIntervalMs;
        }

        /** 获取Acks */
        public String getAcks() {
            return acks;
        }

        /** 设置Acks */
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
        private String username = "";

        /**
         * 密码
         */
        private String password = "";

        /**
         * 虚拟主机
         */
        private String virtualHost = "/";

        /**
         * 连接超时（毫秒）
         */
        private int connectionTimeout = 60000;

        // Lombok 注解处理器未运行时的手动 getter/setter 方法
        /** 获取Host */
        public String getHost() {
            return host;
        }

        /** 设置Host */
        public void setHost(String host) {
            this.host = host;
        }

        /** 获取Port */
        public int getPort() {
            return port;
        }

        /** 设置Port */
        public void setPort(int port) {
            this.port = port;
        }

        /** 获取Username */
        public String getUsername() {
            return username;
        }

        /** 设置Username */
        public void setUsername(String username) {
            this.username = username;
        }

        /** 获取Password */
        public String getPassword() {
            return password;
        }

        /** 设置Password */
        public void setPassword(String password) {
            this.password = password;
        }

        /** 获取VirtualHost */
        public String getVirtualHost() {
            return virtualHost;
        }

        /** 设置VirtualHost */
        public void setVirtualHost(String virtualHost) {
            this.virtualHost = virtualHost;
        }

        /** 获取ConnectionTimeout */
        public int getConnectionTimeout() {
            return connectionTimeout;
        }

        /** 设置ConnectionTimeout */
        public void setConnectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }
    }

    /**
     * NATS配置
     */
    @Data
    public static class NatsConfig {
        /**
         * 服务器地址
         */
        private String url = "nats://localhost:4222";

        /**
         * 用户名
         */
        private String username;

        /**
         * 密码
         */
        private String password;

        /**
         * Token认证
         */
        private String token;

        /**
         * 消费者组
         */
        private String consumerGroup = "default-group";

        /**
         * 连接超时（毫秒）
         */
        private long connectionTimeout = 5000;

        /**
         * 重连等待（毫秒）
         */
        private long reconnectWait = 2000;

        /**
         * 最大重连次数（-1 表示无限重连）
         */
        private int maxReconnects = 60;

        // ==================== 手动 Getter/Setter ====================

        /** 获取Url */
        public String getUrl() {
            return url;
        }

        /** 设置Url */
        public void setUrl(String url) {
            this.url = url;
        }

        /** 获取Username */
        public String getUsername() {
            return username;
        }

        /** 设置Username */
        public void setUsername(String username) {
            this.username = username;
        }

        /** 获取Password */
        public String getPassword() {
            return password;
        }

        /** 设置Password */
        public void setPassword(String password) {
            this.password = password;
        }

        /** 获取Token */
        public String getToken() {
            return token;
        }

        /** 设置Token */
        public void setToken(String token) {
            this.token = token;
        }

        /** 获取Consumer分组 */
        public String getConsumerGroup() {
            return consumerGroup;
        }

        /** 设置Consumer分组 */
        public void setConsumerGroup(String consumerGroup) {
            this.consumerGroup = consumerGroup;
        }

        /** 获取ConnectionTimeout */
        public long getConnectionTimeout() {
            return connectionTimeout;
        }

        /** 设置ConnectionTimeout */
        public void setConnectionTimeout(long connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        /** 获取ReconnectWait */
        public long getReconnectWait() {
            return reconnectWait;
        }

        /** 设置ReconnectWait */
        public void setReconnectWait(long reconnectWait) {
            this.reconnectWait = reconnectWait;
        }

        /** 获取最大值Reconnects */
        public int getMaxReconnects() {
            return maxReconnects;
        }

        /** 设置最大值Reconnects */
        public void setMaxReconnects(int maxReconnects) {
            this.maxReconnects = maxReconnects;
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
        /** 获取NameServer */
        public String getNameServer() {
            return nameServer;
        }

        /** 设置NameServer */
        public void setNameServer(String nameServer) {
            this.nameServer = nameServer;
        }

        /** 获取Producer分组 */
        public String getProducerGroup() {
            return producerGroup;
        }

        /** 设置Producer分组 */
        public void setProducerGroup(String producerGroup) {
            this.producerGroup = producerGroup;
        }

        /** 获取Consumer分组 */
        public String getConsumerGroup() {
            return consumerGroup;
        }

        /** 设置Consumer分组 */
        public void setConsumerGroup(String consumerGroup) {
            this.consumerGroup = consumerGroup;
        }

        /** 获取发送MsgTimeout */
        public int getSendMsgTimeout() {
            return sendMsgTimeout;
        }

        /** 设置发送MsgTimeout */
        public void setSendMsgTimeout(int sendMsgTimeout) {
            this.sendMsgTimeout = sendMsgTimeout;
        }

        /** 获取最大值Message获取大小 */
        public int getMaxMessageSize() {
            return maxMessageSize;
        }

        /** 设置最大值Message获取大小 */
        public void setMaxMessageSize(int maxMessageSize) {
            this.maxMessageSize = maxMessageSize;
        }
    }

    // Lombok 注解处理器未运行时的手动 getter 方法
    /** 获取Type */
    public String getType() {
        return type;
    }

    /** 获取Mqtt */
    public MqttConfig getMqtt() {
        return mqtt;
    }

    /** 设置Mqtt */
    public void setMqtt(MqttConfig mqtt) {
        this.mqtt = mqtt;
    }

    /** 获取Kafka */
    public KafkaConfig getKafka() {
        return kafka;
    }

    /** 设置Kafka */
    public void setKafka(KafkaConfig kafka) {
        this.kafka = kafka;
    }

    /** 获取Rabbitmq */
    public RabbitMQConfig getRabbitmq() {
        return rabbitmq;
    }

    /** 设置Rabbitmq */
    public void setRabbitmq(RabbitMQConfig rabbitmq) {
        this.rabbitmq = rabbitmq;
    }

    /** 获取Rocketmq */
    public RocketMQConfig getRocketmq() {
        return rocketmq;
    }

    /** 设置Rocketmq */
    public void setRocketmq(RocketMQConfig rocketmq) {
        this.rocketmq = rocketmq;
    }

    /** 获取Local */
    public LocalConfig getLocal() {
        return local;
    }

    /** 获取Nats */
    public NatsConfig getNats() {
        return nats;
    }

    /** 设置Nats */
    public void setNats(NatsConfig nats) {
        this.nats = nats;
    }
}
