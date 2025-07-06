package com.chua.report.client.starter.controller;

import com.chua.report.client.starter.pojo.ClientHealthInfo;
import com.chua.report.client.starter.service.ClientHealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查控制器
 * @author CH
 * @since 2024/12/19
 */
@Slf4j
@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthController {

    private final ClientHealthService clientHealthService;

    /**
     * 客户端健康状态检查
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        try {
            ClientHealthInfo healthInfo = clientHealthService.getHealthInfo();
            boolean isHealthy = clientHealthService.isHealthy();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", isHealthy ? "UP" : "DOWN");
            response.put("healthy", isHealthy);
            response.put("timestamp", System.currentTimeMillis());
            
            if (healthInfo != null) {
                response.put("deviceId", healthInfo.getDeviceId());
                response.put("deviceName", healthInfo.getDeviceName());
                response.put("lastUpdateTime", healthInfo.getLastUpdateTime());
                response.put("validityPeriod", healthInfo.getValidityPeriod());
                response.put("expireTime", healthInfo.getExpireTime());
                response.put("remainingTime", healthInfo.getRemainingTime());
                response.put("clientVersion", healthInfo.getClientVersion());
                response.put("clientIp", healthInfo.getClientIp());
                response.put("osInfo", healthInfo.getOsInfo());
                response.put("javaVersion", healthInfo.getJavaVersion());
                response.put("startTime", healthInfo.getStartTime());
                response.put("uptime", healthInfo.getUptime());
            }
            
            log.debug("健康检查响应: status={}, deviceId={}, remainingTime={}s", 
                response.get("status"), response.get("deviceId"), response.get("remainingTime"));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("健康检查失败", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "DOWN");
            errorResponse.put("healthy", false);
            errorResponse.put("timestamp", System.currentTimeMillis());
            errorResponse.put("error", e.getMessage());
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 获取详细健康信息
     */
    @GetMapping("/info")
    public ResponseEntity<ClientHealthInfo> getHealthInfo() {
        try {
            ClientHealthInfo healthInfo = clientHealthService.getHealthInfo();
            return ResponseEntity.ok(healthInfo);
        } catch (Exception e) {
            log.error("获取健康信息失败", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * 更新健康状态
     */
    @PostMapping("/update")
    public ResponseEntity<Map<String, Object>> updateHealth(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String deviceName) {
        
        try {
            // 如果没有提供设备信息，使用默认值
            String actualDeviceId = deviceId != null ? deviceId : "unknown";
            String actualDeviceName = deviceName != null ? deviceName : "unknown";
            
            clientHealthService.updateHealthStatus(actualDeviceId, actualDeviceName);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "健康状态更新成功");
            response.put("timestamp", System.currentTimeMillis());
            response.put("deviceId", actualDeviceId);
            response.put("deviceName", actualDeviceName);
            
            log.info("健康状态更新成功: deviceId={}, deviceName={}", actualDeviceId, actualDeviceName);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("更新健康状态失败: deviceId={}, deviceName={}", deviceId, deviceName, e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "健康状态更新失败: " + e.getMessage());
            errorResponse.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 重置健康状态
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetHealth() {
        try {
            clientHealthService.resetHealth();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "健康状态重置成功");
            response.put("timestamp", System.currentTimeMillis());
            
            log.info("健康状态重置成功");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("重置健康状态失败", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "健康状态重置失败: " + e.getMessage());
            errorResponse.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 检查健康状态是否过期
     */
    @GetMapping("/expired")
    public ResponseEntity<Map<String, Object>> checkExpired() {
        try {
            boolean expired = clientHealthService.isHealthExpired();
            long lastUpdateTime = clientHealthService.getLastUpdateTime();
            long validityPeriod = clientHealthService.getHealthValidityPeriod();
            
            Map<String, Object> response = new HashMap<>();
            response.put("expired", expired);
            response.put("lastUpdateTime", lastUpdateTime);
            response.put("validityPeriod", validityPeriod);
            response.put("timestamp", System.currentTimeMillis());
            
            if (!expired) {
                ClientHealthInfo healthInfo = clientHealthService.getHealthInfo();
                if (healthInfo != null) {
                    response.put("remainingTime", healthInfo.getRemainingTime());
                }
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("检查健康状态过期失败", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("expired", true);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 获取健康状态统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getHealthStats() {
        try {
            ClientHealthInfo healthInfo = clientHealthService.getHealthInfo();
            boolean isHealthy = clientHealthService.isHealthy();
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("healthy", isHealthy);
            stats.put("validityPeriod", clientHealthService.getHealthValidityPeriod());
            stats.put("lastUpdateTime", clientHealthService.getLastUpdateTime());
            stats.put("expired", clientHealthService.isHealthExpired());
            
            if (healthInfo != null) {
                stats.put("uptime", healthInfo.getUptime());
                stats.put("remainingTime", healthInfo.getRemainingTime());
                stats.put("deviceId", healthInfo.getDeviceId());
                stats.put("deviceName", healthInfo.getDeviceName());
                stats.put("startTime", healthInfo.getStartTime());
            }
            
            // 系统信息
            Runtime runtime = Runtime.getRuntime();
            stats.put("totalMemory", runtime.totalMemory());
            stats.put("freeMemory", runtime.freeMemory());
            stats.put("maxMemory", runtime.maxMemory());
            stats.put("usedMemory", runtime.totalMemory() - runtime.freeMemory());
            stats.put("availableProcessors", runtime.availableProcessors());
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("获取健康状态统计信息失败", e);
            return ResponseEntity.status(500).build();
        }
    }
}
