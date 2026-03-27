package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付调度任务配置
 */
@Data
@TableName("payment_scheduler_config")
public class PaymentSchedulerConfig {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskKey;
    private String taskName;
    private String cronExpression;
    private Boolean enabled;
    private String description;
    private LocalDateTime lastStartedAt;
    private LocalDateTime lastFinishedAt;
    private String lastRunStatus;
    private String lastRunMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
