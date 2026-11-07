package com.chua.starter.monitor.support.tshark.cdp.config;

import com.chua.starter.monitor.support.tshark.cdp.ws.CdpWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * CDP 调试 WebSocket 端点注册
 *
 * <p>将 {@link CdpWebSocketHandler} 注册到 {@code spring.cdp.ws-path} 定义的路径上（默认 {@code /ws/cdp}），
 * 允许所有源访问，使前端的 {@code WebSocket} 连接可以穿透。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Configuration
@EnableWebSocket
public class CdpWebSocketConfig implements WebSocketConfigurer {

    /**
     * CDP 属性
     */
    private final CdpProperties properties;

    /**
     * CDP WebSocket 处理器
     */
    private final CdpWebSocketHandler cdpWebSocketHandler;

    /**
     * @param properties           属性
     * @param cdpWebSocketHandler  处理器
     */
    public CdpWebSocketConfig(CdpProperties properties, CdpWebSocketHandler cdpWebSocketHandler) {
        this.properties = properties;
        this.cdpWebSocketHandler = cdpWebSocketHandler;
    }

    /**
     * 注册 WebSocket 端点
     *
     * @param registry WS 端点注册表
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(cdpWebSocketHandler, properties.getWsPath())
                .setAllowedOrigins("*");
    }
}
