package com.chua.starter.ai.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 指标历史分析请求
 *
 * @author CH
 * @since 2026/7/30
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerMetricHistoryAnalyzeRequest {

    private String serverName;
    private String serverCode;
    private String host;
    private String metricType;
    private Integer sampleCount;
    private String historySummary;
    private java.util.List<String> alerts;
}
