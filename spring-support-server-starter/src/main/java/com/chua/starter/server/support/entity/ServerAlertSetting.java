package com.chua.starter.server.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("服务器预警规则")
@TableName("server_alert_setting")
public class ServerAlertSetting extends SysBase {

    @TableId(value = "server_alert_setting_id", type = IdType.AUTO)
    @ApiModelProperty("服务器预警规则ID")
    private Integer serverAlertSettingId;

    @TableField("server_id")
    @ApiModelProperty("服务器ID，空表示全局")
    private Integer serverId;

    @TableField("server_alert_inherit_global")
    @ApiModelProperty("是否继承全局")
    private Boolean inheritGlobal;

    @TableField("server_alert_enabled")
    @ApiModelProperty("是否启用预警")
    private Boolean enabled;

    @TableField("server_alert_message_enabled")
    @ApiModelProperty("是否同步消息中心")
    private Boolean messageEnabled;

    @TableField("server_alert_cpu_warning_percent")
    @ApiModelProperty("CPU预警阈值")
    private Double cpuWarningPercent;

    @TableField("server_alert_cpu_danger_percent")
    @ApiModelProperty("CPU危险阈值")
    private Double cpuDangerPercent;

    @TableField("server_alert_memory_warning_percent")
    @ApiModelProperty("内存预警阈值")
    private Double memoryWarningPercent;

    @TableField("server_alert_memory_danger_percent")
    @ApiModelProperty("内存危险阈值")
    private Double memoryDangerPercent;

    @TableField("server_alert_disk_warning_percent")
    @ApiModelProperty("磁盘预警阈值")
    private Double diskWarningPercent;

    @TableField("server_alert_disk_danger_percent")
    @ApiModelProperty("磁盘危险阈值")
    private Double diskDangerPercent;

    @TableField("server_alert_disk_io_warning_bytes_per_second")
    @ApiModelProperty("磁盘IO预警阈值")
    private Double diskIoWarningBytesPerSecond;

    @TableField("server_alert_disk_io_danger_bytes_per_second")
    @ApiModelProperty("磁盘IO危险阈值")
    private Double diskIoDangerBytesPerSecond;

    @TableField("server_alert_io_warning_bytes_per_second")
    @ApiModelProperty("IO预警阈值")
    private Double ioWarningBytesPerSecond;

    @TableField("server_alert_io_danger_bytes_per_second")
    @ApiModelProperty("IO危险阈值")
    private Double ioDangerBytesPerSecond;

    @TableField("server_alert_latency_warning_ms")
    @ApiModelProperty("延迟预警阈值")
    private Integer latencyWarningMs;

    @TableField("server_alert_latency_danger_ms")
    @ApiModelProperty("延迟危险阈值")
    private Integer latencyDangerMs;
}
