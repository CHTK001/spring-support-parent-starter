package com.chua.payment.support.task;

/**
 * 支付模块托管任务
 */
public interface PaymentManagedTask {

    /**
     * 唯一任务编码
     */
    String taskKey();

    /**
     * 任务名称
     */
    String taskName();

    /**
     * 默认 Cron 表达式
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
