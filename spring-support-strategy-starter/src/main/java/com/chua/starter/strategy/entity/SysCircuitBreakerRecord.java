package com.chua.starter.strategy.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 熔断记录实体
 * 记录熔断器触发的详细信息
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("sys_circuit_breaker_record")
@Schema(description = "熔断记录")
public class SysCircuitBreakerRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "sys_circuit_breaker_record_id", type = IdType.AUTO)
    @Schema(description = "熔断记录ID")
    private Long sysCircuitBreakerRecordId;

    /**
     * 关联的熔断配置ID
     */
    @TableField("sys_circuit_breaker_id")
    @Schema(description = "关联的熔断配置ID")
    private Long sysCircuitBreakerId;

    /**
     * 熔断器名称
     */
    @TableField("sys_circuit_breaker_name")
    @Schema(description = "熔断器名称")
    private String sysCircuitBreakerName;

    /**
     * 触发熔断的接口路径
     */
    @TableField("sys_circuit_breaker_path")
    @Schema(description = "触发熔断的接口路径")
    private String sysCircuitBreakerPath;

    /**
     * 熔断器状态
     * CLOSED: 关闭（正常）
     * OPEN: 打开（熔断中）
     * HALF_OPEN: 半开
     */
    @TableField("circuit_breaker_state")
    @Schema(description = "熔断器状态：CLOSED-关闭, OPEN-打开, HALF_OPEN-半开")
    private String circuitBreakerState;

    /**
     * 触发原因
     * FAILURE_RATE: 失败率超过阈值
     * SLOW_CALL_RATE: 慢调用率超过阈值
     */
    @TableField("trigger_reason")
    @Schema(description = "触发原因：FAILURE_RATE-失败率超过阈值, SLOW_CALL_RATE-慢调用率超过阈值")
    private String triggerReason;

    /**
     * 当前失败率
     */
    @TableField("failure_rate")
    @Schema(description = "当前失败率（百分比）")
    private Double failureRate;

    /**
     * 当前慢调用率
     */
    @TableField("slow_call_rate")
    @Schema(description = "当前慢调用率（百分比）")
    private Double slowCallRate;

    /**
     * 触发熔断的用户ID
     */
    @TableField("sys_user_id")
    @Schema(description = "触发熔断的用户ID")
    private Long sysUserId;

    /**
     * 触发熔断的用户名
     */
    @TableField("sys_user_name")
    @Schema(description = "触发熔断的用户名")
    private String sysUserName;

    /**
     * 客户端IP地址
     */
    @TableField("client_ip")
    @Schema(description = "客户端IP地址")
    private String clientIp;

    /**
     * HTTP请求方法
     */
    @TableField("request_method")
    @Schema(description = "HTTP请求方法")
    private String requestMethod;

    /**
     * 异常信息
     */
    @TableField("exception_message")
    @Schema(description = "异常信息")
    private String exceptionMessage;

    /**
     * 熔断触发时间
     */
    @TableField("trigger_time")
    @Schema(description = "熔断触发时间")
    private LocalDateTime triggerTime;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
