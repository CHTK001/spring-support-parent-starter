package com.chua.report.server.starter.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;

/**
 * @author CH
 * @since 2024/5/13
 */
@ApiModel(description = "monitor_proxy")
@Schema
@Data
@TableName(value = "monitor_proxy")
public class MonitorProxy implements Serializable {
    @TableId(value = "proxy_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer proxyId;

    /**
     * 名称
     */
    @TableField(value = "proxy_name")
    @ApiModelProperty(value = "名称")
    @Schema(description = "名称")
    @Size(max = 255, message = "名称最大长度要小于 255")
    private String proxyName;

    /**
     * 描述
     */
    @TableField(value = "proxy_desc")
    @ApiModelProperty(value = "描述")
    @Schema(description = "描述")
    @Size(max = 255, message = "描述最大长度要小于 255")
    private String proxyDesc;

    /**
     * 启动地址
     */
    @TableField(value = "proxy_host")
    @ApiModelProperty(value = "启动地址")
    @Schema(description = "启动地址")
    @Size(max = 255, message = "启动地址最大长度要小于 255")
    private String proxyHost;

    /**
     * 端口
     */
    @TableField(value = "proxy_port")
    @ApiModelProperty(value = "端口")
    @Schema(description = "端口")
    private Integer proxyPort;

    /**
     * 协议
     */
    @TableField(value = "proxy_protocol")
    @ApiModelProperty(value = "协议")
    @Schema(description = "协议")
    @Size(max = 255, message = "协议最大长度要小于 255")
    private String proxyProtocol;

    /**
     * 0: 未开启
     */
    @TableField(value = "proxy_status")
    @ApiModelProperty(value = "0: 未开启")
    @Schema(description = "0: 未开启")
    private Integer proxyStatus;

    /**
     * 版本
     */
    @TableField(value = "proxy_version")
    @ApiModelProperty(value = "版本")
    @Schema(description = "版本")
    @Version
    private Integer proxyVersion;

    private static final long serialVersionUID = 1L;
}