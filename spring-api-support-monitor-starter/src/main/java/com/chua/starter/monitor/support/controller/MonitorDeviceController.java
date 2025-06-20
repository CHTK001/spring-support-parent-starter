package com.chua.starter.monitor.support.controller;

import com.chua.common.support.annotations.ApiJSDoc;
import com.chua.common.support.protocol.request.PageRequest;
import com.chua.common.support.protocol.request.Request;
import com.chua.common.support.protocol.response.Response;
import com.chua.common.support.protocol.response.ResponseUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.monitor.support.entity.MonitorDeviceInfo;
import com.chua.starter.monitor.support.entity.MonitorDeviceMetrics;
import com.chua.starter.monitor.support.service.MonitorDeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备监控数据接收控制器
 * @author CH
 * @since 2024/12/19
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/monitor/device")
@RequiredArgsConstructor
@ApiJSDoc("设备监控数据接收")
public class MonitorDeviceController {

    private final MonitorDeviceService deviceService;

    /**
     * 接收设备指标数据
     */
    @PostMapping("/metrics")
    @ApiJSDoc("接收设备指标数据")
    public Response<Map<String, Object>> receiveDeviceMetrics(
            @RequestBody Request<DeviceMetricsDto> request,
            HttpServletRequest httpRequest) {
        
        try {
            DeviceMetricsDto dto = request.getData();
            if (dto == null) {
                return ResponseUtils.error("设备指标数据不能为空");
            }

            // 获取客户端IP
            String clientIp = getClientIpAddress(httpRequest);
            log.debug("接收到设备指标数据，来源IP: {}, 设备ID: {}", clientIp, dto.getDeviceId());

            // 转换为实体对象
            MonitorDeviceMetrics metrics = convertToMetricsEntity(dto);
            
            // 如果没有IP地址，使用客户端IP
            if (StringUtils.isBlank(metrics.getMonitorDeviceMetricsIpAddress())) {
                metrics.setMonitorDeviceMetricsIpAddress(clientIp);
            }

            // 保存指标数据
            boolean success;
            if (StringUtils.isNotBlank(dto.getDeviceId())) {
                success = deviceService.saveDeviceMetrics(metrics);
            } else {
                // 根据IP和端口保存
                success = deviceService.saveDeviceMetricsByIpAndPort(
                    clientIp, dto.getPort(), metrics);
            }

            if (success) {
                Map<String, Object> result = new HashMap<>();
                result.put("received", true);
                result.put("timestamp", System.currentTimeMillis());
                result.put("deviceId", metrics.getMonitorDeviceMetricsDeviceId());
                
                return ResponseUtils.ok(result);
            } else {
                return ResponseUtils.error("保存设备指标数据失败");
            }

        } catch (Exception e) {
            log.error("接收设备指标数据异常", e);
            return ResponseUtils.error("接收设备指标数据异常: " + e.getMessage());
        }
    }

    /**
     * 接收设备基本信息
     */
    @PostMapping("/info")
    @ApiJSDoc("接收设备基本信息")
    public Response<Map<String, Object>> receiveDeviceInfo(
            @RequestBody Request<DeviceInfoDto> request,
            HttpServletRequest httpRequest) {
        
        try {
            DeviceInfoDto dto = request.getData();
            if (dto == null) {
                return ResponseUtils.error("设备信息不能为空");
            }

            // 获取客户端IP
            String clientIp = getClientIpAddress(httpRequest);
            log.debug("接收到设备基本信息，来源IP: {}, 设备ID: {}", clientIp, dto.getDeviceId());

            // 转换为实体对象
            MonitorDeviceInfo deviceInfo = convertToDeviceInfoEntity(dto);
            
            // 如果没有IP地址，使用客户端IP
            if (StringUtils.isBlank(deviceInfo.getMonitorDeviceInfoIpAddress())) {
                deviceInfo.setMonitorDeviceInfoIpAddress(clientIp);
            }

            // 保存设备信息
            boolean success = deviceService.saveOrUpdateDeviceInfo(deviceInfo);

            if (success) {
                Map<String, Object> result = new HashMap<>();
                result.put("received", true);
                result.put("timestamp", System.currentTimeMillis());
                result.put("deviceId", deviceInfo.getMonitorDeviceInfoDeviceId());
                
                return ResponseUtils.ok(result);
            } else {
                return ResponseUtils.error("保存设备信息失败");
            }

        } catch (Exception e) {
            log.error("接收设备基本信息异常", e);
            return ResponseUtils.error("接收设备基本信息异常: " + e.getMessage());
        }
    }

    /**
     * 设备心跳检测
     */
    @PostMapping("/ping")
    @ApiJSDoc("设备心跳检测")
    public Response<Map<String, Object>> devicePing(
            @RequestBody Request<Map<String, Object>> request,
            HttpServletRequest httpRequest) {
        
        try {
            Map<String, Object> data = request.getData();
            String deviceId = (String) data.get("deviceId");
            String clientIp = getClientIpAddress(httpRequest);
            
            log.debug("接收到设备心跳，来源IP: {}, 设备ID: {}", clientIp, deviceId);

            // 处理设备心跳
            boolean success = deviceService.handleDevicePing(deviceId, clientIp, null);

            Map<String, Object> result = new HashMap<>();
            result.put("pong", true);
            result.put("timestamp", System.currentTimeMillis());
            result.put("success", success);
            
            return ResponseUtils.ok(result);

        } catch (Exception e) {
            log.error("处理设备心跳异常", e);
            return ResponseUtils.error("处理设备心跳异常: " + e.getMessage());
        }
    }

    /**
     * 查询设备列表
     */
    @GetMapping("/list")
    @ApiJSDoc("查询设备列表")
    public Response<List<MonitorDeviceInfo>> getDeviceList() {
        try {
            List<MonitorDeviceInfo> devices = deviceService.getOnlineDevices();
            return ResponseUtils.ok(devices);
        } catch (Exception e) {
            log.error("查询设备列表异常", e);
            return ResponseUtils.error("查询设备列表异常: " + e.getMessage());
        }
    }

    /**
     * 查询设备详情
     */
    @GetMapping("/detail")
    @ApiJSDoc("查询设备详情")
    public Response<Map<String, Object>> getDeviceDetail(@RequestParam String deviceId) {
        try {
            MonitorDeviceInfo deviceInfo = deviceService.getDeviceInfo(deviceId);
            MonitorDeviceMetrics latestMetrics = deviceService.getLatestMetrics(deviceId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("deviceInfo", deviceInfo);
            result.put("latestMetrics", latestMetrics);
            
            return ResponseUtils.ok(result);
        } catch (Exception e) {
            log.error("查询设备详情异常", e);
            return ResponseUtils.error("查询设备详情异常: " + e.getMessage());
        }
    }

    /**
     * 获取客户端IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.isNotBlank(xForwardedFor) && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (StringUtils.isNotBlank(xRealIp) && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }

    /**
     * 转换为指标实体
     */
    private MonitorDeviceMetrics convertToMetricsEntity(DeviceMetricsDto dto) {
        MonitorDeviceMetrics metrics = new MonitorDeviceMetrics();
        BeanUtils.copyProperties(dto, metrics, "collectTime");
        
        // 设置字段映射
        metrics.setMonitorDeviceMetricsDeviceId(dto.getDeviceId());
        metrics.setMonitorDeviceMetricsDeviceName(dto.getDeviceName());
        metrics.setMonitorDeviceMetricsIpAddress(dto.getIpAddress());
        metrics.setMonitorDeviceMetricsPort(dto.getPort());
        metrics.setMonitorDeviceMetricsOsName(dto.getOsName());
        metrics.setMonitorDeviceMetricsOsVersion(dto.getOsVersion());
        metrics.setMonitorDeviceMetricsOsArch(dto.getOsArch());
        metrics.setMonitorDeviceMetricsHostname(dto.getHostname());
        metrics.setMonitorDeviceMetricsCpuUsage(dto.getCpuUsage());
        metrics.setMonitorDeviceMetricsCpuCores(dto.getCpuCores());
        metrics.setMonitorDeviceMetricsCpuFrequency(dto.getCpuFrequency());
        metrics.setMonitorDeviceMetricsMemoryUsage(dto.getMemoryUsage());
        metrics.setMonitorDeviceMetricsTotalMemory(dto.getTotalMemory());
        metrics.setMonitorDeviceMetricsUsedMemory(dto.getUsedMemory());
        metrics.setMonitorDeviceMetricsAvailableMemory(dto.getAvailableMemory());
        metrics.setMonitorDeviceMetricsDiskUsage(dto.getDiskUsage());
        metrics.setMonitorDeviceMetricsTotalDisk(dto.getTotalDisk());
        metrics.setMonitorDeviceMetricsUsedDisk(dto.getUsedDisk());
        metrics.setMonitorDeviceMetricsAvailableDisk(dto.getAvailableDisk());
        metrics.setMonitorDeviceMetricsNetworkInBytes(dto.getNetworkInBytes());
        metrics.setMonitorDeviceMetricsNetworkOutBytes(dto.getNetworkOutBytes());
        metrics.setMonitorDeviceMetricsNetworkInPackets(dto.getNetworkInPackets());
        metrics.setMonitorDeviceMetricsNetworkOutPackets(dto.getNetworkOutPackets());
        metrics.setMonitorDeviceMetricsLoadAverage(dto.getLoadAverage());
        metrics.setMonitorDeviceMetricsUptime(dto.getUptime());
        metrics.setMonitorDeviceMetricsProcessCount(dto.getProcessCount());
        metrics.setMonitorDeviceMetricsThreadCount(dto.getThreadCount());
        metrics.setMonitorDeviceMetricsTemperature(dto.getTemperature());
        metrics.setMonitorDeviceMetricsOnline(dto.getOnline());
        metrics.setMonitorDeviceMetricsCollectTime(dto.getCollectTime());
        metrics.setMonitorDeviceMetricsExtraData(dto.getExtraData());
        
        return metrics;
    }

    /**
     * 转换为设备信息实体
     */
    private MonitorDeviceInfo convertToDeviceInfoEntity(DeviceInfoDto dto) {
        MonitorDeviceInfo deviceInfo = new MonitorDeviceInfo();
        
        deviceInfo.setMonitorDeviceInfoDeviceId(dto.getDeviceId());
        deviceInfo.setMonitorDeviceInfoDeviceName(dto.getDeviceName());
        deviceInfo.setMonitorDeviceInfoIpAddress(dto.getIpAddress());
        deviceInfo.setMonitorDeviceInfoPort(dto.getPort());
        deviceInfo.setMonitorDeviceInfoOsName(dto.getOsName());
        deviceInfo.setMonitorDeviceInfoOsVersion(dto.getOsVersion());
        deviceInfo.setMonitorDeviceInfoOsArch(dto.getOsArch());
        deviceInfo.setMonitorDeviceInfoHostname(dto.getHostname());
        deviceInfo.setMonitorDeviceInfoCpuCores(dto.getCpuCores());
        deviceInfo.setMonitorDeviceInfoCpuFrequency(dto.getCpuFrequency());
        deviceInfo.setMonitorDeviceInfoTotalMemory(dto.getTotalMemory());
        deviceInfo.setMonitorDeviceInfoTotalDisk(dto.getTotalDisk());
        deviceInfo.setMonitorDeviceInfoExtraData(dto.getExtraData());
        
        return deviceInfo;
    }

    // DTO类定义
    public static class DeviceMetricsDto {
        // 这里应该包含所有DeviceMetrics的字段
        // 为了简化，这里只列出主要字段
        private String deviceId;
        private String deviceName;
        private String ipAddress;
        private Integer port;
        private String osName;
        private String osVersion;
        private String osArch;
        private String hostname;
        private Double cpuUsage;
        private Integer cpuCores;
        private Long cpuFrequency;
        private Double memoryUsage;
        private Long totalMemory;
        private Long usedMemory;
        private Long availableMemory;
        private Double diskUsage;
        private Long totalDisk;
        private Long usedDisk;
        private Long availableDisk;
        private Long networkInBytes;
        private Long networkOutBytes;
        private Long networkInPackets;
        private Long networkOutPackets;
        private String loadAverage;
        private Long uptime;
        private Integer processCount;
        private Integer threadCount;
        private Double temperature;
        private Boolean online;
        private LocalDateTime collectTime;
        private String extraData;
        
        // getter和setter方法
        // ... 省略具体实现
    }

    public static class DeviceInfoDto {
        private String deviceId;
        private String deviceName;
        private String ipAddress;
        private Integer port;
        private String osName;
        private String osVersion;
        private String osArch;
        private String hostname;
        private Integer cpuCores;
        private Long cpuFrequency;
        private Long totalMemory;
        private Long totalDisk;
        private String extraData;
        
        // getter和setter方法
        // ... 省略具体实现
    }
}
