package com.chua.starter.sync.data.support.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 同步进度WebSocket处理器
 *
 * @author System
 * @since 2026/03/09
 */
@Slf4j
@Component
public class SyncProgressWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 任务ID -> WebSocket会话集合
    private final Map<Long, Set<WebSocketSession>> taskSessions = new ConcurrentHashMap<>();
    
    // 会话ID -> 任务ID
    private final Map<String, Long> sessionTaskMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket连接建立: sessionId={}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            
            String action = (String) data.get("action");
            
            if ("subscribe".equals(action)) {
                Long taskId = ((Number) data.get("taskId")).longValue();
                subscribeTask(session, taskId);
            } else if ("unsubscribe".equals(action)) {
                unsubscribeTask(session);
            }
        } catch (Exception e) {
            log.error("处理WebSocket消息失败", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        unsubscribeTask(session);
        log.info("WebSocket连接关闭: sessionId={}, status={}", session.getId(), status);
    }

    /**
     * 订阅任务进度
     */
    private void subscribeTask(WebSocketSession session, Long taskId) {
        taskSessions.computeIfAbsent(taskId, k -> ConcurrentHashMap.newKeySet())
                    .add(session);
        sessionTaskMap.put(session.getId(), taskId);
        log.info("订阅任务进度: sessionId={}, taskId={}", session.getId(), taskId);
    }

    /**
     * 取消订阅
     */
    private void unsubscribeTask(WebSocketSession session) {
        Long taskId = sessionTaskMap.remove(session.getId());
        if (taskId != null) {
            Set<WebSocketSession> sessions = taskSessions.get(taskId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    taskSessions.remove(taskId);
                }
            }
            log.info("取消订阅任务进度: sessionId={}, taskId={}", session.getId(), taskId);
        }
    }

    /**
     * 广播进度更新
     */
    public void broadcastProgress(Long taskId, Map<String, Object> progress) {
        Set<WebSocketSession> sessions = taskSessions.get(taskId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        
        try {
            String json = objectMapper.writeValueAsString(progress);
            TextMessage message = new TextMessage(json);
            
            sessions.removeIf(session -> {
                if (!session.isOpen()) {
                    return true;
                }
                try {
                    session.sendMessage(message);
                    return false;
                } catch (IOException e) {
                    log.error("发送进度消息失败: sessionId={}", session.getId(), e);
                    return true;
                }
            });
        } catch (Exception e) {
            log.error("广播进度失败: taskId={}", taskId, e);
        }
    }

    /**
     * 获取任务订阅数
     */
    public int getSubscriberCount(Long taskId) {
        Set<WebSocketSession> sessions = taskSessions.get(taskId);
        return sessions != null ? sessions.size() : 0;
    }
}
