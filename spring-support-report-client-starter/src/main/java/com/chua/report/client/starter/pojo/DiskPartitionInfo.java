package com.chua.report.client.starter.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 磁盘分区信息
 *
 * @author CH
 * @since 2024/12/30
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiskPartitionInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 分区名称
     */
    private String name;

    /**
     * 挂载点
     */
    private String mount;

    /**
     * 文件系统类型
     */
    private String type;

    /**
     * 总空间 (字节)
     */
    private Long totalSpace;

    /**
     * 可用空间 (字节)
     */
    private Long freeSpace;

    /**
     * 已用空间 (字节)
     */
    private Long usedSpace;

    /**
     * 使用率 (百分比)
     */
    private Double usagePercent;

    /**
     * 是否为主分区
     */
    private Boolean isPrimary;

    /**
     * 设备名称
     */
    private String deviceName;
}
