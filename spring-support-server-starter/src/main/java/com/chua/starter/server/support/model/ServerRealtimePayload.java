package com.chua.starter.server.support.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServerRealtimePayload {
    private Integer serverId;
    private String serverCode;
    private String status;
    private Boolean online;
    private Integer latencyMs;
    private Double cpuUsage;
    private Integer cpuCores;
    private Double memoryUsage;
    private Long memoryTotalBytes;
    private Long memoryUsedBytes;
    private Double diskUsage;
    private Long diskTotalBytes;
    private Long diskUsedBytes;
    private Double diskReadBytesPerSecond;
    private Double diskWriteBytesPerSecond;
    private Double ioReadBytesPerSecond;
    private Double ioWriteBytesPerSecond;
    private Double networkRxPacketsPerSecond;
    private Double networkTxPacketsPerSecond;
    private String path;
    private Long watchId;
    private String line;
    private String message;
    private boolean finished;
}
