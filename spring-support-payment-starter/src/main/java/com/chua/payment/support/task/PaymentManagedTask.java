package com.chua.payment.support.task;

/**
 * 支付模块托管任务
 * @author CH
 * @since 4.0.0
 */
public interface PaymentManagedTask {

    /**
     * 唯一任务标识
     * @return 任务标识
     */
    String taskKey();

    /**
     * 任务名称
     * @return 任务名称
     */
    String taskName();

    /**
     * 默认 Cron 表达式
     * @return 默认 cron 表达式
     */
    String defaultCron();

    /**
     * 任务说明
     */
    String description();

    /**
     * 执行任务
     */
    void execute();
}
