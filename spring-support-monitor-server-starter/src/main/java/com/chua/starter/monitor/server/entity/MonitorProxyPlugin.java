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
@ApiModel(description="monitor_proxy_plugin")
@Schema
@Data
@TableName(value = "monitor_proxy_plugin")
public class MonitorProxyPlugin implements Serializable {
    @TableId(value = "plugin_id", type = IdType.AUTO)
    @ApiModelProperty(value="")
    @Schema(description="")
    @NotNull(message = "不能为null")
    private Integer pluginId;

    /**
     * 代理ID
     */
    @TableField(value = "proxy_id")
    @ApiModelProperty(value="代理ID")
    @Schema(description="代理ID")
    private Integer proxyId;
    /**
     * 插件优先级
     */
    @TableField(value = "plugin_sort")
    @ApiModelProperty(value="插件优先级")
    @Schema(description="插件优先级")
    private Integer pluginSort;
    /**
     * 插件名称
     */
    @TableField(value = "plugin_name")
    @ApiModelProperty(value="插件名称")
    @Schema(description="插件名称")
    @Size(max = 255,message = "插件名称最大长度要小于 255")
    private String pluginName;
    /**
     * 插件描述
     */
    @TableField(value = "plugin_desc")
    @ApiModelProperty(value="插件描述")
    @Schema(description="插件描述")
    @Size(max = 255,message = "插件描述最大长度要小于 255")
    private String pluginDesc;

    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    @ApiModelProperty(value="创建时间")
    @Schema(description="创建时间")
    private Date createTime;

    private static final long serialVersionUID = 1L;
}