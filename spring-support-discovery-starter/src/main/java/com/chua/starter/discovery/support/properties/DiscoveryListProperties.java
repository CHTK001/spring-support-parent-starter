package com.chua.starter.discovery.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedList;
import java.util.List;

/**
 * 服务发现总配置，绑定前缀 {@code plugin.discovery}（{@code ignoreInvalidFields = true}，非法字段名不会导致启动失败）。
 *
 * <p>完整 YAML 配置示例：</p>
 * <pre>{@code
 * plugin:
 *   discovery:
 *     enable: true                # 总开关，默认 false；false 时仅装配本地空实现，不连注册中心
 *     type: client                # 运行模式：client(默认)/server/embbed
 *     server:                     # 仅 type=server/embbed 生效
 *       protocol: http            # 注册中心传输协议，默认 http（SyncServer SPI：http/websocket/tcp/udp）
 *       host: 0.0.0.0             # 监听地址；留空时 embbed 回落 127.0.0.1，server 回落 0.0.0.0
 *       port: 9600                # 监听端口，默认 9600（<=0 时同样按 9600 处理）
 *       node:                     # embbed 模式本机种子服务节点
 *         - server-id: demo-app
 *           namespace: /demo-service
 *           protocol: http
 *     properties:                 # 发现客户端列表（type=client 时生效），enable=true 才装配
 *       - enabled: true           # 单条开关，默认 true
 *         protocol: nacos         # SPI 协议名：nacos/zookeeper/redis/apollo/udp-broadcast/registry-client 等
 *         address: http://localhost:8848
 *         username: your-username
 *         password: your-password
 *         connection-timeout-millis: 10000   # 连接超时，默认 10000
 *         session-timeout-millis: 10000      # 会话超时，默认 10000
 *         node:                     # 要注册的本机服务节点
 *           - server-id: demo-app   # 留空时运行时回落 Project.calcApplicationUuid()
 *             namespace: /demo-service
 *             protocol: http        # 节点通信协议，默认 http
 * }</pre>
 *
 * @since 2024/9/9
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = DiscoveryListProperties.PRE, ignoreInvalidFields = true)
public class DiscoveryListProperties {
    /**
     * 是否启用服务发现，默认 {@code false}。
     *
     * <p>{@code false} 时仅注册内含 {@code DefaultServiceDiscovery} 的 {@code discoveryService#embedd}，
     * 注册/查询均为本地空操作，不连任何注册中心；置为 {@code true} 后才按 {@link #properties}
     * （或 server 模式的 {@code registry-hub}）装配真实实现。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   discovery:
     *     enable: true
     * }</pre>
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
     *
     * <p>默认 {@code client}；判定大小写不敏感（server/embbed 按 equalsIgnoreCase 匹配）。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   discovery:
     *     type: embbed
     * }</pre>
     */
    private String type = "client";

    /**
     * 发现客户端配置列表，默认空列表；仅 {@code enable=true} 且 {@code type=client} 时逐条装配
     * {@code serviceDiscovery#<protocol>} Bean，元素结构见 {@link DiscoveryProperties}。
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   discovery:
     *     properties:
     *       - protocol: nacos
     *         address: http://localhost:8848
     *         node:
     *           - namespace: /demo-service
     * }</pre>
     */
    private List<DiscoveryProperties> properties = new LinkedList<>();

    /**
     * 注册中心服务端配置，默认 {@code new Server()}；仅 {@code type=server/embbed} 时生效，
     * 字段结构见 {@link Server}。
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   discovery:
     *     type: server
     *     server:
     *       protocol: http
     *       host: 0.0.0.0
     *       port: 9600
     * }</pre>
     */
    private Server server = new Server();

    /**
     * 注册中心服务端配置（仅 server/embbed 模式生效）。
     *
     * <p>完整 YAML 配置示例：</p>
     * <pre>{@code
     * plugin:
     *   discovery:
     *     type: embbed
     *     server:
     *       protocol: http            # 默认 http
     *       host: 127.0.0.1           # 留空回落：embbed=127.0.0.1，server=0.0.0.0
     *       port: 9600                # 默认 9600，配置 <=0 时同样按 9600 处理
     *       node:                     # embbed 模式本机种子服务
     *         - server-id: demo-app
     *           namespace: /demo-service
     *           protocol: http
     * }</pre>
     *
     * @since 4.0.0.52
     * @author CH
     */
    @Data
    public static class Server {

        /**
         * 注册中心传输协议，默认 {@code http}（对应 SyncServer SPI：http/websocket/tcp/udp）。
         *
         * <p>YAML 示例：</p>
         * <pre>{@code
         * plugin:
         *   discovery:
         *     server:
         *       protocol: http
         * }</pre>
         */
        private String protocol = "http";

        /**
         * 监听地址，字段默认 {@code null}；留空时按运行模式回落：{@code embbed} 回落 {@code 127.0.0.1}
         * （仅本进程使用），{@code server} 回落 {@code 0.0.0.0}（供远程客户端接入）。
         *
         * <p>YAML 示例：</p>
         * <pre>{@code
         * plugin:
         *   discovery:
         *     server:
         *       host: 0.0.0.0
         * }</pre>
         */
        private String host;

        /**
         * 监听端口，默认 {@code 9600}；配置为 {@code <=0} 时同样按 {@code 9600} 处理。
         *
         * <p>YAML 示例：</p>
         * <pre>{@code
         * plugin:
         *   discovery:
         *     server:
         *       port: 9600
         * }</pre>
         */
        private int port = 9600;

        /**
         * embbed 模式下本机要注册的服务节点（种子），默认空列表；在注册中心 {@code start()} 阶段注册。
         *
         * <p>YAML 示例：</p>
         * <pre>{@code
         * plugin:
         *   discovery:
         *     server:
         *       node:
         *         - server-id: demo-app
         *           namespace: /demo-service
         *           protocol: http
         * }</pre>
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
