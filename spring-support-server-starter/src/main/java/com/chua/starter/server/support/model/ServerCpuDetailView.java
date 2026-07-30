package com.chua.starter.server.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * CPU 详细信息视图
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerCpuDetailView {

    private String name;
    private Integer physicalCoreCount;
    private Integer logicalProcessorCount;
    private Integer socketCount;
    private Double baseSpeedMhz;
    private Double currentSpeedMhz;
    private Double utilizationPercent;
    private Boolean virtualizationEnabled;
    private Long l1CacheBytes;
    private Long l2CacheBytes;
    private Long l3CacheBytes;
    private Integer handleCount;
    private Integer threadCount;
    private Long uptimeMs;
    private Integer processCount;
    private List<Double> perProcessorUtilization;
}
