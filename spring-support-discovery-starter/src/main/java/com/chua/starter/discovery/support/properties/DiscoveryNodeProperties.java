package com.chua.starter.discovery.support.properties;

import lombok.Data;

/**
* @since 2024/12/26
* @author CH
 */
@Data
public class DiscoveryNodeProperties {
    /**
    * 服务标识
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
