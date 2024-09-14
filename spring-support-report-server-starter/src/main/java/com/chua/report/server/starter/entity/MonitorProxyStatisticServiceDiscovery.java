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
 * 静态IP
 */
@ApiModel(description = "静态IP")
@Schema(description = "静态IP")
@Data
@TableName(value = "monitor_proxy_statistic_service_discovery")
public class MonitorProxyStatisticServiceDiscovery implements Serializable {
    @TableId(value = "proxy_statistic_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer proxyStatisticId;

    /**
     * 名称说明
     */
    @TableField(value = "proxy_statistic_name")
    @ApiModelProperty(value = "名称说明")
    @Schema(description = "名称说明")
    @Size(max = 255, message = "名称说明最大长度要小于 255")
    private String proxyStatisticName;

    /**
     * 地址前缀
     */
    @TableField(value = "proxy_statistic_url")
    @ApiModelProperty(value = "地址前缀")
    @Schema(description = "地址前缀")
    @Size(max = 255, message = "地址前缀最大长度要小于 255")
    private String proxyStatisticUrl;

    /**
     * 协议
     */
    @TableField(value = "proxy_statistic_protocol")
    @ApiModelProperty(value = "协议")
    @Schema(description = "协议")
    @Size(max = 255, message = "协议最大长度要小于 255")
    private String proxyStatisticProtocol;

    /**
     * 地址
     */
    @TableField(value = "proxy_statistic_hostname")
    @ApiModelProperty(value = "地址")
    @Schema(description = "地址")
    @Size(max = 255, message = "地址最大长度要小于 255")
    private String proxyStatisticHostname;

    /**
     * 权重
     */
    @TableField(value = "proxy_statistic_weight")
    @ApiModelProperty(value = "权重")
    @Schema(description = "权重")
    private Integer proxyStatisticWeight;

    /**
     * 代理ID
     */
    @TableField(value = "proxy_id")
    @ApiModelProperty(value = "代理ID")
    @Schema(description = "代理ID")
    private Integer proxyId;

    /**
     * 状态;0:禁用
     */
    @TableField(value = "proxy_statistic_status")
    @ApiModelProperty(value = "状态;0:禁用")
    @Schema(description = "状态;0:禁用")
    private Integer proxyStatisticStatus;

    private static final long serialVersionUID = 1L;
}