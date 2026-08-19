package com.chua.starter.ai.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 指标历史分析请求
 *
 * @since 2026/7/30
 * @author CH
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerMetricHistoryAnalyzeRequest {

    /**
     * 服务器名称
     */
    private String serverName;

    /**
     * 服务器编码
     */
    private String serverCode;

    /**
     * 主机地址
     */
    private String host;

    /**
     * 指标类型
     */
    private String metricType;

    /**
     * 采样数量
     */
    private Integer sampleCount;

    /**
     * 历史指标摘要
     */
    private String historySummary;

    /**
     * 告警列表
     */
    private java.util.List<String> alerts;
}
