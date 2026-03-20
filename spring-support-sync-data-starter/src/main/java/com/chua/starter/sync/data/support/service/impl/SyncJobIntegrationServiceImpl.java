package com.chua.starter.sync.data.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.core.utils.StringUtils;
import com.chua.common.support.text.json.Json;
import com.chua.starter.job.support.entity.SysJob;
import com.chua.starter.job.support.mapper.SysJobMapper;
import com.chua.starter.job.support.scheduler.JobHelper;
import com.chua.starter.job.support.scheduler.LocalJobTrigger;
import com.chua.starter.job.support.scheduler.TriggerTypeEnum;
import com.chua.starter.sync.data.support.entity.MonitorSyncTask;
import com.chua.starter.sync.data.support.mapper.MonitorSyncTaskMapper;
import com.chua.starter.sync.data.support.properties.SyncJobIntegrationProperties;
import com.chua.starter.sync.data.support.service.sync.SyncJobIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Sync 与 Job 调度集成服务实现
 *
 * @author CH
 * @since 2026/03/19
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(SysJobMapper.class)
@ConditionalOnProperty(prefix = SyncJobIntegrationProperties.PRE, name = "enabled", havingValue = "true")
public class SyncJobIntegrationServiceImpl implements SyncJobIntegrationService {

    private static final String EXECUTE_BEAN = "syncTaskJobHandler";

    private final SysJobMapper sysJobMapper;
    private final MonitorSyncTaskMapper taskMapper;
    private final SyncJobIntegrationProperties properties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer createOrUpdateJob(MonitorSyncTask task) {
        if (task == null || task.getSyncTaskId() == null) {
            throw new IllegalArgumentException("同步任务不能为空");
        }

        SysJob existingJob = findJobByTaskId(task.getSyncTaskId());
        SysJob job = existingJob != null ? existingJob : new SysJob();

        String scheduleType = resolveScheduleType(task);
        job.setJobName(buildJobName(task));
        job.setJobScheduleType(scheduleType);
        job.setJobScheduleTime(resolveScheduleTime(task));
        job.setJobExecuteBean(EXECUTE_BEAN);
        job.setJobExecuteParam(buildExecuteParam(task.getSyncTaskId()));
        job.setJobDesc(StringUtils.isNotEmpty(task.getSyncTaskDesc())
                ? task.getSyncTaskDesc()
                : "同步任务: " + task.getSyncTaskName());
        job.setJobGlueType("BEAN");
        job.setJobGlueUpdatetime(new Date());
        job.setJobFailRetry(properties.isUseJobRetry()
                ? defaultIfNull(task.getSyncTaskRetryCount(), 0)
                : 0);
        job.setJobExecuteTimeout(resolveTimeoutSeconds(task));
        job.setJobExecuteMisfireStrategy("DO_NOTHING");

        if (existingJob == null) {
            job.setJobTriggerStatus(isSchedulable(task) && "RUNNING".equals(task.getSyncTaskStatus()) ? 1 : 0);
            job.setJobTriggerLastTime(0L);
            job.setJobTriggerNextTime(calculateNextTriggerTime(job));
            sysJobMapper.insert(job);
            log.info("创建 Sync 对应 Job 成功, taskId={}, jobId={}", task.getSyncTaskId(), job.getJobId());
            return job.getJobId();
        }

        if (!isSchedulable(task)) {
            job.setJobTriggerStatus(0);
            job.setJobTriggerLastTime(0L);
            job.setJobTriggerNextTime(0L);
        } else if (existingJob.getJobTriggerStatus() != null && existingJob.getJobTriggerStatus() == 1) {
            job.setJobTriggerNextTime(calculateNextTriggerTime(job));
        }

        sysJobMapper.updateById(job);
        log.info("更新 Sync 对应 Job 成功, taskId={}, jobId={}", task.getSyncTaskId(), job.getJobId());
        return job.getJobId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean startJob(Long taskId) {
        MonitorSyncTask task = taskMapper.selectCompatibleById(taskId);
        if (task == null) {
            return false;
        }

        Integer jobId = createOrUpdateJob(task);
        if (!isSchedulable(task)) {
            log.info("同步任务未配置调度信息，仅保留 Job 配置不启动调度, taskId={}, jobId={}", taskId, jobId);
            return true;
        }

        SysJob job = sysJobMapper.selectById(jobId);
        if (job == null) {
            return false;
        }

        job.setJobTriggerStatus(1);
        job.setJobTriggerLastTime(0L);
        job.setJobTriggerNextTime(calculateNextTriggerTime(job));
        return sysJobMapper.updateById(job) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean stopJob(Long taskId) {
        SysJob job = findJobByTaskId(taskId);
        if (job == null) {
            return true;
        }

        job.setJobTriggerStatus(0);
        job.setJobTriggerLastTime(0L);
        job.setJobTriggerNextTime(0L);
        return sysJobMapper.updateById(job) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteJob(Long taskId) {
        SysJob job = findJobByTaskId(taskId);
        if (job == null) {
            return true;
        }
        return sysJobMapper.deleteById(job.getJobId()) > 0;
    }

    @Override
    public boolean triggerJob(Long taskId) {
        MonitorSyncTask task = taskMapper.selectCompatibleById(taskId);
        if (task == null) {
            return false;
        }

        Integer jobId = createOrUpdateJob(task);
        if (jobId == null) {
            return false;
        }

        LocalJobTrigger.trigger(jobId, TriggerTypeEnum.API, -1, null, buildExecuteParam(taskId));
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncTaskStatus(Long taskId) {
        MonitorSyncTask task = taskMapper.selectCompatibleById(taskId);
        if (task == null) {
            return;
        }

        SysJob job = findJobByTaskId(taskId);
        if (job == null) {
            return;
        }

        if (properties.isSyncStatus()) {
            task.setSyncTaskStatus(job.getJobTriggerStatus() != null && job.getJobTriggerStatus() == 1
                    ? "RUNNING"
                    : "STOPPED");
            taskMapper.updateById(task);
        }
    }

    @Override
    public Integer getJobId(Long taskId) {
        SysJob job = findJobByTaskId(taskId);
        return job != null ? job.getJobId() : null;
    }

    private SysJob findJobByTaskId(Long taskId) {
        SysJob job = sysJobMapper.selectOne(Wrappers.<SysJob>lambdaQuery()
                .eq(SysJob::getJobExecuteBean, EXECUTE_BEAN)
                .eq(SysJob::getJobExecuteParam, buildExecuteParam(taskId))
                .last("LIMIT 1"));
        if (job != null) {
            return job;
        }

        return sysJobMapper.selectOne(Wrappers.<SysJob>lambdaQuery()
                .eq(SysJob::getJobExecuteBean, EXECUTE_BEAN)
                .eq(SysJob::getJobExecuteParam, String.valueOf(taskId))
                .last("LIMIT 1"));
    }

    private String buildJobName(MonitorSyncTask task) {
        String taskName = StringUtils.isNotEmpty(task.getSyncTaskName()) ? task.getSyncTaskName().trim() : "unnamed";
        String name = properties.getJobNamePrefix() + task.getSyncTaskId() + "_" + taskName;
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    private String resolveScheduleType(MonitorSyncTask task) {
        if (StringUtils.isNotEmpty(task.getSyncTaskCron())) {
            return "CRON";
        }
        if (task.getSyncTaskSyncInterval() != null && task.getSyncTaskSyncInterval() > 0) {
            return "FIXED_MS";
        }
        return "NONE";
    }

    private String resolveScheduleTime(MonitorSyncTask task) {
        if (StringUtils.isNotEmpty(task.getSyncTaskCron())) {
            return task.getSyncTaskCron();
        }
        if (task.getSyncTaskSyncInterval() != null && task.getSyncTaskSyncInterval() > 0) {
            return String.valueOf(task.getSyncTaskSyncInterval());
        }
        return null;
    }

    private boolean isSchedulable(MonitorSyncTask task) {
        return StringUtils.isNotEmpty(task.getSyncTaskCron())
                || (task.getSyncTaskSyncInterval() != null && task.getSyncTaskSyncInterval() > 0);
    }

    private Integer resolveTimeoutSeconds(MonitorSyncTask task) {
        Long timeout = task.getSyncTaskConsumeTimeout();
        if (timeout == null || timeout <= 0) {
            return 0;
        }
        return (int) Math.ceil(timeout / 1000D);
    }

    private int defaultIfNull(Integer value, int defaultValue) {
        return value != null ? value : defaultValue;
    }

    private long calculateNextTriggerTime(SysJob job) {
        try {
            if ("NONE".equalsIgnoreCase(job.getJobScheduleType())) {
                return 0L;
            }
            Date next = JobHelper.generateNextValidTime(job, new Date());
            return next != null ? next.getTime() : 0L;
        } catch (Exception e) {
            log.warn("计算 Sync Job 下次触发时间失败, jobId={}, scheduleType={}, scheduleTime={}",
                    job.getJobId(), job.getJobScheduleType(), job.getJobScheduleTime(), e);
            return 0L;
        }
    }

    private String buildExecuteParam(Long taskId) {
        Map<String, Object> param = new HashMap<>();
        param.put("taskId", taskId);
        return Json.toJson(param);
    }
}
