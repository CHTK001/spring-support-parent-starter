package com.chua.starter.job.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.job.support.entity.MonitorJob;
import com.chua.starter.job.support.mapper.MonitorJobMapper;
import com.chua.starter.job.support.scheduler.LocalJobTrigger;
import com.chua.starter.job.support.scheduler.TriggerTypeEnum;
import com.chua.starter.job.support.service.JobDynamicConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final MonitorJobMapper monitorJobMapper;

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
        log.info("注册或更新任务: jobName={}, cron={}, bean={}", jobName, cronExpr, beanName);

        MonitorJob existingJob = getJobByName(jobName);
        if (existingJob != null) {
            // 更新现有任务
            existingJob.setJobScheduleTime(cronExpr);
            existingJob.setJobExecuteBean(beanName);
            existingJob.setJobExecuteParam(param);
            existingJob.setJobDesc(desc);
            if (autoStart && existingJob.getJobTriggerStatus() != STATUS_RUNNING) {
                existingJob.setJobTriggerStatus(STATUS_RUNNING);
            }
            monitorJobMapper.updateById(existingJob);
            log.info("任务更新成功: jobId={}, jobName={}", existingJob.getJobId(), jobName);
            return existingJob.getJobId();
        }

        // 创建新任务
        MonitorJob job = new MonitorJob();
        job.setJobName(jobName);
        job.setJobScheduleType("cron");
        job.setJobScheduleTime(cronExpr);
        job.setJobExecuteBean(beanName);
        job.setJobExecuteParam(param);
        job.setJobDesc(desc);
        job.setJobTriggerStatus(autoStart ? STATUS_RUNNING : STATUS_STOP);
        job.setJobGlueType("BEAN");
        job.setJobGlueUpdatetime(new Date());
        job.setJobFailRetry(0);
        job.setJobExecuteTimeout(0);
        job.setJobExecuteMisfireStrategy("DO_NOTHING");

        monitorJobMapper.insert(job);
        log.info("任务创建成功: jobId={}, jobName={}", job.getJobId(), jobName);
        return job.getJobId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer createJob(MonitorJob job) {
        log.info("创建任务: jobName={}", job.getJobName());
        
        // 设置默认值
        if (job.getJobScheduleType() == null) {
            job.setJobScheduleType("cron");
        }
        if (job.getJobGlueType() == null) {
            job.setJobGlueType("BEAN");
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
            job.setJobExecuteMisfireStrategy("DO_NOTHING");
        }

        monitorJobMapper.insert(job);
        log.info("任务创建成功: jobId={}", job.getJobId());
        return job.getJobId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateJob(MonitorJob job) {
        log.info("更新任务: jobId={}", job.getJobId());
        return monitorJobMapper.updateById(job) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteJob(Integer jobId) {
        log.info("删除任务: jobId={}", jobId);
        // 先停止任务
        stopJob(jobId);
        return monitorJobMapper.deleteById(jobId) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteJobByName(String jobName) {
        log.info("根据名称删除任务: jobName={}", jobName);
        MonitorJob job = getJobByName(jobName);
        if (job == null) {
            return false;
        }
        return deleteJob(job.getJobId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean startJob(Integer jobId) {
        log.info("启动任务: jobId={}", jobId);
        MonitorJob job = monitorJobMapper.selectById(jobId);
        if (job == null) {
            log.warn("任务不存在: jobId={}", jobId);
            return false;
        }
        
        job.setJobTriggerStatus(STATUS_RUNNING);
        // 计算下次执行时间
        long nextTriggerTime = calculateNextTriggerTime(job.getJobScheduleType(), job.getJobScheduleTime());
        job.setJobTriggerNextTime(nextTriggerTime);
        
        return monitorJobMapper.updateById(job) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean stopJob(Integer jobId) {
        log.info("停止任务: jobId={}", jobId);
        MonitorJob job = monitorJobMapper.selectById(jobId);
        if (job == null) {
            log.warn("任务不存在: jobId={}", jobId);
            return false;
        }
        
        job.setJobTriggerStatus(STATUS_STOP);
        job.setJobTriggerLastTime(0L);
        job.setJobTriggerNextTime(0L);
        
        return monitorJobMapper.updateById(job) > 0;
    }

    @Override
    public MonitorJob getJobByName(String jobName) {
        return monitorJobMapper.selectOne(Wrappers.<MonitorJob>lambdaQuery()
                .eq(MonitorJob::getJobName, jobName)
                .last("LIMIT 1"));
    }

    @Override
    public MonitorJob getJobById(Integer jobId) {
        return monitorJobMapper.selectById(jobId);
    }

    @Override
    public List<MonitorJob> getAllJobs() {
        return monitorJobMapper.selectList(null);
    }

    @Override
    public List<MonitorJob> getJobsByBeanName(String beanName) {
        return monitorJobMapper.selectList(Wrappers.<MonitorJob>lambdaQuery()
                .eq(MonitorJob::getJobExecuteBean, beanName));
    }

    @Override
    public boolean triggerJob(Integer jobId, String param) {
        log.info("手动触发任务: jobId={}, param={}", jobId, param);
        MonitorJob job = monitorJobMapper.selectById(jobId);
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
        log.info("更新任务Cron: jobId={}, cron={}", jobId, cronExpr);
        MonitorJob job = monitorJobMapper.selectById(jobId);
        if (job == null) {
            return false;
        }
        
        job.setJobScheduleTime(cronExpr);
        // 如果任务正在运行，重新计算下次执行时间
        if (STATUS_RUNNING == job.getJobTriggerStatus()) {
            long nextTriggerTime = calculateNextTriggerTime(job.getJobScheduleType(), cronExpr);
            job.setJobTriggerNextTime(nextTriggerTime);
        }
        
        return monitorJobMapper.updateById(job) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateJobParam(Integer jobId, String param) {
        log.info("更新任务参数: jobId={}, param={}", jobId, param);
        MonitorJob job = monitorJobMapper.selectById(jobId);
        if (job == null) {
            return false;
        }
        
        job.setJobExecuteParam(param);
        return monitorJobMapper.updateById(job) > 0;
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
            if ("cron".equalsIgnoreCase(scheduleType)) {
                // 使用Spring的CronExpression计算
                org.springframework.scheduling.support.CronExpression cron = 
                        org.springframework.scheduling.support.CronExpression.parse(scheduleTime);
                java.time.LocalDateTime next = cron.next(java.time.LocalDateTime.now());
                if (next != null) {
                    return next.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                }
            } else if ("fixed".equalsIgnoreCase(scheduleType)) {
                // 固定间隔
                long interval = Long.parseLong(scheduleTime);
                return System.currentTimeMillis() + interval;
            }
        } catch (Exception e) {
            log.error("计算下次执行时间失败: scheduleType={}, scheduleTime={}", scheduleType, scheduleTime, e);
        }
        return 0L;
    }
}
