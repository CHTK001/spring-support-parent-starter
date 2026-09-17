package com.chua.starter.monitor.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
* 告警规则实体
* <p>
* 对应表 {@code ms_monitor_alarm_rule}：对某一指标名的阈值判定定义。
* 监控_标识 为空表示对该指标的全量目标生效。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "告警规则")
@TableName("ms_monitor_alarm_rule")
public class MsMonitorAlarmRule implements Serializable {

    /** 串行版本UID */
    private static final long serialVersionUID = 1L;

    /**
    * 主键
    */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id; // 标识

    /**
    * 所属策略 标识
    */
    @TableField("ms_monitor_alarm_rule_policy_id")
    @Schema(description = "所属策略ID", example = "1")
    private Long msMonitorAlarmRulePolicyId; // ms监控报警器rulepolicyid

    /**
    * 限定目标 标识（空表示全部目标）
    */
    @TableField("ms_monitor_alarm_rule_monitor_id")
    @Schema(description = "限定监控目标ID,空为全部", example = "1")
    private Long msMonitorAlarmRuleMonitorId; // ms监控报警器rule监控标识

    /**
    * 指标名（如 cpu_usage / 内存_usage）
    */
    @TableField("ms_monitor_alarm_rule_metric_name")
    @Schema(description = "指标名", example = "cpu_usage")
    private String msMonitorAlarmRuleMetricName; // ms监控报警器rule指标名称

    /**
    * 触发条件
    */
    @TableField("ms_monitor_alarm_rule_condition")
    @Schema(description = "触发条件: GT/GE/EQ/LE/LT", example = "GT")
    private String msMonitorAlarmRuleCondition; // ms监控报警器rule条件

    /**
    * 阈值
    */
    @TableField("ms_monitor_alarm_rule_threshold")
    @Schema(description = "阈值", example = "80")
    private BigDecimal msMonitorAlarmRuleThreshold; // ms监控报警器rule阈值

    /**
    * 连续触发次数（连续 N 次采样超限才算真告警）
    */
    @TableField("ms_monitor_alarm_rule_consecutive_count")
    @Schema(description = "连续触发次数", example = "3")
    private Integer msMonitorAlarmRuleConsecutiveCount; // ms监控报警器ruleconsecutive数量

    /**
    * 静默期（分钟），触发后该时长内不重复告警
    */
    @TableField("ms_monitor_alarm_rule_silence_minutes")
    @Schema(description = "静默期(分钟)", example = "10")
    private Integer msMonitorAlarmRuleSilenceMinutes; // ms监控报警器rulesilenceminutes

    /**
    * 告警级别
    */
    @TableField("ms_monitor_alarm_rule_level")
    @Schema(description = "级别: P0严重~P4信息", example = "P2")
    private String msMonitorAlarmRuleLevel; // ms监控报警器rule水平仪

    /**
    * 启用状态 1启用 0停用
    */
    @TableField("ms_monitor_alarm_rule_enabled")
    @Schema(description = "启用状态 1启用 0停用", example = "1")
    private Integer msMonitorAlarmRuleEnabled; // ms监控报警器rule启用
}
