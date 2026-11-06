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
 * <p>
 * 策略隶属于主机（{at code ms_server_host_id}）：一台主机一套策略，
 * 只在这台主机的指标上评估。为空表示全局策略，对所有主机生效
 * （存量数据与「项目守护」这类与主机无关的指标规则保持为空）。
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

    /**
     * 串行版本UID
    */
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
    private Integer msMonitorAlarmPolicyEnabled; // ms监控报警器policy启用

    /**
     * 备注
     */
    @TableField("ms_monitor_alarm_policy_remark")
    @Schema(description = "备注")
    private String msMonitorAlarmPolicyRemark; // ms监控报警器policy评论

    /**
     * 归属主机 标识（空表示全局策略）
     */
    @TableField("ms_server_host_id")
    @Schema(description = "归属主机ID,空为全局策略", example = "12")
    private Long msServerHostId; // ms服务端主机标识

    /**
     * 置为全局策略（只读入参，非表列）
     * <p>
     * 更新时 {at code ms_server_host_id} 为空表示「不修改」，改回全局需要显式传
     * {at code true}，否则 updateById 跳过 null 字段会让「改回全局」静默失效。
     * </p>
     */
    @TableField(exist = false)
    @Schema(description = "置为全局策略(仅更新时生效,true=清空归属主机)")
    private Boolean globalScope; // 置为全局策略开关
}
