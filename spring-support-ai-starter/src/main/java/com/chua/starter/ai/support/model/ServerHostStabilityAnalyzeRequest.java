package com.chua.starter.ai.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 主机稳定性分析请求
 *
 * @since 2026/7/30
 * @author CH
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerHostStabilityAnalyzeRequest {

    /**
     * 服务器名称
     */
    private String serverName;

    /**
     * 服务器编码
     */
    private String serverCode;

    /**
     * 服务器类型
     */
    private String serverType;

    /**
     * 操作系统类型
     */
    private String osType;

    /**
     * 主机地址
     */
    private String host;

    /**
     * 实际操作系统名称
     */
    private String actualOsName;

    /**
     * 公网 IP
     */
    private String publicIp;

    /**
     * CPU 使用率
     */
    private Double cpuUsage;

    /**
     * 内存使用率
     */
    private Double memoryUsage;

    /**
     * 磁盘使用率
     */
    private Double diskUsage;

    /**
     * 网络接收速率（包/秒）
     */
    private Double networkRxPacketsPerSecond;

    /**
     * 网络发送速率（包/秒）
     */
    private Double networkTxPacketsPerSecond;

    /**
     * 延迟（毫秒）
     */
    private Double latencyMs;

    /**
     * 告警列表
     */
    private java.util.List<String> alerts;
}
