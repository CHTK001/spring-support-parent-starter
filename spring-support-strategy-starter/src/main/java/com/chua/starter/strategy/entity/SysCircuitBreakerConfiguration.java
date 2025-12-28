package com.chua.starter.strategy.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 熔断配置实体
 * 用于存储服务熔断策略配置
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("sys_circuit_breaker_configuration")
@Schema(description = "熔断配置")
public class SysCircuitBreakerConfiguration implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "sys_circuit_breaker_id", type = IdType.AUTO)
    @Schema(description = "熔断配置ID")
    private Long sysCircuitBreakerId;

    /**
     * 熔断器名称
     */
    @TableField("sys_circuit_breaker_name")
    @Schema(description = "熔断器名称")
    private String sysCircuitBreakerName;

    /**
     * 服务/接口路径
     */
    @TableField("sys_circuit_breaker_path")
    @Schema(description = "服务/接口路径，支持Ant风格匹配")
    private String sysCircuitBreakerPath;

    /**
     * 失败率阈值（百分比）
     * 当失败率超过此阈值时，熔断器打开
     */
    @TableField("failure_rate_threshold")
    @Schema(description = "失败率阈值（百分比），默认50")
    private Double failureRateThreshold;

    /**
     * 慢调用率阈值（百分比）
     */
    @TableField("slow_call_rate_threshold")
    @Schema(description = "慢调用率阈值（百分比），默认100")
    private Double slowCallRateThreshold;

    /**
     * 慢调用持续时间阈值（毫秒）
     */
    @TableField("slow_call_duration_threshold_ms")
    @Schema(description = "慢调用持续时间阈值（毫秒），默认60000")
    private Long slowCallDurationThresholdMs;

    /**
     * 最小调用次数
     * 在计算失败率之前需要的最小调用次数
     */
    @TableField("minimum_number_of_calls")
    @Schema(description = "最小调用次数，默认10")
    private Integer minimumNumberOfCalls;

    /**
     * 滑动窗口大小
     */
    @TableField("sliding_window_size")
    @Schema(description = "滑动窗口大小，默认10")
    private Integer slidingWindowSize;

    /**
     * 滑动窗口类型
     * COUNT_BASED: 基于计数
     * TIME_BASED: 基于时间
     */
    @TableField("sliding_window_type")
    @Schema(description = "滑动窗口类型：COUNT_BASED-基于计数, TIME_BASED-基于时间")
    private String slidingWindowType;

    /**
     * 熔断器打开状态的等待时间（毫秒）
     */
    @TableField("wait_duration_in_open_state_ms")
    @Schema(description = "熔断器打开状态的等待时间（毫秒），默认60000")
    private Long waitDurationInOpenStateMs;

    /**
     * 半开状态允许的调用次数
     */
    @TableField("permitted_calls_in_half_open_state")
    @Schema(description = "半开状态允许的调用次数，默认3")
    private Integer permittedCallsInHalfOpenState;

    /**
     * 是否自动从打开状态转换到半开状态
     */
    @TableField("automatic_transition_from_open")
    @Schema(description = "是否自动从打开状态转换到半开状态，默认true")
    private Boolean automaticTransitionFromOpen;

    /**
     * 降级方法名称
     */
    @TableField("fallback_method")
    @Schema(description = "降级方法名称")
    private String fallbackMethod;

    /**
     * 降级返回值（JSON格式）
     */
    @TableField("fallback_value")
    @Schema(description = "降级返回值（JSON格式）")
    private String fallbackValue;

    /**
     * 是否启用
     */
    @TableField("sys_circuit_breaker_status")
    @Schema(description = "状态：0-禁用, 1-启用")
    private Integer sysCircuitBreakerStatus;

    /**
     * 描述信息
     */
    @TableField("sys_circuit_breaker_description")
    @Schema(description = "描述信息")
    private String sysCircuitBreakerDescription;

    /**
     * 排序
     */
    @TableField("sys_circuit_breaker_sort")
    @Schema(description = "排序值")
    private Integer sysCircuitBreakerSort;

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

    // ==================== 拦截器适配方法 ====================

    public Long getId() {
        return sysCircuitBreakerId;
    }

    public String getName() {
        return sysCircuitBreakerName;
    }

    public String getUrlPattern() {
        return sysCircuitBreakerPath;
    }

    public String getHttpMethod() {
        return "*"; // 默认匹配所有HTTP方法
    }

    public Boolean getEnabled() {
        return sysCircuitBreakerStatus != null && sysCircuitBreakerStatus == 1;
    }

    public Long getSlowCallDurationThreshold() {
        return slowCallDurationThresholdMs;
    }

    public Long getWaitDurationInOpenState() {
        return waitDurationInOpenStateMs != null ? waitDurationInOpenStateMs / 1000 : 60;
    }

    public Integer getPermittedNumberOfCallsInHalfOpenState() {
        return permittedCallsInHalfOpenState;
    }

    public String getFallbackMessage() {
        return fallbackValue != null ? fallbackValue : "服务暂时不可用，请稍后重试";
    }
}
