package com.chua.starter.server.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerDiskPartitionView {

    private String name;
    private String mountPoint;
    private String fileSystem;
    private String label;
    private Long totalBytes;
    private Long usedBytes;
    private Long freeBytes;
    private Double usagePercent;
    private String status;
}
