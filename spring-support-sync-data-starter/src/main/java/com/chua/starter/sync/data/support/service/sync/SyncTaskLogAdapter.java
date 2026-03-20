package com.chua.starter.sync.data.support.service.sync;

import com.chua.starter.sync.data.support.entity.MonitorSyncTask;
import com.chua.starter.sync.data.support.entity.MonitorSyncTaskLog;

/**
 * Sync 日志适配到 Job 日志
 *
 * @author CH
 * @since 2026/03/19
 */
public interface SyncTaskLogAdapter {

    /**
     * 准备 Job 日志并返回 Job 日志ID
     *
     * @param task    同步任务
     * @param syncLog 同步日志
     * @return Job 日志ID
     */
    Integer createJobLog(MonitorSyncTask task, MonitorSyncTaskLog syncLog);

    /**
     * 回写 Job 日志结果
     *
     * @param task     同步任务
     * @param syncLog  同步日志
     * @param jobLogId Job 日志ID
     */
    void updateJobLog(MonitorSyncTask task, MonitorSyncTaskLog syncLog, Integer jobLogId);
}
