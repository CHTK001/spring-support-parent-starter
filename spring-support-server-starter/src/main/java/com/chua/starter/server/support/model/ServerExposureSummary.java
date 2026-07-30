package com.chua.starter.server.support.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerExposureSummary {
    private Integer serverId;
    private String serverCode;
    private String host;
    private String targetHost;
    private String actualOsName;
    private Long scannedAt;
    private Long duration;
    private Integer totalPorts;
    private Integer openPorts;
    private List<ServerExposurePortView> ports;
}
