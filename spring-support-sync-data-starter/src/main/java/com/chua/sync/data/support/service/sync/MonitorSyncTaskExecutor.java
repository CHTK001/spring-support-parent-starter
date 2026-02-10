package com.chua.sync.data.support.service.sync;

/**
 * 同步任务执行器接口
 *
 * @author CH
 * @since 2024/12/19
 */
public interface MonitorSyncTaskExecutor {

    /**
     * 启动任务
     *
     * @param taskId 任务ID
     */
    void start(Long taskId);

    /**
     * 停止任务
     *
     * @param taskId 任务ID
     */
    void stop(Long taskId);

    /**
     * 手动执行一次
     *
     * @param taskId 任务ID
     * @return 执行日志ID
     */
    Long executeOnce(Long taskId);

    /**
     * 检查任务是否在运行
     *
     * @param taskId 任务ID
     * @return 是否运行中
     */
    boolean isRunning(Long taskId);

    /**
     * 停止所有任务
     */
    void stopAll();
}
