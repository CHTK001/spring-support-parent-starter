package com.chua.starter.monitor.support.model;

import java.time.LocalDateTime;

/**
 * Agent 列表行视图（登记了探针标识的监控目标 + 在线状态）
 *
 * @param monitorId      监控目标 标识
 * @param monitorName    监控目标 名称
 * @param monitorHost    监控目标 地址
 * @param monitorAgentKey 探针标识
 * @param online         是否在线
 * @param lastHeartbeat  最后一次心跳时间，可为空
 * @author CH
 * @since 4.0.0.42
 */
public record AgentListItemVO(Long monitorId, String monitorName, String monitorHost,
                              String monitorAgentKey, boolean online, LocalDateTime lastHeartbeat) {
}
