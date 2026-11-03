// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.sync.support.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.chua.starter.sync.support.properties.SyncProperties.PRE;

/**
 * 同步协议配置
 * <p>
 * 支持服务端多端口配置，客户端和服务端配置分离
 * </p>
 *
 * @since 2024/12/04
 * @author CH
 */
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class SyncProperties {

    /**
     * PRE
    */
    public static final String PRE = "plugin.sync";

    /**
     * 程序类型：服务端-服务端，客户端-客户端，both-同时启用
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
     * 使用方（业务 处理器）无感知；未配置时明文传输（向后兼容）。
     * </p>
     */
    private String cryptoSecret;

    /**
     * 数据同步交换配置
     */
    private DataSyncConfig dataSync = new DataSyncConfig();

    /**
     * 获取类型
     *
     * @return 获取类型的结果
     */
    public String getType() {
        return type;
    }

    /**
     * 设置类型
     *
     * @param type 类型
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * 获取Topics
     *
     * @return 获取topics的结果
     */
    public Map<String, String> getTopics() {
        return topics;
    }

    /**
     * 设置Topics
     *
     * @param topics topics
     */
    public void setTopics(Map<String, String> topics) {
        this.topics = topics;
    }

    /**
     * 获取服务端
     *
     * @return 获取服务端的结果
     */
    public ServerConfig getServer() {
        return server;
    }

    /**
     * 设置服务端
     *
     * @param server 服务端
     */
    public void setServer(ServerConfig server) {
        this.server = server;
    }

    /**
     * 获取客户端
     *
     * @return 获取客户端的结果
     */
    public ClientConfig getClient() {
        return client;
    }

    /**
     * 设置客户端
     *
     * @param client 客户端
     */
    public void setClient(ClientConfig client) {
        this.client = client;
    }

    /**
     * 获取数据同步
     *
     * @return 获取数据同步的结果
     */
    public DataSyncConfig getDataSync() {
        return dataSync;
    }

    /**
     * 设置数据同步
     *
     * @param dataSync 数据同步
     */
    public void setDataSync(DataSyncConfig dataSync) {
        this.dataSync = dataSync;
    }

    /**
     * 复制的
     *
     * @param source 源
     * @return 副本的的结果
     */
    public static SyncProperties copyOf(SyncProperties source) {
        if (source == null) {
            /**
             * 同步属性。
             */
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

    /**
     * 获取加密密钥
     *
     * @return 加密密钥
     */
    public String getCryptoSecret() {
        return cryptoSecret;
    }

    /**
     * 设置加密密钥
     *
     * @param cryptoSecret 加密密钥
     */
    public void setCryptoSecret(String cryptoSecret) {
        this.cryptoSecret = cryptoSecret;
    }

    // ==================== 服务端配置 ====================

    /**
     * 服务端配置
     * @author CH
     * @since 4.0.0
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

        /**
         * 是否启用
         *
         * @return 是否enable的结果
         */
        public boolean isEnable() {
            return enable;
        }

        /**
         * 设置启用
         *
         * @param enable enable
         */
        public void setEnable(boolean enable) {
            this.enable = enable;
        }

        /**
         * 获取Instances
         *
         * @return 获取instances的结果
         */
        public List<ServerInstance> getInstances() {
            return instances;
        }

        /**
         * 设置Instances
         *
         * @param instances instances
         */
        public void setInstances(List<ServerInstance> instances) {
            this.instances = instances;
        }

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
         * 获取协议
         *
         * @return 获取协议的结果
         */
        public String getProtocol() {
            return protocol;
        }

        /**
         * 设置协议
         *
         * @param protocol 协议
         */
        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        /**
         * 是否心跳
         *
         * @return 是否心跳的结果
         */
        public boolean isHeartbeat() {
            return heartbeat;
        }

        /**
         * 设置心跳
         *
         * @param heartbeat 心跳
         */
        public void setHeartbeat(boolean heartbeat) {
            this.heartbeat = heartbeat;
        }

        /**
         * 获取心跳间隔
         *
         * @return 获取心跳间隔的结果
         */
        public int getHeartbeatInterval() {
            return heartbeatInterval;
        }

        /**
         * 设置心跳间隔
         *
         * @param heartbeatInterval 心跳间隔
         */
        public void setHeartbeatInterval(int heartbeatInterval) {
            this.heartbeatInterval = heartbeatInterval;
        }

        /**
         * 获取连接超时
         *
         * @return 获取连接超时的结果
         */
        public int getConnectTimeout() {
            return connectTimeout;
        }

        /**
         * 设置连接超时
         *
         * @param connectTimeout 连接超时
         */
        public void setConnectTimeout(int connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        /**
         * 获取所有服务实例配置
         * 如果没有配置 instances，则返回默认配置
         * @return 获取effectiveinstances的结果
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

        /**
         * 复制的
         *
         * @param source 源
         * @return 副本的的结果
         */
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
     * @author CH
     * @since 4.0.0
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

        /**
         * 获取名称
         *
         * @return 获取名称的结果
         */
        public String getName() {
            return name;
        }

        /**
         * 设置名称
         *
         * @param name 名称
         */
        public void setName(String name) {
            this.name = name;
        }

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
         * 获取协议
         *
         * @return 获取协议的结果
         */
        public String getProtocol() {
            return protocol;
        }

        /**
         * 设置协议
         *
         * @param protocol 协议
         */
        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        /**
         * 是否启用
         *
         * @return 是否enable的结果
         */
        public boolean isEnable() {
            return enable;
        }

        /**
         * 设置启用
         *
         * @param enable enable
         */
        public void setEnable(boolean enable) {
            this.enable = enable;
        }

        /**
         * 获取Description
         *
         * @return 获取description的结果
         */
        public String getDescription() {
            return description;
        }

        /**
         * 设置Description
         *
         * @param description description
         */
        public void setDescription(String description) {
            this.description = description;
        }

        /**
         * 获取Topics
         *
         * @return 获取topics的结果
         */
        public Map<String, String> getTopics() {
            return topics;
        }

        /**
         * 设置Topics
         *
         * @param topics topics
         */
        public void setTopics(Map<String, String> topics) {
            this.topics = topics;
        }

        /**
         * 复制的
         *
         * @param source 源
         * @return 副本的的结果
         */
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
     * @author CH
     * @since 4.0.0
     */
    public static class ClientConfig {

        /**
         * 是否启用客户端
         */
        private boolean enable = false;

        /**
         * 实例标识 (默认自动生成)
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
         * 服务端地址 (完整格式，优先级高于 服务端主机:服务端端口)
         * 格式：主机:端口 或 ws://主机:端口
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
         * 是否启用
         *
         * @return 是否enable的结果
         */
        public boolean isEnable() {
            return enable;
        }

        /**
         * 设置启用
         *
         * @param enable enable
         */
        public void setEnable(boolean enable) {
            this.enable = enable;
        }

        /**
         * 获取instanceid
         *
         * @return 获取instanceid的结果
         */
        public String getInstanceId() {
            return instanceId;
        }

        /**
         * 设置instanceid
         *
         * @param instanceId instanceid
         */
        public void setInstanceId(String instanceId) {
            this.instanceId = instanceId;
        }

        /**
         * 获取ip地址
         *
         * @return 获取ip地址的结果
         */
        public String getIpAddress() {
            return ipAddress;
        }

        /**
         * 设置ip地址
         *
         * @param ipAddress ip地址
         */
        public void setIpAddress(String ipAddress) {
            this.ipAddress = ipAddress;
        }

        /**
         * 获取协议
         *
         * @return 获取协议的结果
         */
        public String getProtocol() {
            return protocol;
        }

        /**
         * 设置协议
         *
         * @param protocol 协议
         */
        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        /**
         * 获取服务端主机
         *
         * @return 获取服务端主机的结果
         */
        public String getServerHost() {
            return serverHost;
        }

        /**
         * 设置服务端主机
         *
         * @param serverHost 服务端主机
         */
        public void setServerHost(String serverHost) {
            this.serverHost = serverHost;
        }

        /**
         * 获取服务端端口
         *
         * @return 获取服务端端口的结果
         */
        public int getServerPort() {
            return serverPort;
        }

        /**
         * 设置服务端端口
         *
         * @param serverPort 服务端端口
         */
        public void setServerPort(int serverPort) {
            this.serverPort = serverPort;
        }

        /**
         * 获取服务端地址
         *
         * @return 获取服务端地址的结果
         */
        public String getServerAddress() {
            return serverAddress;
        }

        /**
         * 设置服务端地址
         *
         * @param serverAddress 服务端地址
         */
        public void setServerAddress(String serverAddress) {
            this.serverAddress = serverAddress;
        }

        /**
         * 是否心跳
         *
         * @return 是否心跳的结果
         */
        public boolean isHeartbeat() {
            return heartbeat;
        }

        /**
         * 设置心跳
         *
         * @param heartbeat 心跳
         */
        public void setHeartbeat(boolean heartbeat) {
            this.heartbeat = heartbeat;
        }

        /**
         * 获取心跳间隔
         *
         * @return 获取心跳间隔的结果
         */
        public int getHeartbeatInterval() {
            return heartbeatInterval;
        }

        /**
         * 设置心跳间隔
         *
         * @param heartbeatInterval 心跳间隔
         */
        public void setHeartbeatInterval(int heartbeatInterval) {
            this.heartbeatInterval = heartbeatInterval;
        }

        /**
         * 获取连接超时
         *
         * @return 获取连接超时的结果
         */
        public int getConnectTimeout() {
            return connectTimeout;
        }

        /**
         * 设置连接超时
         *
         * @param connectTimeout 连接超时
         */
        public void setConnectTimeout(int connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        /**
         * 是否autoreconnect
         *
         * @return 是否autoreconnect的结果
         */
        public boolean isAutoReconnect() {
            return autoReconnect;
        }

        /**
         * 设置autoreconnect
         *
         * @param autoReconnect autoreconnect
         */
        public void setAutoReconnect(boolean autoReconnect) {
            this.autoReconnect = autoReconnect;
        }

        /**
         * 获取最大值reconnect尝试
         *
         * @return 获取最大reconnect尝试的结果
         */
        public int getMaxReconnectAttempts() {
            return maxReconnectAttempts;
        }

        /**
         * 设置最大值reconnect尝试
         *
         * @param maxReconnectAttempts 最大reconnect尝试
         */
        public void setMaxReconnectAttempts(int maxReconnectAttempts) {
            this.maxReconnectAttempts = maxReconnectAttempts;
        }

        /**
         * 获取reconnect间隔
         *
         * @return 获取reconnect间隔的结果
         */
        public long getReconnectInterval() {
            return reconnectInterval;
        }

        /**
         * 设置reconnect间隔
         *
         * @param reconnectInterval reconnect间隔
         */
        public void setReconnectInterval(long reconnectInterval) {
            this.reconnectInterval = reconnectInterval;
        }

        /**
         * 获取Capabilities
         *
         * @return 获取capabilities的结果
         */
        public String[] getCapabilities() {
            return capabilities;
        }

        /**
         * 设置Capabilities
         *
         * @param capabilities capabilities
         */
        public void setCapabilities(String[] capabilities) {
            this.capabilities = capabilities;
        }

        /**
         * 获取Metadata
         *
         * @return 获取metadata的结果
         */
        public Map<String, Object> getMetadata() {
            return metadata;
        }

        /**
         * 设置Metadata
         *
         * @param metadata metadata
         */
        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }

        /**
         * 是否Auto注册
         *
         * @return 是否auto注册的结果
         */
        public boolean isAutoRegister() {
            return autoRegister;
        }

        /**
         * 设置Auto注册
         *
         * @param autoRegister auto注册
         */
        public void setAutoRegister(boolean autoRegister) {
            this.autoRegister = autoRegister;
        }

        /**
         * 获取注册Topic
         *
         * @return 获取注册topic的结果
         */
        public String getRegisterTopic() {
            return registerTopic;
        }

        /**
         * 设置注册Topic
         *
         * @param registerTopic 注册topic
         */
        public void setRegisterTopic(String registerTopic) {
            this.registerTopic = registerTopic;
        }

        /**
         * 获取心跳topic
         *
         * @return 获取心跳topic的结果
         */
        public String getHeartbeatTopic() {
            return heartbeatTopic;
        }

        /**
         * 设置心跳topic
         *
         * @param heartbeatTopic 心跳topic
         */
        public void setHeartbeatTopic(String heartbeatTopic) {
            this.heartbeatTopic = heartbeatTopic;
        }

        /**
         * 获取offlinetopic
         *
         * @return 获取offlinetopic的结果
         */
        public String getOfflineTopic() {
            return offlineTopic;
        }

        /**
         * 设置offlinetopic
         *
         * @param offlineTopic offlinetopic
         */
        public void setOfflineTopic(String offlineTopic) {
            this.offlineTopic = offlineTopic;
        }

        /**
         * 获取有效的服务端地址
         * @return 获取effective服务端地址的结果
         */
        public String getEffectiveServerAddress() {
            if (serverAddress != null && !serverAddress.isEmpty()) {
                return serverAddress;
            }
            return serverHost + ":" + serverPort;
        }

        /**
         * 复制的
         *
         * @param source 源
         * @return 副本的的结果
         */
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
     * @author CH
     * @since 4.0.0
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

        /**
         * 是否已启用
         *
         * @return 是否已启用的结果
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置已启用
         *
         * @param enabled 已启用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取topic前缀
         *
         * @return 获取topic前缀的结果
         */
        public String getTopicPrefix() {
            return topicPrefix;
        }

        /**
         * 设置topic前缀
         *
         * @param topicPrefix topic前缀
         */
        public void setTopicPrefix(String topicPrefix) {
            this.topicPrefix = topicPrefix;
        }

        /**
         * 获取请求超时
         *
         * @return 获取请求超时的结果
         */
        public long getRequestTimeout() {
            return requestTimeout;
        }

        /**
         * 设置请求超时
         *
         * @param requestTimeout 请求超时
         */
        public void setRequestTimeout(long requestTimeout) {
            this.requestTimeout = requestTimeout;
        }

        /**
         * 获取默认通道
         *
         * @return 获取默认通道的结果
         */
        public String getDefaultChannel() {
            return defaultChannel;
        }

        /**
         * 设置默认通道
         *
         * @param defaultChannel 默认通道
         */
        public void setDefaultChannel(String defaultChannel) {
            this.defaultChannel = defaultChannel;
        }

        /**
         * 复制的
         *
         * @param source 源
         * @return 副本的的结果
         */
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
