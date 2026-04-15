package com.chua.starter.spider.support.service.impl;

import com.chua.starter.job.support.entity.SysJob;
import com.chua.starter.job.support.service.JobDynamicConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * 基于 job-starter {@link JobDynamicConfigService} 的客户端实现。
 *
 * <p>仅在 job-starter 可用时装配。</p>
 *
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(name = "com.chua.starter.job.support.service.JobDynamicConfigService")
public class JobDynamicConfigServiceClient implements JobStarterClient {

    private final JobDynamicConfigService jobDynamicConfigService;

    @Override
    public String registerJob(String jobName, String cron, String jobChannel, String param, String desc) {
        Integer jobId = jobDynamicConfigService.registerOrUpdateJob(
                jobName, cron, "spiderJobHandler", param, desc, true);
        if (jobId == null) {
            throw new IllegalStateException("job-starter 注册任务失败，返回 null jobId");
        }
        return String.valueOf(jobId);
    }

    @Override
    public void pauseJob(String jobBindingId) {
        boolean success = jobDynamicConfigService.stopJob(parseJobId(jobBindingId));
        if (!success) {
            throw new IllegalStateException("job-starter 暂停任务失败, jobBindingId=" + jobBindingId);
        }
    }

    @Override
    public void resumeJob(String jobBindingId) {
        boolean success = jobDynamicConfigService.startJob(parseJobId(jobBindingId));
        if (!success) {
            throw new IllegalStateException("job-starter 恢复任务失败, jobBindingId=" + jobBindingId);
        }
    }

    @Override
    public void deleteJob(String jobBindingId) {
        boolean success = jobDynamicConfigService.deleteJob(parseJobId(jobBindingId));
        if (!success) {
            log.warn("[Spider] job-starter 删除任务返回 false, jobBindingId={}", jobBindingId);
        }
    }

    private int parseJobId(String jobBindingId) {
        try {
            return Integer.parseInt(jobBindingId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无效的 jobBindingId: " + jobBindingId, e);
        }
    }
}
