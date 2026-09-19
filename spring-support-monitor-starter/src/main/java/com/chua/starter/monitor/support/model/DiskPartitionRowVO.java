package com.chua.starter.monitor.support.model;

import lombok.Data;

/**
 * 磁盘分区行视图。
 *
 * <p>Linux 来自 {@code df -hT}，Windows 来自 {@code Get-Volume}，两侧都归一到同一组展示字段。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class DiskPartitionRowVO {

    /**
     * 文件系统标识（设备名或盘符）
     */
    private String filesystem;

    /**
     * 文件系统类型
     */
    private String type;

    /**
     * 容量展示文本
     */
    private String size;

    /**
     * 已用展示文本
     */
    private String used;

    /**
     * 可用展示文本
     */
    private String available;

    /**
     * 使用率展示文本
     */
    private String usePercent;

    /**
     * 挂载点或卷标
     */
    private String mountPoint;
}
