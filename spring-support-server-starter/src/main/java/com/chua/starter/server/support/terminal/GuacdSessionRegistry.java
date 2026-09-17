package com.chua.starter.server.support.terminal;

import lombok.extern.slf4j.Slf4j;
import org.apache.guacamole.GuacamoleException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
* guacd 会话注册表
* <p>
* 以 tunnelid 为键集中管理全部在线远控会话，提供注册、查找、
* 主动关闭与空闲扫描能力。容器使用 {@link ConcurrentHashMap}
* 保证多连接并发安全。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@Slf4j
@Component
public class GuacdSessionRegistry {

    /** 在线会话表：tunnelid -> 会话 */
    private final Map<String, GuacdSession> sessions = new ConcurrentHashMap<>();

    /**
    * 审计记录 标识 -> tunnelid 索引（供管理端按记录强制关闭）
    */
    private final Map<Long, String> sessionRecordIndex = new ConcurrentHashMap<>();

    /**
    * 注册会话
    *
    * @param session 会话
    */
    public void register(GuacdSession session) {
        sessions.put(session.getTunnelId(), session);
        if (session.getSessionId() != null) {
            sessionRecordIndex.put(session.getSessionId(), session.getTunnelId());
        }
    }

    /**
    * 按 WS 会话 标识 查找会话
    *
    * @param wsSessionId 浏览器 WS 会话 标识
    * @return 会话，不存在时返回 空
    */
    public GuacdSession findByWsSessionId(String wsSessionId) {
        for (GuacdSession session : sessions.values()) {
            if (session.getWebSocketSession().getId().equals(wsSessionId)) {
                return session;
            }
        }
        return null;
    }

    /**
    * 按审计记录 标识 强制关闭会话
    *
    * @param recordId 审计记录 标识
    * @param reason   关闭原因
    * @return true 表示存在会话并已触发关闭
    */
    public boolean closeByRecordId(Long recordId, String reason) {
        String tunnelId = sessionRecordIndex.get(recordId);
        if (tunnelId == null) {
            return false;
        }
        GuacdSession session = sessions.get(tunnelId);
        if (session == null) {
            sessionRecordIndex.remove(recordId);
            return false;
        }
        /**
        * 关闭。
        * @param session 会话
        * @param reason ReasonMLmlml
        */
        close(session, reason);
        return true;
    }

    /**
    * 关闭指定会话并从注册表移除
    * <p>
    * 幂等：重复调用无副作用。关闭顺序：guacd 套接字 -> 浏览器 WS。
    * </p>
    *
    * @param session 会话
    * @param reason  关闭原因（写入审计）
    */
    public void close(GuacdSession session, String reason) {
        if (!session.markClosed()) {
            return;
        }
        // 中断读线程，令其尽快退出阻塞读取
        if (session.getReaderThread() != null && session.getReaderThread().isAlive()) {
            session.getReaderThread().interrupt();
        }
        // 关闭 guacd 隧道（幂等）
        session.getTunnel().close();
        try {
            if (session.getWebSocketSession().isOpen()) {
                synchronized (session.getSendLock()) {
                    session.getWebSocketSession().close();
                }
            }
        } catch (IOException e) {
            log.warn("[terminal] 关闭浏览器 WS 失败 tunnelId={}", session.getTunnelId(), e);
        }
        sessions.remove(session.getTunnelId());
        if (session.getSessionId() != null) {
            sessionRecordIndex.remove(session.getSessionId());
        }
        log.info("[terminal] 会话关闭 tunnelId={} hostId={} reason={}",
                session.getTunnelId(), session.getHostId(), reason);
    }

    /**
    * 扫描并关闭空闲超时会话
    *
    * @param idleMinutes 最大空闲分钟数
    * @return 本次关闭的会话数
    */
    public int sweepIdle(int idleMinutes) {
        List<GuacdSession> idleSessions = new ArrayList<>();
        for (GuacdSession session : sessions.values()) {
            long idleMinutes_ = ChronoUnit.MINUTES.between(session.getLastActiveTime(), LocalDateTime.now());
            if (idleMinutes_ >= idleMinutes) {
                idleSessions.add(session);
            }
        }
        for (GuacdSession session : idleSessions) {
            close(session, "空闲超时");
        }
        return idleSessions.size();
    }

    /**
    * 当前在线会话列表快照
    *
    * @return 会话集合
    */
    public List<GuacdSession> snapshot() {
        return new ArrayList<>(sessions.values());
    }

    /**
    * 当前在线会话数量
    *
    * @return 数量
    */
    public int size() {
        return sessions.size();
    }

    /**
    * 判断 WS 会话是否仍属于某个活跃 guacd 会话
    *
    * @param wsSession WS 会话
    * @return 是否活跃
    */
    public boolean isTracked(WebSocketSession wsSession) {
        return findByWsSessionId(wsSession.getId()) != null;
    }
}
