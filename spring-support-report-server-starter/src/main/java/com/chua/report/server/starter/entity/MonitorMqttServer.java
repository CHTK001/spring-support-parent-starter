package com.chua.report.server.starter.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;

@ApiModel(description = "monitor_mqtt_server")
@Schema
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "monitor_mqtt_server")
public class MonitorMqttServer extends SysBase implements Serializable {
    @TableId(value = "monitor_mqtt_server_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer monitorMqttServerId;

    /**
     * 名称
     */
    @TableField(value = "monitor_mqtt_server_name")
    @ApiModelProperty(value = "名称")
    @Schema(description = "名称")
    @Size(max = 255, message = "名称最大长度要小于 255")
    private String monitorMqttServerName;

    /**
     * 地址
     */
    @TableField(value = "monitor_mqtt_server_host")
    @ApiModelProperty(value = "地址")
    @Schema(description = "地址")
    @Size(max = 255, message = "地址最大长度要小于 255")
    private String monitorMqttServerHost;

    /**
     * 端口
     */
    @TableField(value = "monitor_mqtt_server_port")
    @ApiModelProperty(value = "端口")
    @Schema(description = "端口")
    private Integer monitorMqttServerPort;

    /**
     * 账号
     */
    @TableField(value = "monitor_mqtt_server_username")
    @ApiModelProperty(value = "账号")
    @Schema(description = "账号")
    @Size(max = 255, message = "账号最大长度要小于 255")
    private String monitorMqttServerUsername;

    /**
     * 密码
     */
    @TableField(value = "monitor_mqtt_server_password")
    @ApiModelProperty(value = "密码")
    @Schema(description = "密码")
    @Size(max = 255, message = "密码最大长度要小于 255")
    private String monitorMqttServerPassword;

    /**
     * 版本号
     */
    @TableField(value = "monitor_mqtt_server_version")
    @ApiModelProperty(value = "版本号")
    @Schema(description = "版本号")
    @Version
    private Integer monitorMqttServerVersion;

    /**
     * 是否启用;0:禁用
     */
    @TableField(value = "monitor_mqtt_server_status")
    @ApiModelProperty(value = "是否启动;0:未启动")
    @Schema(description = "是否启动;0:未启动")
    private Integer monitorMqttServerStatus;
}