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
@ApiModel("服务器预警事件")
@TableName("server_alert_event")
public class ServerAlertEvent extends SysBase {

    @TableId(value = "server_alert_event_id", type = IdType.AUTO)
    @ApiModelProperty("服务器预警事件ID")
    private Integer serverAlertEventId;

    @TableField("server_id")
    @ApiModelProperty("服务器ID")
    private Integer serverId;

    @TableField("server_code")
    @ApiModelProperty("服务器编码")
    private String serverCode;

    @TableField("server_alert_metric_type")
    @ApiModelProperty("指标类型")
    private String metricType;

    @TableField("server_alert_severity")
    @ApiModelProperty("告警级别")
    private String severity;

    @TableField("server_alert_metric_value")
    @ApiModelProperty("当前值")
    private Double metricValue;

    @TableField("server_alert_warning_threshold")
    @ApiModelProperty("预警阈值")
    private Double warningThreshold;

    @TableField("server_alert_danger_threshold")
    @ApiModelProperty("危险阈值")
    private Double dangerThreshold;

    @TableField("server_alert_snapshot_json")
    @ApiModelProperty("指标快照")
    private String snapshotJson;

    @TableField("server_alert_message")
    @ApiModelProperty("告警消息")
    private String alertMessage;
}
