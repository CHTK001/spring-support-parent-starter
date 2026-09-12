package com.chua.starter.server.support.config;

import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.terminal.handler.TerminalWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
   * 远程终端 web套接字 配置
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
      * 注册终端 web套接字 端点
     * <p>
      * 握手处理器声明支持 guacamole 子协议（guacamole-通用-js 的 web套接字tunnel
     * 以 {@code new WebSocket(url, "guacamole")} 连接，缺省握手不回显子协议会被浏览器拒绝）。
     * </p>
     *
     * @param registry 注册器
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
 // 回显 guacamole 子协议：guacamole-通用-js 的 web套接字tunnel 以
 // 新 web套接字(url, "guacamole") 连接，缺省握手不回显子协议会被浏览器拒绝。
        DefaultHandshakeHandler handshakeHandler = new DefaultHandshakeHandler();
        handshakeHandler.setSupportedProtocols("guacamole");
        registry.addHandler(terminalWebSocketHandler, ServerConstants.WEBSOCKET_TERMINAL_PATH)
                .setHandshakeHandler(handshakeHandler)
                .setAllowedOriginPatterns("*");
    }
}
