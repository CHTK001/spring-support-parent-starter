package com.chua.starter.server.support.config;

import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.terminal.handler.SerialWebSocketHandler;
import com.chua.starter.server.support.terminal.handler.TerminalWebSocketHandler;
import com.chua.starter.server.support.terminal.handler.SshDirectWebSocketHandler;
import com.chua.starter.server.support.terminal.handler.VncWebsockifyWebSocketHandler;
import com.chua.starter.server.support.terminal.telnet.TelnetWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 远控终端 webSocket 端点注册
 * <p>
 * 将 {@link TerminalWebSocketHandler} 注册到
 * {@link ServerConstants#WEBSOCKET_TERMINAL_PATH}，由 guacd 完成 RDP/SSH/VNC
 * 协议转换；并将 SSH 直连桥 {@link SshDirectWebSocketHandler} 注册到
 * {@link ServerConstants#WEBSOCKET_SSH_DIRECT_PATH}（不经 guacd）；
 * VNC 直连桥 {@link VncWebsockifyWebSocketHandler} 注册到
 * {@link ServerConstants#WEBSOCKET_VNC_DIRECT_PATH}（websockify 透明字节转发，
 * 浏览器侧 noVNC 讲 RFB）；以及 Telnet 直连桥 {@link TelnetWebSocketHandler}
 * 注册到 {@link ServerConstants#WEBSOCKET_TELNET_DIRECT_PATH}（原始 TCP 透明转发）；
 * Serial 直连桥 {@link SerialWebSocketHandler} 注册到
 * {@link ServerConstants#WEBSOCKET_SERIAL_DIRECT_PATH}（jSerialComm 原样收发，不经 guacd）。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Configuration
@EnableWebSocket
public class TerminalWebSocketConfig implements WebSocketConfigurer {

    /**
     * guacd 桥接处理器
     */
    private final TerminalWebSocketHandler terminalWebSocketHandler;

    /**
     * SSH 直连处理器
     */
    private final SshDirectWebSocketHandler sshDirectWebSocketHandler;

    /**
     * VNC websockify 桥接处理器
     */
    private final VncWebsockifyWebSocketHandler vncWebsockifyWebSocketHandler;

    /**
     * Telnet 直连处理器
     */
    private final TelnetWebSocketHandler telnetDirectWebSocketHandler;

    /**
     * Serial 直连处理器
     */
    private final SerialWebSocketHandler serialDirectWebSocketHandler;

    /**
     * 构造并注入各处理器
     *
     * @param terminalWebSocketHandler       guacd 桥接处理器
     * @param sshDirectWebSocketHandler      SSH 直连处理器
     * @param vncWebsockifyWebSocketHandler  VNC websockify 桥接处理器
     * @param telnetDirectWebSocketHandler   Telnet 直连处理器
     * @param serialDirectWebSocketHandler   Serial 直连处理器
     */
    public TerminalWebSocketConfig(TerminalWebSocketHandler terminalWebSocketHandler,
                                   SshDirectWebSocketHandler sshDirectWebSocketHandler,
                                   VncWebsockifyWebSocketHandler vncWebsockifyWebSocketHandler,
                                   TelnetWebSocketHandler telnetDirectWebSocketHandler,
                                   SerialWebSocketHandler serialDirectWebSocketHandler) {
        this.terminalWebSocketHandler = terminalWebSocketHandler;
        this.sshDirectWebSocketHandler = sshDirectWebSocketHandler;
        this.vncWebsockifyWebSocketHandler = vncWebsockifyWebSocketHandler;
        this.telnetDirectWebSocketHandler = telnetDirectWebSocketHandler;
        this.serialDirectWebSocketHandler = serialDirectWebSocketHandler;
    }

    /**
     * 注册全部远控 WS 端点
     *
     * @param registry WS 注册表
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(terminalWebSocketHandler, ServerConstants.WEBSOCKET_TERMINAL_PATH)
                .setAllowedOriginPatterns("*");
        registry.addHandler(sshDirectWebSocketHandler, ServerConstants.WEBSOCKET_SSH_DIRECT_PATH)
                .setAllowedOriginPatterns("*");
        registry.addHandler(vncWebsockifyWebSocketHandler, ServerConstants.WEBSOCKET_VNC_DIRECT_PATH)
                .setAllowedOriginPatterns("*");
        registry.addHandler(telnetDirectWebSocketHandler, ServerConstants.WEBSOCKET_TELNET_DIRECT_PATH)
                .setAllowedOriginPatterns("*");
        registry.addHandler(serialDirectWebSocketHandler, ServerConstants.WEBSOCKET_SERIAL_DIRECT_PATH)
                .setAllowedOriginPatterns("*");
    }
}
