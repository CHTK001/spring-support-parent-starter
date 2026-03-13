package com.chua.starter.sync.data.support.service;

import com.chua.starter.sync.data.support.entity.MonitorSyncAlert;

import java.util.List;
import java.util.Map;

/**
 * 告警服务接口
 *
 * @author System
 * @since 2026/03/09
 */
public interface AlertService {

    /**
     * 触发告警
     *
     * @param taskId 任务ID
     * @param alertType 告警类型
     * @param alertLevel 告警级别
     * @param message 告警消息
     */
    void triggerAlert(Long taskId, String alertType, String alertLevel, String message);

    /**
     * 查询告警列表
     *
     * @param taskId 任务ID（可选）
     * @param level 告警级别（可选）
     * @param resolved 是否已解决（可选）
     * @return 告警列表
     */
    List<MonitorSyncAlert> listAlerts(Long taskId, String level, Boolean resolved);

    /**
     * 确认告警
     *
     * @param alertId 告警ID
     */
    void resolveAlert(Long alertId);

    /**
     * 发送告警通知
     *
     * @param alert 告警信息
     */
    void sendNotification(MonitorSyncAlert alert);

    /**
     * 检查告警规则
     *
     * @param taskId 任务ID
     * @param metrics 性能指标
     */
    void checkAlertRules(Long taskId, Map<String, Object> metrics);
}
