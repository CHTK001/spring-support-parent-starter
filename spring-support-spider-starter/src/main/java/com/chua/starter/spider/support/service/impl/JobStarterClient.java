package com.chua.starter.spider.support.service.impl;

/**
 * job-starter 客户端抽象接口。
 *
 * <p>当 job-starter 可用时使用 {@link JobDynamicConfigServiceClient} 实现；
 * 不可用时使用 {@link NoOpJobStarterClient} 降级。</p>
 *
 * @author CH
 */
public interface JobStarterClient {

    /**
     * 注册调度任务。
     *
     * @param jobName    任务名称（唯一标识）
     * @param cron       Cron 表达式
     * @param jobChannel 调度通道（可为 null）
     * @param param      执行参数
     * @param desc       任务描述
     * @return jobBindingId（job-starter 返回的任务标识）
     */
    String registerJob(String jobName, String cron, String jobChannel, String param, String desc);

    /**
     * 暂停调度任务。
     *
     * @param jobBindingId job-starter 任务标识
     */
    void pauseJob(String jobBindingId);

    /**
     * 恢复调度任务。
     *
     * @param jobBindingId job-starter 任务标识
     */
    void resumeJob(String jobBindingId);

    /**
     * 删除调度任务。
     *
     * @param jobBindingId job-starter 任务标识
     */
    void deleteJob(String jobBindingId);
}
