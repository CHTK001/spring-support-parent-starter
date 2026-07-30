package com.chua.starter.server.support.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServerMetricsTaskSettings {

    private Integer serverId;

    private String serverName;

    private Boolean inheritGlobal;

    private Boolean enabled;

    private String schedulerMode;

    private Boolean jobEnabled;

    private Long refreshIntervalMs;

    private Integer timeoutMs;

    private Boolean cacheEnabled;

    private Long cacheTtlSeconds;

    private Long lastRefreshAt;

    private Long nextRefreshAt;

    private Integer historyLimit;

    private String status;

    private Integer jobId;

    private String jobNo;

    private String jobName;

    private String jobScheduleType;

    private String jobScheduleTime;

    private String jobStatus;

    private Long jobLastTriggerAt;

    private Long jobNextTriggerAt;

    private Boolean manualTriggerSupported;
}
