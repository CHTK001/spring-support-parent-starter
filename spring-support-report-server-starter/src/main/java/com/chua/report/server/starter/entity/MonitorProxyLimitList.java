package com.chua.report.server.starter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 *
 * @since 2024/7/30
 * @author CH    
 */

/**
 * 名单
 */
@ApiModel(description = "名单")
@Schema(description = "名单")
@Data
@TableName(value = "monitor_proxy_limit_list")
public class MonitorProxyLimitList implements Serializable {
    @TableId(value = "list_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer listId;

    /**
     * 类型;0:白名单; 1:黑名单
     */
    @TableField(value = "list_type")
    @ApiModelProperty(value = "类型;0:白名单; 1:黑名单")
    @Schema(description = "类型;0:白名单; 1:黑名单")
    private Integer listType;

    /**
     * 是否开启; 0:不开启
     */
    @TableField(value = "list_status")
    @ApiModelProperty(value = "是否开启; 0:不开启")
    @Schema(description = "是否开启; 0:不开启")
    private Integer listStatus;

    /**
     * IP
     */
    @TableField(value = "list_ip")
    @ApiModelProperty(value = "IP")
    @Schema(description = "IP")
    @Size(max = 255, message = "IP最大长度要小于 255")
    private String listIp;

    /**
     * 代理ID
     */
    @TableField(value = "proxy_id")
    @ApiModelProperty(value = "代理ID")
    @Schema(description = "代理ID")
    @Size(max = 255, message = "代理ID最大长度要小于 255")
    private String proxyId;

    /**
     * 代理ID
     */
    @TableField(exist = false)
    @ApiModelProperty(value = "代理名称(系统自动生成)")
    @Schema(description = "代理名称")
    private String proxyName;

    private static final long serialVersionUID = 1L;
}