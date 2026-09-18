package com.chua.starter.ai.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
* 告警历史分析请求
*
* @since 2026/7/30
* @author CH
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerAlertHistoryAnalyzeRequest {

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
    * 告警次数
    */
    private Integer alertCount;

    /**
    * 告警时间线摘要
    */
    private String alertTimeline;
}
