package com.chua.starter.filesystem.support.service;

/**
 * 文件系统异步调度服务接口。
 *
 * <p>包含两个阶段调度：合并调度（分片→合并文件）与上传 OSS 调度（合并文件→对象存储）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface AsyncMergeService {

    /**
     * 合并调度：扫描待合并任务并执行合并。
     */
    void mergePendingTasks();

    /**
     * 上传 OSS 调度：扫描待上传任务并上传到对象存储。
     */
    void uploadPendingTasks();

    /**
     * 重试调度：扫描失败但未超重试次数的任务，重新执行合并或上传。
     */
    void retryFailedTasks();
}
