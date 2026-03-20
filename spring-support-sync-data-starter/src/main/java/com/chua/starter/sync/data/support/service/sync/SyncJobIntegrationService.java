package com.chua.starter.sync.data.support.service.sync;

import com.chua.starter.sync.data.support.entity.MonitorSyncTask;

/**
 * Sync 任务与 Job 调度集成服务
 *
 * @author CH
 * @since 2026/03/19
 */
public interface SyncJobIntegrationService {

    /**
     * 创建或更新 Job 配置
     *
     * @param task 同步任务
     * @return Job ID
     */
    Integer createOrUpdateJob(MonitorSyncTask task);

    /**
     * 启动 Job 调度
     *
     * @param taskId 任务ID
     * @return 是否成功
     */
    boolean startJob(Long taskId);

    /**
     * 停止 Job 调度
     *
     * @param taskId 任务ID
     * @return 是否成功
     */
    boolean stopJob(Long taskId);

    /**
     * 删除 Job 配置
     *
     * @param taskId 任务ID
     * @return 是否成功
     */
    boolean deleteJob(Long taskId);

    /**
     * 手动触发 Job 执行
     *
     * @param taskId 任务ID
     * @return 是否成功
     */
    boolean triggerJob(Long taskId);

    /**
     * 同步任务状态
     *
     * @param taskId 任务ID
     */
    void syncTaskStatus(Long taskId);

    /**
     * 获取关联的 Job ID
     *
     * @param taskId 任务ID
     * @return Job ID
     */
    Integer getJobId(Long taskId);
}
