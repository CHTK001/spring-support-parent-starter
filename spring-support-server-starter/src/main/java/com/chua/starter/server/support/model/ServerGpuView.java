package com.chua.starter.server.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GPU 视图
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerGpuView {

    private String name;
    private String driverVersion;
    private String driverDate;
    private String directXVersion;
    private Double temperature;
    private Long memoryTotalBytes;
    private Long memoryUsedBytes;
    private Double utilizationPercent;
}
