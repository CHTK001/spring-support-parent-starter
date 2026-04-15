package com.chua.starter.spider.support.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * job-starter 不可用时的降级实现。
 *
 * <p>所有操作均记录警告日志并抛出异常，明确告知调用方 job-starter 未配置。</p>
 *
 * @author CH
 */
@Slf4j
@Component
@ConditionalOnMissingBean(JobDynamicConfigServiceClient.class)
public class NoOpJobStarterClient implements JobStarterClient {

    @Override
    public String registerJob(String jobName, String cron, String jobChannel, String param, String desc) {
        log.warn("[Spider] job-starter 未配置，无法注册调度任务: {}", jobName);
        throw new IllegalStateException("job-starter 未配置，无法注册调度任务");
    }

    @Override
    public void pauseJob(String jobBindingId) {
        log.warn("[Spider] job-starter 未配置，无法暂停调度任务: {}", jobBindingId);
        throw new IllegalStateException("job-starter 未配置，无法暂停调度任务");
    }

    @Override
    public void resumeJob(String jobBindingId) {
        log.warn("[Spider] job-starter 未配置，无法恢复调度任务: {}", jobBindingId);
        throw new IllegalStateException("job-starter 未配置，无法恢复调度任务");
    }

    @Override
    public void deleteJob(String jobBindingId) {
        log.warn("[Spider] job-starter 未配置，跳过删除调度任务: {}", jobBindingId);
        // 删除操作降级为静默忽略，避免阻塞任务删除流程
    }
}
