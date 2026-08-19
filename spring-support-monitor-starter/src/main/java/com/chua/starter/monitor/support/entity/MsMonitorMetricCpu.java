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
 * 监控 CPU 明细
 *
 * @since 2026/8/4
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "监控 CPU 明细")
@TableName("ms_monitor_metric_cpu")
public class MsMonitorMetricCpu implements Serializable {

    /** Serial版本UID */
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    /** ID */
    private Long id;

    /**
     * 监控主机 id
     */
    @TableField("ms_monitor_metric_cpu_monitor_id")
    @Schema(description = "监控主机 id", example = "1")
    /** MonitorID */
    private Long monitorId;

    /**
     * CPU 型号
     */
    @TableField("ms_monitor_metric_cpu_cpu_model")
    @Schema(description = "CPU 型号", example = "Intel(R) Xeon(R) CPU E5-2680 v4 @ 2.40GHz")
    /** CPU模型 */
    private String cpuModel;

    /**
     * 核数
     */
    @TableField("ms_monitor_metric_cpu_cpu_cores")
    @Schema(description = "核数", example = "8")
    /** CPUcores */
    private Integer cpuCores;

    /**
     * 使用率(0-100)
     */
    @TableField("ms_monitor_metric_cpu_cpu_usage")
    @Schema(description = "使用率", example = "23.5")
    /** CPUusage */
    private Double cpuUsage;

    /**
     * 1 分钟平均负载
     */
    @TableField("ms_monitor_metric_cpu_load1")
    @Schema(description = "1 分钟平均负载", example = "0.85")
    /** Load1 */
    private Double load1;

    /**
     * 5 分钟平均负载
     */
    @TableField("ms_monitor_metric_cpu_load5")
    @Schema(description = "5 分钟平均负载", example = "0.72")
    /** Load5 */
    private Double load5;

    /**
     * 15 分钟平均负载
     */
    @TableField("ms_monitor_metric_cpu_load15")
    @Schema(description = "15 分钟平均负载", example = "0.68")
    /** Load15 */
    private Double load15;

    /**
     * 采集时间
     */
    @TableField("ms_monitor_metric_cpu_collect_time")
    @Schema(description = "采集时间", example = "2026-08-04T10:00:00")
    /** Collect时间 */
    private LocalDateTime collectTime;
}