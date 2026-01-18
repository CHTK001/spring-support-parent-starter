package com.chua.starter.discovery.support.properties;

import lombok.Data;

import java.util.LinkedList;
import java.util.List;

/**
 * 发现配置
 * @author CH
 * @since 2024/9/9
 */
@Data
public class DiscoveryProperties {

    /**
     * 是否启用
     */
    private boolean enabled = true;

    /**
     * 协议
     */
    private String protocol;
    
    // Lombok @Data 生成的 getter/setter 方法（如果 Lombok 未生效，这些方法会被使用）
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getProtocol() {
        return protocol;
    }
    
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }
    
    public String getAddress() {
        return address;
    }
    
    public void setAddress(String address) {
        this.address = address;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public int getConnectionTimeoutMillis() {
        return connectionTimeoutMillis;
    }
    
    public void setConnectionTimeoutMillis(int connectionTimeoutMillis) {
        this.connectionTimeoutMillis = connectionTimeoutMillis;
    }
    
    public int getSessionTimeoutMillis() {
        return sessionTimeoutMillis;
    }
    
    public void setSessionTimeoutMillis(int sessionTimeoutMillis) {
        this.sessionTimeoutMillis = sessionTimeoutMillis;
    }
    
    public List<DiscoveryNodeProperties> getNode() {
        return node;
    }
    
    public void setNode(List<DiscoveryNodeProperties> node) {
        this.node = node;
    }
    /**
     * 地址
     */
    private String address;
    /**
     * 账号
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 连接超时时间
     */
    private int connectionTimeoutMillis = 10_000;
    /**
     * 会话超时时间
     */
    private int sessionTimeoutMillis = 10_000;

    /**
     * 节点
     */
    private List<DiscoveryNodeProperties> node = new LinkedList<>();

}
