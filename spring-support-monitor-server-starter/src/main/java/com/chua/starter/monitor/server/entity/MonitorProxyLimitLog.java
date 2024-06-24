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
 * 限流日志
 */
@ApiModel(description = "限流日志")
@Schema(description = "限流日志")
@Data
@TableName(value = "monitor_proxy_limit_log")
public class MonitorProxyLimitLog implements Serializable {
    @TableId(value = "limit_log_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer limitLogId;

    /**
     * 日志对应的代理
     */
    @TableField(value = "limit_log_server_id")
    @ApiModelProperty(value = "日志对应的代理")
    @Schema(description = "日志对应的代理")
    @Size(max = 255, message = "日志对应的代理最大长度要小于 255")
    private String limitLogServerId;

    /**
     * 请求地址
     */
    @TableField(value = "limit_log_url")
    @ApiModelProperty(value = "请求地址")
    @Schema(description = "请求地址")
    @Size(max = 255, message = "请求地址最大长度要小于 255")
    private String limitLogUrl;

    /**
     * 类型;deny;allow
     */
    @TableField(value = "limit_log_type")
    @ApiModelProperty(value = "类型;deny;allow")
    @Schema(description = "类型;deny;allow")
    @Size(max = 255, message = "类型;deny;allow最大长度要小于 255")
    private String limitLogType;

    /**
     * 请求端地址
     */
    @TableField(value = "limit_log_address")
    @ApiModelProperty(value = "请求端地址")
    @Schema(description = "请求端地址")
    @Size(max = 255, message = "请求端地址最大长度要小于 255")
    private String limitLogAddress;

    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    @ApiModelProperty(value = "创建时间")
    @Schema(description = "创建时间")
    private Date createTime;

    private static final long serialVersionUID = 1L;
    /**
     * 名称
     */
    @TableField(exist = false)
    @ApiModelProperty(value = "代理名称")
    @Schema(description = "代理名称")
    private String proxyName;
    /**
     * 每秒生产令牌数
     */
    @TableField(exist = false)
    @ApiModelProperty(value = "每秒生产令牌数")
    @Schema(description = "每秒生产令牌数")
    private Integer limitPermitsPerSecond;
}