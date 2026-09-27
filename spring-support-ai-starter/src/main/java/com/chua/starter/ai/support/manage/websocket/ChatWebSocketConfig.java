package com.chua.starter.ai.support.manage.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 聊天 WebSocket 配置：注册 {@code /ws/chat}。
 *
 * <p>与桌面桥 {@code /ws}（name 协议）分开：本端点承载聊天多路复用流（event 协议），
 * 握手同样经 {@link AiWebSocketHandshakeInterceptor} 校验登录令牌。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class ChatWebSocketConfig implements WebSocketConfigurer {

    /**
     * 聊天 WS 处理器
     */
    private final ChatWebSocketHandler chatHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatHandler, "/ws/chat")
                .addInterceptors(new AiWebSocketHandshakeInterceptor())
                .setAllowedOriginPatterns("*");
    }
}
