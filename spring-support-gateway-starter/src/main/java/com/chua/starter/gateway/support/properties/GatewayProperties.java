package com.chua.starter.gateway.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 网关配置属性
 *
 * @author CH
 * @since 2024/12/26
 */
@Data
@ConfigurationProperties(prefix = GatewayProperties.PREFIX)
public class GatewayProperties {

    public static final String PREFIX = "plugin.gateway";

    /**
     * 是否启用网关
     */
    private boolean enable = false;

    /**
     * 运行模式: server/client
     */
    private Mode mode = Mode.SERVER;

    /**
     * 服务端配置
     */
    private ServerProperties server = new ServerProperties();

    /**
     * 客户端配置
     */
    private ClientProperties client = new ClientProperties();

    /**
     * 运行模式枚举
     */
    public enum Mode {
        /**
         * 服务端模式 - 启动代理端口，从发现服务获取路由
         */
        SERVER,
        /**
         * 客户端模式 - 注册到发现服务
         */
        CLIENT
    }

    /**
     * 服务端配置
     */
    @Data
    public static class ServerProperties {
        /**
         * 代理端口配置列表
         */
        private List<PortConfig> ports = new ArrayList<>();

        /**
         * 负载均衡策略: round-robin, random, weight
         */
        private String loadBalancer = "round-robin";

        /**
         * 服务刷新间隔
         */
        private Duration refreshInterval = Duration.ofSeconds(30);

        /**
         * 自动优化
         */
        private boolean automaticOptimization = true;

        /**
         * 主机地址
         */
        private String host = "0.0.0.0";
    }

    /**
     * 客户端配置
     */
    @Data
    public static class ClientProperties {
        /**
         * 注册的服务ID
         */
        private String serviceId;

        /**
         * 协议类型
         */
        private String protocol = "http";

        /**
         * 权重 (用于负载均衡)
         */
        private double weight = 1.0;

        /**
         * 元数据
         */
        private java.util.Map<String, String> metadata = new java.util.LinkedHashMap<>();
    }

    /**
     * 端口配置
     */
    @Data
    public static class PortConfig {
        /**
         * 端口号
         */
        private int port;

        /**
         * 协议类型: http, tcp
         */
        private String protocol = "http";

        /**
         * 绑定的服务ID (可选，为空时代理所有服务)
         */
        private String serviceId;

        /**
         * 路径前缀 (HTTP代理时使用)
         */
        private String pathPrefix;

        /**
         * 是否启用
         */
        private boolean enabled = true;
    }
}
