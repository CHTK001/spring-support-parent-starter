package com.chua.starter.monitor.support.model;

/**
 * 磁盘分区行视图。
 *
 * <p>Linux 来自 {@code df -hT}，Windows 来自 {@code Get-Volume}，两侧都归一到同一组展示字段。</p>
 *
 * @param filesystem 文件系统标识（设备名或盘符）
 * @param type       文件系统类型
 * @param size       容量展示文本
 * @param used       已用展示文本
 * @param available  可用展示文本
 * @param usePercent 使用率展示文本
 * @param mountPoint 挂载点或卷标
 * @author CH
 * @since 4.0.0.42
 */
public record DiskPartitionRowVO(
        String filesystem,
        String type,
        String size,
        String used,
        String available,
        String usePercent,
        String mountPoint) {
}
