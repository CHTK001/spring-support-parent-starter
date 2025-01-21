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
 * 代理 - 组件- 限流
 */
@ApiModel(description="代理 - 组件- 限流")
@Schema(description="代理 - 组件- 限流")
@Data
@TableName(value = "monitor_proxy_plugin_limit")
public class MonitorProxyPluginLimit implements Serializable {
    @TableId(value = "proxy_config_limit_id", type = IdType.AUTO)
    @ApiModelProperty(value="")
    @Schema(description="")
    @NotNull(message = "不能为null")
    private Integer proxyConfigLimitId;

    /**
     * 限流路径
     */
    @TableField(value = "proxy_config_limit_path_or_ip")
    @ApiModelProperty(value="限流路径")
    @Schema(description="限流路径")
    @Size(max = 255,message = "限流路径最大长度要小于 255")
    private String proxyConfigLimitPathOrIp;

    /**
     * 每秒限流次数
     */
    @TableField(value = "proxy_config_limit_per_seconds")
    @ApiModelProperty(value="每秒限流次数")
    @Schema(description="每秒限流次数")
    private Integer proxyConfigLimitPerSeconds;

    /**
     * 类型;  PATH; IP
     */
    @TableField(value = "proxy_config_limit_type")
    @ApiModelProperty(value="类型;  PATH; IP")
    @Schema(description="类型;  PATH; IP")
    @Size(max = 255,message = "类型;  PATH; IP最大长度要小于 255")
    private String proxyConfigLimitType;

    /**
     * 是否禁用；0: 启用
     */
    @TableField(value = "proxy_config_limit_disabled")
    @ApiModelProperty(value="是否禁用；0: 启用")
    @Schema(description="是否禁用；0: 启用")
    private Integer proxyConfigLimitDisabled;

    /**
     * 插件ID
     */
    @TableField(value = "proxy_plugin_id")
    @ApiModelProperty(value="插件ID")
    @Schema(description="插件ID")
    private Integer proxyPluginId;

    /**
     * 代理ID
     */
    @TableField(value = "proxy_id")
    @ApiModelProperty(value="代理ID")
    @Schema(description="代理ID")
    private Integer proxyId;

    private static final long serialVersionUID = 1L;
}