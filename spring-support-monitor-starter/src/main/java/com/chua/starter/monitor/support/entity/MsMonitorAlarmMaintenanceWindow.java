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
 * 告警静默/维护窗口实体
 *
 * @since 2026/8/4
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "告警静默/维护窗口")
@TableName("ms_monitor_alarm_maintenance_window")
public class MsMonitorAlarmMaintenanceWindow implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    /**
     * 策略 ID
     */
    @TableField("ms_monitor_alarm_maintenance_window_policy_id")
    @Schema(description = "策略 ID", example = "1")
    private Long policyId;

    /**
     * 窗口名称
     */
    @TableField("ms_monitor_alarm_maintenance_window_name")
    @Schema(description = "窗口名称", example = "例行维护窗口")
    private String windowName;

    /**
     * 告警级别（为空则全局静默，否则仅对应级别规则）
     */
    @TableField("ms_monitor_alarm_maintenance_window_level")
    @Schema(description = "告警级别", example = "P2")
    private String windowLevel;

    /**
     * 起始时间（每天）
     */
    @TableField("ms_monitor_alarm_maintenance_window_start_time")
    @Schema(description = "起始时间", example = "22:00")
    private String startTime;

    /**
     * 结束时间（每天）
     */
    @TableField("ms_monitor_alarm_maintenance_window_end_time")
    @Schema(description = "结束时间", example = "06:00")
    private String endTime;

    /**
     * 一周中的哪几天（0-6, 逗号分隔，为空则每天）
     */
    @TableField("ms_monitor_alarm_maintenance_window_weekdays")
    @Schema(description = "一周中的哪天", example = "0,1,2,3,4,5,6")
    private String weekdays;

    /**
     * 是否启用
     */
    @TableField("ms_monitor_alarm_maintenance_window_enabled")
    @Schema(description = "是否启用", example = "1")
    private Integer enabled;

    /**
     * 备注
     */
    @TableField("ms_monitor_alarm_maintenance_window_remark")
    @Schema(description = "备注", example = "系统升级维护期")
    private String remark;

    /**
     * 创建时间
     */
    @TableField("ms_monitor_alarm_maintenance_window_create_time")
    @Schema(description = "创建时间", example = "2026-08-04T10:00:00")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("ms_monitor_alarm_maintenance_window_update_time")
    @Schema(description = "更新时间", example = "2026-08-04T10:00:00")
    private LocalDateTime updateTime;
}