package com.chua.starter.discovery.support.properties;

import lombok.Data;

import java.util.LinkedList;
import java.util.List;

/**
 * 单条发现客户端配置，对应 {@code plugin.discovery.properties[n]}（由 {@link DiscoveryListProperties} 聚合，前缀 {@code plugin.discovery}）。
 *
 * <p>完整 YAML 配置示例：</p>
 * <pre>{@code
 * plugin:
 *   discovery:
 *     properties:
 *       - enabled: true                      # 单条开关，默认 true
 *         protocol: nacos                    # SPI 协议名，缺省为 null（不配置无法命中实现）
 *         address: http://localhost:8848     # 注册中心地址
 *         username: your-username            # 账号，可省略
 *         password: your-password            # 密码，可省略
 *         connection-timeout-millis: 10000   # 连接超时，默认 10000
 *         session-timeout-millis: 10000      # 会话超时，默认 10000
 *         node:                              # 本机要注册的服务节点
 *           - server-id: demo-app
 *             namespace: /demo-service
 *             protocol: http
 * }</pre>
 *
 * @since 2024/9/9
 * @author CH
 */
@Data
public class DiscoveryProperties {

    /**
     * 单条发现配置是否启用，默认 {@code true}；置为 {@code false} 时跳过该条，不创建对应 Bean。
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   discovery:
     *     properties:
     *       - enabled: false
     * }</pre>
     */
    private boolean enabled = true;

    /**
     * SPI 协议名，默认 {@code null}（不配置则无法命中任何实现，该条降级为本地空实现并告警）。
     * 可选值：{@code nacos} / {@code zookeeper} / {@code redis} / {@code apollo} /
     * {@code udp-broadcast} / {@code registry-client} / 自定义 SPI 名。
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   discovery:
     *     properties:
     *       - protocol: nacos
     * }</pre>
     */
    private String protocol;
    
 // Lombok @数据 生成的 getter/setter 方法（如果 Lombok 未生效，这些方法会被使用）
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
     * 获取connection超时millis
     *
     * @return 获取connection超时millis的结果
     */
    public int getConnectionTimeoutMillis() {
        return connectionTimeoutMillis;
    }
    
    /**
     * 设置connection超时millis
     *
     * @param connectionTimeoutMillis connection超时millis
     */
    public void setConnectionTimeoutMillis(int connectionTimeoutMillis) {
        this.connectionTimeoutMillis = connectionTimeoutMillis;
    }
    
    /**
     * 获取会话超时millis
     *
     * @return 获取会话超时millis的结果
     */
    public int getSessionTimeoutMillis() {
        return sessionTimeoutMillis;
    }
    
    /**
     * 设置会话超时millis
     *
     * @param sessionTimeoutMillis 会话超时millis
     */
    public void setSessionTimeoutMillis(int sessionTimeoutMillis) {
        this.sessionTimeoutMillis = sessionTimeoutMillis;
    }
    
    /**
     * 获取节点
     *
     * @return 获取节点的结果
     */
    public List<DiscoveryNodeProperties> getNode() {
        return node;
    }
    
    /**
     * 设置节点
     *
     * @param node 节点
     */
    public void setNode(List<DiscoveryNodeProperties> node) {
        this.node = node;
    }
    /**
     * 注册中心地址，默认 {@code null}；绑定到 {@code DiscoveryOption#setAddress}，
     * 如 Nacos 的 {@code http://localhost:8848}。
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   discovery:
     *     properties:
     *       - address: http://localhost:8848
     * }</pre>
     */
    private String address;
    /**
     * 注册中心账号，默认 {@code null}；绑定到 {@code DiscoveryOption#setUser}，无需鉴权时可省略。
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   discovery:
     *     properties:
     *       - username: your-username
     * }</pre>
     */
    private String username;

    /**
     * 注册中心密码，默认 {@code null}；绑定到 {@code DiscoveryOption#setPassword}，无需鉴权时可省略。
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   discovery:
     *     properties:
     *       - password: your-password
     * }</pre>
     */
    private String password;

    /**
     * 连接超时时间（毫秒），默认 {@code 10000}；绑定到 {@code DiscoveryOption#setConnectionTimeoutMillis}。
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   discovery:
     *     properties:
     *       - connection-timeout-millis: 10000
     * }</pre>
     */
    private int connectionTimeoutMillis = 10_000;
    /**
     * 会话超时时间（毫秒），默认 {@code 10000}；绑定到 {@code DiscoveryOption#setSessionTimeoutMillis}。
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   discovery:
     *     properties:
     *       - session-timeout-millis: 10000
     * }</pre>
     */
    private int sessionTimeoutMillis = 10_000;

    /**
     * 本机要注册的服务节点列表，默认空列表；元素结构见 {@link DiscoveryNodeProperties}。
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   discovery:
     *     properties:
     *       - node:
     *           - server-id: demo-app
     *             namespace: /demo-service
     *             protocol: http
     * }</pre>
     */
    private List<DiscoveryNodeProperties> node = new LinkedList<>();

}
