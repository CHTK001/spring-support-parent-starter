package com.chua.starter.socket.websocket.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * WebSocket 配置属性
 *
 * @author CH
 * @since 2025-12-25
 */
@Data
@ConfigurationProperties(prefix = WebSocketProperties.PREFIX)
public class WebSocketProperties {

    public static final String PREFIX = "plugin.websocket";

    /**
     * 是否启用
     */
    private boolean enable = false;

    /**
     * WebSocket 端点路径列表
     */
    private List<String> endpoints = new ArrayList<>(List.of("/ws"));

    /**
     * 允许的源（CORS）
     */
    private List<String> allowedOrigins = new ArrayList<>(List.of("*"));

    /**
     * 是否启用 SockJS 支持
     */
    private boolean sockJs = false;

    /**
     * 心跳间隔（毫秒），0 表示禁用
     */
    private long heartbeatInterval = 25000;

    /**
     * 消息大小限制（字节）
     */
    private int maxMessageSize = 65536;

    /**
     * 发送缓冲区大小限制（字节）
     */
    private int sendBufferSizeLimit = 524288;

    /**
     * 发送超时时间（毫秒）
     */
    private int sendTimeLimit = 10000;

    /**
     * 获取允许的源
     *
     * @return 允许的源列表
     */
    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    /**
     * 设置允许的源
     *
     * @param allowedOrigins 允许的源列表
     */
    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    /**
     * 获取是否启用 SockJS
     *
     * @return 是否启用 SockJS
     */
    public boolean isSockJs() {
        return sockJs;
    }

    /**
     * 设置是否启用 SockJS
     *
     * @param sockJs 是否启用 SockJS
     */
    public void setSockJs(boolean sockJs) {
        this.sockJs = sockJs;
    }

    /**
     * 获取心跳间隔
     *
     * @return 心跳间隔（毫秒）
     */
    public long getHeartbeatInterval() {
        return heartbeatInterval;
    }

    /**
     * 设置心跳间隔
     *
     * @param heartbeatInterval 心跳间隔（毫秒）
     */
    public void setHeartbeatInterval(long heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    /**
     * 获取消息大小限制
     *
     * @return 消息大小限制（字节）
     */
    public int getMaxMessageSize() {
        return maxMessageSize;
    }

    /**
     * 设置消息大小限制
     *
     * @param maxMessageSize 消息大小限制（字节）
     */
    public void setMaxMessageSize(int maxMessageSize) {
        this.maxMessageSize = maxMessageSize;
    }

    /**
     * 获取发送超时时间
     *
     * @return 发送超时时间（毫秒）
     */
    public int getSendTimeLimit() {
        return sendTimeLimit;
    }

    /**
     * 设置发送超时时间
     *
     * @param sendTimeLimit 发送超时时间（毫秒）
     */
    public void setSendTimeLimit(int sendTimeLimit) {
        this.sendTimeLimit = sendTimeLimit;
    }

    /**
     * 获取端点列表
     *
     * @return 端点列表
     */
    public List<String> getEndpoints() {
        return endpoints;
    }

    /**
     * 设置端点列表
     *
     * @param endpoints 端点列表
     */
    public void setEndpoints(List<String> endpoints) {
        this.endpoints = endpoints;
    }
}
