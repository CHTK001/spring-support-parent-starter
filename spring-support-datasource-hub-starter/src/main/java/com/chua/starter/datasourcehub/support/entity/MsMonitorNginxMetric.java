package com.chua.starter.datasourcehub.support.entity;

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
 * Nginx 时序指标
 *
 * <p>数据来源：SSH 读目标机 {@code stub_status}。一条记录 = 一个数据源在一个时刻的一个指标值。
 * 累计类指标（accepts / handled / requests）存的是「worker 启动以来」的原始计数，
 * 画趋势时必须在查询侧换算成速率（相邻两点差值 / 间隔），直接画累计值只会得到单调上升的直线。</p>
 *
 * <p>保留窗口由配置 {@code monitor.data.retention.days} 控制（默认 1，即滚动 24 小时），
 * 清理逻辑复用 {@code MetricServiceImpl#cleanupOldData}，不另起一套。</p>
 *
 * @since 4.0.0
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "Nginx 时序指标")
@TableName("ms_monitor_nginx_metric")
public class MsMonitorNginxMetric implements Serializable {

    /**
     * 串行版本UID
     */
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    /**
     * 数据源 id
     */
    @TableField("ms_monitor_nginx_metric_datasource_id")
    @Schema(description = "数据源 id（ms_datasource.id）", example = "1")
    private Long datasourceId;

    /**
     * 指标名
     */
    @TableField("ms_monitor_nginx_metric_name")
    @Schema(description = "指标名", example = "active")
    private String metricName;

    /**
     * 指标值
     *
     * <p><strong>字段名刻意不叫 {@code value}</strong>：MyBatis-Plus 的
     * {@code selectList} 会把列名按属性名起别名，于是生成
     * {@code ms_monitor_nginx_metric_value AS value}；而 {@code value} 是
     * H2 的保留字，H2 直接报语法错（101.200.205.64 走查时复现：
     * 「读趋势」500，趋势图永远空白，而采集其实一直在写数据）。
     * 属性名改成 {@code metricValue} 后别名变成 {@code metricValue}，两个库都安全，
     * 表结构与接口 JSON 字段都不变（对外仍是 {@code value}，见下方 {@code @JsonProperty} 说明）。</p>
     */
    @TableField("ms_monitor_nginx_metric_value")
    @Schema(description = "指标值（累计类为原始计数）", example = "12.0")
    private Double metricValue;

    /**
     * 采集时刻
     */
    @TableField("ms_monitor_nginx_metric_collect_time")
    @Schema(description = "采集时刻", example = "2026-09-27 10:00:00")
    private LocalDateTime collectTime;
}
