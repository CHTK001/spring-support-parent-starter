package com.chua.starter.strategy.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 限流配置实体
 * 用于存储 API 接口的限流规则配置
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("sys_limit_configuration")
@Schema(description = "限流配置")
public class SysLimitConfiguration implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "sys_limit_configuration_id", type = IdType.AUTO)
    @Schema(description = "限流配置ID")
    private Long sysLimitConfigurationId;

    /**
     * 接口路径
     */
    @TableField("sys_limit_path")
    @Schema(description = "接口路径，支持Ant风格匹配")
    private String sysLimitPath;

    /**
     * 限流规则名称
     */
    @TableField("sys_limit_name")
    @Schema(description = "限流规则名称")
    private String sysLimitName;

    /**
     * 每个周期的许可数量
     */
    @TableField("sys_limit_for_period")
    @Schema(description = "每个周期允许的请求数量")
    private Integer sysLimitForPeriod;

    /**
     * 限制刷新周期（秒）
     */
    @TableField("sys_limit_refresh_period_seconds")
    @Schema(description = "限流刷新周期（秒）")
    private Integer sysLimitRefreshPeriodSeconds;

    /**
     * 获取许可的超时时间（毫秒）
     */
    @TableField("sys_limit_timeout_duration_millis")
    @Schema(description = "获取许可的超时时间（毫秒）")
    private Long sysLimitTimeoutDurationMillis;

    /**
     * 限流维度
     * GLOBAL: 全局限流
     * IP: 按IP限流
     * USER: 按用户限流
     * API: 按接口限流
     */
    @TableField("sys_limit_dimension")
    @Schema(description = "限流维度：GLOBAL-全局, IP-按IP, USER-按用户, API-按接口")
    private String sysLimitDimension;

    /**
     * 自定义键表达式（SpEL）
     */
    @TableField("sys_limit_key_expression")
    @Schema(description = "自定义键表达式（SpEL）")
    private String sysLimitKeyExpression;

    /**
     * 降级方法名称
     */
    @TableField("sys_limit_fallback_method")
    @Schema(description = "降级方法名称")
    private String sysLimitFallbackMethod;

    /**
     * 错误消息
     */
    @TableField("sys_limit_message")
    @Schema(description = "限流触发时的错误消息")
    private String sysLimitMessage;

    /**
     * 是否启用
     * 0: 禁用
     * 1: 启用
     */
    @TableField("sys_limit_status")
    @Schema(description = "状态：0-禁用, 1-启用")
    private Integer sysLimitStatus;

    /**
     * 描述信息
     */
    @TableField("sys_limit_description")
    @Schema(description = "描述信息")
    private String sysLimitDescription;

    /**
     * 排序
     */
    @TableField("sys_limit_sort")
    @Schema(description = "排序值，越小越优先")
    private Integer sysLimitSort;

    /**
     * 创建人ID
     */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @Schema(description = "创建人ID")
    private Long createBy;

    /**
     * 创建人姓名
     */
    @TableField(value = "create_name", fill = FieldFill.INSERT)
    @Schema(description = "创建人姓名")
    private String createName;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 更新人ID
     */
    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新人ID")
    private Long updateBy;

    /**
     * 更新人姓名
     */
    @TableField(value = "update_name", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新人姓名")
    private String updateName;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
