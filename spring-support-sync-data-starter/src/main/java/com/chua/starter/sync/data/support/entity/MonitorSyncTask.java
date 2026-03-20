package com.chua.starter.sync.data.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 同步任务实体类
 *
 * @author CH
 * @since 2024/12/19
 */
@EqualsAndHashCode(callSuper = true)
@Schema(description = "同步任务")
@Data
@TableName(
        value = "monitor_sync_task",
        excludeProperty = {
                "createName", "createBy", "createTime", "updateName", "updateBy", "updateTime"
        }
)
public class MonitorSyncTask extends SysBase {

    /**
     * 任务ID
     */
    @TableId(value = "sync_task_id", type = IdType.AUTO)
    @Schema(description = "任务ID")
    private Long syncTaskId;

    /**
     * 任务名称
     */
    @TableField(value = "sync_task_name")
    @Schema(description = "任务名称")
    @NotBlank(message = "任务名称不能为空")
    @Size(max = 255, message = "任务名称最大长度255")
    private String syncTaskName;

    /**
     * 任务描述
     */
    @TableField(value = "sync_task_desc")
    @Schema(description = "任务描述")
    @Size(max = 500, message = "任务描述最大长度500")
    private String syncTaskDesc;

    /**
     * 任务状态: STOPPED/RUNNING/ERROR
     */
    @TableField(value = "sync_task_status")
    @Schema(description = "任务状态: STOPPED/RUNNING/ERROR")
    private String syncTaskStatus;

    /**
     * 批次大小
     */
    @TableField(value = "sync_task_batch_size")
    @Schema(description = "批次大小")
    private Integer syncTaskBatchSize;

    /**
     * 消费超时时间(毫秒)
     */
    @TableField(value = "sync_task_consume_timeout")
    @Schema(description = "消费超时时间(毫秒)")
    private Long syncTaskConsumeTimeout;

    /**
     * 重试次数
     */
    @TableField(value = "sync_task_retry_count")
    @Schema(description = "重试次数")
    private Integer syncTaskRetryCount;

    /**
     * 重试间隔(毫秒)
     */
    @TableField(value = "sync_task_retry_interval")
    @Schema(description = "重试间隔(毫秒)")
    private Long syncTaskRetryInterval;

    /**
     * 同步间隔(毫秒)
     */
    @TableField(value = "sync_task_sync_interval")
    @Schema(description = "同步间隔(毫秒)")
    private Long syncTaskSyncInterval;

    /**
     * 是否启用ACK: 0否 1是
     */
    @TableField(value = "sync_task_ack_enabled")
    @Schema(description = "是否启用ACK: 0否 1是")
    private Integer syncTaskAckEnabled;

    /**
     * 是否启用事务: 0否 1是
     */
    @TableField(value = "sync_task_transaction_enabled")
    @Schema(description = "是否启用事务: 0否 1是")
    private Integer syncTaskTransactionEnabled;

    /**
     * CRON表达式(定时执行)
     */
    @TableField(value = "sync_task_cron")
    @Schema(description = "CRON表达式(定时执行)")
    @Size(max = 100, message = "CRON表达式最大长度100")
    private String syncTaskCron;

    /**
     * 前端连线布局JSON
     */
    @TableField(value = "sync_task_layout")
    @Schema(description = "前端连线布局JSON")
    private String syncTaskLayout;

    /**
     * 最后执行时间
     */
    @TableField(value = "sync_task_last_run_time")
    @Schema(description = "最后执行时间")
    private LocalDateTime syncTaskLastRunTime;

    /**
     * 最后执行状态
     */
    @TableField(value = "sync_task_last_run_status")
    @Schema(description = "最后执行状态")
    private String syncTaskLastRunStatus;

    /**
     * 总执行次数
     */
    @TableField(value = "sync_task_run_count")
    @Schema(description = "总执行次数")
    private Long syncTaskRunCount;

    /**
     * 成功执行次数
     */
    @TableField(value = "sync_task_success_count")
    @Schema(description = "成功执行次数")
    private Long syncTaskSuccessCount;

    /**
     * 失败执行次数
     */
    @TableField(value = "sync_task_fail_count")
    @Schema(description = "失败执行次数")
    private Long syncTaskFailCount;

    /**
     * 创建时间
     */
    @TableField(value = "sync_task_create_time")
    @Schema(description = "创建时间")
    private LocalDateTime syncTaskCreateTime;

    /**
     * 更新时间
     */
    @TableField(value = "sync_task_update_time")
    @Schema(description = "更新时间")
    private LocalDateTime syncTaskUpdateTime;

    /**
     * 数据转换配置JSON
     */
    @TableField(value = "sync_task_transform_config")
    @Schema(description = "数据转换配置JSON")
    private String syncTaskTransformConfig;

    /**
     * 数据过滤配置JSON
     */
    @TableField(value = "sync_task_filter_config")
    @Schema(description = "数据过滤配置JSON")
    private String syncTaskFilterConfig;

    /**
     * 同步模式: FULL/INCREMENTAL/BIDIRECTIONAL
     */
    @TableField(value = "sync_task_sync_mode")
    @Schema(description = "同步模式: FULL/INCREMENTAL/BIDIRECTIONAL")
    private String syncTaskSyncMode;

    /**
     * 增量同步字段
     */
    @TableField(value = "sync_task_incremental_field")
    @Schema(description = "增量同步字段")
    @Size(max = 100, message = "增量同步字段最大长度100")
    private String syncTaskIncrementalField;

    /**
     * 冲突策略: OVERWRITE/SKIP/MERGE
     */
    @TableField(value = "sync_task_conflict_strategy")
    @Schema(description = "冲突策略: OVERWRITE/SKIP/MERGE")
    private String syncTaskConflictStrategy;

    /**
     * 最大内存限制(MB)
     */
    @TableField(value = "sync_task_max_memory_mb")
    @Schema(description = "最大内存限制(MB)")
    private Integer syncTaskMaxMemoryMb;

    /**
     * 线程池大小
     */
    @TableField(value = "sync_task_thread_pool_size")
    @Schema(description = "线程池大小")
    private Integer syncTaskThreadPoolSize;
}
