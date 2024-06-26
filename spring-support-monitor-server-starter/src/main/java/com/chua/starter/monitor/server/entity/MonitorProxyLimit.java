package com.chua.starter.monitor.server.entity;

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
import java.util.Date;

/**
 *
 *
 * @since 2024/6/24 
 * @author CH
 */

/**
 * 代理限流器
 */
@ApiModel(description = "代理限流器")
@Schema(description = "代理限流器")
@Data
@TableName(value = "monitor_proxy_limit")
public class MonitorProxyLimit implements Serializable {
    @TableId(value = "limit_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer limitId;

    /**
     * 限流ip地址
     */
    @TableField(value = "limit_address")
    @ApiModelProperty(value = "限流ip地址")
    @Schema(description = "限流ip地址")
    @Size(max = 255, message = "限流ip地址最大长度要小于 255")
    private String limitAddress;

    /**
     * 限流url地址
     */
    @TableField(value = "limit_url")
    @ApiModelProperty(value = "限流url地址")
    @Schema(description = "限流url地址")
    @Size(max = 255, message = "限流url地址最大长度要小于 255")
    private String limitUrl;

    /**
     * 是否开启； 0：未开启
     */
    @TableField(value = "limit_disable")
    @ApiModelProperty(value = "是否开启； 0：未开启")
    @Schema(description = "是否开启； 0：未开启")
    private Integer limitDisable;
    @TableField(value = "limit_black")
    @ApiModelProperty(value = "是否黑名单； 0：否")
    @Schema(description = "是否黑名单； 0：否")
    private Integer limitBlack;

    /**
     * 每秒生产令牌数
     */
    @TableField(value = "limit_permits_per_second")
    @ApiModelProperty(value = "每秒生产令牌数")
    @Schema(description = "每秒生产令牌数")
    private Integer limitPermitsPerSecond;

    /**
     * 代理ID
     */
    @TableField(value = "proxy_id")
    @ApiModelProperty(value = "代理ID")
    @Schema(description = "代理ID")
    @Size(max = 255, message = "代理ID最大长度要小于 255")
    private String proxyId;

    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    @ApiModelProperty(value = "创建时间")
    @Schema(description = "创建时间")
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time")
    @ApiModelProperty(value = "更新时间")
    @Schema(description = "更新时间")
    private Date updateTime;

    private static final long serialVersionUID = 1L;
    /**
     * 代理ID
     */
    @TableField(exist = false)
    @ApiModelProperty(value = "代理名称(系统自动生成)")
    @Schema(description = "代理名称")
    private String proxyName;
}