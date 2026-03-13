package com.chua.starter.sync.data.support.service;

import com.chua.starter.sync.data.support.entity.MonitorSyncAlert;
import com.chua.starter.sync.data.support.mapper.MonitorSyncAlertMapper;
import com.chua.starter.sync.data.support.properties.SyncProperties;
import com.chua.starter.sync.data.support.sync.performance.MemoryMonitor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 内存告警服务
 *
 * @author System
 * @since 2026/03/09
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.sync", name = "alert-enabled", havingValue = "true", matchIfMissing = true)
public class MemoryAlertService {
    
    private final MemoryMonitor memoryMonitor;
    private final MonitorSyncAlertMapper alertMapper;
    private final SyncProperties syncProperties;
    
    /**
     * 定时检查内存使用率并发送告警
     * 每分钟检查一次
     */
    @Scheduled(fixedRate = 60000)
    public void checkMemoryAndAlert() {
        if (!syncProperties.isAlertEnabled()) {
            return;
        }
        
        double memoryUsage = memoryMonitor.getCurrentMemoryUsage();
        double threshold = syncProperties.getMemoryThreshold();
        
        if (memoryUsage > threshold) {
            sendMemoryAlert(memoryUsage, threshold);
        }
    }
    
    /**
     * 发送内存告警
     *
     * @param memoryUsage 当前内存使用率
     * @param threshold 告警阈值
     */
    public void sendMemoryAlert(double memoryUsage, double threshold) {
        String level = determineAlertLevel(memoryUsage, threshold);
        String message = String.format("系统内存使用率过高: %.2f%% (阈值: %.2f%%)", 
                memoryUsage * 100, threshold * 100);
        
        MonitorSyncAlert alert = new MonitorSyncAlert();
        alert.setSyncTaskId(0L); // 系统级告警
        alert.setAlertType("MEMORY");
        alert.setAlertLevel(level);
        alert.setAlertMessage(message);
        alert.setAlertTime(LocalDateTime.now());
        alert.setIsResolved(0);
        
        try {
            alertMapper.insert(alert);
            log.warn("内存告警已记录: {}", message);
        } catch (Exception e) {
            log.error("记录内存告警失败", e);
        }
    }
    
    /**
     * 为特定任务发送内存告警
     *
     * @param taskId 任务ID
     * @param memoryUsage 当前内存使用率
     */
    public void sendTaskMemoryAlert(Long taskId, double memoryUsage) {
        double threshold = syncProperties.getMemoryThreshold();
        String level = determineAlertLevel(memoryUsage, threshold);
        String message = String.format("任务执行期间内存使用率过高: %.2f%% (阈值: %.2f%%)", 
                memoryUsage * 100, threshold * 100);
        
        MonitorSyncAlert alert = new MonitorSyncAlert();
        alert.setSyncTaskId(taskId);
        alert.setAlertType("MEMORY");
        alert.setAlertLevel(level);
        alert.setAlertMessage(message);
        alert.setAlertTime(LocalDateTime.now());
        alert.setIsResolved(0);
        
        try {
            alertMapper.insert(alert);
            log.warn("任务内存告警已记录: taskId={}, {}", taskId, message);
        } catch (Exception e) {
            log.error("记录任务内存告警失败, taskId={}", taskId, e);
        }
    }
    
    /**
     * 确定告警级别
     *
     * @param memoryUsage 当前内存使用率
     * @param threshold 告警阈值
     * @return 告警级别
     */
    private String determineAlertLevel(double memoryUsage, double threshold) {
        if (memoryUsage >= threshold + 0.10) {
            return "CRITICAL"; // 超过阈值10%以上
        } else if (memoryUsage >= threshold + 0.05) {
            return "ERROR"; // 超过阈值5%-10%
        } else if (memoryUsage >= threshold) {
            return "WARNING"; // 刚超过阈值
        } else {
            return "INFO";
        }
    }
}
