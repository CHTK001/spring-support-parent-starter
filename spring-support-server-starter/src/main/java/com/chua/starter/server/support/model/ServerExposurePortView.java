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
public class ServerExposurePortView {
    private Integer port;
    private String localAddress;
    private String protocol;
    private String state;
    private String serviceName;
    private String serviceProduct;
    private String serviceVersion;
    private Long processId;
    private String processName;
    private String banner;
    private Long responseTime;
    private List<String> tags;
}
