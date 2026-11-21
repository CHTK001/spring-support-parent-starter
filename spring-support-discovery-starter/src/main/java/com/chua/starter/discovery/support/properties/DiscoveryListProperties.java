package com.chua.starter.discovery.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedList;
import java.util.List;

/**
 * 发现配置
 * @since 2024/9/9
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = DiscoveryListProperties.PRE, ignoreInvalidFields = true)
public class DiscoveryListProperties {
    /**
     * 是否启用
     */
    private boolean enable = false;


    /**
     * PRE
    */
    public static final String PRE = "plugin.discovery";

    /**
     * 运行模式：
     * <ul>
     *     <li>{@code client}（默认）：连接外部注册中心，按 {@link #properties} 装配发现客户端；</li>
     *     <li>{@code server}：以独立注册中心身份启动（{@code registry-hub}），只维护服务表并接受远程
     *     {@code registry-client} 接入；</li>
     *     <li>{@code embbed}：内嵌注册中心（{@code registry-hub}），注册中心与本应用同进程，
     *     应用直接以该实例完成注册与查询，并按 {@link Server#getNode() server.node} 注册本机种子服务。</li>
     * </ul>
     */
    private String type = "client";

    /**
     * 发现配置
     */
    private List<DiscoveryProperties> properties = new LinkedList<>();

    /**
     * server/embbed 模式下注册中心服务端的配置
     */
    private Server server = new Server();

    /**
     * 注册中心服务端配置（仅 server/embbed 模式生效）。
     * @since 4.0.0.52
     * @author CH
     */
    @Data
    public static class Server {

        /**
         * 注册中心传输协议（对应 SyncServer SPI：http/websocket/tcp/udp）
         */
        private String protocol = "http";

        /**
         * 监听地址：server 模式默认 0.0.0.0（供远程客户端接入），embbed 模式默认 127.0.0.1（仅本进程使用）
         */
        private String host;

        /**
         * 监听端口
         */
        private int port = 9600;

        /**
         * embbed 模式下本机要注册的服务节点（种子）
         */
        private List<DiscoveryNodeProperties> node = new LinkedList<>();
    }

 // Lombok @数据 生成的 getter/setter 方法（如果 Lombok 未生效，这些方法会被使用）
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
     * 获取属性
     *
     * @return 获取属性的结果
     */
    public List<DiscoveryProperties> getProperties() {
        return properties;
    }

    /**
     * 设置属性
     *
     * @param properties 属性
     */
    public void setProperties(List<DiscoveryProperties> properties) {
        this.properties = properties;
    }

    /**
     * 获取运行模式
     *
     * @return 运行模式（client/server/embbed）
     */
    public String getType() {
        return type;
    }

    /**
     * 设置运行模式
     *
     * @param type 运行模式（client/server/embbed）
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * 获取注册中心服务端配置
     *
     * @return 注册中心服务端配置
     */
    public Server getServer() {
        return server;
    }

    /**
     * 设置注册中心服务端配置
     *
     * @param server 注册中心服务端配置
     */
    public void setServer(Server server) {
        this.server = server;
    }

}
