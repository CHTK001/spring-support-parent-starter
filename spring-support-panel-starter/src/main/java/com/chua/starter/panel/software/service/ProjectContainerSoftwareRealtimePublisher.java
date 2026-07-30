package com.chua.starter.panel.software.service;

import java.util.Map;

/**
 * 实时推送接口，用于通过 WebSocket 推送软件操作进度和日志。
 */
public interface ProjectContainerSoftwareRealtimePublisher {
    void publish(String event, String operationId, Map<String, Object> payload);
}
