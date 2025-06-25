package com.chua.report.client.starter.service.impl;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.report.client.starter.entity.DeviceMetrics;
import com.chua.report.client.starter.properties.ReportClientProperties;
import com.chua.report.client.starter.service.ReportPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * HTTP数据推送服务实现
 * @author CH
 * @since 2024/12/19
 */
@Slf4j
@Service
@Spi("http")
@RequiredArgsConstructor
public class HttpReportPushServiceImpl implements ReportPushService {

    private final ReportClientProperties properties;
    private final RestTemplate deviceReportRestTemplate;

    @Override
    public boolean pushDeviceMetrics(DeviceMetrics metrics) {
        if (!isConfigValid()) {
            log.warn("推送配置无效，跳过设备指标数据推送");
            return false;
        }

        try {
            String url = buildUrl("/api/v1/monitor/device/metrics");
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<DeviceMetrics> request = new HttpEntity<>(metrics, headers);
            
            ResponseEntity<Map> response = deviceReportRestTemplate.exchange(
                url, HttpMethod.POST, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.debug("设备指标数据推送成功: {}", metrics.getDeviceId());
                return true;
            } else {
                log.warn("设备指标数据推送失败，状态码: {}", response.getStatusCode());
                return false;
            }
            
        } catch (Exception e) {
            log.error("推送设备指标数据异常: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean pushDeviceInfo(DeviceMetrics deviceInfo) {
        if (!isConfigValid()) {
            log.warn("推送配置无效，跳过设备基本信息推送");
            return false;
        }

        try {
            String url = buildUrl("/api/v1/monitor/device/info");
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<DeviceMetrics> request = new HttpEntity<>(deviceInfo, headers);
            
            ResponseEntity<Map> response = deviceReportRestTemplate.exchange(
                url, HttpMethod.POST, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.debug("设备基本信息推送成功: {}", deviceInfo.getDeviceId());
                return true;
            } else {
                log.warn("设备基本信息推送失败，状态码: {}", response.getStatusCode());
                return false;
            }
            
        } catch (Exception e) {
            log.error("推送设备基本信息异常: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean testConnection() {
        if (!isConfigValid()) {
            log.warn("推送配置无效，无法测试连接");
            return false;
        }

        try {
            String url = buildUrl("/api/v1/monitor/device/ping");
            
            Map<String, Object> pingData = new HashMap<>();
            pingData.put("deviceId", properties.getDeviceId());
            pingData.put("timestamp", System.currentTimeMillis());
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(pingData, headers);
            
            ResponseEntity<Map> response = deviceReportRestTemplate.exchange(
                url, HttpMethod.POST, request, Map.class);
            
            boolean success = response.getStatusCode().is2xxSuccessful();
            log.info("连接测试结果: {}", success ? "成功" : "失败");
            return success;
            
        } catch (Exception e) {
            log.error("测试连接异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 构建完整的URL
     */
    private String buildUrl(String path) {
        String address = properties.getAddress();
        if (!address.startsWith("http://") && !address.startsWith("https://")) {
            address = "http://" + address;
        }
        
        if (address.endsWith("/")) {
            address = address.substring(0, address.length() - 1);
        }

        return FileUtils.normalize(address, properties.getAddressReportPath(), path);
    }

    /**
     * 检查配置是否有效
     */
    private boolean isConfigValid() {
        return properties.isEnable() && 
               StringUtils.isNotBlank(properties.getAddress());
    }

    /**
     * 重试推送
     */
    private boolean retryPush(String url, HttpEntity<?> request, String operation) {
        int retryCount = properties.getRetryCount();
        
        for (int i = 0; i < retryCount; i++) {
            try {
                ResponseEntity<Map> response = deviceReportRestTemplate.exchange(
                    url, HttpMethod.POST, request, Map.class);
                
                if (response.getStatusCode().is2xxSuccessful()) {
                    log.debug("{}重试第{}次成功", operation, i + 1);
                    return true;
                }
                
            } catch (Exception e) {
                log.warn("{}重试第{}次失败: {}", operation, i + 1, e.getMessage());
                
                if (i < retryCount - 1) {
                    try {
                        Thread.sleep(1000 * (i + 1)); // 递增延迟
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        log.error("{}重试{}次后仍然失败", operation, retryCount);
        return false;
    }
}
