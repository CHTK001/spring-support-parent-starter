package com.chua.starter.monitor.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 监控解析规则实体
 * <p>
 * 任务的 N 条解析规则, 每条规则描述如何从采集输出中抽取一个指标。
 * </p>
 *
 * @author CH
 * @since 2026/8/4
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "监控解析规则")
@TableName("ms_monitor_metric_config")
public class MsMonitorMetricConfig extends SysBase {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    /**
     * 关联任务 id
     */
    @TableField("ms_monitor_metric_config_task_id")
    @Schema(description = "关联任务 id", example = "1")
    private Long taskId;

    /**
     * 指标名
     */
    @TableField("ms_monitor_metric_config_metric_name")
    @Schema(description = "指标名", example = "cpu_usage")
    private String metricName;

    /**
     * 目标表
     */
    @TableField("ms_monitor_metric_config_target_table")
    @Schema(description = "目标表", example = "ms_monitor_metric_cpu")
    private String targetTable;

    /**
     * 解析规则 JSON
     */
    @TableField("ms_monitor_metric_config_parse_rule")
    @Schema(description = "解析规则 JSON",
            example = "{\"type\":\"regex\",\"pattern\":\"Cpu\\(s\\):\\s+(\\d+\\.\\d+)\",\"group\":1}")
    private String parseRule;

    /**
     * 指标单位
     */
    @TableField("ms_monitor_metric_config_metric_unit")
    @Schema(description = "指标单位", example = "%")
    private String metricUnit;

    /**
     * 备注
     */
    @TableField("ms_monitor_metric_config_remark")
    @Schema(description = "备注", example = "top 命令输出的整体 CPU 使用率")
    private String remark;
}
