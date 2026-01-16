package com.chua.starter.job.support.scheduler;

/**
 * 触发处理器接口
 * <p>
 * 定义任务调度器的基本生命周期管理方法。
 * 所有调度处理器都需要实现此接口，以便统一管理启动和停止。
 * </p>
 *
 * <h3>实现类</h3>
 * <ul>
 *     <li>{@link CoreTriggerHandler} - 核心调度处理器，负责数据库扫描</li>
 *     <li>{@link RingTriggerHandler} - 时间环处理器，负责秒级触发</li>
 * </ul>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 * @see CoreTriggerHandler
 * @see RingTriggerHandler
 * @see SchedulerTrigger
 */
public interface TriggerHandler {

    /**
     * 启动处理器
     * <p>初始化并启动调度线程</p>
     */
    void start();

    /**
     * 停止处理器
     * <p>优雅关闭调度线程，等待当前任务执行完成</p>
     */
    void stop();
}
