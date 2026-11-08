package com.chua.starter.server.support.terminal;

import com.chua.starter.server.support.service.TerminalSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 串口（Serial）会话注册表
 * <p>
 * 以 tunnelId 为键集中管理全部在线串口会话，提供注册、查找、主动关闭与空闲扫描能力。
 * 容器使用 {@link ConcurrentHashMap} 保证多连接并发安全。
 * 设计与 {@link GuacdSessionRegistry} 同构：关闭顺序为 读线程中断 → 串口关闭 → 浏览器 WS → 审计记录闭环。
 * </p>
 *
 * @author CH
 * @since 4.0.0.48
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SerialSessionRegistry {

    /**
     * 在线会话表：tunnelId -> 会话
     */
    private final Map<String, SerialSession> sessions = new ConcurrentHashMap<>();

    /**
     * 审计会话记录 标识 -> tunnelId 索引（供管理端按记录强制关闭）
     */
    private final Map<Long, String> recordIdIndex = new ConcurrentHashMap<>();

    /**
     * 主机 标识 -> tunnelId 索引（按主机级别关停全部会话）
     */
    private final Map<Long, String> hostIdIndex = new ConcurrentHashMap<>();

    /**
     * 设备路径 -> tunnelId 索引（供同设备多会话管理，最后的连接优先）
     */
    private final Map<String, String> deviceIndex = new ConcurrentHashMap<>();

    /**
     * 终端会话审计服务
     */
    private final TerminalSessionService terminalSessionService;

    /**
     * 注册会话
     *
     * @param session 会话
     */
    public void register(SerialSession session) {
        sessions.put(session.getTunnelId(), session);
        if (session.getSessionId() != null) {
            recordIdIndex.put(session.getSessionId(), session.getTunnelId());
        }
        if (session.getHostId() != null) {
            hostIdIndex.put(session.getHostId(), session.getTunnelId());
        }
        if (session.getDevice() != null) {
            deviceIndex.put(session.getDevice(), session.getTunnelId());
        }
    }

    /**
     * 按 WS 会话 标识 查找会话
     *
     * @param wsSessionId 浏览器 WS 会话 标识
     * @return 会话，不存在时返回 空
     */
    public SerialSession findByWsSessionId(String wsSessionId) {
        for (SerialSession session : sessions.values()) {
            if (session.getWebSocketSession().getId().equals(wsSessionId)) {
                return session;
            }
        }
        return null;
    }

    /**
     * 按设备路径查找活跃会话（同设备只保留最后连接）
     *
     * @param device 设备路径
     * @return 会话，不存在时返回 空
     */
    public SerialSession findByDevice(String device) {
        String tunnelId = deviceIndex.get(device);
        if (tunnelId == null) {
            return null;
        }
        return sessions.get(tunnelId);
    }

    /**
     * 按审计记录 标识 强制关闭会话
     *
     * @param recordId 审计记录 标识
     * @param reason   关闭原因
     * @return true 表示存在会话并已触发关闭
     */
    public boolean closeByRecordId(Long recordId, String reason) {
        String tunnelId = recordIdIndex.get(recordId);
        if (tunnelId == null) {
            return false;
        }
        SerialSession session = sessions.get(tunnelId);
        if (session == null) {
            recordIdIndex.remove(recordId);
            return false;
        }
        return close(session, reason);
    }

    /**
     * 按主机 标识 强制关闭全部会话
     *
     * @param hostId 主机 标识
     * @param reason 关闭原因
     * @return true 表示至少有关闭成功的会话
     */
    public boolean closeByHostId(Long hostId, String reason) {
        String tunnelId = hostIdIndex.get(hostId);
        if (tunnelId == null) {
            return false;
        }
        SerialSession session = sessions.get(tunnelId);
        if (session == null) {
            hostIdIndex.remove(hostId);
            return false;
        }
        return close(session, reason);
    }

    /**
     * 关闭指定会话并从注册表移除
     * <p>
     * 幂等：重复调用无副作用。关闭顺序：串口 → 浏览器 WebSocket。
     * 会话关闭后同步闭环审计记录。
     * </p>
     *
     * @param session 会话
     * @param reason  关闭原因（写入审计）
     * @return true 表示本次调用真正执行了关闭，false 表示会话已由其它路径关闭过
     */
    public boolean close(SerialSession session, String reason) {
        if (!session.markClosed()) {
            return false;
        }
        // 中断读线程，令其尽快退出阻塞读取
        if (session.getReaderThread() != null && session.getReaderThread().isAlive()) {
            session.getReaderThread().interrupt();
        }
        // 关闭串口（幂等）
        session.close();
        // 关闭浏览器 WebSocket
        try {
            if (session.getWebSocketSession().isOpen()) {
                synchronized (session.getWriteLock()) {
                    session.getWebSocketSession().close(CloseStatus.NORMAL);
                }
            }
        } catch (IOException e) {
            log.warn("[serial] 关闭浏览器 WS 失败 tunnelId={}", session.getTunnelId(), e);
        }
        // 闭环审计记录（写入关闭原因）
        Long recordId = session.getSessionId();
        if (recordId != null) {
            try {
                terminalSessionService.closeRecord(recordId, reason);
            } catch (Exception e) {
                log.warn("[serial] 关闭审计记录失败 recordId={}: {}", recordId, e.getMessage());
            }
            recordIdIndex.remove(recordId);
        }
        sessions.remove(session.getTunnelId());
        if (session.getHostId() != null) {
            hostIdIndex.remove(session.getHostId());
        }
        if (session.getDevice() != null) {
            deviceIndex.remove(session.getDevice());
        }
        log.info("[serial] 会话关闭 tunnelId={} device={} reason={}",
                session.getTunnelId(), session.getDevice(), reason);
        return true;
    }

    /**
     * 扫描并关闭空闲超时的会话
     *
     * @param idleMinutes 最大空闲分钟数
     * @return 本次关闭的会话数
     */
    public int sweepIdle(int idleMinutes) {
        List<SerialSession> idleSessions = new ArrayList<>();
        for (SerialSession session : sessions.values()) {
            long idleMinutes_ = ChronoUnit.MINUTES.between(session.getLastActiveTime(), LocalDateTime.now());
            if (idleMinutes_ >= idleMinutes) {
                idleSessions.add(session);
            }
        }
        int closed = 0;
        for (SerialSession session : idleSessions) {
            if (close(session, "空闲超时")) {
                closed++;
            }
        }
        return closed;
    }

    /**
     * 当前在线会话列表快照
     *
     * @return 会话集合
     */
    public List<SerialSession> snapshot() {
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
     * 判断 WS 会话是否仍属于某个活跃串口会话
     *
     * @param wsSession WS 会话
     * @return 是否活跃
     */
    public boolean isTracked(WebSocketSession wsSession) {
        return findByWsSessionId(wsSession.getId()) != null;
    }
}
