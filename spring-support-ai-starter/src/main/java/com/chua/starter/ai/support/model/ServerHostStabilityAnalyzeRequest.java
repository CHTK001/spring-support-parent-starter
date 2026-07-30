package com.chua.starter.ai.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 主机稳定性分析请求
 *
 * @author CH
 * @since 2026/7/30
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerHostStabilityAnalyzeRequest {

    private String serverName;
    private String serverCode;
    private String serverType;
    private String osType;
    private String host;
    private String actualOsName;
    private String publicIp;
    private Double cpuUsage;
    private Double memoryUsage;
    private Double diskUsage;
    private Double networkRxPacketsPerSecond;
    private Double networkTxPacketsPerSecond;
    private Double latencyMs;
    private java.util.List<String> alerts;
}
