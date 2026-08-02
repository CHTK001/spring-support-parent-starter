package com.chua.starter.device.support.service;

import com.chua.starter.device.spi.DeviceSession;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备会话管理器
 * <p>维护设备 ID 与会话的映射关系，统一生命周期管理。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Component
public class DeviceSessionManager {

    /**
     * 设备会话映射表
     */
    @Getter
    private final Map<String, DeviceSession> sessions = new ConcurrentHashMap<>();

    /**
     * 注册会话
     *
     * @param deviceId 设备 ID
     * @param session  设备会话
     */
    public void register(String deviceId, DeviceSession session) {
        sessions.put(deviceId, session);
    }

    /**
     * 注销会话并关闭连接
     *
     * @param deviceId 设备 ID
     */
    public void unregister(String deviceId) {
        DeviceSession session = sessions.remove(deviceId);
        if (session != null) {
            session.close();
        }
    }

    /**
     * 获取会话
     *
     * @param deviceId 设备 ID
     * @return 设备会话，可能为 null
     */
    public DeviceSession getSession(String deviceId) {
        return sessions.get(deviceId);
    }

    /**
     * 获取所有会话
     *
     * @return 会话映射表
     */
    public Map<String, DeviceSession> getAllSessions() {
        return sessions;
    }

    /**
     * 在线会话数
     *
     * @return 在线数量
     */
    public long onlineCount() {
        return sessions.values().stream().filter(DeviceSession::isOnline).count();
    }
}