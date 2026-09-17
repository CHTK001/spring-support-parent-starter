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
import java.time.LocalDateTime;

/**
* 告警事件实体
* <p>
* 对应表 {@code ms_monitor_alarm_event}：告警引擎真实触发后的落库记录，
* 支持处理/误报标记与按条件清理。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "告警事件")
@TableName("ms_monitor_alarm_event")
public class MsMonitorAlarmEvent implements Serializable {

    /** 串行版本UID */
    private static final long serialVersionUID = 1L;

    /**
    * 主键
    */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id; // 标识

    /**
    * 监控目标 标识
    */
    @TableField("ms_monitor_alarm_event_monitor_id")
    @Schema(description = "监控目标ID", example = "1")
    private Long msMonitorAlarmEventMonitorId; // ms监控报警器事件监控标识

    /**
    * 目标名称（冗余）
    */
    @TableField("ms_monitor_alarm_event_monitor_name")
    @Schema(description = "目标名称", example = "redis-prod-01")
    private String msMonitorAlarmEventMonitorName; // ms监控报警器事件监控名称

    /**
    * 命中规则 标识
    */
    @TableField("ms_monitor_alarm_event_rule_id")
    @Schema(description = "规则ID", example = "1")
    private Long msMonitorAlarmEventRuleId; // ms监控报警器事件ruleid

    /**
    * 指标名
    */
    @TableField("ms_monitor_alarm_event_metric_name")
    @Schema(description = "指标名", example = "cpu_usage")
    private String msMonitorAlarmEventMetricName; // ms监控报警器事件指标名称

    /**
    * 实际值
    */
    @TableField("ms_monitor_alarm_event_alarm_value")
    @Schema(description = "实际值", example = "92.5")
    private BigDecimal msMonitorAlarmEventAlarmValue; // ms监控报警器事件报警器价值观

    /**
    * 阈值
    */
    @TableField("ms_monitor_alarm_event_threshold")
    @Schema(description = "阈值", example = "80")
    private BigDecimal msMonitorAlarmEventThreshold; // ms监控报警器事件阈值

    /**
    * 级别
    */
    @TableField("ms_monitor_alarm_event_level")
    @Schema(description = "级别: P0~P4", example = "P2")
    private String msMonitorAlarmEventLevel; // ms监控报警器事件水平仪

    /**
    * 事件状态
    */
    @TableField("ms_monitor_alarm_event_status")
    @Schema(description = "状态: NEW/PROCESSED/FALSE_ALARM", example = "NEW")
    private String msMonitorAlarmEventStatus; // ms监控报警器事件状态

    /**
    * 处理备注
    */
    @TableField("ms_monitor_alarm_event_handle_remark")
    @Schema(description = "处理备注")
    private String msMonitorAlarmEventHandleRemark; // ms监控报警器事件把手评论

    /**
    * 触发时间
    */
    @TableField("ms_monitor_alarm_event_trigger_time")
    @Schema(description = "触发时间")
    private LocalDateTime msMonitorAlarmEventTriggerTime; // ms监控报警器事件trigger时间

    /**
    * 触发时规则配置快照（JSON：条件/阈值/连续次数/静默期/级别，规则后续修改可追溯）
    */
    @TableField("ms_monitor_alarm_event_rule_snapshot")
    @Schema(description = "触发时规则配置快照(JSON)")
    private String msMonitorAlarmEventRuleSnapshot; // ms监控报警器事件rulesnapshot

    /**
    * 处理时间
    */
    @TableField("ms_monitor_alarm_event_handle_time")
    @Schema(description = "处理时间")
    private LocalDateTime msMonitorAlarmEventHandleTime; // ms监控报警器事件把手时间
}
