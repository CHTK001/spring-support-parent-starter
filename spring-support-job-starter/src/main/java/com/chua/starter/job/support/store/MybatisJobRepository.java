package com.chua.starter.job.support.store;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.task.scheduler.CronExpression;
import com.chua.starter.job.support.dispatch.JobDefinition;
import com.chua.starter.job.support.dispatch.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * MyBatis-Plus 版 {@link JobRepository}, 从 {@code sys_job} 表读取任务。
 *
 * <h3>查询策略</h3>
 * <ol>
 *   <li>按 {@code sys_job_trigger_status=1, sys_job_is_deleted=0, dispatchMode=LOCAL 或 NULL} 粗筛;</li>
 *   <li>加载候选后用 {@link CronExpression} 计算下一次触发时间, 与 {@code now + preReadMs} 比较;</li>
 *   <li>返回到期的 JobDefinition 列表给 LeaderLoop。</li>
 * </ol>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RequiredArgsConstructor
public class MybatisJobRepository implements JobRepository {

    /**
     * 任务 Mapper
     */
    private final JobMapper jobMapper;

    @Override
    public List<JobDefinition> findDue(long now, long preReadMs, int preReadCount) {
        List<JobEntity> candidates = jobMapper.selectList(
                Wrappers.<JobEntity>lambdaQuery()
                        .eq(JobEntity::getSysJobTriggerStatus, 1)
                        .eq(JobEntity::getSysJobIsDeleted, 0)
                        .and(w -> w.isNull(JobEntity::getSysJobDispatchMode)
                                .or()
                                .eq(JobEntity::getSysJobDispatchMode, "LOCAL"))
                        .last("LIMIT " + Math.max(preReadCount, 1))
        );

        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<JobDefinition> due = new ArrayList<>();
        long upperBound = now + preReadMs;
        for (JobEntity entity : candidates) {
            long nextFire = nextFireTime(entity, now);
            if (nextFire < 0L) {
                continue;
            }
            if (nextFire <= upperBound) {
                due.add(toDefinition(entity));
            }
        }
        if (due.size() > preReadCount) {
            return new ArrayList<>(due.subList(0, preReadCount));
        }
        return due;
    }

    /**
     * 计算下次触发时间。
     *
     * @param entity 任务实体
     * @param now    当前时间戳
     * @return 下次触发时间, cron 解析失败返回 -1
     */
    private long nextFireTime(JobEntity entity, long now) {
        String cron = entity.getSysJobScheduleTime();
        if (cron == null || cron.isBlank()) {
            return -1L;
        }
        try {
            CronExpression expression = new CronExpression(cron);
            java.util.Date next = expression.getNextValidTimeAfter(new java.util.Date(now));
            return next == null ? -1L : next.getTime();
        } catch (Exception ex) {
            log.warn("[job] cron 解析失败 sysJobId={} cron={} cause={}",
                    entity.getSysJobId(), cron, ex.getMessage());
            return -1L;
        }
    }

    /**
     * 实体转内存定义。
     *
     * @param entity 实体
     * @return 定义
     */
    private JobDefinition toDefinition(JobEntity entity) {
        JobDefinition definition = new JobDefinition();
        definition.setName(entity.getSysJobExecuteBean() != null
                ? entity.getSysJobExecuteBean()
                : entity.getSysJobName());
        definition.setCron(entity.getSysJobScheduleTime());
        definition.setEnabled(entity.getSysJobTriggerStatus() != null
                && entity.getSysJobTriggerStatus() == 1);
        definition.setDesc(entity.getSysJobDesc());
        return definition;
    }
}