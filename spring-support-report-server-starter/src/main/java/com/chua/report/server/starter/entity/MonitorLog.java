package com.chua.report.server.starter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.common.support.protocol.protocol.CommandType;
import com.chua.starter.mybatis.pojo.SysTenantBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@ApiModel(description = "monitor_log")
@Schema
@Data
@TableName(value = "monitor_log")
public class MonitorLog extends SysTenantBase implements Serializable {
    @TableId(value = "log_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer logId;

    /**
     * 模块
     */
    @TableField(value = "log_module_type")
    @ApiModelProperty(value = "模块")
    @Schema(description = "模块")
    @Size(max = 255, message = "模块最大长度要小于 255")
    private String logModuleType;

    /**
     * 命令
     */
    @TableField(value = "log_command_type")
    @ApiModelProperty(value = "命令")
    @Schema(description = "命令")
    @Size(max = 255, message = "命令最大长度要小于 255")
    private CommandType logCommandType;

    /**
     * 内容
     */
    @TableField(value = "log_content")
    @ApiModelProperty(value = "内容")
    @Schema(description = "内容")
    private String logContent;

    /**
     * 应用
     */
    @TableField(value = "log_appname")
    @ApiModelProperty(value = "应用")
    @Schema(description = "应用")
    @Size(max = 255, message = "应用最大长度要小于 255")
    private String logAppname;

    /**
     * 环境
     */
    @TableField(value = "log_profile")
    @ApiModelProperty(value = "环境")
    @Schema(description = "环境")
    @Size(max = 255, message = "环境最大长度要小于 255")
    private String logProfile;

    /**
     * 地址
     */
    @TableField(value = "log_host")
    @ApiModelProperty(value = "地址")
    @Schema(description = "地址")
    @Size(max = 255, message = "地址最大长度要小于 255")
    private String logHost;

    /**
     * 端口
     */
    @TableField(value = "log_port")
    @ApiModelProperty(value = "端口")
    @Schema(description = "端口")
    private Integer logPort;

    /**
     * 响应码
     */
    @TableField(value = "log_code")
    @ApiModelProperty(value = "响应码")
    @Schema(description = "响应码")
    @Size(max = 255, message = "响应码最大长度要小于 255")
    private String logCode;

    /**
     * 错误消息
     */
    @TableField(value = "log_msg")
    @ApiModelProperty(value = "错误消息")
    @Schema(description = "错误消息")
    @Size(max = 255, message = "错误消息最大长度要小于 255")
    private String logMsg;

    private static final long serialVersionUID = 1L;
}