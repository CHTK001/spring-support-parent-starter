package com.chua.starter.monitor.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 *
 *
 * @since 2024/6/19 
 * @author CH
 */

/**
 * 终端管理
 */
@ApiModel(description = "终端管理")
@Schema(description = "终端管理")
@Data
@TableName(value = "monitor_terminal")
public class MonitorTerminal implements Serializable {
    @TableId(value = "terminal_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer terminalId;

    /**
     * 名称
     */
    @TableField(value = "terminal_name")
    @ApiModelProperty(value = "名称")
    @Schema(description = "名称")
    @Size(max = 255, message = "名称最大长度要小于 255")
    private String terminalName;

    /**
     * 地址
     */
    @TableField(value = "terminal_host")
    @ApiModelProperty(value = "地址")
    @Schema(description = "地址")
    @Size(max = 255, message = "地址最大长度要小于 255")
    private String terminalHost;

    /**
     * 端口
     */
    @TableField(value = "terminal_port")
    @ApiModelProperty(value = "端口")
    @Schema(description = "端口")
    @Size(max = 255, message = "端口最大长度要小于 255")
    private String terminalPort;

    /**
     * 账号
     */
    @TableField(value = "terminal_user")
    @ApiModelProperty(value = "账号")
    @Schema(description = "账号")
    @Size(max = 255, message = "账号最大长度要小于 255")
    private String terminalUser;

    /**
     * 密码
     */
    @TableField(value = "terminal_password")
    @ApiModelProperty(value = "密码")
    @Schema(description = "密码")
    @JsonIgnore
    @Size(max = 255, message = "密码最大长度要小于 255")
    private String terminalPassword;

    /**
     * 描述
     */
    @TableField(value = "terminal_desc")
    @ApiModelProperty(value = "描述")
    @Schema(description = "描述")
    @Size(max = 255, message = "描述最大长度要小于 255")
    private String terminalDesc;

    /**
     * 状态;0:未启动
     */
    @TableField(value = "terminal_status")
    @ApiModelProperty(value = "状态;0:未启动1:已启动; 2:启动中")
    @Schema(description = "状态;0:未启动;1:已启动; 2:启动中")
    private Integer terminalStatus;

    private static final long serialVersionUID = 1L;

}