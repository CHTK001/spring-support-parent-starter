package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付调度任务配置
 *
 * @author CH
 * @since 4.0.0
 */
@Data
@TableName("pay_scheduler_config")
public class PaymentSchedulerConfig {
    @TableId(value = "pay_scheduler_config_id", type = IdType.AUTO)
    /**
     * 标识
     */
    private Long id;

    @TableField("pay_scheduler_config_task_key")
    /**
     * 任务键
     */
    private String taskKey;

    @TableField("pay_scheduler_config_task_name")
    /**
     * 任务名称
     */
    private String taskName;

    @TableField("pay_scheduler_config_cron_expression")
    /**
     * Cron表达式
     */
    private String cronExpression;

    @TableField("pay_scheduler_config_enabled")
    /**
     * 是否启用
     */
    private Boolean enabled;

    @TableField("pay_scheduler_config_description")
    /**
     * 描述
     */
    private String description;

    @TableField("pay_scheduler_config_last_started_at")
    /**
     * 最后启动时间
     */
    private LocalDateTime lastStartedAt;

    @TableField("pay_scheduler_config_last_finished_at")
    /**
     * 最后完成时间
     */
    private LocalDateTime lastFinishedAt;

    @TableField("pay_scheduler_config_last_run_status")
    /**
     * 最后运行状态
     */
    private String lastRunStatus;

    @TableField("pay_scheduler_config_last_run_message")
    /**
     * 最后运行消息
     */
    private String lastRunMessage;

    @TableField("pay_scheduler_config_create_time")
    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    @TableField("pay_scheduler_config_update_time")
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
