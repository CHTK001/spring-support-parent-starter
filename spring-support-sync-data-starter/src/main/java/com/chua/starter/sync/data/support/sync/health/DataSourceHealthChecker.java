package com.chua.starter.sync.data.support.sync.health;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据源健康检查器
 *
 * @author System
 * @since 2026/03/09
 */
@Slf4j
@Component
public class DataSourceHealthChecker {
    
    private final Map<String, DataSourceHealth> healthCache = new ConcurrentHashMap<>();
    
    /**
     * 检查数据源健康状态
     *
     * @param name 数据源名称
     * @param dataSource 数据源
     * @return 健康状态
     */
    public DataSourceHealth checkHealth(String name, DataSource dataSource) {
        long startTime = System.currentTimeMillis();
        DataSourceHealth health = new DataSourceHealth();
        health.setName(name);
        health.setCheckTime(startTime);
        
        try (Connection conn = dataSource.getConnection()) {
            boolean isValid = conn.isValid(5);
            health.setHealthy(isValid);
            
            if (isValid) {
                health.setStatus("UP");
                health.setMessage("数据源连接正常");
            } else {
                health.setStatus("DOWN");
                health.setMessage("数据源连接无效");
            }
            
            health.setResponseTime(System.currentTimeMillis() - startTime);
            
        } catch (SQLException e) {
            health.setHealthy(false);
            health.setStatus("DOWN");
            health.setMessage("数据源连接失败: " + e.getMessage());
            health.setResponseTime(System.currentTimeMillis() - startTime);
            health.setError(e.getMessage());
            
            log.error("数据源健康检查失败: name={}", name, e);
        }
        
        healthCache.put(name, health);
        return health;
    }
    
    /**
     * 获取缓存的健康状态
     *
     * @param name 数据源名称
     * @return 健康状态，不存在返回null
     */
    public DataSourceHealth getCachedHealth(String name) {
        return healthCache.get(name);
    }
    
    /**
     * 获取所有数据源的健康状态
     *
     * @return 健康状态映射
     */
    public Map<String, DataSourceHealth> getAllHealth() {
        return new ConcurrentHashMap<>(healthCache);
    }
    
    /**
     * 清除健康状态缓存
     *
     * @param name 数据源名称
     */
    public void clearCache(String name) {
        healthCache.remove(name);
    }
    
    /**
     * 清除所有健康状态缓存
     */
    public void clearAllCache() {
        healthCache.clear();
    }
    
    /**
     * 数据源健康状态
     */
    @Data
    public static class DataSourceHealth {
        /**
         * 数据源名称
         */
        private String name;
        
        /**
         * 是否健康
         */
        private boolean healthy;
        
        /**
         * 状态：UP/DOWN
         */
        private String status;
        
        /**
         * 消息
         */
        private String message;
        
        /**
         * 响应时间（毫秒）
         */
        private long responseTime;
        
        /**
         * 检查时间
         */
        private long checkTime;
        
        /**
         * 错误信息
         */
        private String error;
    }
}
