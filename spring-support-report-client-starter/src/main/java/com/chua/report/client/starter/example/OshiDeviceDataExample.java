package com.chua.report.client.starter.example;

import com.chua.report.client.starter.entity.DeviceMetrics;
import com.chua.report.client.starter.properties.ReportClientProperties;
import com.chua.report.client.starter.service.impl.OshiDeviceDataServiceImpl;
import lombok.extern.slf4j.Slf4j;

/**
 * OshiDeviceDataService 使用示例
 * 演示如何使用修复后的 OshiDeviceDataServiceImpl 获取设备指标
 * 
 * @author CH
 * @since 2024/12/19
 */
@Slf4j
public class OshiDeviceDataExample {

    public static void main(String[] args) {
        // 创建配置属性
        ReportClientProperties properties = new ReportClientProperties();
        properties.setDeviceId("example-device-001");
        properties.setDeviceName("示例设备");
        properties.setReceivablePort(8080);

        // 创建设备数据服务实例
        OshiDeviceDataServiceImpl deviceDataService = new OshiDeviceDataServiceImpl(properties);

        try {
            log.info("开始获取设备指标数据...");
            
            // 获取设备基本信息
            DeviceMetrics deviceInfo = deviceDataService.getDeviceInfo();
            printDeviceInfo(deviceInfo);
            
            // 获取完整的设备指标
            DeviceMetrics deviceMetrics = deviceDataService.getDeviceMetrics();
            printDeviceMetrics(deviceMetrics);
            
            // 检查设备在线状态
            boolean isOnline = deviceDataService.isDeviceOnline();
            log.info("设备在线状态: {}", isOnline ? "在线" : "离线");
            
        } catch (Exception e) {
            log.error("获取设备数据时发生错误", e);
        }
    }

    /**
     * 打印设备基本信息
     */
    private static void printDeviceInfo(DeviceMetrics info) {
        log.info("=== 设备基本信息 ===");
        log.info("设备ID: {}", info.getDeviceId());
        log.info("设备名称: {}", info.getDeviceName());
        log.info("IP地址: {}", info.getIpAddress());
        log.info("端口: {}", info.getPort());
        log.info("主机名: {}", info.getHostname());
        log.info("操作系统: {} {}", info.getOsName(), info.getOsVersion());
        log.info("系统架构: {}", info.getOsArch());
        log.info("在线状态: {}", formatOnlineStatus(info.getOnline()));
        log.info("采集时间: {}", info.getCollectTime());
    }

    /**
     * 打印设备指标信息
     */
    private static void printDeviceMetrics(DeviceMetrics metrics) {
        log.info("=== 设备指标信息 ===");
        
        // CPU指标
        log.info("CPU使用率: {}%", formatPercentage(metrics.getCpuUsage()));
        log.info("CPU核心数: {}", metrics.getCpuCores());
        log.info("CPU频率: {} Hz", metrics.getCpuFrequency());

        // 内存指标
        log.info("总内存: {} MB", bytesToMB(metrics.getTotalMemory()));
        log.info("已用内存: {} MB", bytesToMB(metrics.getUsedMemory()));
        log.info("可用内存: {} MB", bytesToMB(metrics.getAvailableMemory()));
        log.info("内存使用率: {}%", formatPercentage(metrics.getMemoryUsage()));

        // 磁盘指标
        log.info("总磁盘空间: {} GB", bytesToGB(metrics.getTotalDisk()));
        log.info("已用磁盘空间: {} GB", bytesToGB(metrics.getUsedDisk()));
        log.info("可用磁盘空间: {} GB", bytesToGB(metrics.getAvailableDisk()));
        log.info("磁盘使用率: {}%", formatPercentage(metrics.getDiskUsage()));
        
        // 网络指标
        log.info("网络接收字节数: {}", metrics.getNetworkInBytes());
        log.info("网络发送字节数: {}", metrics.getNetworkOutBytes());
        log.info("网络接收包数: {}", metrics.getNetworkInPackets());
        log.info("网络发送包数: {}", metrics.getNetworkOutPackets());
        
        // 系统指标
        log.info("系统负载: {}", metrics.getLoadAverage());
        log.info("系统运行时间: {} 秒", metrics.getUptime());
        log.info("进程数: {}", metrics.getProcessCount());
        log.info("线程数: {}", metrics.getThreadCount());
        
        // 温度指标
        if (metrics.getTemperature() != null && metrics.getTemperature() > 0) {
            log.info("CPU温度: {}°C", formatTemperature(metrics.getTemperature()));
        }
    }

    /**
     * 字节转换为MB
     */
    private static long bytesToMB(Long bytes) {
        return bytes != null ? bytes / (1024 * 1024) : 0;
    }

    /**
     * 字节转换为GB
     */
    private static long bytesToGB(Long bytes) {
        return bytes != null ? bytes / (1024 * 1024 * 1024) : 0;
    }

    /**
     * 格式化百分比
     */
    private static String formatPercentage(Double value) {
        return value != null ? String.format("%.2f", value) : "0.00";
    }

    /**
     * 格式化温度
     */
    private static String formatTemperature(Double value) {
        return value != null ? String.format("%.1f", value) : "0.0";
    }

    /**
     * 格式化在线状态
     */
    private static String formatOnlineStatus(Boolean online) {
        return online != null && online ? "在线" : "离线";
    }
}
