package com.chua.starter.discovery.support.properties;

import lombok.Data;

/**
 * @since 2024/12/26
 * @author CH
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
    /** 获取ServerId */
    public String getServerId() {
        return serverId;
    }
    
    /** 设置ServerId */
    public void setServerId(String serverId) {
        this.serverId = serverId;
    }
    
    /** 获取Namespace */
    public String getNamespace() {
        return namespace;
    }
    
    /** 设置Namespace */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
    
    /** 获取Protocol */
    public String getProtocol() {
        return protocol;
    }
    
    /** 设置Protocol */
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }
}
