package com.chua.report.server.starter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import javax.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 *
 * @since 2024/9/16
 * @author CH    
 */
/**
 * 黑白名单
 */
@ApiModel(description="黑白名单")
@Schema(description="黑白名单")
@Data
@TableName(value = "monitor_proxy_plugin_list")
public class MonitorProxyPluginList implements Serializable {
    @TableId(value = "proxy_config_list_id", type = IdType.AUTO)
    @ApiModelProperty(value="")
    @Schema(description="")
    @NotNull(message = "不能为null")
    private Integer proxyConfigListId;

    /**
     * 黑白名单
     */
    @TableField(value = "proxy_config_list")
    @ApiModelProperty(value="黑白名单")
    @Schema(description="黑白名单")
    @Size(max = 255,message = "黑白名单最大长度要小于 255")
    private String proxyConfigList;

    /**
     * 类型;  WHITE; BLACK
     */
    @TableField(value = "proxy_config_list_type")
    @ApiModelProperty(value="类型;  WHITE; BLACK")
    @Schema(description="类型;  WHITE; BLACK")
    @Size(max = 255,message = "类型;  WHITE; BLACK最大长度要小于 255")
    private String proxyConfigListType;

    /**
     * 是否禁用；0: 启用
     */
    @TableField(value = "proxy_config_list_disabled")
    @ApiModelProperty(value="是否禁用；0: 启用")
    @Schema(description="是否禁用；0: 启用")
    private Integer proxyConfigListDisabled;

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