package com.chua.starter.sync.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 同步任务执行日志实体类
 *
 * @author CH
 * @since 2024/12/19
 */
@EqualsAndHashCode(callSuper = true)
@Schema(description = "同步任务执行日志")
@Data
@TableName(value = "monitor_sync_task_log")
public class MonitorSyncTaskLog extends SysBase {

    /**
     * 日志ID
     */
    @TableId(value = "sync_log_id", type = IdType.AUTO)
    @Schema(description = "日志ID")
    private Long syncLogId;

    /**
     * 关联任务ID
     */
    @TableField(value = "sync_task_id")
    @Schema(description = "关联任务ID")
    @NotNull(message = "任务ID不能为空")
    private Long syncTaskId;

    /**
     * 执行状态: RUNNING/SUCCESS/FAIL/TIMEOUT
     */
    @TableField(value = "sync_log_status")
    @Schema(description = "执行状态: RUNNING/SUCCESS/FAIL/TIMEOUT")
    private String syncLogStatus;

    /**
     * 触发类型: MANUAL/SCHEDULE/API
     */
    @TableField(value = "sync_log_trigger_type")
    @Schema(description = "触发类型: MANUAL/SCHEDULE/API")
    private String syncLogTriggerType;

    /**
     * 读取数量
     */
    @TableField(value = "sync_log_read_count")
    @Schema(description = "读取数量")
    private Long syncLogReadCount;

    /**
     * 写入数量
     */
    @TableField(value = "sync_log_write_count")
    @Schema(description = "写入数量")
    private Long syncLogWriteCount;

    /**
     * 成功数量
     */
    @TableField(value = "sync_log_success_count")
    @Schema(description = "成功数量")
    private Long syncLogSuccessCount;

    /**
     * 失败数量
     */
    @TableField(value = "sync_log_fail_count")
    @Schema(description = "失败数量")
    private Long syncLogFailCount;

    /**
     * 重试数量
     */
    @TableField(value = "sync_log_retry_count")
    @Schema(description = "重试数量")
    private Long syncLogRetryCount;

    /**
     * 死信数量
     */
    @TableField(value = "sync_log_dead_letter_count")
    @Schema(description = "死信数量")
    private Long syncLogDeadLetterCount;

    /**
     * 过滤数量
     */
    @TableField(value = "sync_log_filter_count")
    @Schema(description = "过滤数量")
    private Long syncLogFilterCount;

    /**
     * 开始时间
     */
    @TableField(value = "sync_log_start_time")
    @Schema(description = "开始时间")
    private LocalDateTime syncLogStartTime;

    /**
     * 结束时间
     */
    @TableField(value = "sync_log_end_time")
    @Schema(description = "结束时间")
    private LocalDateTime syncLogEndTime;

    /**
     * 耗时(毫秒)
     */
    @TableField(value = "sync_log_cost")
    @Schema(description = "耗时(毫秒)")
    private Long syncLogCost;

    /**
     * 平均处理时间(毫秒)
     */
    @TableField(value = "sync_log_avg_process_time")
    @Schema(description = "平均处理时间(毫秒)")
    private Double syncLogAvgProcessTime;

    /**
     * 吞吐量(条/秒)
     */
    @TableField(value = "sync_log_throughput")
    @Schema(description = "吞吐量(条/秒)")
    private Double syncLogThroughput;

    /**
     * 执行消息/错误信息
     */
    @TableField(value = "sync_log_message")
    @Schema(description = "执行消息/错误信息")
    private String syncLogMessage;

    /**
     * 详细堆栈(错误时)
     */
    @TableField(value = "sync_log_stack_trace")
    @Schema(description = "详细堆栈(错误时)")
    private String syncLogStackTrace;
}
