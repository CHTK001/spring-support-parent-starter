package com.chua.starter.monitor.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 监控小时聚合
 *
 * @since 2026/8/4
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "监控小时聚合")
@TableName("ms_monitor_metric_statistics")
public class MsMonitorMetricStatistics implements Serializable {

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
    @TableField("ms_monitor_metric_statistics_monitor_id")
    @Schema(description = "监控主机 id", example = "1")
    /**
     * 监控标识
    */
    private Long monitorId;

    /**
     * 指标名
     */
    @TableField("ms_monitor_metric_statistics_metric_name")
    @Schema(description = "指标名", example = "cpu_usage")
    /**
     * 指标名称
    */
    private String metricName;

    /**
     * 小时桶
     */
    @TableField("ms_monitor_metric_statistics_hour_bucket")
    @Schema(description = "小时桶(整点)", example = "2026-08-04T10:00:00")
    /**
     * Hour存储桶
    */
    private LocalDateTime hourBucket;

    /**
     * 平均值
     */
    @TableField("ms_monitor_metric_statistics_avg_value")
    @Schema(description = "平均值", example = "23.5")
    /**
     * AVG值
    */
    private Double avgValue;

    /**
     * 最小值
     */
    @TableField("ms_monitor_metric_statistics_min_value")
    @Schema(description = "最小值", example = "5.0")
    /**
     * 最小值值
    */
    private Double minValue;

    /**
     * 最大值
     *
     * <p><strong>属性名刻意不叫 maxValue</strong>：MyBatis 会用属性名当列别名
     * （生成 {@code ... AS <属性名>}），而 {@code MAXVALUE} 是 MySQL 保留字
     * （分区语法 {@code VALUES LESS THAN MAXVALUE}），不加反引号的别名会直接报
     * {@code ERROR 1064} —— 后果是整条小时聚合 SQL 失败，统计表永远写不进去。
     * 属性改名即可让别名变成 {@code AS maxVal}；接口契约用 {@link JsonProperty}
     * 把 JSON 字段名锁回 {@code maxValue}，对外不变。</p>
     */
    @TableField("ms_monitor_metric_statistics_max_value")
    @Schema(description = "最大值", example = "78.2")
    @JsonProperty("maxValue")
    private Double maxVal;

    /**
     * 样本数
     */
    @TableField("ms_monitor_metric_statistics_count_value")
    @Schema(description = "样本数", example = "360")
    /**
     * 数量值
    */
    private Long countValue;
}
