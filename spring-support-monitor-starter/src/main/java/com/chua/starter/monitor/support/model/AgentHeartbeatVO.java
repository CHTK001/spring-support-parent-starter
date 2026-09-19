package com.chua.starter.monitor.support.model;

import java.time.LocalDateTime;

/**
 * Agent 心跳应答
 *
 * @param serverTime 服务端当前时间（ISO-8601 文本）
 * @param monitorId  监控目标 标识
 * @author CH
 * @since 4.0.0.42
 */
public record AgentHeartbeatVO(String serverTime, Long monitorId) {
    /**
     * 构造心跳应答。
     *
     * @param monitorId 目标 标识
     * @return 包含服务器当前时间和目标 标识的心跳 数据传输对象
     */
    public static AgentHeartbeatVO of(Long monitorId) {
        return new AgentHeartbeatVO(LocalDateTime.now().toString(), monitorId);
    }
}
