package com.chua.starter.spider.support.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chua.starter.spider.support.domain.SpiderExecutionPolicy;
import com.chua.starter.spider.support.domain.SpiderJobBinding;
import com.chua.starter.spider.support.domain.SpiderTaskDefinition;
import com.chua.starter.spider.support.domain.enums.SpiderTaskStatus;
import com.chua.starter.spider.support.repository.SpiderJobBindingRepository;
import com.chua.starter.spider.support.repository.SpiderTaskRepository;
import com.chua.starter.spider.support.service.SpiderScheduledJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * 爬虫任务调度绑定服务实现。
 *
 * <p>通过 {@link JobStarterClient} 与 job-starter 交互，
 * 若 job-starter 不可用则使用 {@link NoOpJobStarterClient} 降级。</p>
 *
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpiderScheduledJobServiceImpl implements SpiderScheduledJobService {

    private final SpiderTaskRepository taskRepository;
    private final SpiderJobBindingRepository jobBindingRepository;
    private final JobStarterClient jobStarterClient;

    // ── 8.1 registerJob ──────────────────────────────────────────────────────

    @Override
    public void registerJob(Long taskId) {
        SpiderTaskDefinition task = requireTask(taskId);
        SpiderExecutionPolicy policy = parsePolicy(task);

        String cron = policy != null ? policy.getCron() : null;
        String jobChannel = policy != null ? policy.getJobChannel() : null;

        if (cron == null || cron.isBlank()) {
            throw new IllegalStateException("任务 [" + taskId + "] 缺少 cron 表达式，无法注册调度");
        }

        String jobName = buildJobName(taskId);
        String param = String.valueOf(taskId);
        String desc = "Spider 任务调度: " + task.getTaskName();

        try {
            String jobBindingId = jobStarterClient.registerJob(jobName, cron, jobChannel, param, desc);

            SpiderJobBinding binding = SpiderJobBinding.builder()
                    .taskId(taskId)
                    .jobBindingId(jobBindingId)
                    .jobChannel(jobChannel)
                    .active(true)
                    .build();
            jobBindingRepository.save(binding);

            log.info("[Spider] 调度任务注册成功, taskId={}, jobBindingId={}", taskId, jobBindingId);
        } catch (Exception e) {
            // 8.4 失败回滚：恢复任务状态
            rollbackTaskStatus(task, SpiderTaskStatus.READY);
            throw new IllegalStateException("注册调度任务失败，已回滚任务状态: " + e.getMessage(), e);
        }
    }

    // ── 8.2 pauseJob / resumeJob ─────────────────────────────────────────────

    @Override
    public void pauseJob(Long taskId) {
        SpiderJobBinding binding = requireBinding(taskId);
        try {
            jobStarterClient.pauseJob(binding.getJobBindingId());
            log.info("[Spider] 调度任务已暂停, taskId={}, jobBindingId={}", taskId, binding.getJobBindingId());
        } catch (Exception e) {
            throw new IllegalStateException("暂停调度任务失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void resumeJob(Long taskId) {
        SpiderJobBinding binding = requireBinding(taskId);
        try {
            jobStarterClient.resumeJob(binding.getJobBindingId());
            log.info("[Spider] 调度任务已恢复, taskId={}, jobBindingId={}", taskId, binding.getJobBindingId());
        } catch (Exception e) {
            throw new IllegalStateException("恢复调度任务失败: " + e.getMessage(), e);
        }
    }

    // ── deleteJob ─────────────────────────────────────────────────────────────

    @Override
    public void deleteJob(Long taskId) {
        jobBindingRepository.findActiveByTaskId(taskId).ifPresent(binding -> {
            try {
                jobStarterClient.deleteJob(binding.getJobBindingId());
                log.info("[Spider] 调度任务已删除, taskId={}, jobBindingId={}", taskId, binding.getJobBindingId());
            } catch (Exception e) {
                log.warn("[Spider] 删除调度任务失败（继续清理本地绑定）, taskId={}, error={}", taskId, e.getMessage());
            }
        });
        jobBindingRepository.deleteByTaskId(taskId);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private SpiderTaskDefinition requireTask(Long taskId) {
        SpiderTaskDefinition task = taskRepository.getById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务 [" + taskId + "] 不存在");
        }
        return task;
    }

    private SpiderJobBinding requireBinding(Long taskId) {
        return jobBindingRepository.findActiveByTaskId(taskId)
                .orElseThrow(() -> new IllegalStateException("任务 [" + taskId + "] 未找到有效的调度绑定"));
    }

    private SpiderExecutionPolicy parsePolicy(SpiderTaskDefinition task) {
        if (task.getExecutionPolicy() == null || task.getExecutionPolicy().isBlank()) {
            return null;
        }
        try {
            return JSON.parseObject(task.getExecutionPolicy(), SpiderExecutionPolicy.class);
        } catch (Exception e) {
            log.warn("[Spider] 解析执行策略失败, taskId={}", task.getId(), e);
            return null;
        }
    }

    private void rollbackTaskStatus(SpiderTaskDefinition task, SpiderTaskStatus targetStatus) {
        try {
            task.setStatus(targetStatus);
            taskRepository.updateById(task);
        } catch (Exception ex) {
            log.error("[Spider] 回滚任务状态失败, taskId={}", task.getId(), ex);
        }
    }

    private String buildJobName(Long taskId) {
        return "spider-task-" + taskId;
    }
}
