package com.chua.starter.monitor.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 告警策略实体
 * <p>
 * 对应表 {@code ms_monitor_alarm_policy}：一组告警规则的逻辑容器，
   * 规则通过 policy_标识 归属；策略停用即整组规则失效。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "告警策略")
@TableName("ms_monitor_alarm_policy")
public class MsMonitorAlarmPolicy implements Serializable {

    /** 串行版本UID */
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id; // 标识

    /**
     * 策略名称
     */
    @TableField("ms_monitor_alarm_policy_name")
    @Schema(description = "策略名称", example = "服务器基础告警")
    private String msMonitorAlarmPolicyName; // ms监控报警器policy名称

    /**
     * 启用状态 1启用 0停用
     */
    @TableField("ms_monitor_alarm_policy_enabled")
    @Schema(description = "启用状态 1启用 0停用", example = "1")
    private Integer msMonitorAlarmPolicyEnabled; // ms监控报警器policy已启用

    /**
     * 备注
     */
    @TableField("ms_monitor_alarm_policy_remark")
    @Schema(description = "备注")
    private String msMonitorAlarmPolicyRemark; // ms监控报警器policy评论
}
