package com.chua.report.client.starter.task;

import com.chua.report.client.starter.pojo.DeviceMetrics;
import com.chua.report.client.starter.properties.ReportClientProperties;
import com.chua.report.client.starter.service.DeviceDataService;
import com.chua.report.client.starter.service.ReportPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 设备数据上报定时任务
 * @author CH
 * @since 2024/12/19
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = ReportClientProperties.PRE, name = "enable", havingValue = "true")
public class DeviceReportTask {

    private final DeviceDataService deviceDataService;
    private final ReportPushService reportPushService;
    private final ReportClientProperties properties;

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicLong lastSuccessTime = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);

    @PostConstruct
    public void init() {
        log.info("设备数据上报任务初始化完成");
        log.info("推送间隔: {}秒", properties.getPushInterval());
        log.info("目标服务器: {}", properties.getAddress());
        
        // 初始化时推送设备基本信息
        pushDeviceInfo();
        initialized.set(true);
    }

    /**
     * 定时推送设备指标数据
     */
    @Scheduled(fixedDelayString = "#{@reportClientProperties.pushInterval * 1000}", 
               initialDelayString = "#{@reportClientProperties.initialDelay * 1000}")
    public void pushDeviceMetrics() {
        if (!properties.isEnable()) {
            log.debug("设备数据上报已禁用，跳过推送");
            return;
        }

        try {
            log.debug("开始收集设备指标数据");
            
            // 获取设备指标数据
            DeviceMetrics metrics = deviceDataService.getDeviceMetrics();
            if (metrics == null) {
                log.warn("获取设备指标数据失败，跳过本次推送");
                return;
            }

            // 推送数据
            boolean success = reportPushService.pushDeviceMetrics(metrics);
            
            if (success) {
                lastSuccessTime.set(System.currentTimeMillis());
                failureCount.set(0);
                log.debug("设备指标数据推送成功: CPU={}%, 内存={}%, 磁盘={}%", 
                    formatPercentage(metrics.getCpuUsage()),
                    formatPercentage(metrics.getMemoryUsage()),
                    formatPercentage(metrics.getDiskUsage()));
            } else {
                long failures = failureCount.incrementAndGet();
                log.warn("设备指标数据推送失败，连续失败次数: {}", failures);
                
                // 连续失败超过阈值时，尝试重新推送设备信息
                if (failures % 10 == 0) {
                    log.info("连续失败{}次，尝试重新推送设备基本信息", failures);
                    pushDeviceInfo();
                }
            }

        } catch (Exception e) {
            failureCount.incrementAndGet();
            log.error("推送设备指标数据异常", e);
        }
    }

    /**
     * 定时推送设备基本信息（频率较低）
     */
    @Scheduled(fixedRate = 300000, initialDelay = 5000) // 每5分钟推送一次设备信息
    public void pushDeviceInfoPeriodically() {
        if (!properties.isEnable() || !initialized.get()) {
            return;
        }

        pushDeviceInfo();
    }

    /**
     * 推送设备基本信息
     */
    private void pushDeviceInfo() {
        try {
            log.debug("开始推送设备基本信息");
            
            DeviceMetrics deviceInfo = deviceDataService.getDeviceInfo();
            if (deviceInfo == null) {
                log.warn("获取设备基本信息失败");
                return;
            }

            boolean success = reportPushService.pushDeviceInfo(deviceInfo);
            if (success) {
                log.info("设备基本信息推送成功: {} ({})", 
                    deviceInfo.getDeviceName(), deviceInfo.getIpAddress());
            } else {
                log.warn("设备基本信息推送失败");
            }

        } catch (Exception e) {
            log.error("推送设备基本信息异常", e);
        }
    }

    /**
     * 定时推送客户端健康状态
     */
    @Scheduled(fixedDelayString = "#{@reportClientProperties.pushInterval * 1000}",
               initialDelayString = "#{@reportClientProperties.initialDelay * 1000}")
    public void pushClientHealthStatus() {
        if (!properties.isEnable()) {
            log.debug("设备数据上报已禁用，跳过客户端健康状态上报");
            return;
        }

        try {
            // 获取客户端IP地址
            String clientIp = getClientIpAddress();
            // 获取客户端端口（从配置中获取）
            Integer clientPort = properties.getReceivablePort();

            // 如果端口未配置，使用默认值
            if (clientPort == null || clientPort <= 0) {
                clientPort = 8080; // 默认端口
            }

            boolean success = reportPushService.pushClientHealth(clientIp, clientPort);

            if (success) {
                log.debug("客户端健康状态上报成功: {}:{}", clientIp, clientPort);
            } else {
                log.warn("客户端健康状态上报失败: {}:{}", clientIp, clientPort);
            }

        } catch (Exception e) {
            log.error("推送客户端健康状态异常", e);
        }
    }

    /**
     * 健康检查任务
     */
    @Scheduled(fixedRate = 60000, initialDelay = 30000) // 每分钟检查一次
    public void healthCheck() {
        if (!properties.isEnable()) {
            return;
        }

        try {
            long currentTime = System.currentTimeMillis();
            long lastSuccess = lastSuccessTime.get();
            long failures = failureCount.get();

            // 检查是否长时间未成功推送
            if (lastSuccess > 0 && (currentTime - lastSuccess) > 300000) { // 5分钟
                log.warn("设备数据推送已超过5分钟未成功，最后成功时间: {}", 
                    new java.util.Date(lastSuccess));
                
                // 尝试测试连接
                boolean connected = reportPushService.testConnection();
                log.info("连接测试结果: {}", connected ? "成功" : "失败");
            }

            // 记录统计信息
            if (failures > 0) {
                log.info("设备数据推送统计 - 连续失败次数: {}, 最后成功时间: {}", 
                    failures, lastSuccess > 0 ? new java.util.Date(lastSuccess) : "从未成功");
            }

        } catch (Exception e) {
            log.error("健康检查异常", e);
        }
    }

    /**
     * 格式化百分比
     */
    private String formatPercentage(Double value) {
        return value != null ? String.format("%.1f", value) : "N/A";
    }

    /**
     * 获取客户端IP地址
     */
    private String getClientIpAddress() {
        try {
            // 首先尝试从设备信息中获取IP地址
            DeviceMetrics deviceInfo = deviceDataService.getDeviceInfo();
            if (deviceInfo != null && deviceInfo.getIpAddress() != null) {
                return deviceInfo.getIpAddress();
            }

            // 如果设备信息中没有IP地址，则获取本机IP地址
            InetAddress localHost = InetAddress.getLocalHost();
            return localHost.getHostAddress();

        } catch (Exception e) {
            log.warn("获取客户端IP地址失败，使用默认值: {}", e.getMessage());
            return "127.0.0.1"; // 默认IP地址
        }
    }

    /**
     * 获取推送统计信息
     */
    public String getStatistics() {
        return String.format("连续失败次数: %d, 最后成功时间: %s",
            failureCount.get(),
            lastSuccessTime.get() > 0 ? new java.util.Date(lastSuccessTime.get()) : "从未成功");
    }
}
