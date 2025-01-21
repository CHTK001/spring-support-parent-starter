package com.chua.report.server.starter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 *
 * @since 2024/9/16
 * @author CH    
 */

/**
 * 代理-插件关系表
 */
@ApiModel(description = "代理-插件关系表")
@Schema(description = "代理-插件关系表")
@Data
@TableName(value = "monitor_proxy_plugin")
public class MonitorProxyPlugin implements Serializable {
    @TableId(value = "proxy_plugin_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer proxyPluginId;

    /**
     * 组件实现
     */
    @TableField(value = "proxy_plugin_spi")
    @ApiModelProperty(value = "组件实现")
    @Schema(description = "组件实现")
    @Size(max = 11, message = "组件实现最大长度要小于 11")
    private String proxyPluginSpi;

    /**
     * 代理ID
     */
    @TableField(value = "proxy_id")
    @ApiModelProperty(value = "代理ID")
    @Schema(description = "代理ID")
    private Integer proxyId;

    /**
     * 优先级
     */
    @TableField(value = "proxy_plugin_sort")
    @ApiModelProperty(value = "优先级")
    @Schema(description = "优先级")
    private Integer proxyPluginSort;

    /**
     * 插件名称
     */
    @TableField(value = "proxy_plugin_name")
    @ApiModelProperty(value = "插件名称")
    @Schema(description = "插件名称")
    @Size(max = 255, message = "插件名称最大长度要小于 255")
    private String proxyPluginName;

    /**
     * 插件配置
     */
    @TableField(exist = false)
    private List<MonitorProxyPluginConfig> configList;

    private static final long serialVersionUID = 1L;
}