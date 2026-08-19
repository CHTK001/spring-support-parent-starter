package com.chua.starter.discovery.support.properties;

import lombok.Data;

import java.util.LinkedList;
import java.util.List;

/**
 * 发现配置
 * @since 2024/9/9
 * @author CH
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
    /** 是否Enabled */
    public boolean isEnabled() {
        return enabled;
    }
    
    /** 设置Enabled */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    /** 获取Protocol */
    public String getProtocol() {
        return protocol;
    }
    
    /** 设置Protocol */
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }
    
    /** 获取Address */
    public String getAddress() {
        return address;
    }
    
    /** 设置Address */
    public void setAddress(String address) {
        this.address = address;
    }
    
    /** 获取Username */
    public String getUsername() {
        return username;
    }
    
    /** 设置Username */
    public void setUsername(String username) {
        this.username = username;
    }
    
    /** 获取Password */
    public String getPassword() {
        return password;
    }
    
    /** 设置Password */
    public void setPassword(String password) {
        this.password = password;
    }
    
    /** 获取ConnectionTimeoutMillis */
    public int getConnectionTimeoutMillis() {
        return connectionTimeoutMillis;
    }
    
    /** 设置ConnectionTimeoutMillis */
    public void setConnectionTimeoutMillis(int connectionTimeoutMillis) {
        this.connectionTimeoutMillis = connectionTimeoutMillis;
    }
    
    /** 获取SessionTimeoutMillis */
    public int getSessionTimeoutMillis() {
        return sessionTimeoutMillis;
    }
    
    /** 设置SessionTimeoutMillis */
    public void setSessionTimeoutMillis(int sessionTimeoutMillis) {
        this.sessionTimeoutMillis = sessionTimeoutMillis;
    }
    
    /** 获取Node */
    public List<DiscoveryNodeProperties> getNode() {
        return node;
    }
    
    /** 设置Node */
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
