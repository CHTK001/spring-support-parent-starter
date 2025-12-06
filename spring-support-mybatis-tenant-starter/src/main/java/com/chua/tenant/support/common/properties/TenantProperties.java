package com.chua.tenant.support.common.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.Set;

import static com.chua.tenant.support.common.properties.TenantProperties.PRE;

/**
 * 租户配置属性
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/06
 */
@Data
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class TenantProperties {

    public static final String PRE = "plugin.mybatis-plus.tenant";

    /**
     * 是否启用租户功能
     */
    private boolean enable = false;

    /**
     * 运行模式: server-服务端, client-客户端
     */
    private String mode = "client";

    /**
     * 租户ID字段名
     */
    private String tenantIdColumn = "sys_tenant_id";

    /**
     * 忽略的表（不添加租户条件）
     */
    private Set<String> ignoreTable = new HashSet<>();

    /**
     * 服务端配置
     */
    private ServerConfig server = new ServerConfig();

    /**
     * 客户端配置
     */
    private ClientConfig client = new ClientConfig();

    /**
     * 判断是否为服务端模式
     *
     * @return 是否为服务端模式
     */
    public boolean isServerMode() {
        return "server".equalsIgnoreCase(mode);
    }

    /**
     * 判断是否为客户端模式
     *
     * @return 是否为客户端模式
     */
    public boolean isClientMode() {
        return "client".equalsIgnoreCase(mode);
    }

    /**
     * 服务端配置
     */
    @Data
    public static class ServerConfig {

        /**
         * 是否启用同步下发
         */
        private boolean syncEnable = false;

        /**
         * 通信协议: rsocket, websocket
         */
        private String protocol = "rsocket";

        /**
         * 绑定地址
         */
        private String host = "0.0.0.0";

        /**
         * 监听端口
         */
        private int port = 19380;
    }

    /**
     * 客户端配置
     */
    @Data
    public static class ClientConfig {

        /**
         * 是否启用同步接收
         */
        private boolean syncEnable = false;

        /**
         * 当前租户ID
         */
        private String tenantId;

        /**
         * 是否自动添加租户字段到数据库表
         */
        private boolean autoAddColumn = false;

        /**
         * 通信协议: rsocket, websocket
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
    }
}
