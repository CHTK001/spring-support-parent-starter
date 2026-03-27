package com.chua.starter.sync.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.*;

import static com.chua.starter.sync.support.properties.SyncProperties.PRE;

/**
 * 同步协议配置
 * <p>
 * 支持服务端多端口配置，客户端和服务端配置分离
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/04
 */
@Data
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class SyncProperties {

    public static final String PRE = "plugin.sync";

    /**
     * 程序类型：server-服务端，client-客户端，both-同时启用
     */
    private String type = "client";

    /**
     * 主题与处理器映射 (全局)
     */
    private Map<String, String> topics = new LinkedHashMap<>();

    /**
     * 服务端配置
     */
    private ServerConfig server = new ServerConfig();

    /**
     * 客户端配置
     */
    private ClientConfig client = new ClientConfig();

    /**
     * 数据同步交换配置
     */
    private DataSyncConfig dataSync = new DataSyncConfig();

    public static SyncProperties copyOf(SyncProperties source) {
        if (source == null) {
            return new SyncProperties();
        }

        SyncProperties target = new SyncProperties();
        target.type = source.type;
        target.topics = source.topics == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source.topics);
        target.server = ServerConfig.copyOf(source.server);
        target.client = ClientConfig.copyOf(source.client);
        target.dataSync = DataSyncConfig.copyOf(source.dataSync);
        return target;
    }

    // ==================== 服务端配置 ====================

    /**
     * 服务端配置
     */
    @Data
    public static class ServerConfig {

        /**
         * 是否启用服务端
         */
        private boolean enable = false;

        /**
         * 是否启用
         *
         * @return 是否启用
         * @author CH
         * @since 1.0.0
         */
        public boolean isEnable() {
            return enable;
        }

        /**
         * 服务实例列表 (支持多端口)
         */
        private List<ServerInstance> instances = new ArrayList<>();

        /**
         * 默认绑定地址
         */
        private String host = "0.0.0.0";

        /**
         * 默认端口 (当 instances 为空时使用)
         */
        private int port = 19380;

        /**
         * 默认协议
         */
        private String protocol = "rsocket-sync";

        /**
         * 心跳开关
         */
        private boolean heartbeat = true;

        /**
         * 心跳间隔（秒）
         */
        private int heartbeatInterval = 30;

        /**
         * 连接超时时间（毫秒）
         */
        private int connectTimeout = 10000;

        /**
         * 获取所有服务实例配置
         * 如果没有配置 instances，则返回默认配置
         */
        public List<ServerInstance> getEffectiveInstances() {
            if (instances == null || instances.isEmpty()) {
                var defaultInstance = new ServerInstance();
                defaultInstance.name = "default";
                defaultInstance.host = host;
                defaultInstance.port = port;
                defaultInstance.protocol = protocol;
                return List.of(defaultInstance);
            }
            return instances;
        }

        public static ServerConfig copyOf(ServerConfig source) {
            ServerConfig target = new ServerConfig();
            if (source == null) {
                return target;
            }

            target.enable = source.enable;
            target.instances = source.instances == null
                    ? new ArrayList<>()
                    : source.instances.stream().map(ServerInstance::copyOf).toList();
            target.host = source.host;
            target.port = source.port;
            target.protocol = source.protocol;
            target.heartbeat = source.heartbeat;
            target.heartbeatInterval = source.heartbeatInterval;
            target.connectTimeout = source.connectTimeout;
            return target;
        }

    }

    /**
     * 服务实例配置 (支持多端口)
     */
    @Data
    public static class ServerInstance {

        /**
         * 实例名称 (用于标识)
         */
        private String name = UUID.randomUUID().toString();

        /**
         * 是否启用
         *
         * @return 是否启用
         * @author CH
         * @since 1.0.0
         */
        public boolean isEnable() {
            return enable;
        }

        /**
         * 获取实例名称
         *
         * @return 实例名称
         * @author CH
         * @since 1.0.0
         */
        public String getName() {
            return name;
        }

        /**
         * 获取主题映射
         *
         * @return 主题映射
         * @author CH
         * @since 1.0.0
         */
        public Map<String, String> getTopics() {
            return topics;
        }

        /**
         * 绑定地址
         */
        private String host = "0.0.0.0";

        /**
         * 端口
         */
        private int port = 19380;

        /**
         * 协议类型
         */
        private String protocol = "rsocket-sync";

        /**
         * 是否启用
         */
        private boolean enable = true;

        /**
         * 描述
         */
        private String description;

        /**
         * 该实例的主题映射 (可覆盖全局)
         */
        private Map<String, String> topics = new LinkedHashMap<>();

        public static ServerInstance copyOf(ServerInstance source) {
            ServerInstance target = new ServerInstance();
            if (source == null) {
                return target;
            }

            target.name = source.name;
            target.host = source.host;
            target.port = source.port;
            target.protocol = source.protocol;
            target.enable = source.enable;
            target.description = source.description;
            target.topics = source.topics == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source.topics);
            return target;
        }

    }

    // ==================== 客户端配置 ====================

    /**
     * 客户端配置
     */
    @Data
    public static class ClientConfig {

        /**
         * 是否启用客户端
         */
        private boolean enable = false;

        /**
         * 是否启用
         *
         * @return 是否启用
         * @author CH
         * @since 1.0.0
         */
        public boolean isEnable() {
            return enable;
        }

        /**
         * 实例ID (默认自动生成)
         */
        private String instanceId;

        /**
         * 客户端 IP 地址
         * <p>
         * 多网卡场景下指定使用的 IP 地址，为空则自动获取
         * </p>
         */
        private String ipAddress;

        /**
         * 协议类型
         */
        private String protocol = "rsocket";

        /**
         * 服务端地址
         */
        private String serverHost = "localhost";

        /**
         * 服务端端口
         */
        private int serverPort = 19380;

        /**
         * 服务端地址 (完整格式，优先级高于 serverHost:serverPort)
         * 格式：host:port 或 ws://host:port
         */
        private String serverAddress;

        /**
         * 心跳开关
         */
        private boolean heartbeat = true;

        /**
         * 心跳间隔（秒）
         */
        private int heartbeatInterval = 30;

        /**
         * 连接超时时间（毫秒）
         */
        private int connectTimeout = 10000;

        /**
         * 是否启用自动重连
         */
        private boolean autoReconnect = true;

        /**
         * 最大重连次数，-1表示无限重连
         */
        private int maxReconnectAttempts = -1;

        /**
         * 重连间隔时间（毫秒）
         */
        private long reconnectInterval = 5000;

        /**
         * 支持的功能列表
         */
        private String[] capabilities = new String[]{"job", "actuator", "file", "log"};

        /**
         * 扩展元数据
         */
        private Map<String, Object> metadata = new HashMap<>();

        /**
         * 连接后是否自动注册
         */
        private boolean autoRegister = true;

        /**
         * 注册主题
         */
        private String registerTopic = "sync/client/register";

        /**
         * 心跳主题
         */
        private String heartbeatTopic = "sync/client/heartbeat";

        /**
         * 下线主题
         */
        private String offlineTopic = "sync/client/offline";

        /**
         * 获取有效的服务端地址
         */
        public String getEffectiveServerAddress() {
            if (serverAddress != null && !serverAddress.isEmpty()) {
                return serverAddress;
            }
            return serverHost + ":" + serverPort;
        }

        public static ClientConfig copyOf(ClientConfig source) {
            ClientConfig target = new ClientConfig();
            if (source == null) {
                return target;
            }

            target.enable = source.enable;
            target.instanceId = source.instanceId;
            target.ipAddress = source.ipAddress;
            target.protocol = source.protocol;
            target.serverHost = source.serverHost;
            target.serverPort = source.serverPort;
            target.serverAddress = source.serverAddress;
            target.heartbeat = source.heartbeat;
            target.heartbeatInterval = source.heartbeatInterval;
            target.connectTimeout = source.connectTimeout;
            target.autoReconnect = source.autoReconnect;
            target.maxReconnectAttempts = source.maxReconnectAttempts;
            target.reconnectInterval = source.reconnectInterval;
            target.capabilities = source.capabilities == null ? null : Arrays.copyOf(source.capabilities, source.capabilities.length);
            target.metadata = source.metadata == null ? new HashMap<>() : new HashMap<>(source.metadata);
            target.autoRegister = source.autoRegister;
            target.registerTopic = source.registerTopic;
            target.heartbeatTopic = source.heartbeatTopic;
            target.offlineTopic = source.offlineTopic;
            return target;
        }

    }

    // ==================== 便捷方法 ====================

    /**
     * 是否启用服务端
     *
     * @return 是否启用
     * @author CH
     * @since 1.0.0
     */
    public boolean isServerEnabled() {
        return ("server".equalsIgnoreCase(type) || "both".equalsIgnoreCase(type))
                && server.isEnable();
    }

    /**
     * 是否启用客户端
     *
     * @return 是否启用
     * @author CH
     * @since 1.0.0
     */
    public boolean isClientEnabled() {
        return ("client".equalsIgnoreCase(type) || "both".equalsIgnoreCase(type))
                && client.isEnable();
    }

    /**
     * 获取客户端配置
     *
     * @return 客户端配置
     * @author CH
     * @since 1.0.0
     */
    public ClientConfig getClient() {
        return client;
    }

    /**
     * 获取服务端配置
     *
     * @return 服务端配置
     * @author CH
     * @since 1.0.0
     */
    public ServerConfig getServer() {
        return server;
    }

    /**
     * 获取主题映射
     *
     * @return 主题映射
     * @author CH
     * @since 1.0.0
     */
    public Map<String, String> getTopics() {
        return topics;
    }

    /**
     * 获取数据同步配置
     *
     * @return 数据同步配置
     */
    public DataSyncConfig getDataSync() {
        return dataSync;
    }

    /**
     * 数据同步交换配置
     */
    @Data
    public static class DataSyncConfig {

        /**
         * 是否启用数据同步交换服务
         */
        private boolean enabled = true;

        /**
         * 主题前缀
         */
        private String topicPrefix = "sync/data";

        /**
         * 默认请求超时时间（毫秒）
         */
        private long requestTimeout = 30000L;

        /**
         * 默认逻辑通道
         */
        private String defaultChannel = "default";

        public static DataSyncConfig copyOf(DataSyncConfig source) {
            DataSyncConfig target = new DataSyncConfig();
            if (source == null) {
                return target;
            }

            target.enabled = source.enabled;
            target.topicPrefix = source.topicPrefix;
            target.requestTimeout = source.requestTimeout;
            target.defaultChannel = source.defaultChannel;
            return target;
        }
    }
}
