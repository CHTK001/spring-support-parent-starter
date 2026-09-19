package com.chua.starter.server.support.config;

import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.terminal.handler.SshDirectWebSocketHandler;
import com.chua.starter.server.support.terminal.handler.TerminalWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * 远程终端 webSocket 配置
 * <p>
 * 将 {@link TerminalWebSocketHandler} 注册到
 * {@link ServerConstants#WEBSOCKET_TERMINAL_PATH} 端点（guacd 通道），
 * 将 {@link SshDirectWebSocketHandler} 注册到
 * {@link ServerConstants#WEBSOCKET_SSH_DIRECT_PATH} 端点（SSH 原生直连）。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocket
public class TerminalWebSocketConfig implements WebSocketConfigurer {

    /**
     * 终端处理器
    */
    private final TerminalWebSocketHandler terminalWebSocketHandler;

    /**
     * SSH 直连处理器
    */
    private final SshDirectWebSocketHandler sshDirectWebSocketHandler;

    /**
     * 构造注入
     *
     * @param terminalWebSocketHandler 终端处理器
     * @param sshDirectWebSocketHandler SSH 直连处理器
     */
    public TerminalWebSocketConfig(TerminalWebSocketHandler terminalWebSocketHandler,
                                   SshDirectWebSocketHandler sshDirectWebSocketHandler) {
        this.terminalWebSocketHandler = terminalWebSocketHandler;
        this.sshDirectWebSocketHandler = sshDirectWebSocketHandler;
    }

    /**
     * 注册终端 webSocket 端点
     * <p>
     * 握手处理器声明支持 guacamole 子协议（guacamole-通用-js 的 webSockettunnel
     * 以 {@code new WebSocket(url, "guacamole")} 连接，缺省握手不回显子协议会被浏览器拒绝）。
     * 直连端点由自研前端以无子协议方式连接，不复用该握手处理器。
     * </p>
     *
     * @param registry 注册器
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
 // 回显 guacamole 子协议：guacamole-通用-js 的 webSockettunnel 以
 // 新 webSocket(url, "guacamole") 连接，缺省握手不回显子协议会被浏览器拒绝。
        // 不限制子协议，避免Vite代理转发时子协议丢失导致握手失败
        DefaultHandshakeHandler handshakeHandler = new DefaultHandshakeHandler();
        handshakeHandler.setSupportedProtocols("guacamole");
        registry.addHandler(terminalWebSocketHandler, ServerConstants.WEBSOCKET_TERMINAL_PATH)
                .setAllowedOriginPatterns("*")
                .setHandshakeHandler(handshakeHandler);
        registry.addHandler(sshDirectWebSocketHandler, ServerConstants.WEBSOCKET_SSH_DIRECT_PATH)
                .setAllowedOriginPatterns("*");
    }
}
