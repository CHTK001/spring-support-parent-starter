package com.chua.starter.sse.support.socket;

import com.chua.common.support.json.Json;
import com.chua.common.support.utils.StringUtils;
import com.chua.socket.support.session.SocketSession;
import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.socket.support.session.SocketUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * SSE Socket 会话模板实现
 * 实现 SocketSessionTemplate 接口，提供统一的消息发送能力
 *
 * @author CH
 * @version 1.0.0
 * @since 2024-12-24
 */
@Slf4j
public class SseSocketSessionTemplate implements SocketSessionTemplate {

    /**
     * SSE 连接超时时间（毫秒），默认30分钟
     */
    private final long timeout;

    /**
     * 客户端连接映射
     * key: clientId, value: SseSocketSession
     */
    private final Map<String, SseSocketSession> sessions = new ConcurrentHashMap<>();

    /**
     * 用户到会话的映射
     * key: userId, value: clientId
     */
    private final Map<String, String> userSessionMap = new ConcurrentHashMap<>();

    public SseSocketSessionTemplate() {
        this(30 * 60 * 1000L);
    }

    public SseSocketSessionTemplate(long timeout) {
        this.timeout = timeout;
    }

    /**
     * 创建新的 SSE 连接
     *
     * @param clientId 客户端ID，如果为空则自动生成
     * @return SseEmitter
     */
    public SseEmitter createConnection(String clientId) {
        if (StringUtils.isEmpty(clientId)) {
            clientId = UUID.randomUUID().toString().replace("-", "");
        }

        // 如果已存在连接，先关闭旧连接
        if (sessions.containsKey(clientId)) {
            remove(clientId, sessions.get(clientId));
        }

        SseEmitter emitter = new SseEmitter(timeout);
        SseSocketSession session = new SseSocketSession(clientId, emitter);

        String finalClientId = clientId;
        emitter.onCompletion(() -> {
            log.debug("[SSE] 连接完成: clientId={}", finalClientId);
            sessions.remove(finalClientId);
        });
        emitter.onTimeout(() -> {
            log.debug("[SSE] 连接超时: clientId={}", finalClientId);
            sessions.remove(finalClientId);
        });
        emitter.onError(e -> {
            log.debug("[SSE] 连接错误: clientId={}", finalClientId, e);
            sessions.remove(finalClientId);
        });

        sessions.put(clientId, session);
        log.info("[SSE] 新连接创建: clientId={}", clientId);

        // 发送连接成功消息
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Json.toJson(Map.of(
                            "clientId", clientId,
                            "timestamp", System.currentTimeMillis(),
                            "message", "连接成功"
                    ))));
        } catch (IOException e) {
            log.error("[SSE] 发送连接消息失败: clientId={}", clientId, e);
        }

        return emitter;
    }

    @Override
    public SocketSession save(String clientId, SocketSession session) {
        if (session instanceof SseSocketSession sseSession) {
            sessions.put(clientId, sseSession);
            return session;
        }
        return null;
    }

    @Override
    public void remove(String clientId, SocketSession session) {
        SseSocketSession removed = sessions.remove(clientId);
        if (removed != null) {
            removed.disconnect();
        }
    }

    @Override
    public SocketSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    @Override
    public void send(String sessionId, String event, String msg) {
        SseSocketSession session = sessions.get(sessionId);
        if (session != null && session.isConnected()) {
            session.send(event, msg);
        }
    }

    @Override
    public void broadcast(String event, String msg) {
        List<String> failedClients = new ArrayList<>();

        for (Map.Entry<String, SseSocketSession> entry : sessions.entrySet()) {
            SseSocketSession session = entry.getValue();
            if (session.isConnected()) {
                try {
                    session.send(event, msg);
                } catch (Exception e) {
                    failedClients.add(entry.getKey());
                }
            } else {
                failedClients.add(entry.getKey());
            }
        }

        // 清理失效连接
        failedClients.forEach(sessions::remove);

        log.debug("[SSE] 广播消息: event={}, 成功={}, 失败={}",
                event, sessions.size(), failedClients.size());
    }

    @Override
    public void sendToUser(String userId, String event, String msg) {
        String clientId = userSessionMap.get(userId);
        if (clientId != null) {
            send(clientId, event, msg);
        }
    }

    @Override
    public List<SocketSession> getOnlineSessions() {
        return sessions.values().stream()
                .filter(SocketSession::isConnected)
                .collect(Collectors.toList());
    }

    @Override
    public List<SocketSession> getOnlineSession(String type, String roomId) {
        // SSE 不支持房间概念，返回所有在线会话
        return getOnlineSessions();
    }

    @Override
    public List<SocketUser> getOnlineUsers(String type) {
        return sessions.values().stream()
                .filter(SocketSession::isConnected)
                .map(SocketSession::getUser)
                .filter(user -> user != null)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public int getOnlineCount() {
        return (int) sessions.values().stream()
                .filter(SocketSession::isConnected)
                .count();
    }

    @Override
    public void start() {
        log.info("[SSE] Socket 服务启动");
    }

    @Override
    public void stop() {
        log.info("[SSE] Socket 服务停止");
        sessions.values().forEach(SseSocketSession::disconnect);
        sessions.clear();
        userSessionMap.clear();
    }

    /**
     * 绑定用户到会话
     *
     * @param userId   用户ID
     * @param clientId 客户端ID
     */
    public void bindUser(String userId, String clientId) {
        userSessionMap.put(userId, clientId);
        SseSocketSession session = sessions.get(clientId);
        if (session != null) {
            SocketUser user = new SocketUser();
            user.setUserId(userId);
            session.setUser(user);
        }
    }

    /**
     * 解绑用户
     *
     * @param userId 用户ID
     */
    public void unbindUser(String userId) {
        userSessionMap.remove(userId);
    }

    /**
     * 获取所有连接状态
     *
     * @return 连接状态信息
     */
    public Map<String, Object> getConnectionStatus() {
        return Map.of(
                "totalConnections", sessions.size(),
                "onlineCount", getOnlineCount(),
                "timestamp", System.currentTimeMillis()
        );
    }
}
