package com.chua.starter.ai.support.workspace.terminal;

import com.chua.starter.ai.support.manage.websocket.AiWebSocketHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

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
     * WebSocket 文本帧上限（字节）：终端整段粘贴可能一次发送数万字符，
     * 容器默认 8KB 会直接判为超限并断开连接
     */
    private static final int MAX_TEXT_MESSAGE_BUFFER_SIZE = 256 * 1024;

    /**
     * WebSocket 二进制帧上限（字节）
     */
    private static final int MAX_BINARY_MESSAGE_BUFFER_SIZE = 256 * 1024;

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

    /**
     * 放宽 WebSocket 单帧上限，避免整段粘贴触发容器级超限断开。
     *
     * @return WebSocket 容器配置
     */
    @Bean
    public ServletServerContainerFactoryBean webSocketContainerFactory() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(MAX_TEXT_MESSAGE_BUFFER_SIZE);
        container.setMaxBinaryMessageBufferSize(MAX_BINARY_MESSAGE_BUFFER_SIZE);
        return container;
    }
}
