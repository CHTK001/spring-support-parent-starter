package com.chua.starter.job.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.job.support.JobNumberGenerator;
import com.chua.starter.job.support.entity.SysJob;
import com.chua.starter.job.support.scheduler.JobDispatchModeEnum;
import com.chua.starter.job.support.mapper.SysJobMapper;
import com.chua.starter.job.support.scheduler.JobHelper;
import com.chua.starter.job.support.scheduler.JobStorageModeEnum;
import com.chua.starter.job.support.scheduler.LocalJobTrigger;
import com.chua.starter.job.support.scheduler.SchedulerTypeEnum;
import com.chua.starter.job.support.scheduler.TriggerTypeEnum;
import com.chua.starter.job.support.service.JobDynamicConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

/**
 * Job动态配置服务实现类
 *
 * @author CH
 * @since 2024/12/20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobDynamicConfigServiceImpl implements JobDynamicConfigService {

    private static final String DEFAULT_SCHEDULE_TYPE = "CRON";
    private static final String DEFAULT_GLUE_TYPE = "BEAN";
    private static final String DEFAULT_MISFIRE_STRATEGY = "DO_NOTHING";

    private final SysJobMapper sysJobMapper;

    /**
     * 任务状态：停止
     */
    private static final int STATUS_STOP = 0;

    /**
     * 任务状态：运行中
     */
    private static final int STATUS_RUNNING = 1;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer registerOrUpdateJob(String jobName, String cronExpr, String beanName,
                                       String param, String desc, boolean autoStart) {
        requireText(jobName, "任务名称不能为空");
        requireText(beanName, "执行 Bean 名称不能为空");
        requireText(cronExpr, "Cron 表达式不能为空");
        log.info("注册或更新任务: jobName={}, cron={}, bean={}", jobName, cronExpr, beanName);

        SysJob existingJob = getJobByName(jobName);
        if (existingJob != null) {
            // 更新现有任务
            existingJob.setJobScheduleType(DEFAULT_SCHEDULE_TYPE);
            existingJob.setJobScheduleTime(cronExpr.trim());
            existingJob.setJobExecuteBean(beanName.trim());
            existingJob.setJobExecuteParam(param);
            existingJob.setJobDesc(desc);
            if (autoStart || STATUS_RUNNING == safeStatus(existingJob.getJobTriggerStatus())) {
                applyTriggerState(existingJob, true);
            }
            sysJobMapper.updateById(existingJob);
            log.info("任务更新成功: jobId={}, jobName={}", existingJob.getJobId(), jobName);
            return existingJob.getJobId();
        }

        // 创建新任务
        SysJob job = new SysJob();
        job.setJobNo(JobNumberGenerator.nextJobNo());
        job.setJobName(jobName.trim());
        job.setJobScheduleType(DEFAULT_SCHEDULE_TYPE);
        job.setJobScheduleTime(cronExpr.trim());
        job.setJobExecuteBean(beanName.trim());
        job.setJobExecuteParam(param);
        job.setJobDesc(desc);
        job.setJobTriggerStatus(autoStart ? STATUS_RUNNING : STATUS_STOP);
        job.setJobGlueType(DEFAULT_GLUE_TYPE);
        job.setJobGlueUpdatetime(new Date());
        job.setJobFailRetry(0);
        job.setJobExecuteTimeout(0);
        job.setJobExecuteMisfireStrategy(DEFAULT_MISFIRE_STRATEGY);

        applyTriggerState(job, autoStart);
        sysJobMapper.insert(job);
        log.info("任务创建成功: jobId={}, jobName={}", job.getJobId(), jobName);
        return job.getJobId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer createJob(SysJob job) {
        if (job == null) {
            throw new IllegalArgumentException("任务内容不能为空");
        }
        log.info("创建任务: jobName={}", job.getJobName());

        // 设置默认值
        if (job.getJobScheduleType() == null) {
            job.setJobScheduleType(DEFAULT_SCHEDULE_TYPE);
        }
        if (job.getJobGlueType() == null) {
            job.setJobGlueType(DEFAULT_GLUE_TYPE);
        }
        if (!StringUtils.hasText(job.getJobNo())) {
            job.setJobNo(JobNumberGenerator.nextJobNo());
        }
        if (job.getJobGlueUpdatetime() == null) {
            job.setJobGlueUpdatetime(new Date());
        }
        if (job.getJobTriggerStatus() == null) {
            job.setJobTriggerStatus(STATUS_STOP);
        }
        if (job.getJobFailRetry() == null) {
            job.setJobFailRetry(0);
        }
        if (job.getJobExecuteTimeout() == null) {
            job.setJobExecuteTimeout(0);
        }
        if (job.getJobExecuteMisfireStrategy() == null) {
            job.setJobExecuteMisfireStrategy(DEFAULT_MISFIRE_STRATEGY);
        }
        if (job.getJobRetryInterval() == null) {
            job.setJobRetryInterval(0);
        }
        if (!StringUtils.hasText(job.getJobDispatchMode())) {
            job.setJobDispatchMode(JobDispatchModeEnum.LOCAL.name());
        }
        if (!StringUtils.hasText(job.getJobStorageMode())) {
            job.setJobStorageMode(JobStorageModeEnum.DATABASE.name());
        }

        normalizeJob(job);
        applyTriggerState(job, STATUS_RUNNING == safeStatus(job.getJobTriggerStatus()));
        sysJobMapper.insert(job);
        log.info("任务创建成功: jobId={}", job.getJobId());
        return job.getJobId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateJob(SysJob job) {
        if (job == null || job.getJobId() == null) {
            throw new IllegalArgumentException("任务ID不能为空");
        }
        log.info("更新任务: jobId={}", job.getJobId());
        if (StringUtils.hasText(job.getJobScheduleType())) {
            job.setJobScheduleType(normalizeScheduleType(job.getJobScheduleType()));
        }
        if (STATUS_RUNNING == safeStatus(job.getJobTriggerStatus())
                && StringUtils.hasText(job.getJobScheduleType())
                && StringUtils.hasText(job.getJobScheduleTime())) {
            job.setJobTriggerNextTime(calculateNextTriggerTime(job.getJobScheduleType(), job.getJobScheduleTime()));
        } else if (STATUS_STOP == safeStatus(job.getJobTriggerStatus())) {
            resetTriggerState(job);
        }
        return sysJobMapper.updateById(job) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteJob(Integer jobId) {
        log.info("删除任务: jobId={}", jobId);
        // 先停止任务
        stopJob(jobId);
        return sysJobMapper.deleteById(jobId) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteJobByName(String jobName) {
        log.info("根据名称删除任务: jobName={}", jobName);
        SysJob job = getJobByName(jobName);
        if (job == null) {
            return false;
        }
        return deleteJob(job.getJobId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean startJob(Integer jobId) {
        log.info("启动任务: jobId={}", jobId);
        SysJob job = sysJobMapper.selectById(jobId);
        if (job == null) {
            log.warn("任务不存在: jobId={}", jobId);
            return false;
        }

        normalizeJob(job);
        applyTriggerState(job, true);
        return sysJobMapper.updateById(job) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean stopJob(Integer jobId) {
        log.info("停止任务: jobId={}", jobId);
        SysJob job = sysJobMapper.selectById(jobId);
        if (job == null) {
            log.warn("任务不存在: jobId={}", jobId);
            return false;
        }

        job.setJobTriggerStatus(STATUS_STOP);
        resetTriggerState(job);

        return sysJobMapper.updateById(job) > 0;
    }

    @Override
    public SysJob getJobByName(String jobName) {
        return sysJobMapper.selectOne(Wrappers.<SysJob>lambdaQuery()
                .eq(SysJob::getJobName, jobName)
                .last("LIMIT 1"));
    }

    @Override
    public SysJob getJobById(Integer jobId) {
        return sysJobMapper.selectById(jobId);
    }

    @Override
    public SysJob getJobByNo(String jobNo) {
        return sysJobMapper.selectOne(Wrappers.<SysJob>lambdaQuery()
                .eq(SysJob::getJobNo, jobNo)
                .last("LIMIT 1"));
    }

    @Override
    public List<SysJob> getAllJobs() {
        return sysJobMapper.selectList(null);
    }

    @Override
    public List<SysJob> getJobsByBeanName(String beanName) {
        return sysJobMapper.selectList(Wrappers.<SysJob>lambdaQuery()
                .eq(SysJob::getJobExecuteBean, beanName));
    }

    @Override
    public boolean triggerJob(Integer jobId, String param) {
        log.info("手动触发任务: jobId={}, param={}", jobId, param);
        SysJob job = sysJobMapper.selectById(jobId);
        if (job == null) {
            log.warn("任务不存在: jobId={}", jobId);
            return false;
        }
        
        try {
            // 使用传入的参数或任务配置的参数
            String executeParam = param != null ? param : job.getJobExecuteParam();
            // 使用静态方法触发任务，TriggerTypeEnum.API 表示通过 API 手动触发
            LocalJobTrigger.trigger(job.getJobId(), TriggerTypeEnum.API, -1, null, executeParam);
            log.info("任务触发成功: jobId={}", jobId);
            return true;
        } catch (Exception e) {
            log.error("任务触发失败: jobId={}", jobId, e);
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateJobCron(Integer jobId, String cronExpr) {
        requireText(cronExpr, "Cron 表达式不能为空");
        log.info("更新任务Cron: jobId={}, cron={}", jobId, cronExpr);
        SysJob job = sysJobMapper.selectById(jobId);
        if (job == null) {
            return false;
        }

        job.setJobScheduleType(DEFAULT_SCHEDULE_TYPE);
        job.setJobScheduleTime(cronExpr.trim());
        // 如果任务正在运行，重新计算下次执行时间
        if (STATUS_RUNNING == job.getJobTriggerStatus()) {
            long nextTriggerTime = calculateNextTriggerTime(job.getJobScheduleType(), job.getJobScheduleTime());
            job.setJobTriggerNextTime(nextTriggerTime);
        }

        return sysJobMapper.updateById(job) > 0;
    }

    @Override
    public boolean triggerJobByNo(String jobNo, String param) {
        requireText(jobNo, "任务编号不能为空");
        SysJob job = getJobByNo(jobNo.trim());
        if (job == null || job.getJobId() == null) {
            log.warn("任务不存在: jobNo={}", jobNo);
            return false;
        }
        return triggerJob(job.getJobId(), param);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateJobParam(Integer jobId, String param) {
        log.info("更新任务参数: jobId={}, param={}", jobId, param);
        SysJob job = sysJobMapper.selectById(jobId);
        if (job == null) {
            return false;
        }
        
        job.setJobExecuteParam(param);
        return sysJobMapper.updateById(job) > 0;
    }

    /**
     * 计算下次执行时间
     *
     * @param scheduleType 调度类型
     * @param scheduleTime 调度配置
     * @return 下次执行时间戳
     */
    private long calculateNextTriggerTime(String scheduleType, String scheduleTime) {
        try {
            SysJob job = new SysJob();
            job.setJobScheduleType(normalizeScheduleType(scheduleType));
            job.setJobScheduleTime(scheduleTime == null ? null : scheduleTime.trim());
            Date nextValidTime = JobHelper.generateNextValidTime(job, new Date());
            if (nextValidTime != null) {
                return nextValidTime.getTime();
            }
        } catch (Exception e) {
            log.error("计算下次执行时间失败: scheduleType={}, scheduleTime={}", scheduleType, scheduleTime, e);
            throw new IllegalArgumentException("无效的调度配置: type=" + scheduleType + ", time=" + scheduleTime, e);
        }
        throw new IllegalArgumentException("无效的调度配置: type=" + scheduleType + ", time=" + scheduleTime);
    }

    private void applyTriggerState(SysJob job, boolean running) {
        if (running) {
            requireText(job.getJobScheduleTime(), "运行中的任务必须配置调度时间");
            job.setJobTriggerStatus(STATUS_RUNNING);
            job.setJobTriggerNextTime(calculateNextTriggerTime(job.getJobScheduleType(), job.getJobScheduleTime()));
            if (job.getJobTriggerLastTime() == null) {
                job.setJobTriggerLastTime(0L);
            }
            return;
        }
        job.setJobTriggerStatus(STATUS_STOP);
        resetTriggerState(job);
    }

    private void normalizeJob(SysJob job) {
        if (StringUtils.hasText(job.getJobName())) {
            job.setJobName(job.getJobName().trim());
        }
        if (StringUtils.hasText(job.getJobNo())) {
            job.setJobNo(job.getJobNo().trim());
        }
        if (StringUtils.hasText(job.getJobExecuteBean())) {
            job.setJobExecuteBean(job.getJobExecuteBean().trim());
        }
        if (StringUtils.hasText(job.getJobScheduleTime())) {
            job.setJobScheduleTime(job.getJobScheduleTime().trim());
        }
        if (StringUtils.hasText(job.getJobDispatchMode())) {
            job.setJobDispatchMode(JobDispatchModeEnum.match(job.getJobDispatchMode()).name());
        }
        if (StringUtils.hasText(job.getJobStorageMode())) {
            job.setJobStorageMode(JobStorageModeEnum.match(job.getJobStorageMode()).name());
        }
        job.setJobScheduleType(normalizeScheduleType(job.getJobScheduleType()));
    }

    private String normalizeScheduleType(String scheduleType) {
        if (!StringUtils.hasText(scheduleType)) {
            return DEFAULT_SCHEDULE_TYPE;
        }
        SchedulerTypeEnum matched = SchedulerTypeEnum.match(scheduleType, null);
        if (matched == null) {
            throw new IllegalArgumentException("不支持的调度类型: " + scheduleType);
        }
        return matched.name();
    }

    private void resetTriggerState(SysJob job) {
        job.setJobTriggerLastTime(0L);
        job.setJobTriggerNextTime(0L);
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private int safeStatus(Integer triggerStatus) {
        return triggerStatus == null ? STATUS_STOP : triggerStatus;
    }
}
