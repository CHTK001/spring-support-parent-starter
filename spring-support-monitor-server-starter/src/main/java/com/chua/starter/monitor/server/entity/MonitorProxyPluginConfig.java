package com.chua.starter.monitor.server.entity;

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
import java.util.Date;

/**
 *
 *
 * @since 2024/6/18 
 * @author CH
 */
@ApiModel(description="monitor_proxy_plugin_config")
@Schema
@Data
@TableName(value = "monitor_proxy_plugin_config")
public class MonitorProxyPluginConfig implements Serializable {
    @TableId(value = "plugin_config_id", type = IdType.AUTO)
    @ApiModelProperty(value="")
    @Schema(description="")
    @NotNull(message = "不能为null")
    private Integer pluginConfigId;
    /**
     * 代理ID
     */
    @ApiModelProperty(value="代理ID")
    @Schema(description="代理ID")
    @TableField(value = "proxy_id")
    @NotNull(message = "不能为null")
    private Integer proxyId;

    /**
     * 插件名称
     */
    @TableField(value = "plugin_name")
    @ApiModelProperty(value="插件名称")
    @Schema(description="插件名字")
    private String pluginName;
    /**
     * 插件优先级
     */
    @TableField(value = "plugin_sort")
    @ApiModelProperty(value="插件优先级")
    @Schema(description="插件优先级")
    private Integer pluginSort;

    /**
     * 配置名称
     */
    @TableField(value = "plugin_config_name")
    @ApiModelProperty(value="配置名称")
    @Schema(description="配置名称")
    @Size(max = 255,message = "配置名称最大长度要小于 255")
    private String pluginConfigName;

    /**
     * 配置
     */
    @TableField(value = "plugin_config_value")
    @ApiModelProperty(value="配置")
    @Schema(description="配置")
    @Size(max = 255,message = "配置最大长度要小于 255")
    private String pluginConfigValue;

    /**
     * 配置类型
     */
    @TableField(value = "plugin_config_type")
    @ApiModelProperty(value="配置类型")
    @Schema(description="配置类型")
    @Size(max = 255,message = "配置类型最大长度要小于 255")
    private String pluginConfigType;

    /**
     * 更新时间
     */
    @TableField(value = "update_time")
    @ApiModelProperty(value="更新时间")
    @Schema(description="更新时间")
    private Date updateTime;

    private static final long serialVersionUID = 1L;
}