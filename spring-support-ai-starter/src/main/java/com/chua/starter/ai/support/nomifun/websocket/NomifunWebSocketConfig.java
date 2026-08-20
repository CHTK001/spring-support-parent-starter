package com.chua.starter.ai.support.nomifun.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Nomifun /ws WebSocket 配置。
 *
 * <p>注册 {@code /ws} 端点，供前端桌面壳模式建立流式/推送连接。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class NomifunWebSocketConfig implements WebSocketConfigurer {

    /**
     * WS 处理器
     */
    private final NomifunWebSocketHandler handler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws").setAllowedOriginPatterns("*");
    }
}
