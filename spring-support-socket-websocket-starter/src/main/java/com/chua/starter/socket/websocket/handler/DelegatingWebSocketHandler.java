package com.chua.starter.socket.websocket.handler;

import com.chua.starter.socket.websocket.session.WebSocketSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;

/**
 * 委托 WebSocket 处理器
 * <p>
 * 管理会话并将事件委托给用户实现的 {@link WebSocketMessageHandler}
 * </p>
 *
 * @author CH
 * @since 2025-12-25
 */
@Slf4j
@RequiredArgsConstructor
public class DelegatingWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionManager sessionManager;
    private final List<WebSocketMessageHandler> handlers;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessionManager.addSession(session);
        log.info("[WebSocket] 连接建立: sessionId={}, uri={}", session.getId(), session.getUri());
        
        for (WebSocketMessageHandler handler : handlers) {
            try {
                handler.onConnect(session);
            } catch (Exception e) {
                log.error("[WebSocket] onConnect 处理异常: {}", e.getMessage(), e);
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("[WebSocket] 收到消息: sessionId={}, payload={}", session.getId(), payload);
        
        for (WebSocketMessageHandler handler : handlers) {
            try {
                handler.onMessage(session, payload);
            } catch (Exception e) {
                log.error("[WebSocket] onMessage 处理异常: {}", e.getMessage(), e);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessionManager.removeSession(session);
        log.info("[WebSocket] 连接关闭: sessionId={}, code={}, reason={}", 
                session.getId(), status.getCode(), status.getReason());
        
        for (WebSocketMessageHandler handler : handlers) {
            try {
                handler.onClose(session, status.getCode(), status.getReason());
            } catch (Exception e) {
                log.error("[WebSocket] onClose 处理异常: {}", e.getMessage(), e);
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("[WebSocket] 传输错误: sessionId={}", session.getId(), exception);
        
        for (WebSocketMessageHandler handler : handlers) {
            try {
                handler.onError(session, exception);
            } catch (Exception e) {
                log.error("[WebSocket] onError 处理异常: {}", e.getMessage(), e);
            }
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
