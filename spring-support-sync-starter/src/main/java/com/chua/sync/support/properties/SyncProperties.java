package com.chua.sync.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.*;

import static com.chua.sync.support.properties.SyncProperties.PRE;

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

    // ==================== 服务端配置 ====================

    /**
     * 服务端配置
     */
    @Data
    public static class ServerConfig {

        /**
         * 是否启用服务端
         */
        private boolean enable = true;

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
                ServerInstance defaultInstance = new ServerInstance();
                defaultInstance.setName("default");
                defaultInstance.setHost(host);
                defaultInstance.setPort(port);
                defaultInstance.setProtocol(protocol);
                return List.of(defaultInstance);
            }
            return instances;
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
        private boolean enable = true;

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
}
