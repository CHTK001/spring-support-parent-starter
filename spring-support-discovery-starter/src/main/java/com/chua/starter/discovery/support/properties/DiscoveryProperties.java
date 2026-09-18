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
