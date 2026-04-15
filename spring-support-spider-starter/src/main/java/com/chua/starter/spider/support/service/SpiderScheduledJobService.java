package com.chua.starter.spider.support.service;

/**
 * 爬虫任务调度绑定服务接口。
 *
 * @author CH
 */
public interface SpiderScheduledJobService {

    /**
     * 向 job-starter 注册调度任务，并持久化返回的 jobBindingId 到 SpiderJobBinding。
     *
     * @param taskId 爬虫任务 ID
     */
    void registerJob(Long taskId);

    /**
     * 暂停调度任务。
     *
     * @param taskId 爬虫任务 ID
     */
    void pauseJob(Long taskId);

    /**
     * 恢复调度任务。
     *
     * @param taskId 爬虫任务 ID
     */
    void resumeJob(Long taskId);

    /**
     * 删除调度任务，并清理 SpiderJobBinding。
     *
     * @param taskId 爬虫任务 ID
     */
    void deleteJob(Long taskId);
}
