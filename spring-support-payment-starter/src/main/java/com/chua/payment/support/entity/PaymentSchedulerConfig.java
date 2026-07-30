package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付调度任务配置
 */
@Data
@TableName("pay_scheduler_config")
public class PaymentSchedulerConfig {
    @TableId(value = "pay_scheduler_config_id", type = IdType.AUTO)
    private Long id;

    @TableField("pay_scheduler_config_task_key")
    private String taskKey;

    @TableField("pay_scheduler_config_task_name")
    private String taskName;

    @TableField("pay_scheduler_config_cron_expression")
    private String cronExpression;

    @TableField("pay_scheduler_config_enabled")
    private Boolean enabled;

    @TableField("pay_scheduler_config_description")
    private String description;

    @TableField("pay_scheduler_config_last_started_at")
    private LocalDateTime lastStartedAt;

    @TableField("pay_scheduler_config_last_finished_at")
    private LocalDateTime lastFinishedAt;

    @TableField("pay_scheduler_config_last_run_status")
    private String lastRunStatus;

    @TableField("pay_scheduler_config_last_run_message")
    private String lastRunMessage;

    @TableField("pay_scheduler_config_create_time")
    private LocalDateTime createTime;

    @TableField("pay_scheduler_config_update_time")
    private LocalDateTime updateTime;
}
