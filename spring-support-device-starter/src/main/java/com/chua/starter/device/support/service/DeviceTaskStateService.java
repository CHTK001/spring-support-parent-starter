package com.chua.starter.device.support.service;

import com.chua.starter.device.support.entity.DeviceTaskState;

/**
 * 任务状态服务接口（Trigger 模式）
 * <p>后台每秒执行的任务通过判断 Trigger 标志决定是否真正执行业务逻辑，</p>
 * <p>实现单线程单实例运行，避免并发问题。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface DeviceTaskStateService {

    /**
     * 判断任务是否应该运行
     * <p>如果上次运行时间距今超过配置的间隔，则返回 true。</p>
     *
     * @param taskName 任务名称
     * @return 是否应该运行
     */
    boolean shouldRun(String taskName);

    /**
     * 标记任务开始运行
     *
     * @param taskName 任务名称
     * @return 是否成功获取执行权
     */
    boolean markRunning(String taskName);

    /**
     * 标记任务运行结束
     *
     * @param taskName 任务名称
     */
    void markFinished(String taskName);

    /**
     * 获取任务状态
     *
     * @param taskName 任务名称
     * @return 任务状态实体
     */
    DeviceTaskState getState(String taskName);

    /**
     * 更新任务运行间隔
     *
     * @param taskName 任务名称
     * @param interval 间隔（秒）
     */
    void updateInterval(String taskName, int interval);
}