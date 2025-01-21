package com.chua.report.server.starter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 *
 * @since 2024/9/16
 * @author CH    
 */

/**
 * 代理 - 组件- 配置
 */
@ApiModel(description = "代理 - 组件- 配置")
@Schema(description = "代理 - 组件- 配置")
@Data
@TableName(value = "monitor_proxy_plugin_config")
public class MonitorProxyPluginConfig implements Serializable {
    @TableId(value = "proxy_config_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer proxyConfigId;

    /**
     * 配置名称
     */
    @TableField(value = "proxy_config_name")
    @ApiModelProperty(value = "配置名称")
    @Schema(description = "配置名称")
    @Size(max = 255, message = "配置名称最大长度要小于 255")
    private String proxyConfigName;

    /**
     * 配置值
     */
    @TableField(value = "proxy_config_value")
    @ApiModelProperty(value = "配置值")
    @Schema(description = "配置值")
    @Size(max = 255, message = "配置值最大长度要小于 255")
    private String proxyConfigValue;

    /**
     * 插件ID
     */
    @TableField(value = "proxy_plugin_id")
    @ApiModelProperty(value = "插件ID")
    @Schema(description = "插件ID")
    private Integer proxyPluginId;

    /**
     * 代理ID
     */
    @TableField(value = "proxy_id")
    @ApiModelProperty(value = "代理ID")
    @Schema(description = "代理ID")
    private Integer proxyId;

    private static final long serialVersionUID = 1L;
}