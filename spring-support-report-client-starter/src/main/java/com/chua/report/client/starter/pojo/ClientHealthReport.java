package com.chua.report.client.starter.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 客户端健康状态上报
 * @author CH
 * @since 2024/12/19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientHealthReport {

    /**
     * 客户端IP地址
     */
    private String clientIp;

    /**
     * 客户端端口
     */
    private Integer clientPort;

    /**
     * 上报时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reportTime;

    /**
     * 客户端版本
     */
    private String clientVersion;

    /**
     * 主机名
     */
    private String hostname;

    /**
     * 操作系统信息
     */
    private String osInfo;

    /**
     * CPU使用率（百分比）
     */
    private Double cpuUsage;

    /**
     * 内存使用率（百分比）
     */
    private Double memoryUsage;

    /**
     * 磁盘使用率（百分比）
     */
    private Double diskUsage;

    /**
     * 网络延迟（毫秒）
     */
    private Long networkLatency;

    /**
     * 额外信息（JSON格式）
     */
    private String extraInfo;

    /**
     * 获取客户端地址
     * 
     * @return 客户端地址（IP:端口）
     */
    public String getClientAddress() {
        return clientIp + ":" + clientPort;
    }

    /**
     * 判断健康状态是否正常
     * 
     * @return true表示健康状态正常
     */
    public boolean isHealthy() {
        // 基本的健康检查逻辑
        if (cpuUsage != null && cpuUsage > 90) {
            return false;
        }
        if (memoryUsage != null && memoryUsage > 90) {
            return false;
        }
        if (diskUsage != null && diskUsage > 95) {
            return false;
        }
        if (networkLatency != null && networkLatency > 5000) {
            return false;
        }
        return true;
    }

    /**
     * 获取健康状态描述
     * 
     * @return 健康状态描述
     */
    public String getHealthStatus() {
        if (isHealthy()) {
            return "健康";
        }
        
        StringBuilder status = new StringBuilder();
        if (cpuUsage != null && cpuUsage > 90) {
            status.append("CPU使用率过高(").append(cpuUsage).append("%) ");
        }
        if (memoryUsage != null && memoryUsage > 90) {
            status.append("内存使用率过高(").append(memoryUsage).append("%) ");
        }
        if (diskUsage != null && diskUsage > 95) {
            status.append("磁盘使用率过高(").append(diskUsage).append("%) ");
        }
        if (networkLatency != null && networkLatency > 5000) {
            status.append("网络延迟过高(").append(networkLatency).append("ms) ");
        }
        
        return status.toString().trim();
    }

    /**
     * 创建默认的健康状态上报
     * 
     * @param clientIp 客户端IP
     * @param clientPort 客户端端口
     * @return 健康状态上报对象
     */
    public static ClientHealthReport createDefault(String clientIp, Integer clientPort) {
        return ClientHealthReport.builder()
                .clientIp(clientIp)
                .clientPort(clientPort)
                .reportTime(LocalDateTime.now())
                .build();
    }
}
