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

    /**
     * 前缀
    */
    public static final String PREFIX = "plugin.queue";

    /**
     * 是否启用
     */
    private boolean enable = false;

    /**
     * 消息队列类型（chronicle-队列/本地/MQTT/Kafka/RabbitMQ/RocketMQ/NATS）
     */
    private String type = "chronicle-queue";

    /**
     * 默认目标地址
     */
    private String defaultDestination;

    /**
     * 本地队列配置（基于Guava 事件bus）
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
     * 本地队列配置（基于Guava 事件bus）
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class LocalConfig {
        /**
         * 延迟消息调度线程数
         */
        private int delayThreads = 2;

        // Lombok 注解处理器未运行时的手动 getter/setter 方法
        /**
         * 获取延迟threads
         *
         * @return 获取延迟threads的结果
         */
        public int getDelayThreads() {
            return delayThreads;
        }

        /**
         * 设置延迟threads
         *
         * @param delayThreads 延迟threads
         */
        public void setDelayThreads(int delayThreads) {
            this.delayThreads = delayThreads;
        }
    }

    /**
     * MQTT配置
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class MqttConfig {
        /**
         * 服务器地址
         */
        private String address = "tcp://localhost:1883";

        /**
         * 客户端标识
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
         * qos级别
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
        /**
         * 获取地址
         *
         * @return 获取地址的结果
         */
        public String getAddress() {
            return address;
        }

        /**
         * 设置地址
         *
         * @param address 地址
         */
        public void setAddress(String address) {
            this.address = address;
        }

        /**
         * 获取客户端标识
         *
         * @return 获取客户端id的结果
         */
        public String getClientId() {
            return clientId;
        }

        /**
         * 设置客户端标识
         *
         * @param clientId 客户端标识
         */
        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        /**
         * 获取用户名
         *
         * @return 获取用户名的结果
         */
        public String getUsername() {
            return username;
        }

        /**
         * 设置用户名
         *
         * @param username 用户名
         */
        public void setUsername(String username) {
            this.username = username;
        }

        /**
         * 获取密码
         *
         * @return 获取密码的结果
         */
        public String getPassword() {
            return password;
        }

        /**
         * 设置密码
         *
         * @param password 密码
         */
        public void setPassword(String password) {
            this.password = password;
        }

        /**
         * 获取Qos
         *
         * @return 获取qos的结果
         */
        public int getQos() {
            return qos;
        }

        /**
         * 设置Qos
         *
         * @param qos qos
         */
        public void setQos(int qos) {
            this.qos = qos;
        }

        /**
         * 获取connection超时
         *
         * @return 获取connection超时的结果
         */
        public int getConnectionTimeout() {
            return connectionTimeout;
        }

        /**
         * 设置connection超时
         *
         * @param connectionTimeout connection超时
         */
        public void setConnectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        /**
         * 获取keepalive间隔
         *
         * @return 获取keepalive间隔的结果
         */
        public int getKeepAliveInterval() {
            return keepAliveInterval;
        }

        /**
         * 设置keepalive间隔
         *
         * @param keepAliveInterval keepalive间隔
         */
        public void setKeepAliveInterval(int keepAliveInterval) {
            this.keepAliveInterval = keepAliveInterval;
        }
    }

    /**
     * Kafka配置
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class KafkaConfig {
        /**
         * Bootstrap服务器
         */
        private String bootstrapServers = "localhost:9092";

        /**
         * 消费者组标识
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
        /**
         * 获取bootstrap服务端
         *
         * @return 获取bootstrap服务端的结果
         */
        public String getBootstrapServers() {
            return bootstrapServers;
        }

        /**
         * 设置bootstrap服务端
         *
         * @param bootstrapServers bootstrap服务端
         */
        public void setBootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
        }

        /**
         * 获取分组标识
         *
         * @return 获取群体id的结果
         */
        public String getGroupId() {
            return groupId;
        }

        /**
         * 设置分组标识
         *
         * @param groupId 群体标识
         */
        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        /**
         * 获取auto偏移量重置
         *
         * @return 获取auto偏移量reset的结果
         */
        public String getAutoOffsetReset() {
            return autoOffsetReset;
        }

        /**
         * 设置auto偏移量重置
         *
         * @param autoOffsetReset auto偏移量reset
         */
        public void setAutoOffsetReset(String autoOffsetReset) {
            this.autoOffsetReset = autoOffsetReset;
        }

        /**
         * 是否启用Auto提交
         *
         * @return 是否enableautocommit的结果
         */
        public boolean isEnableAutoCommit() {
            return enableAutoCommit;
        }

        /**
         * 设置启用Auto提交
         *
         * @param enableAutoCommit enableautocommit
         */
        public void setEnableAutoCommit(boolean enableAutoCommit) {
            this.enableAutoCommit = enableAutoCommit;
        }

        /**
         * 获取Auto提交间隔ms
         *
         * @return 获取autocommit间隔ms的结果
         */
        public int getAutoCommitIntervalMs() {
            return autoCommitIntervalMs;
        }

        /**
         * 设置Auto提交间隔ms
         *
         * @param autoCommitIntervalMs autocommit间隔ms
         */
        public void setAutoCommitIntervalMs(int autoCommitIntervalMs) {
            this.autoCommitIntervalMs = autoCommitIntervalMs;
        }

        /**
         * 获取ACK
         *
         * @return 获取ACK的结果
         */
        public String getAcks() {
            return acks;
        }

        /**
         * 设置ACK
         *
         * @param acks ACK
         */
        public void setAcks(String acks) {
            this.acks = acks;
        }
    }

    /**
     * RabbitMQ配置
     * @author CH
     * @since 4.0.0
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
        /**
         * 获取主机
         *
         * @return 获取主机的结果
         */
        public String getHost() {
            return host;
        }

        /**
         * 设置主机
         *
         * @param host 主机
         */
        public void setHost(String host) {
            this.host = host;
        }

        /**
         * 获取端口
         *
         * @return 获取端口的结果
         */
        public int getPort() {
            return port;
        }

        /**
         * 设置端口
         *
         * @param port 端口
         */
        public void setPort(int port) {
            this.port = port;
        }

        /**
         * 获取用户名
         *
         * @return 获取用户名的结果
         */
        public String getUsername() {
            return username;
        }

        /**
         * 设置用户名
         *
         * @param username 用户名
         */
        public void setUsername(String username) {
            this.username = username;
        }

        /**
         * 获取密码
         *
         * @return 获取密码的结果
         */
        public String getPassword() {
            return password;
        }

        /**
         * 设置密码
         *
         * @param password 密码
         */
        public void setPassword(String password) {
            this.password = password;
        }

        /**
         * 获取虚拟主机
         *
         * @return 获取虚拟主机的结果
         */
        public String getVirtualHost() {
            return virtualHost;
        }

        /**
         * 设置虚拟主机
         *
         * @param virtualHost 虚拟主机
         */
        public void setVirtualHost(String virtualHost) {
            this.virtualHost = virtualHost;
        }

        /**
         * 获取connection超时
         *
         * @return 获取connection超时的结果
         */
        public int getConnectionTimeout() {
            return connectionTimeout;
        }

        /**
         * 设置connection超时
         *
         * @param connectionTimeout connection超时
         */
        public void setConnectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }
    }

    /**
     * NATS配置
     * @author CH
     * @since 4.0.0
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
         * 令牌认证
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

        /**
         * 获取Url
         *
         * @return 获取url的结果
         */
        public String getUrl() {
            return url;
        }

        /**
         * 设置Url
         *
         * @param url url
         */
        public void setUrl(String url) {
            this.url = url;
        }

        /**
         * 获取用户名
         *
         * @return 获取用户名的结果
         */
        public String getUsername() {
            return username;
        }

        /**
         * 设置用户名
         *
         * @param username 用户名
         */
        public void setUsername(String username) {
            this.username = username;
        }

        /**
         * 获取密码
         *
         * @return 获取密码的结果
         */
        public String getPassword() {
            return password;
        }

        /**
         * 设置密码
         *
         * @param password 密码
         */
        public void setPassword(String password) {
            this.password = password;
        }

        /**
         * 获取令牌
         *
         * @return 获取令牌的结果
         */
        public String getToken() {
            return token;
        }

        /**
         * 设置令牌
         *
         * @param token 令牌
         */
        public void setToken(String token) {
            this.token = token;
        }

        /**
         * 获取Consumer分组
         *
         * @return 获取consumer群体的结果
         */
        public String getConsumerGroup() {
            return consumerGroup;
        }

        /**
         * 设置Consumer分组
         *
         * @param consumerGroup consumer群体
         */
        public void setConsumerGroup(String consumerGroup) {
            this.consumerGroup = consumerGroup;
        }

        /**
         * 获取connection超时
         *
         * @return 获取connection超时的结果
         */
        public long getConnectionTimeout() {
            return connectionTimeout;
        }

        /**
         * 设置connection超时
         *
         * @param connectionTimeout connection超时
         */
        public void setConnectionTimeout(long connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        /**
         * 获取reconnectwait
         *
         * @return 获取reconnectwait的结果
         */
        public long getReconnectWait() {
            return reconnectWait;
        }

        /**
         * 设置reconnectwait
         *
         * @param reconnectWait reconnectwait
         */
        public void setReconnectWait(long reconnectWait) {
            this.reconnectWait = reconnectWait;
        }

        /**
         * 获取最大值Reconnects
         *
         * @return 获取最大reconnects的结果
         */
        public int getMaxReconnects() {
            return maxReconnects;
        }

        /**
         * 设置最大值Reconnects
         *
         * @param maxReconnects 最大reconnects
         */
        public void setMaxReconnects(int maxReconnects) {
            this.maxReconnects = maxReconnects;
        }
    }

    /**
     * RocketMQ配置
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class RocketMQConfig {
        /**
         * 名称服务端地址
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
        /**
         * 获取名称服务端
         *
         * @return 获取名称服务端的结果
         */
        public String getNameServer() {
            return nameServer;
        }

        /**
         * 设置名称服务端
         *
         * @param nameServer 名称服务端
         */
        public void setNameServer(String nameServer) {
            this.nameServer = nameServer;
        }

        /**
         * 获取Producer分组
         *
         * @return 获取producer群体的结果
         */
        public String getProducerGroup() {
            return producerGroup;
        }

        /**
         * 设置Producer分组
         *
         * @param producerGroup producer群体
         */
        public void setProducerGroup(String producerGroup) {
            this.producerGroup = producerGroup;
        }

        /**
         * 获取Consumer分组
         *
         * @return 获取consumer群体的结果
         */
        public String getConsumerGroup() {
            return consumerGroup;
        }

        /**
         * 设置Consumer分组
         *
         * @param consumerGroup consumer群体
         */
        public void setConsumerGroup(String consumerGroup) {
            this.consumerGroup = consumerGroup;
        }

        /**
         * 获取发送msg超时
         *
         * @return 获取发送msg超时的结果
         */
        public int getSendMsgTimeout() {
            return sendMsgTimeout;
        }

        /**
         * 设置发送msg超时
         *
         * @param sendMsgTimeout 发送msg超时
         */
        public void setSendMsgTimeout(int sendMsgTimeout) {
            this.sendMsgTimeout = sendMsgTimeout;
        }

        /**
         * 获取最大值消息获取大小
         *
         * @return 获取最大消息大小的结果
         */
        public int getMaxMessageSize() {
            return maxMessageSize;
        }

        /**
         * 设置最大值消息获取大小
         *
         * @param maxMessageSize 最大消息大小
         */
        public void setMaxMessageSize(int maxMessageSize) {
            this.maxMessageSize = maxMessageSize;
        }
    }

    // Lombok 注解处理器未运行时的手动 getter 方法
    /**
     * 获取类型
     *
     * @return 获取类型的结果
     */
    public String getType() {
        return type;
    }

    /**
     * 获取MQTT
     *
     * @return 获取mqtt的结果
     */
    public MqttConfig getMqtt() {
        return mqtt;
    }

    /**
     * 设置MQTT
     *
     * @param mqtt MQTT
     */
    public void setMqtt(MqttConfig mqtt) {
        this.mqtt = mqtt;
    }

    /**
     * 获取Kafka
     *
     * @return 获取kafka的结果
     */
    public KafkaConfig getKafka() {
        return kafka;
    }

    /**
     * 设置Kafka
     *
     * @param kafka Kafka
     */
    public void setKafka(KafkaConfig kafka) {
        this.kafka = kafka;
    }

    /**
     * 获取RabbitMQ
     *
     * @return 获取rabbitmq的结果
     */
    public RabbitMQConfig getRabbitmq() {
        return rabbitmq;
    }

    /**
     * 设置RabbitMQ
     *
     * @param rabbitmq RabbitMQ
     */
    public void setRabbitmq(RabbitMQConfig rabbitmq) {
        this.rabbitmq = rabbitmq;
    }

    /**
     * 获取RocketMQ
     *
     * @return 获取rocketmq的结果
     */
    public RocketMQConfig getRocketmq() {
        return rocketmq;
    }

    /**
     * 设置RocketMQ
     *
     * @param rocketmq RocketMQ
     */
    public void setRocketmq(RocketMQConfig rocketmq) {
        this.rocketmq = rocketmq;
    }

    /**
     * 获取本地
     *
     * @return 获取本地的结果
     */
    public LocalConfig getLocal() {
        return local;
    }

    /**
     * 获取NATS
     *
     * @return 获取NATS的结果
     */
    public NatsConfig getNats() {
        return nats;
    }

    /**
     * 设置NATS
     *
     * @param nats NATS
     */
    public void setNats(NatsConfig nats) {
        this.nats = nats;
    }
}
