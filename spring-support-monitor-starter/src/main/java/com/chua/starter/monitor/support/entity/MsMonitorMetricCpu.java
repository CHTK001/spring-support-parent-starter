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
 * @author CH
 * @since 2026/8/4
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "监控 CPU 明细")
@TableName("ms_monitor_metric_cpu")
public class MsMonitorMetricCpu implements Serializable {

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
     * CPU 型号
     */
    @TableField("cpu_model")
    @Schema(description = "CPU 型号", example = "Intel(R) Xeon(R) CPU E5-2680 v4 @ 2.40GHz")
    private String cpuModel;

    /**
     * 核数
     */
    @TableField("cpu_cores")
    @Schema(description = "核数", example = "8")
    private Integer cpuCores;

    /**
     * 使用率(0-100)
     */
    @TableField("cpu_usage")
    @Schema(description = "使用率", example = "23.5")
    private Double cpuUsage;

    /**
     * 采集时间
     */
    @TableField("collect_time")
    @Schema(description = "采集时间", example = "2026-08-04T10:00:00")
    private LocalDateTime collectTime;
}