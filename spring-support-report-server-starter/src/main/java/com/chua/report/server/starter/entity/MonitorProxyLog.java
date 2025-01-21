package com.chua.report.server.starter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 *
 * @since 2024/9/16
 * @author CH    
 */

/**
 * 代理日志
 */
@ApiModel(description = "代理日志")
@Schema(description = "代理日志")
@Data
@TableName(value = "monitor_proxy_log")
public class MonitorProxyLog implements Serializable {
    @TableId(value = "monitor_proxy_log_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer monitorProxyLogId;

    /**
     * 类型； LIMIT, WHITE, BLACK
     */
    @TableField(value = "monitor_proxy_log_type")
    @ApiModelProperty(value = "类型； LIMIT, WHITE, BLACK")
    @Schema(description = "类型； LIMIT, WHITE, BLACK")
    @Size(max = 255, message = "类型； LIMIT, WHITE, BLACK最大长度要小于 255")
    private String monitorProxyLogType;

    /**
     * 错误码
     */
    @TableField(value = "monitor_proxy_log_code")
    @ApiModelProperty(value = "错误码")
    @Schema(description = "错误码")
    @Size(max = 255, message = "错误码最大长度要小于 255")
    private String monitorProxyLogCode;

    /**
     * 耗时
     */
    @TableField(value = "monitor_proxy_log_cost")
    @ApiModelProperty(value = "耗时")
    @Schema(description = "耗时")
    private BigDecimal monitorProxyLogCost;

    /**
     * 原因
     */
    @TableField(value = "monitor_proxy_log_msg")
    @ApiModelProperty(value = "原因")
    @Schema(description = "原因")
    @Size(max = 255, message = "原因最大长度要小于 255")
    private String monitorProxyLogMsg;

    /**
     * 地址
     */
    @TableField(value = "monitor_proxy_log_url")
    @ApiModelProperty(value = "地址")
    @Schema(description = "地址")
    @Size(max = 255, message = "地址最大长度要小于 255")
    private String monitorProxyLogUrl;

    /**
     * 服务 ID
     */
    @TableField(value = "monitor_proxy_log_server_id")
    @ApiModelProperty(value = "服务 ID")
    @Schema(description = "服务 ID")
    @Size(max = 255, message = "服务 ID最大长度要小于 255")
    private String monitorProxyLogServerId;

    /**
     * 客户端地址
     */
    @TableField(value = "monitor_proxy_log_address")
    @ApiModelProperty(value = "客户端地址")
    @Schema(description = "客户端地址")
    @Size(max = 255, message = "客户端地址最大长度要小于 255")
    private String monitorProxyLogAddress;

    /**
     * 客户端地址
     */
    @TableField(value = "monitor_proxy_log_address_geo")
    @ApiModelProperty(value = "客户端地址")
    @Schema(description = "客户端地址")
    @Size(max = 255, message = "客户端地址最大长度要小于 255")
    private String monitorProxyLogAddressGeo;

    /**
     * 发生时间(day)
     */
    @TableField(value = "monitor_proxy_log_date")
    @ApiModelProperty(value = "发生时间(day)")
    @Schema(description = "发生时间(day)")
    private Long monitorProxyLogDate;

    /**
     * 发生时间(hour)
     */
    @TableField(value = "monitor_proxy_log_hour")
    @ApiModelProperty(value = "发生时间(hour)")
    @Schema(description = "发生时间(hour)")
    private Long monitorProxyLogHour;

    /**
     * 创建人姓名
     */
    @TableField(value = "create_name")
    @ApiModelProperty(value = "创建人姓名")
    @Schema(description = "创建人姓名")
    @Size(max = 255, message = "创建人姓名最大长度要小于 255")
    private String createName;

    /**
     * 创建人
     */
    @TableField(value = "create_by")
    @ApiModelProperty(value = "创建人")
    @Schema(description = "创建人")
    private Integer createBy;

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

    /**
     * 更新人姓名
     */
    @TableField(value = "update_name")
    @ApiModelProperty(value = "更新人姓名")
    @Schema(description = "更新人姓名")
    @Size(max = 255, message = "更新人姓名最大长度要小于 255")
    private String updateName;

    /**
     * 更新人
     */
    @TableField(value = "update_by")
    @ApiModelProperty(value = "更新人")
    @Schema(description = "更新人")
    private Integer updateBy;


    /**
     * 开始时间
     */
    @Schema(description = "开始时间")
    @TableField(exist = false)
    private Date startDate;

    /**
     * 结束时间
     */
    @Schema(description = "结束时间")
    @TableField(exist = false)
    private Date endDate;
    /**
     * 代理名称
     */
    @Schema(description = "代理名称")
    @TableField(exist = false)
    private String proxyName;

    private static final long serialVersionUID = 1L;
}