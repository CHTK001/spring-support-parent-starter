package com.chua.payment.support.task;

/**
* 支付模块托管任务
* @author CH
* @since 4.0.0
 */
public interface PaymentManagedTask {

    /**
    * 唯一任务编码
    * @return 任务Key的结果
    */
    String taskKey();

    /**
    * 任务名称
    * @return 任务名称的结果
    */
    String taskName();

    /**
    * 默认 Cron 表达式
    * @return 默认cron的结果
    */
    String defaultCron();

    /**
    * 任务说明
    * @return description的结果
    */
    String description();

    /**
    * 执行任务
    */
    void execute();
}

