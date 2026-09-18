package com.chua.starter.sse.support.socket;

import com.chua.socket.support.session.SocketSession;
import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.socket.support.session.SocketUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
* SSE Socket 会话模板实现
* 实现 Socket会话template 接口，提供统一的消息发送能力
*
* @版本 1.0.0
* @since 2024-12-24
* @author CH
 */
@Slf4j
public class SseSocketSessionTemplate implements SocketSessionTemplate {

    /** 对象_映射器 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
    * SSE 连接超时时间（毫秒），默认30分钟
    */
    private final long timeout;

    /**
    * 客户端连接映射
    * Key: 客户端标识, 值: sseSocket会话
    */
    private final Map<String, SseSocketSession> sessions = new ConcurrentHashMap<>();

    /**
    * 用户到会话的映射
    * Key: 用户标识, 值: 客户端标识
    */
    private final Map<String, String> userSessionMap = new ConcurrentHashMap<>();

    /** 创建 sseSocket会话template 实例 */
    public SseSocketSessionTemplate() {
        /**
    * this。
    * @param 1000L 1000L
    */
        this(30 * 60 * 1000L);
    }

    /**
    * 创建 sseSocket会话template 实例
    * @param timeout 超时
    */
    public SseSocketSessionTemplate(long timeout) {
        this.timeout = timeout;
    }

    /**
    * 创建新的 SSE 连接
    *
    * @param clientId 客户端标识，如果为空则自动生成
    * @return SseEmitter
    */
    public SseEmitter createConnection(String clientId) {
        if (clientId == null || clientId.isEmpty()) {
            clientId = UUID.randomUUID().toString().replace("-", "");
        }

        // 如果已存在连接，先关闭旧连接
        if (sessions.containsKey(clientId)) {
            /**
            * 移除。
            * @param clientId 客户端标识
            * @param sessions.get(clientId 会话.获取(客户端标识
            */
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
                    .data(OBJECT_MAPPER.writeValueAsString(Map.of(
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
    /** 保存 */
    public SocketSession save(String clientId, SocketSession session) {
        if (session instanceof SseSocketSession sseSession) {
            sessions.put(clientId, sseSession);
            return session;
        }
        return null;
    }

    @Override
    /** 移除 */
    public void remove(String clientId, SocketSession session) {
        SseSocketSession removed = sessions.remove(clientId);
        if (removed != null) {
            removed.disconnect();
        }
    }

    @Override
    /** 获取会话 */
    public SocketSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    @Override
    /** 发送 */
    public void send(String sessionId, String event, String msg) {
        SseSocketSession session = sessions.get(sessionId);
        if (session != null && session.isConnected()) {
            session.send(event, msg);
        }
    }

    @Override
    /** Broadcast */
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
    /** 发送转为用户 */
    public void sendToUser(String userId, String event, String msg) {
        String clientId = userSessionMap.get(userId);
        if (clientId != null) {
            /**
    * 发送。
    * @param clientId 客户端标识
    * @param event 事件
    * @param msg msg
    */
            send(clientId, event, msg);
        }
    }

    @Override
    /** 获取online会话 */
    public List<SocketSession> getOnlineSessions() {
        return sessions.values().stream()
                .filter(SocketSession::isConnected)
                .collect(Collectors.toList());
    }

    @Override
    /** 获取online会话 */
    public List<SocketSession> getOnlineSession(String type, String roomId) {
        // SSE 不支持房间概念，返回所有在线会话
        return getOnlineSessions();
    }

    @Override
    /** 获取online用户 */
    public List<SocketUser> getOnlineUsers(String type) {
        return sessions.values().stream()
                .filter(SocketSession::isConnected)
                .map(SocketSession::getUser)
                .filter(user -> user != null)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    /** 获取Online计算数量 */
    public int getOnlineCount() {
        return (int) sessions.values().stream()
                .filter(SocketSession::isConnected)
                .count();
    }

    @Override
    /** 开始 */
    public void start() {
        log.info("[SSE] Socket 服务启动");
    }

    @Override
    /** 停止 */
    public void stop() {
        log.info("[SSE] Socket 服务停止");
        sessions.values().forEach(SseSocketSession::disconnect);
        sessions.clear();
        userSessionMap.clear();
    }

    /**
    * 绑定用户到会话
    *
    * @param userId   用户标识
    * @param clientId 客户端标识
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
    * @param userId 用户标识
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
