package com.chua.starter.sync.support.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.*;

import static com.chua.starter.sync.support.properties.SyncProperties.PRE;

/**
 * 同步协议配置
 * <p>
 * 支持服务端多端口配置，客户端和服务端配置分离
 * </p>
 *
 * @version 1.0.0
 * @since 2024/12/04
 * @author CH
 */
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class SyncProperties {

    /** PRE */
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
     * 同步消息传输加密密钥。
     * <p>
     * 服务端与客户端需配置一致。配置后，消息 payload 在传输层自动 AES-GCM 加密，
     * 使用方（业务 handler）无感知；未配置时明文传输（向后兼容）。
     * </p>
     */
    private String cryptoSecret;

    /**
     * 数据同步交换配置
     */
    private DataSyncConfig dataSync = new DataSyncConfig();

    /** 获取Type */
    public String getType() {
        return type;
    }

    /** 设置Type */
    public void setType(String type) {
        this.type = type;
    }

    /** 获取Topics */
    public Map<String, String> getTopics() {
        return topics;
    }

    /** 设置Topics */
    public void setTopics(Map<String, String> topics) {
        this.topics = topics;
    }

    /** 获取Server */
    public ServerConfig getServer() {
        return server;
    }

    /** 设置Server */
    public void setServer(ServerConfig server) {
        this.server = server;
    }

    /** 获取Client */
    public ClientConfig getClient() {
        return client;
    }

    /** 设置Client */
    public void setClient(ClientConfig client) {
        this.client = client;
    }

    /** 获取DataSync */
    public DataSyncConfig getDataSync() {
        return dataSync;
    }

    /** 设置DataSync */
    public void setDataSync(DataSyncConfig dataSync) {
        this.dataSync = dataSync;
    }

    /** 复制Of */
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
        target.cryptoSecret = source.cryptoSecret;
        return target;
    }

    /** 获取CryptoSecret */
    public String getCryptoSecret() {
        return cryptoSecret;
    }

    /** 设置CryptoSecret */
    public void setCryptoSecret(String cryptoSecret) {
        this.cryptoSecret = cryptoSecret;
    }

    // ==================== 服务端配置 ====================

    /**
     * 服务端配置
     */
    public static class ServerConfig {

        /**
         * 是否启用服务端
         */
        private boolean enable = false;

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
        private String protocol = "http";

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

        /** 是否启用 */
        public boolean isEnable() {
            return enable;
        }

        /** 设置启用 */
        public void setEnable(boolean enable) {
            this.enable = enable;
        }

        /** 获取Instances */
        public List<ServerInstance> getInstances() {
            return instances;
        }

        /** 设置Instances */
        public void setInstances(List<ServerInstance> instances) {
            this.instances = instances;
        }

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

        /** 获取Protocol */
        public String getProtocol() {
            return protocol;
        }

        /** 设置Protocol */
        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        /** 是否Heartbeat */
        public boolean isHeartbeat() {
            return heartbeat;
        }

        /** 设置Heartbeat */
        public void setHeartbeat(boolean heartbeat) {
            this.heartbeat = heartbeat;
        }

        /** 获取HeartbeatInterval */
        public int getHeartbeatInterval() {
            return heartbeatInterval;
        }

        /** 设置HeartbeatInterval */
        public void setHeartbeatInterval(int heartbeatInterval) {
            this.heartbeatInterval = heartbeatInterval;
        }

        /** 获取连接Timeout */
        public int getConnectTimeout() {
            return connectTimeout;
        }

        /** 设置连接Timeout */
        public void setConnectTimeout(int connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

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

        /** 复制Of */
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

    // ==================== 服务实例配置 ====================

    /**
     * 服务实例配置 (支持多端口)
     */
    public static class ServerInstance {

        /**
         * 实例名称 (用于标识)
         */
        private String name = UUID.randomUUID().toString();

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

        /** 获取Name */
        public String getName() {
            return name;
        }

        /** 设置Name */
        public void setName(String name) {
            this.name = name;
        }

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

        /** 获取Protocol */
        public String getProtocol() {
            return protocol;
        }

        /** 设置Protocol */
        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        /** 是否启用 */
        public boolean isEnable() {
            return enable;
        }

        /** 设置启用 */
        public void setEnable(boolean enable) {
            this.enable = enable;
        }

        /** 获取Description */
        public String getDescription() {
            return description;
        }

        /** 设置Description */
        public void setDescription(String description) {
            this.description = description;
        }

        /** 获取Topics */
        public Map<String, String> getTopics() {
            return topics;
        }

        /** 设置Topics */
        public void setTopics(Map<String, String> topics) {
            this.topics = topics;
        }

        /** 复制Of */
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
    public static class ClientConfig {

        /**
         * 是否启用客户端
         */
        private boolean enable = false;

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
        private String protocol = "http";

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

        /** 是否启用 */
        public boolean isEnable() {
            return enable;
        }

        /** 设置启用 */
        public void setEnable(boolean enable) {
            this.enable = enable;
        }

        /** 获取InstanceId */
        public String getInstanceId() {
            return instanceId;
        }

        /** 设置InstanceId */
        public void setInstanceId(String instanceId) {
            this.instanceId = instanceId;
        }

        /** 获取IpAddress */
        public String getIpAddress() {
            return ipAddress;
        }

        /** 设置IpAddress */
        public void setIpAddress(String ipAddress) {
            this.ipAddress = ipAddress;
        }

        /** 获取Protocol */
        public String getProtocol() {
            return protocol;
        }

        /** 设置Protocol */
        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        /** 获取ServerHost */
        public String getServerHost() {
            return serverHost;
        }

        /** 设置ServerHost */
        public void setServerHost(String serverHost) {
            this.serverHost = serverHost;
        }

        /** 获取ServerPort */
        public int getServerPort() {
            return serverPort;
        }

        /** 设置ServerPort */
        public void setServerPort(int serverPort) {
            this.serverPort = serverPort;
        }

        /** 获取ServerAddress */
        public String getServerAddress() {
            return serverAddress;
        }

        /** 设置ServerAddress */
        public void setServerAddress(String serverAddress) {
            this.serverAddress = serverAddress;
        }

        /** 是否Heartbeat */
        public boolean isHeartbeat() {
            return heartbeat;
        }

        /** 设置Heartbeat */
        public void setHeartbeat(boolean heartbeat) {
            this.heartbeat = heartbeat;
        }

        /** 获取HeartbeatInterval */
        public int getHeartbeatInterval() {
            return heartbeatInterval;
        }

        /** 设置HeartbeatInterval */
        public void setHeartbeatInterval(int heartbeatInterval) {
            this.heartbeatInterval = heartbeatInterval;
        }

        /** 获取连接Timeout */
        public int getConnectTimeout() {
            return connectTimeout;
        }

        /** 设置连接Timeout */
        public void setConnectTimeout(int connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        /** 是否AutoReconnect */
        public boolean isAutoReconnect() {
            return autoReconnect;
        }

        /** 设置AutoReconnect */
        public void setAutoReconnect(boolean autoReconnect) {
            this.autoReconnect = autoReconnect;
        }

        /** 获取最大值ReconnectAttempts */
        public int getMaxReconnectAttempts() {
            return maxReconnectAttempts;
        }

        /** 设置最大值ReconnectAttempts */
        public void setMaxReconnectAttempts(int maxReconnectAttempts) {
            this.maxReconnectAttempts = maxReconnectAttempts;
        }

        /** 获取ReconnectInterval */
        public long getReconnectInterval() {
            return reconnectInterval;
        }

        /** 设置ReconnectInterval */
        public void setReconnectInterval(long reconnectInterval) {
            this.reconnectInterval = reconnectInterval;
        }

        /** 获取Capabilities */
        public String[] getCapabilities() {
            return capabilities;
        }

        /** 设置Capabilities */
        public void setCapabilities(String[] capabilities) {
            this.capabilities = capabilities;
        }

        /** 获取Metadata */
        public Map<String, Object> getMetadata() {
            return metadata;
        }

        /** 设置Metadata */
        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }

        /** 是否Auto注册 */
        public boolean isAutoRegister() {
            return autoRegister;
        }

        /** 设置Auto注册 */
        public void setAutoRegister(boolean autoRegister) {
            this.autoRegister = autoRegister;
        }

        /** 获取注册Topic */
        public String getRegisterTopic() {
            return registerTopic;
        }

        /** 设置注册Topic */
        public void setRegisterTopic(String registerTopic) {
            this.registerTopic = registerTopic;
        }

        /** 获取HeartbeatTopic */
        public String getHeartbeatTopic() {
            return heartbeatTopic;
        }

        /** 设置HeartbeatTopic */
        public void setHeartbeatTopic(String heartbeatTopic) {
            this.heartbeatTopic = heartbeatTopic;
        }

        /** 获取OfflineTopic */
        public String getOfflineTopic() {
            return offlineTopic;
        }

        /** 设置OfflineTopic */
        public void setOfflineTopic(String offlineTopic) {
            this.offlineTopic = offlineTopic;
        }

        /**
         * 获取有效的服务端地址
         */
        public String getEffectiveServerAddress() {
            if (serverAddress != null && !serverAddress.isEmpty()) {
                return serverAddress;
            }
            return serverHost + ":" + serverPort;
        }

        /** 复制Of */
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
     * @since 1.0.0
     */
    public boolean isClientEnabled() {
        return ("client".equalsIgnoreCase(type) || "both".equalsIgnoreCase(type))
                && client.isEnable();
    }

    /**
     * 数据同步交换配置
     */
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

        /** 是否Enabled */
        public boolean isEnabled() {
            return enabled;
        }

        /** 设置Enabled */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** 获取TopicPrefix */
        public String getTopicPrefix() {
            return topicPrefix;
        }

        /** 设置TopicPrefix */
        public void setTopicPrefix(String topicPrefix) {
            this.topicPrefix = topicPrefix;
        }

        /** 获取RequestTimeout */
        public long getRequestTimeout() {
            return requestTimeout;
        }

        /** 设置RequestTimeout */
        public void setRequestTimeout(long requestTimeout) {
            this.requestTimeout = requestTimeout;
        }

        /** 获取DefaultChannel */
        public String getDefaultChannel() {
            return defaultChannel;
        }

        /** 设置DefaultChannel */
        public void setDefaultChannel(String defaultChannel) {
            this.defaultChannel = defaultChannel;
        }

        /** 复制Of */
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
