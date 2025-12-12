package com.chua.starter.job.support.scheduler;

/**
 * 触发处理器接口
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 */
public interface TriggerHandler {

    /**
     * 启动
     */
    void start();

    /**
     * 停止
     */
    void stop();
}
