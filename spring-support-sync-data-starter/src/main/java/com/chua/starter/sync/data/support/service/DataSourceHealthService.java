package com.chua.starter.sync.data.support.service;

import com.chua.starter.sync.data.support.entity.MonitorSyncAlert;
import com.chua.starter.sync.data.support.mapper.MonitorSyncAlertMapper;
import com.chua.starter.sync.data.support.properties.SyncProperties;
import com.chua.starter.sync.data.support.sync.health.DataSourceHealthChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据源健康检查服务
 *
 * @author System
 * @since 2026/03/09
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.sync", name = "health-check-enabled", havingValue = "true", matchIfMissing = true)
public class DataSourceHealthService {
    
    private final DataSourceHealthChecker healthChecker;
    private final MonitorSyncAlertMapper alertMapper;
    private final SyncProperties syncProperties;
    
    /**
     * 注册的数据源
     */
    private final Map<String, DataSource> dataSources = new ConcurrentHashMap<>();
    
    /**
     * 注册数据源
     *
     * @param name 数据源名称
     * @param dataSource 数据源
     */
    public void registerDataSource(String name, DataSource dataSource) {
        dataSources.put(name, dataSource);
        log.info("数据源已注册: {}", name);
    }
    
    /**
     * 注销数据源
     *
     * @param name 数据源名称
     */
    public void unregisterDataSource(String name) {
        dataSources.remove(name);
        healthChecker.clearCache(name);
        log.info("数据源已注销: {}", name);
    }
    
    /**
     * 定时健康检查
     * 根据配置的间隔执行
     */
    @Scheduled(fixedDelayString = "${plugin.sync.health-check-interval:60000}")
    public void scheduledHealthCheck() {
        if (!syncProperties.isHealthCheckEnabled()) {
            return;
        }
        
        log.debug("开始执行数据源健康检查, 数据源数量: {}", dataSources.size());
        
        dataSources.forEach((name, dataSource) -> {
            try {
                DataSourceHealthChecker.DataSourceHealth health = healthChecker.checkHealth(name, dataSource);
                
                if (!health.isHealthy()) {
                    sendHealthAlert(name, health);
                }
                
                log.debug("数据源健康检查完成: name={}, status={}, responseTime={}ms", 
                        name, health.getStatus(), health.getResponseTime());
                        
            } catch (Exception e) {
                log.error("数据源健康检查异常: name={}", name, e);
            }
        });
    }
    
    /**
     * 手动检查数据源健康状态
     *
     * @param name 数据源名称
     * @return 健康状态
     */
    public DataSourceHealthChecker.DataSourceHealth checkHealth(String name) {
        DataSource dataSource = dataSources.get(name);
        if (dataSource == null) {
            throw new IllegalArgumentException("数据源不存在: " + name);
        }
        
        return healthChecker.checkHealth(name, dataSource);
    }
    
    /**
     * 获取所有数据源的健康状态
     *
     * @return 健康状态映射
     */
    public Map<String, DataSourceHealthChecker.DataSourceHealth> getAllHealth() {
        return healthChecker.getAllHealth();
    }
    
    /**
     * 发送健康告警
     *
     * @param name 数据源名称
     * @param health 健康状态
     */
    private void sendHealthAlert(String name, DataSourceHealthChecker.DataSourceHealth health) {
        if (!syncProperties.isAlertEnabled()) {
            return;
        }
        
        String message = String.format("数据源连接异常: %s - %s", name, health.getMessage());
        
        MonitorSyncAlert alert = new MonitorSyncAlert();
        alert.setSyncTaskId(0L); // 系统级告警
        alert.setAlertType("CONNECTION");
        alert.setAlertLevel("ERROR");
        alert.setAlertMessage(message);
        alert.setAlertTime(LocalDateTime.now());
        alert.setIsResolved(0);
        
        try {
            alertMapper.insert(alert);
            log.warn("数据源健康告警已记录: {}", message);
        } catch (Exception e) {
            log.error("记录数据源健康告警失败", e);
        }
    }
}
