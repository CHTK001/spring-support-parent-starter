package com.chua.starter.device.support.service;

import com.chua.starter.device.support.entity.DeviceTaskState;

import java.util.List;

/**
 * 任务状态服务接口（Trigger 模式）
 * <p>后台每秒执行的任务通过判断 Trigger 标志决定是否真正执行业务逻辑，</p>
 * <p>实现单线程单实例运行，避免并发问题。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface DeviceTaskStateService {

    /**
     * 列出全部任务状态
     *
     * @return 任务状态列表（按任务名升序）
     */
    List<DeviceTaskState> list();

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

    /**
     * 补种任务状态行
     * <p>给时间轮上已注册但尚无状态行的任务（如按凭据切分的任务）补一行
     * {@code device_task_state}，避免 {@link #shouldRun} 因无行而永不执行。</p>
     *
     * @param name        任务名称
     * @param interval    运行间隔（秒）
     * @param description 描述
     * @return 是否新补种（已存在返回 {@code false}）
     */
    boolean seed(String name, int interval, String description);
}
