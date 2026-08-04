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
 * 监控 Redis 时序指标
 *
 * @author CH
 * @since 2026/8/4
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "监控 Redis 时序")
@TableName("ms_monitor_redis_metric")
public class MsMonitorRedisMetric implements Serializable {

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
     * 指标名
     */
    @TableField("metric_name")
    @Schema(description = "指标名", example = "redis_connected_clients")
    private String metricName;

    /**
     * 指标值
     */
    @TableField("metric_value")
    @Schema(description = "指标值", example = "23.0")
    private Double metricValue;

    /**
     * 标签 JSON
     */
    @TableField("metric_tags")
    @Schema(description = "标签 JSON", example = "{\"db\":0}")
    private String metricTags;

    /**
     * 采集时间
     */
    @TableField("collect_time")
    @Schema(description = "采集时间", example = "2026-08-04T10:00:00")
    private LocalDateTime collectTime;
}