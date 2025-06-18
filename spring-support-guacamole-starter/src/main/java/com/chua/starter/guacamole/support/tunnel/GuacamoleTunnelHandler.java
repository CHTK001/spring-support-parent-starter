package com.chua.starter.guacamole.support.tunnel;

import com.chua.starter.guacamole.support.service.GuacamoleService;
import lombok.extern.slf4j.Slf4j;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.net.GuacamoleTunnel;
import org.apache.guacamole.protocol.GuacamoleInstruction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guacamole隧道WebSocket处理器
 *
 * @author CH
 * @since 2024/7/24
 */
@Slf4j
public class GuacamoleTunnelHandler extends TextWebSocketHandler {

    /**
     * 会话与隧道的映射关系
     */
    private final Map<String, GuacamoleTunnel> tunnelMap = new ConcurrentHashMap<>();
    @Autowired
    private GuacamoleService guacamoleService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket连接已建立: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        String sessionId = session.getId();

        // 处理连接请求
        if (payload.startsWith("connect:")) {
            handleConnect(session, payload.substring(8));
            return;
        }

        // 发送指令到Guacamole服务器
        GuacamoleTunnel tunnel = tunnelMap.get(sessionId);
        if (tunnel != null && tunnel.isOpen()) {
            try {
                // 将客户端指令发送到Guacamole服务器
                tunnel.acquireWriter().writeInstruction(new GuacamoleInstruction(payload));

                // 读取Guacamole服务器响应并发送给客户端
                char[] buffer = new char[8192];
                char[] chars = tunnel.acquireReader().read();
                int length = chars.length;
                if (length > 0) {
                    session.sendMessage(new TextMessage(new String(buffer, 0, length)));
                }
            } catch (GuacamoleException e) {
                log.error("处理Guacamole消息时发生错误", e);
                closeTunnel(session);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        closeTunnel(session);
        log.info("WebSocket连接已关闭: {}, 状态: {}", session.getId(), status);
    }

    /**
     * 处理连接请求
     */
    private void handleConnect(WebSocketSession session, String connectionString) {
        try {
            // 解析连接参数
            String[] params = connectionString.split(",");
            if (params.length < 3) {
                sendError(session, "连接参数不足");
                return;
            }

            String protocol = params[0];
            String host = params[1];
            int port = Integer.parseInt(params[2]);

            // 创建Guacamole隧道
            GuacamoleTunnel tunnel = guacamoleService.createTunnel(protocol, host, port);
            tunnelMap.put(session.getId(), tunnel);

            // 发送连接成功消息
            session.sendMessage(new TextMessage("connected"));

            // 启动一个线程读取Guacamole服务器的响应
            new Thread(() -> {
                try {
                    char[] buffer = new char[8192];
                    while (tunnel.isOpen() && session.isOpen()) {
                        char[] chars = tunnel.acquireReader().read();
                        int length = chars.length;
                        if (length > 0) {
                            session.sendMessage(new TextMessage(new String(buffer, 0, length)));
                        }
                    }
                } catch (Exception e) {
                    log.error("读取Guacamole响应时发生错误", e);
                    try {
                        closeTunnel(session);
                    } catch (Exception ex) {
                        log.error("关闭隧道时发生错误", ex);
                    }
                }
            }).start();

        } catch (Exception e) {
            log.error("创建Guacamole隧道时发生错误", e);
            try {
                sendError(session, "创建隧道失败: " + e.getMessage());
            } catch (IOException ex) {
                log.error("发送错误消息时发生异常", ex);
            }
        }
    }

    /**
     * 关闭隧道
     */
    private void closeTunnel(WebSocketSession session) {
        String sessionId = session.getId();
        GuacamoleTunnel tunnel = tunnelMap.remove(sessionId);
        if (tunnel != null) {
            try {
                tunnel.close();
                log.info("Guacamole隧道已关闭: {}", sessionId);
            } catch (GuacamoleException e) {
                log.error("关闭Guacamole隧道时发生错误", e);
            }
        }
    }

    /**
     * 发送错误消息
     */
    private void sendError(WebSocketSession session, String message) throws IOException {
        session.sendMessage(new TextMessage("error:" + message));
    }
} 