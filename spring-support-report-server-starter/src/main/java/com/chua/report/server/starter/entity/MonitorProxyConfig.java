package com.chua.report.server.starter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * @author CH
 * @since 2024/6/17
 */
@ApiModel(description = "monitor_proxy_config")
@Schema
@Data
@TableName(value = "monitor_proxy_config")
public class MonitorProxyConfig implements Serializable {
    @TableId(value = "config_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer configId;

    /**
     * 配置名称
     */
    @TableField(value = "config_name")
    @ApiModelProperty(value = "配置名称")
    @Schema(description = "配置名称")
    @Size(max = 255, message = "配置名称最大长度要小于 255")
    private String configName;

    /**
     * 配置值
     */
    @TableField(value = "config_value")
    @ApiModelProperty(value = "配置值")
    @Schema(description = "配置值")
    @Size(max = 4096, message = "配置值最大长度要小于 4096")
    private String configValue;

    /**
     * 配置描述
     */
    @TableField(value = "config_desc")
    @ApiModelProperty(value = "配置描述")
    @Schema(description = "配置描述")
    @Size(max = 255, message = "配置描述最大长度要小于 255")
    private String configDesc;

    /**
     * 代理ID
     */
    @TableField(value = "proxy_id")
    @ApiModelProperty(value = "代理ID")
    @Schema(description = "代理ID")
    @Size(max = 255, message = "代理ID最大长度要小于 255")
    private String proxyId;

    private static final long serialVersionUID = 1L;
}