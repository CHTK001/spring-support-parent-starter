package com.chua.starter.queue.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 消息队列配置属性（配置前缀：{@code plugin.queue}）。
 *
 * <p>由 {@code QueueAutoConfiguration} 经 {@code @EnableConfigurationProperties} 注册；
 * 总开关 {@code plugin.queue.enable=true} 开启后，本类及其分组配置才参与装配。</p>
 *
 * <p>完整 YAML 配置示例：</p>
 * <pre>{@code
 * plugin:
 *   queue:
 *     enable: true                       # 总开关，默认 false
 *     type: local                        # 队列类型，默认 chronicle-queue；本模块仅内置 local
 *     default-destination: order.events  # 默认目标地址（预留，当前代码未消费）
 *     local:
 *       delay-threads: 2                 # 本地延迟消息调度线程数，默认 2（<=0 回落 2）
 *     mqtt:
 *       address: tcp://localhost:1883    # MQTT 服务器地址
 *       client-id: your-client-id        # 客户端标识
 *       username: your-username          # 凭据（占位符，请勿提交真实口令）
 *       password: your-password
 *       qos: 1                           # QoS 级别
 *       connection-timeout: 30           # 连接超时（秒）
 *       keep-alive-interval: 60          # 心跳间隔（秒）
 *     kafka:
 *       bootstrap-servers: localhost:9092
 *       group-id: default-group
 *       auto-offset-reset: earliest
 *       enable-auto-commit: true
 *       auto-commit-interval-ms: 5000
 *       acks: all
 *     rabbitmq:
 *       host: localhost
 *       port: 5672
 *       username: your-username
 *       password: your-password
 *       virtual-host: /
 *       connection-timeout: 60000        # 毫秒
 *     rocketmq:
 *       name-server: localhost:9876
 *       producer-group: default-producer
 *       consumer-group: default-consumer
 *       send-msg-timeout: 3000           # 毫秒
 *       max-message-size: 4194304        # 字节
 *     nats:
 *       url: nats://localhost:4222
 *       username: your-username
 *       password: your-password
 *       token: your-token
 *       consumer-group: default-group
 *       connection-timeout: 5000         # 毫秒
 *       reconnect-wait: 2000             # 毫秒
 *       max-reconnects: 60
 * }</pre>
 *
 * <p>注意：mqtt/kafka/rabbitmq/rocketmq/nats 分组在本模块内只是属性模型，
 * 需业务方自行引入客户端并提供对应 {@code MessageTemplate} Bean 后才真正生效。</p>
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
     * 是否启用（plugin.queue.enable）。
     * <p>含义：队列自动装配总开关；{@code QueueAutoConfiguration} 以
     * {@code @ConditionalOnProperty(name = "enable", havingValue = "true")} 门控，
     * 未配置或为 false 时模块不注册任何队列 Bean。</p>
     * <p>默认值：{@code false}（字段初始化值，无其它回落逻辑）。</p>
     * <pre>{@code
     * plugin:
     *   queue:
     *     enable: true
     * }</pre>
     */
    private boolean enable = false;

    /**
     * 消息队列类型（plugin.queue.type）。
     * <p>含义：选择队列实现；本模块仅内置 {@code local}（Guava EventBus 本地实现），
     * 其余取值（chronicle-queue/mqtt/kafka/rabbitmq/rocketmq/nats）为属性模型，
     * 需业务方提供 {@code getType()} 匹配的 {@code MessageTemplate} Bean。</p>
     * <p>默认值：{@code chronicle-queue}（字段初始化值；模块内不存在 Chronicle Queue 实现，
     * 保持默认且不自备 Bean 时容器中没有 MessageTemplate）。</p>
     * <pre>{@code
     * plugin:
     *   queue:
     *     type: local
     * }</pre>
     */
    private String type = "chronicle-queue";

    /**
     * 默认目标地址（plugin.queue.default-destination）。
     * <p>含义：预留的默认发送目标；当前模块代码未消费该属性。</p>
     * <p>默认值：未设置（{@code null}，字段无初始化值；为空时无回落行为）。</p>
     * <pre>{@code
     * plugin:
     *   queue:
     *     default-destination: order.events
     * }</pre>
     */
    private String defaultDestination;

    /**
     * 本地队列配置（plugin.queue.local，基于 Guava EventBus）。
     * <p>含义：{@code type=local} 时 {@code LocalMessageTemplate} 的行为参数分组。</p>
     * <p>默认值：{@code new LocalConfig()}（delay-threads 默认 2）。</p>
     * <pre>{@code
     * plugin:
     *   queue:
     *     local:
     *       delay-threads: 2
     * }</pre>
     */
    private LocalConfig local = new LocalConfig();

    /**
     * MQTT 配置（plugin.queue.mqtt）。
     * <p>含义：MQTT 接入参数分组；本模块未内置 MQTT 实现，需业务方自备客户端与
     * {@code MessageTemplate} Bean 后消费。</p>
     * <p>默认值：{@code new MqttConfig()}（address 默认 tcp://localhost:1883，qos 默认 1）。</p>
     * <pre>{@code
     * plugin:
     *   queue:
     *     mqtt:
     *       address: tcp://localhost:1883
     *       client-id: your-client-id
     *       username: your-username
     *       password: your-password
     *       qos: 1
     *       connection-timeout: 30
     *       keep-alive-interval: 60
     * }</pre>
     */
    private MqttConfig mqtt = new MqttConfig();

    /**
     * Kafka 配置（plugin.queue.kafka）。
     * <p>含义：Kafka 接入参数分组；本模块未内置 Kafka 实现，需业务方自备客户端与
     * {@code MessageTemplate} Bean 后消费。</p>
     * <p>默认值：{@code new KafkaConfig()}（bootstrap-servers 默认 localhost:9092，
     * group-id 默认 default-group，acks 默认 all）。</p>
     * <pre>{@code
     * plugin:
     *   queue:
     *     kafka:
     *       bootstrap-servers: localhost:9092
     *       group-id: default-group
     *       auto-offset-reset: earliest
     *       enable-auto-commit: true
     *       auto-commit-interval-ms: 5000
     *       acks: all
     * }</pre>
     */
    private KafkaConfig kafka = new KafkaConfig();

    /**
     * RabbitMQ 配置（plugin.queue.rabbitmq）。
     * <p>含义：RabbitMQ 接入参数分组；本模块未内置 RabbitMQ 实现，需业务方自备客户端与
     * {@code MessageTemplate} Bean 后消费。</p>
     * <p>默认值：{@code new RabbitMQConfig()}（host 默认 localhost，port 默认 5672，
     * username/password 默认空串，virtual-host 默认 /）。</p>
     * <pre>{@code
     * plugin:
     *   queue:
     *     rabbitmq:
     *       host: localhost
     *       port: 5672
     *       username: your-username
     *       password: your-password
     *       virtual-host: /
     *       connection-timeout: 60000
     * }</pre>
     */
    private RabbitMQConfig rabbitmq = new RabbitMQConfig();

    /**
     * NATS 配置（plugin.queue.nats）。
     * <p>含义：NATS 接入参数分组；本模块未内置 NATS 实现，需业务方自备客户端与
     * {@code MessageTemplate} Bean 后消费。</p>
     * <p>默认值：{@code new NatsConfig()}（url 默认 nats://localhost:4222，
     * consumer-group 默认 default-group，max-reconnects 默认 60）。</p>
     * <pre>{@code
     * plugin:
     *   queue:
     *     nats:
     *       url: nats://localhost:4222
     *       username: your-username
     *       password: your-password
     *       token: your-token
     *       consumer-group: default-group
     *       connection-timeout: 5000
     *       reconnect-wait: 2000
     *       max-reconnects: 60
     * }</pre>
     */
    private NatsConfig nats = new NatsConfig();

    /**
     * RocketMQ 配置（plugin.queue.rocketmq）。
     * <p>含义：RocketMQ 接入参数分组；本模块未内置 RocketMQ 实现，需业务方自备客户端与
     * {@code MessageTemplate} Bean 后消费。</p>
     * <p>默认值：{@code new RocketMQConfig()}（name-server 默认 localhost:9876，
     * producer-group 默认 default-producer，consumer-group 默认 default-consumer）。</p>
     * <pre>{@code
     * plugin:
     *   queue:
     *     rocketmq:
     *       name-server: localhost:9876
     *       producer-group: default-producer
     *       consumer-group: default-consumer
     *       send-msg-timeout: 3000
     *       max-message-size: 4194304
     * }</pre>
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
         * 延迟消息调度线程数（plugin.queue.local.delay-threads）。
         * <p>含义：本地实现调度延迟消息（{@code sendDelayed}）的线程数。</p>
         * <p>默认值：{@code 2}；配置为 0 或负数时，{@code LocalMessageTemplate} 构造时回落为 {@code 2}。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     local:
         *       delay-threads: 2
         * }</pre>
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
         * 服务器地址（plugin.queue.mqtt.address）。
         * <p>含义：MQTT broker 连接地址。</p>
         * <p>默认值：{@code tcp://localhost:1883}（字段初始化值；本模块未内置 MQTT 实现，为空时无回落）。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     mqtt:
         *       address: tcp://localhost:1883
         * }</pre>
         */
        private String address = "tcp://localhost:1883";

        /**
         * 客户端标识（plugin.queue.mqtt.client-id）。
         * <p>含义：MQTT 客户端 ID。</p>
         * <p>默认值：未设置（{@code null}，字段无初始化值；为空时无回落）。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     mqtt:
         *       client-id: your-client-id
         * }</pre>
         */
        private String clientId;

        /**
         * 用户名（plugin.queue.mqtt.username）。
         * <p>含义：MQTT 认证用户名。</p>
         * <p>默认值：未设置（{@code null}，字段无初始化值；为空时无回落）。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     mqtt:
         *       username: your-username
         * }</pre>
         */
        private String username;

        /**
         * 密码（plugin.queue.mqtt.password）。
         * <p>含义：MQTT 认证密码；示例中的 your-password 为占位符，请勿提交真实口令。</p>
         * <p>默认值：未设置（{@code null}，字段无初始化值；为空时无回落）。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     mqtt:
         *       password: your-password
         * }</pre>
         */
        private String password;

        /**
         * qos级别（plugin.queue.mqtt.qos）。
         * <p>含义：MQTT 服务质量等级（0/1/2）。</p>
         * <p>默认值：{@code 1}（字段初始化值；无 <=0 回落逻辑）。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     mqtt:
         *       qos: 1
         * }</pre>
         */
        private int qos = 1;

        /**
         * 连接超时（秒）（plugin.queue.mqtt.connection-timeout）。
         * <p>含义：建立 MQTT 连接的超时时间。</p>
         * <p>默认值：{@code 30} 秒（字段初始化值；无 <=0 回落逻辑）。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     mqtt:
         *       connection-timeout: 30
         * }</pre>
         */
        // [P3C A20 豁免]
        private int connectionTimeout = 30;

        /**
         * 心跳间隔（秒）（plugin.queue.mqtt.keep-alive-interval）。
         * <p>含义：MQTT keep-alive 心跳间隔。</p>
         * <p>默认值：{@code 60} 秒（字段初始化值；无 <=0 回落逻辑）。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     mqtt:
         *       keep-alive-interval: 60
         * }</pre>
         */
        // [P3C A20 豁免]
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
         * Bootstrap服务器（plugin.queue.kafka.bootstrap-servers）。
         * <p>含义：Kafka 集群接入地址列表。</p>
         * <p>默认值：{@code localhost:9092}（字段初始化值；为空时无回落，本模块未内置 Kafka 实现）。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     kafka:
         *       bootstrap-servers: localhost:9092
         * }</pre>
         */
        private String bootstrapServers = "localhost:9092";

        /**
         * 消费者组标识（plugin.queue.kafka.group-id）。
         * <p>含义：Kafka 消费组 ID。</p>
         * <p>默认值：{@code default-group}（字段初始化值；为空时无回落）。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     kafka:
         *       group-id: default-group
         * }</pre>
         */
        private String groupId = "default-group";

        /**
         * 自动偏移重置策略（plugin.queue.kafka.auto-offset-reset）。
         * <p>含义：无初始位点或位点越界时的重置策略（earliest/latest/none）。</p>
         * <p>默认值：{@code earliest}（字段初始化值；为空时无回落）。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     kafka:
         *       auto-offset-reset: earliest
         * }</pre>
         */
        private String autoOffsetReset = "earliest";

        /**
         * 是否自动提交（plugin.queue.kafka.enable-auto-commit）。
         * <p>含义：消费者是否自动提交消费位点。</p>
         * <p>默认值：{@code true}（字段初始化值，无其它回落逻辑）。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     kafka:
         *       enable-auto-commit: true
         * }</pre>
         */
        private boolean enableAutoCommit = true;

        /**
         * 自动提交间隔（毫秒）（plugin.queue.kafka.auto-commit-interval-ms）。
         * <p>含义：自动提交位点的间隔时长。</p>
         * <p>默认值：{@code 5000} 毫秒（字段初始化值；无 <=0 回落逻辑）。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     kafka:
         *       auto-commit-interval-ms: 5000
         * }</pre>
         */
        // [P3C A20 豁免]
        private int autoCommitIntervalMs = 5000;

        /**
         * 生产者确认机制（plugin.queue.kafka.acks）。
         * <p>含义：生产者等待 broker 确认的级别（0/1/all）。</p>
         * <p>默认值：{@code all}（字段初始化值；为空时无回落）。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     kafka:
         *       acks: all
         * }</pre>
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
         * 主机地址（plugin.queue.rabbitmq.host）。
         * <p>含义：RabbitMQ 服务器主机。</p>
         * <p>默认值：{@code localhost}（字段初始化值；为空时无回落，本模块未内置 RabbitMQ 实现）。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     rabbitmq:
         *       host: localhost
         * }</pre>
         */
        private String host = "localhost";

        /**
         * 端口（plugin.queue.rabbitmq.port）。
         * <p>含义：RabbitMQ AMQP 监听端口。</p>
         * <p>默认值：{@code 5672}（字段初始化值；无 <=0 回落逻辑）。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     rabbitmq:
         *       port: 5672
         * }</pre>
         */
        // [P3C A20 豁免]
        private int port = 5672;

        /**
         * 用户名（plugin.queue.rabbitmq.username）。
         * <p>含义：RabbitMQ 认证用户名。</p>
         * <p>默认值：空串 {@code ""}（字段初始化值；为空串时无回落）。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     rabbitmq:
         *       username: your-username
         * }</pre>
         */
        private String username = "";

        /**
         * 密码（plugin.queue.rabbitmq.password）。
         * <p>含义：RabbitMQ 认证密码；示例中的 your-password 为占位符，请勿提交真实口令。</p>
         * <p>默认值：空串 {@code ""}（字段初始化值；为空串时无回落）。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     rabbitmq:
         *       password: your-password
         * }</pre>
         */
        private String password = "";

        /**
         * 虚拟主机（plugin.queue.rabbitmq.virtual-host）。
         * <p>含义：RabbitMQ vhost 隔离单元。</p>
         * <p>默认值：{@code /}（字段初始化值；为空时无回落）。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     rabbitmq:
         *       virtual-host: /
         * }</pre>
         */
        private String virtualHost = "/";

        /**
         * 连接超时（毫秒）（plugin.queue.rabbitmq.connection-timeout）。
         * <p>含义：建立 RabbitMQ 连接的超时时间。</p>
         * <p>默认值：{@code 60000} 毫秒（字段初始化值；无 <=0 回落逻辑）。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     rabbitmq:
         *       connection-timeout: 60000
         * }</pre>
         */
        // [P3C A20 豁免]
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
         * 服务器地址（plugin.queue.nats.url）。
         * <p>含义：NATS 服务器连接地址。</p>
         * <p>默认值：{@code nats://localhost:4222}（字段初始化值；为空时无回落，本模块未内置 NATS 实现）。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     nats:
         *       url: nats://localhost:4222
         * }</pre>
         */
        private String url = "nats://localhost:4222";

        /**
         * 用户名（plugin.queue.nats.username）。
         * <p>含义：NATS 认证用户名。</p>
         * <p>默认值：未设置（{@code null}，字段无初始化值；为空时无回落）。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     nats:
         *       username: your-username
         * }</pre>
         */
        private String username;

        /**
         * 密码（plugin.queue.nats.password）。
         * <p>含义：NATS 认证密码；示例中的 your-password 为占位符，请勿提交真实口令。</p>
         * <p>默认值：未设置（{@code null}，字段无初始化值；为空时无回落）。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     nats:
         *       password: your-password
         * }</pre>
         */
        private String password;

        /**
         * 令牌认证（plugin.queue.nats.token）。
         * <p>含义：NATS token 认证凭据；示例中的 your-token 为占位符，请勿提交真实凭据。</p>
         * <p>默认值：未设置（{@code null}，字段无初始化值；为空时无回落）。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     nats:
         *       token: your-token
         * }</pre>
         */
        private String token;

        /**
         * 消费者组（plugin.queue.nats.consumer-group）。
         * <p>含义：NATS 消费组标识。</p>
         * <p>默认值：{@code default-group}（字段初始化值；为空时无回落）。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     nats:
         *       consumer-group: default-group
         * }</pre>
         */
        private String consumerGroup = "default-group";

        /**
         * 连接超时（毫秒）（plugin.queue.nats.connection-timeout）。
         * <p>含义：建立 NATS 连接的超时时间。</p>
         * <p>默认值：{@code 5000} 毫秒（字段初始化值；无 <=0 回落逻辑）。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     nats:
         *       connection-timeout: 5000
         * }</pre>
         */
        // [P3C A20 豁免]
        private long connectionTimeout = 5000;

        /**
         * 重连等待（毫秒）（plugin.queue.nats.reconnect-wait）。
         * <p>含义：断线后两次重连之间的等待时长。</p>
         * <p>默认值：{@code 2000} 毫秒（字段初始化值；无 <=0 回落逻辑）。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     nats:
         *       reconnect-wait: 2000
         * }</pre>
         */
        // [P3C A20 豁免]
        private long reconnectWait = 2000;

        /**
         * 最大重连次数（-1 表示无限重连）（plugin.queue.nats.max-reconnects）。
         * <p>含义：断线重连的最大尝试次数。</p>
         * <p>默认值：{@code 60}（字段初始化值；无 <=0 回落逻辑，-1 表示无限重连）。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     nats:
         *       max-reconnects: 60
         * }</pre>
         */
        // [P3C A20 豁免]
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
         * 名称服务端地址（plugin.queue.rocketmq.name-server）。
         * <p>含义：RocketMQ NameServer 地址。</p>
         * <p>默认值：{@code localhost:9876}（字段初始化值；为空时无回落，本模块未内置 RocketMQ 实现）。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     rocketmq:
         *       name-server: localhost:9876
         * }</pre>
         */
        private String nameServer = "localhost:9876";

        /**
         * 生产者组（plugin.queue.rocketmq.producer-group）。
         * <p>含义：RocketMQ 生产者组标识。</p>
         * <p>默认值：{@code default-producer}（字段初始化值；为空时无回落）。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     rocketmq:
         *       producer-group: default-producer
         * }</pre>
         */
        private String producerGroup = "default-producer";

        /**
         * 消费者组（plugin.queue.rocketmq.consumer-group）。
         * <p>含义：RocketMQ 消费者组标识。</p>
         * <p>默认值：{@code default-consumer}（字段初始化值；为空时无回落）。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     rocketmq:
         *       consumer-group: default-consumer
         * }</pre>
         */
        private String consumerGroup = "default-consumer";

        /**
         * 发送超时（毫秒）（plugin.queue.rocketmq.send-msg-timeout）。
         * <p>含义：同步发送消息的超时时间。</p>
         * <p>默认值：{@code 3000} 毫秒（字段初始化值；无 <=0 回落逻辑）。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     rocketmq:
         *       send-msg-timeout: 3000
         * }</pre>
         */
        // [P3C A20 豁免]
        private int sendMsgTimeout = 3000;

        /**
         * 最大消息大小（字节）（plugin.queue.rocketmq.max-message-size）。
         * <p>含义：单条消息的最大字节数。</p>
         * <p>默认值：{@code 4194304} 字节，即 4MB（字段初始化值；无 <=0 回落逻辑）。</p>
         * <pre>{@code
         * plugin:
         *   queue:
         *     rocketmq:
         *       max-message-size: 4194304
         * }</pre>
         */
        // [P3C A20 豁免]
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
