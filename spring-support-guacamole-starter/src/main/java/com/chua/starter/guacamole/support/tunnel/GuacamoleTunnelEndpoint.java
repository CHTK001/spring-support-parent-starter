package com.chua.starter.guacamole.support.tunnel;

import com.chua.starter.guacamole.support.service.GuacamoleService;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.net.GuacamoleTunnel;
import org.apache.guacamole.protocol.GuacamoleInstruction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guacamole隧道WebSocket端点
 * 使用JSR-356标准实现
 *
 * @author CH
 * @since 2024/7/24
 */
@Slf4j
@Component
@ServerEndpoint("/guacamole/tunnel/{connectionId}")
public class GuacamoleTunnelEndpoint {

    /**
     * 会话与隧道的映射关系
     */
    private static final Map<String, GuacamoleTunnel> TUNNEL_MAP = new ConcurrentHashMap<>();
    /**
     * 静态注入，因为ServerEndpoint不支持直接注入
     */
    private static GuacamoleService guacamoleService;

    @Autowired
    public void setGuacamoleService(GuacamoleService guacamoleService) {
        GuacamoleTunnelEndpoint.guacamoleService = guacamoleService;
    }

    /**
     * 连接建立时调用
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("connectionId") String connectionId) {
        log.info("WebSocket连接已建立: {}, connectionId: {}", session.getId(), connectionId);

        try {
            // 解析连接参数 (格式: protocol,host,port)
            String[] params = connectionId.split(",");
            if (params.length < 3) {
                sendError(session, "连接参数不足");
                session.close();
                return;
            }

            String protocol = params[0];
            String host = params[1];
            int port = Integer.parseInt(params[2]);

            // 创建Guacamole隧道
            GuacamoleTunnel tunnel = guacamoleService.createTunnel(protocol, host, port);
            TUNNEL_MAP.put(session.getId(), tunnel);

            // 发送连接成功消息
            session.getBasicRemote().sendText("connected");

            // 启动一个线程读取Guacamole服务器的响应
            new Thread(() -> {
                try {
                    while (tunnel.isOpen() && session.isOpen()) {
                        char[] chars = tunnel.acquireReader().read();
                        int length = chars.length;
                        if (length > 0) {
                            session.getBasicRemote().sendText(new String(chars, 0, length));
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
                session.close();
            } catch (IOException ex) {
                log.error("发送错误消息时发生异常", ex);
            }
        }
    }

    /**
     * 收到客户端消息时调用
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        GuacamoleTunnel tunnel = TUNNEL_MAP.get(session.getId());
        if (tunnel != null && tunnel.isOpen()) {
            try {
                // 将客户端指令发送到Guacamole服务器
                tunnel.acquireWriter().writeInstruction(new GuacamoleInstruction(message));
            } catch (GuacamoleException e) {
                log.error("处理Guacamole消息时发生错误", e);
                try {
                    closeTunnel(session);
                } catch (Exception ex) {
                    log.error("关闭隧道时发生错误", ex);
                }
            }
        }
    }

    /**
     * 连接关闭时调用
     */
    @OnClose
    public void onClose(Session session) {
        closeTunnel(session);
        log.info("WebSocket连接已关闭: {}", session.getId());
    }

    /**
     * 发生错误时调用
     */
    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WebSocket连接发生错误: {}", session.getId(), error);
        closeTunnel(session);
    }

    /**
     * 关闭隧道
     */
    private void closeTunnel(Session session) {
        String sessionId = session.getId();
        GuacamoleTunnel tunnel = TUNNEL_MAP.remove(sessionId);
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
    private void sendError(Session session, String message) throws IOException {
        session.getBasicRemote().sendText("error:" + message);
    }
} 