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
 * 监控 Redis 小时聚合
 *
 * @since 2026/8/4
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "监控 Redis 小时聚合")
@TableName("ms_monitor_redis_metric_statistics")
public class MsMonitorRedisMetricStatistics implements Serializable {

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
    @TableField("ms_monitor_redis_metric_statistics_monitor_id")
    @Schema(description = "监控主机 id", example = "1")
    /**
     * 监控标识
    */
    private Long monitorId;

    /**
     * 指标名
     */
    @TableField("ms_monitor_redis_metric_statistics_metric_name")
    @Schema(description = "指标名", example = "redis_connected_clients")
    /**
     * 指标名称
    */
    private String metricName;

    /**
     * 小时桶
     */
    @TableField("ms_monitor_redis_metric_statistics_hour_bucket")
    @Schema(description = "小时桶(整点)", example = "2026-08-04T10:00:00")
    /**
     * Hour存储桶
    */
    private LocalDateTime hourBucket;

    /**
     * 平均值
     */
    @TableField("ms_monitor_redis_metric_statistics_avg_value")
    @Schema(description = "平均值", example = "23.0")
    /**
     * AVG值
    */
    private Double avgValue;

    /**
     * 最小值
     */
    @TableField("ms_monitor_redis_metric_statistics_min_value")
    @Schema(description = "最小值", example = "5.0")
    /**
     * 最小值值
    */
    private Double minValue;

    /**
     * 最大值
     */
    @TableField("ms_monitor_redis_metric_statistics_max_value")
    @Schema(description = "最大值", example = "78.0")
    /**
     * 最大值值
    */
    private Double maxValue;

    /**
     * 样本数
     */
    @TableField("ms_monitor_redis_metric_statistics_count_value")
    @Schema(description = "样本数", example = "360")
    /**
     * 数量值
    */
    private Long countValue;
}
