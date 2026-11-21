package com.chua.starter.monitor.support.model;

import java.time.LocalDateTime;

/**
 * Agent 在线状态视图（心跳上下文出参）
 *
 * @param monitorId     监控目标 标识
 * @param lastHeartbeat 最后一次心跳时间，可为空
 * @param online        是否在线
 * @author CH
 * @since 4.0.0.42
 */
public record AgentStatusVO(Long monitorId, LocalDateTime lastHeartbeat, boolean online) {
}
