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
}
