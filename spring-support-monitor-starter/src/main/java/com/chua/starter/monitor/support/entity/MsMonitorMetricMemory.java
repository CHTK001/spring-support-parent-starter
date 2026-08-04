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
 * 监控内存明细
 *
 * @author CH
 * @since 2026/8/4
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "监控内存明细")
@TableName("ms_monitor_metric_memory")
public class MsMonitorMetricMemory implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    /**
     * 监控主机 id
     */
    @TableField("monitor_id")
    @Schema(description = "监控主机 id", example = "1")
    private Long monitorId;

    /**
     * 总内存(字节)
     */
    @TableField("total_bytes")
    @Schema(description = "总内存(字节)", example = "17179869184")
    private Long totalBytes;

    /**
     * 已用(字节)
     */
    @TableField("used_bytes")
    @Schema(description = "已用(字节)", example = "8589934592")
    private Long usedBytes;

    /**
     * 空闲(字节)
     */
    @TableField("free_bytes")
    @Schema(description = "空闲(字节)", example = "8589934592")
    private Long freeBytes;

    /**
     * 使用率(0-100)
     */
    @TableField("usage_percent")
    @Schema(description = "使用率", example = "50.0")
    private Double usagePercent;

    /**
     * 采集时间
     */
    @TableField("collect_time")
    @Schema(description = "采集时间", example = "2026-08-04T10:00:00")
    private LocalDateTime collectTime;
}