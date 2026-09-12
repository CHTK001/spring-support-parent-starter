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
* 监控通用时序指标
*
* @since 2026/8/4
* @author CH
 */
@Data
@Schema(description = "监控通用时序")
@TableName("ms_monitor_metric")
public class MsMonitorMetric implements Serializable {

    /** 串行版本UID */
    private static final long serialVersionUID = 1L;

    /**
    * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    /** 标识 */
    private Long id;

    /**
    * 监控主机 标识
     */
    @TableField("ms_monitor_metric_monitor_id")
    @Schema(description = "监控主机 id", example = "1")
    /** 监控标识 */
    private Long monitorId;

    /**
    * 指标名
     */
    @TableField("ms_monitor_metric_metric_name")
    @Schema(description = "指标名", example = "cpu_usage")
    /** 指标名称 */
    private String metricName;

    /**
    * 指标值
     */
    @TableField("ms_monitor_metric_metric_value")
    @Schema(description = "指标值", example = "23.5")
    /** 指标值 */
    private Double metricValue;

    /**
    * 标签 JSON
     */
    @TableField("ms_monitor_metric_metric_tags")
    @Schema(description = "标签 JSON", example = "{\"core\":\"0\"}")
    /** 指标标签 */
    private String metricTags;

    /**
    * 采集时间
     */
    @TableField("ms_monitor_metric_collect_time")
    @Schema(description = "采集时间", example = "2026-08-04T10:00:00")
    /** Collect时间 */
    private LocalDateTime collectTime;
}