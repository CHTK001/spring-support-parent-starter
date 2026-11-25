package com.chua.starter.discovery.support.properties;

import lombok.Data;

/**
 * 服务节点配置，对应 {@code plugin.discovery.properties[n].node[m]}（{@code type=client} 时）
 * 或 {@code plugin.discovery.server.node[m]}（embbed 模式种子节点）。
 *
 * <p>完整 YAML 配置示例：</p>
 * <pre>{@code
 * plugin:
 *   discovery:
 *     properties:
 *       - protocol: nacos
 *         node:
 *           - server-id: demo-app      # 实例 ID，留空时运行时回落 Project.calcApplicationUuid()
 *             namespace: /demo-service # 服务命名空间（注册/查询的 uriSpec）
 *             protocol: http           # 节点通信协议，默认 http
 * }</pre>
 *
 * @since 2024/12/26
 * @author CH
 */
@Data
public class DiscoveryNodeProperties {
    /**
     * 服务标识（实例 ID），默认 {@code null}；运行时为空则回落 {@code Project.calcApplicationUuid()}。
     * 该值成为 {@code Discovery#id}，也是反注册的键，多实例部署建议显式配置以保证可辨。
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   discovery:
     *     properties:
     *       - node:
     *           - server-id: demo-app
     * }</pre>
     */
    private String serverId;

    /**
     * 服务命名空间（uri），默认 {@code null}；作为注册/查询的 {@code uriSpec}，
     * 也是匹配宿主 {@code DiscoveryEnvironment#getName()} 的键。
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   discovery:
     *     properties:
     *       - node:
     *           - namespace: /demo-service
     * }</pre>
     */
    private String namespace;
    /**
     * 节点通信协议，默认 {@code http}；写入 {@code Discovery#protocol}，表示本实例对外提供服务的协议。
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   discovery:
     *     properties:
     *       - node:
     *           - protocol: http
     * }</pre>
     */
    private String protocol = "http";
    
 // Lombok @数据 生成的 getter/setter 方法（如果 Lombok 未生效，这些方法会被使用）
    /**
     * 获取服务端标识
     *
     * @return 获取服务端id的结果
     */
    public String getServerId() {
        return serverId;
    }
    
    /**
     * 设置服务端标识
     *
     * @param serverId 服务端标识
     */
    public void setServerId(String serverId) {
        this.serverId = serverId;
    }
    
    /**
     * 获取Namespace
     *
     * @return 获取namespace的结果
     */
    public String getNamespace() {
        return namespace;
    }
    
    /**
     * 设置Namespace
     *
     * @param namespace namespace
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
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
}
