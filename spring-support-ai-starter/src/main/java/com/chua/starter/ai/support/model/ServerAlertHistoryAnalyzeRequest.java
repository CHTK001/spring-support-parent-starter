package com.chua.starter.ai.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 告警历史分析请求
 *
 * @author CH
 * @since 2026/7/30
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerAlertHistoryAnalyzeRequest {

    private String serverName;
    private String serverCode;
    private String host;
    private Integer alertCount;
    private String alertTimeline;
}
