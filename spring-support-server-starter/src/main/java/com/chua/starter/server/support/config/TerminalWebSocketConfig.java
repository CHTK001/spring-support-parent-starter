package com.chua.starter.server.support.config;

import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.terminal.handler.TerminalWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 远程终端 WebSocket 配置
 * <p>
 * 将 {@link TerminalWebSocketHandler} 注册到
 * {@link ServerConstants#WEBSOCKET_TERMINAL_PATH} 端点。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocket
public class TerminalWebSocketConfig implements WebSocketConfigurer {

    /** 终端处理器 */
    private final TerminalWebSocketHandler terminalWebSocketHandler;

    /**
     * 构造注入
     *
     * @param terminalWebSocketHandler 终端处理器
     */
    public TerminalWebSocketConfig(TerminalWebSocketHandler terminalWebSocketHandler) {
        this.terminalWebSocketHandler = terminalWebSocketHandler;
    }

    /**
     * 注册终端 WebSocket 端点
     *
     * @param registry 注册器
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(terminalWebSocketHandler, ServerConstants.WEBSOCKET_TERMINAL_PATH)
                .setAllowedOriginPatterns("*");
    }
}
