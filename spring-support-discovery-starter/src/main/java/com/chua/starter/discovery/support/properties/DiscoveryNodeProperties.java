package com.chua.starter.discovery.support.properties;

import lombok.Data;

/**
 * @author CH
 * @since 2024/12/26
 */
@Data
public class DiscoveryNodeProperties {
    /**
     * 服务id
     */
    private String serverId;

    /**
     * uri
     */
    private String namespace;
    /**
     * 协议
     */
    private String protocol = "http";
    
    // Lombok @Data 生成的 getter/setter 方法（如果 Lombok 未生效，这些方法会被使用）
    public String getServerId() {
        return serverId;
    }
    
    public void setServerId(String serverId) {
        this.serverId = serverId;
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
    
    public String getProtocol() {
        return protocol;
    }
    
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }
}
