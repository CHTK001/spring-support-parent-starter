package com.chua.starter.server.support.service;

import com.chua.starter.server.support.entity.ServerAlertEvent;
import com.chua.starter.server.support.model.ServerAlertSettings;
import com.chua.starter.server.support.model.ServerMetricsSnapshot;
import java.util.List;

public interface ServerAlertService {

    ServerAlertSettings getGlobalSettings();

    ServerAlertSettings saveGlobalSettings(ServerAlertSettings settings);

    ServerAlertSettings getHostSettings(Integer serverId);

    ServerAlertSettings saveHostSettings(Integer serverId, ServerAlertSettings settings);

    List<ServerAlertEvent> listAlerts(Integer serverId, Integer limit);

    /**
     * 按条件筛选服务器告警历史。
     */
    List<ServerAlertEvent> listAlerts(
            Integer serverId,
            String metricType,
            String severity,
            Long startTime,
            Long endTime,
            Integer limit
    );

    void processSnapshot(ServerMetricsSnapshot snapshot);
}
