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
 * 监控小时聚合
 *
 * @author CH
 * @since 2026/8/4
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "监控小时聚合")
@TableName("ms_monitor_metric_statistics")
public class MsMonitorMetricStatistics implements Serializable {

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
    @Schema(description = "指标名", example = "cpu_usage")
    private String metricName;

    /**
     * 小时桶
     */
    @TableField("hour_bucket")
    @Schema(description = "小时桶(整点)", example = "2026-08-04T10:00:00")
    private LocalDateTime hourBucket;

    /**
     * 平均值
     */
    @TableField("avg_value")
    @Schema(description = "平均值", example = "23.5")
    private Double avgValue;

    /**
     * 最小值
     */
    @TableField("min_value")
    @Schema(description = "最小值", example = "5.0")
    private Double minValue;

    /**
     * 最大值
     */
    @TableField("max_value")
    @Schema(description = "最大值", example = "78.2")
    private Double maxValue;

    /**
     * 样本数
     */
    @TableField("count_value")
    @Schema(description = "样本数", example = "360")
    private Long countValue;
}