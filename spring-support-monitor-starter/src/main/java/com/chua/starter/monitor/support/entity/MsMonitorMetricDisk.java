package com.chua.starter.monitor.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 监控磁盘明细
 *
 * @since 2026/8/4
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "监控磁盘明细")
@TableName("ms_monitor_metric_disk")
public class MsMonitorMetricDisk implements Serializable {

    /**
     * 串行版本UID
    */
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    /**
     * 标识
    */
    private Long id;

    /**
     * 监控主机 标识
     */
    @TableField("ms_monitor_metric_disk_monitor_id")
    @Schema(description = "监控主机 id", example = "1")
    /**
     * 监控标识
    */
    private Long monitorId;

    /**
     * 挂载点
     */
    @TableField("ms_monitor_metric_disk_mount_point")
    @Schema(description = "挂载点", example = "/")
    /**
     * Mountpoint
    */
    private String mountPoint;

    /**
     * 文件系统
     */
    @TableField("ms_monitor_metric_disk_file_system")
    @Schema(description = "文件系统", example = "ext4")
    /**
     * 文件系统
    */
    private String fileSystem;

    /**
     * 总磁盘(字节)
     */
    @TableField("ms_monitor_metric_disk_total_bytes")
    @Schema(description = "总磁盘(字节)", example = "536870912000")
    /**
     * 总数bytes
    */
    private Long totalBytes;

    /**
     * 已用(字节)
     */
    @TableField("ms_monitor_metric_disk_used_bytes")
    @Schema(description = "已用(字节)", example = "268435456000")
    /**
     * Usedbytes
    */
    private Long usedBytes;

    /**
     * 使用率(0-100)
     */
    @TableField("ms_monitor_metric_disk_usage_percent")
    @Schema(description = "使用率", example = "50.0")
    /**
     * Usage百分比
    */
    private Double usagePercent;

    /**
     * 采集时间
     */
    @TableField("ms_monitor_metric_disk_collect_time")
    @Schema(description = "采集时间", example = "2026-08-04T10:00:00")
    /**
     * Collect时间
    */
    private LocalDateTime collectTime;
}
