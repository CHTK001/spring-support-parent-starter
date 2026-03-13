package com.chua.starter.sync.data.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.sync.data.support.entity.MonitorSyncAlert;
import com.chua.starter.sync.data.support.mapper.MonitorSyncAlertMapper;
import com.chua.starter.sync.data.support.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 告警服务实现
 *
 * @author System
 * @since 2026/03/09
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    private final MonitorSyncAlertMapper alertMapper;
    
    // 告警阈值配置
    private static final double ERROR_RATE_THRESHOLD = 0.05;
    private static final double MEMORY_THRESHOLD = 0.85;
    private static final long LATENCY_THRESHOLD = 5000;

    @Override
    public void triggerAlert(Long taskId, String alertType, String alertLevel, String message) {
        MonitorSyncAlert alert = new MonitorSyncAlert();
        alert.setSyncTaskId(taskId);
        alert.setAlertType(alertType);
        alert.setAlertLevel(alertLevel);
        alert.setAlertMessage(message);
        alert.setAlertTime(LocalDateTime.now());
        alert.setIsResolved(0);
        
        alertMapper.insert(alert);
        log.warn("触发告警: taskId={}, type={}, level={}, message={}", 
            taskId, alertType, alertLevel, message);
        
        sendNotification(alert);
    }

    @Override
    public List<MonitorSyncAlert> listAlerts(Long taskId, String level, Boolean resolved) {
        LambdaQueryWrapper<MonitorSyncAlert> wrapper = new LambdaQueryWrapper<>();

        
        if (taskId != null) {
            wrapper.eq(MonitorSyncAlert::getSyncTaskId, taskId);
        }
        if (level != null && !level.isEmpty()) {
            wrapper.eq(MonitorSyncAlert::getAlertLevel, level);
        }
        if (resolved != null) {
            wrapper.eq(MonitorSyncAlert::getIsResolved, resolved ? 1 : 0);
        }
        
        wrapper.orderByDesc(MonitorSyncAlert::getAlertTime);
        
        return alertMapper.selectList(wrapper);
    }

    @Override
    public void resolveAlert(Long alertId) {
        MonitorSyncAlert alert = alertMapper.selectById(alertId);
        if (alert != null) {
            alert.setIsResolved(1);
            alert.setResolvedTime(LocalDateTime.now());
            alertMapper.updateById(alert);
            log.info("告警已确认: alertId={}", alertId);
        }
    }

    @Override
    public void sendNotification(MonitorSyncAlert alert) {
        // 邮件通知
        sendEmailNotification(alert);
        
        // Webhook通知
        sendWebhookNotification(alert);
    }

    @Override
    public void checkAlertRules(Long taskId, Map<String, Object> metrics) {
        // 检查错误率
        if (metrics.containsKey("errorRate")) {
            BigDecimal errorRate = (BigDecimal) metrics.get("errorRate");
            if (errorRate.doubleValue() > ERROR_RATE_THRESHOLD) {
                triggerAlert(taskId, "PERFORMANCE", "WARNING", 
                    String.format("错误率过高: %.2f%%", errorRate.doubleValue() * 100));
            }
        }
        
        // 检查内存使用
        if (metrics.containsKey("memoryUsage")) {
            BigDecimal memoryUsage = (BigDecimal) metrics.get("memoryUsage");
            if (memoryUsage.doubleValue() > MEMORY_THRESHOLD) {
                triggerAlert(taskId, "MEMORY", "ERROR", 
                    String.format("内存使用率过高: %.2f%%", memoryUsage.doubleValue() * 100));
            }
        }
        
        // 检查延迟
        if (metrics.containsKey("avgLatency")) {
            BigDecimal avgLatency = (BigDecimal) metrics.get("avgLatency");
            if (avgLatency.longValue() > LATENCY_THRESHOLD) {
                triggerAlert(taskId, "PERFORMANCE", "WARNING", 
                    String.format("平均延迟过高: %dms", avgLatency.longValue()));
            }
        }
    }

    private void sendEmailNotification(MonitorSyncAlert alert) {
        // TODO: 实现邮件通知
        log.debug("发送邮件通知: {}", alert.getAlertMessage());
    }

    private void sendWebhookNotification(MonitorSyncAlert alert) {
        // TODO: 实现Webhook通知
        log.debug("发送Webhook通知: {}", alert.getAlertMessage());
    }
}
