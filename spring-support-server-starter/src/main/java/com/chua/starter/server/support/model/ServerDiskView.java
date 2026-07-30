package com.chua.starter.server.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单个物理磁盘视图（含分区信息）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerDiskView {

    private String name;
    private String type;
    private Boolean systemDisk;
    private Long totalBytes;
    private Long usedBytes;
    private Double readSpeedBytesPerSecond;
    private Double writeSpeedBytesPerSecond;
    private Double avgResponseTimeMs;
    private List<ServerDiskPartitionView> partitions;
}
