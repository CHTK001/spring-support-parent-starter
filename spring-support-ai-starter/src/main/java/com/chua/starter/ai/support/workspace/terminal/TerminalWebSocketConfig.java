package com.chua.starter.ai.support.workspace.terminal;

import com.chua.starter.ai.support.manage.websocket.AiWebSocketHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 工作区终端 WebSocket 配置：注册 {@code /ws/terminal}。
 *
 * <p>与聊天 WebSocket 共用同一套握手身份校验，登录用户才能建立终端。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class TerminalWebSocketConfig implements WebSocketConfigurer {

    /**
     * 终端 WS 处理器
     */
    private final TerminalWebSocketHandler terminalHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(terminalHandler, "/ws/terminal")
                .addInterceptors(new AiWebSocketHandshakeInterceptor())
                .setAllowedOriginPatterns("*");
    }
}
