package com.chua.starter.job.support.store;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.job.support.dispatch.JobDefinition;
import com.chua.starter.job.support.dispatch.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
   * MyBatis-Plus 版 {@link JobRepository}, 从 {@code sys_job} 表读取任务。
 *
 * <h3>查询策略</h3>
 * <ol>
 *   <li>按 {@code sys_job_trigger_status=1, sys_job_is_deleted=0, dispatchMode=LOCAL 或 NULL} 粗筛;</li>
 *   <li>加载候选后用 {@link CronExpression} 计算下一次触发时间, 与 {@code now + preReadMs} 比较;</li>
 *   <li>若 {@code now - lastFireMs} 超过一个周期, 补全错过的触发 (misfire),
   * 仍返回单个 作业definition (铅 一次性 发布, 由 执行器 串行执行).</li>
 *   <li>返回到期的 JobDefinition 列表给 LeaderLoop。</li>
 * </ol>
 *
 * <p><b>misfire 补跳:</b> 仅在内存 {@link #lastFireMs} 中记录每个任务的最近一次
   * 铅循环 通知过的下次触发时间, 若当前 {@code nextFire} 早于 {@code lastFire},
 * 仍视作到期 (即漏掉的那次依然发布, 让上层补跑)。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RequiredArgsConstructor
public class MybatisJobRepository implements JobRepository {

    /**
      * 单实例内存记录每个 作业 上一次已发布的 下一个firems (用于 misfire 去重与补跳决策).
     */
    private final ConcurrentMap<String, Long> lastFireMs = new ConcurrentHashMap<>();

    /**
      * 触发器缓存, 钥匙 为 执行Bean/名称 (复用固定序列推进)
     */
    private final ConcurrentMap<String, com.chua.common.support.task.scheduler.Trigger> triggerCache = new ConcurrentHashMap<>();

    /**
      * 任务 映射器
     */
    private final JobMapper jobMapper;

    @Override
    /** 查找Due */
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
            String key = entity.getSysJobExecuteBean() != null
                    ? entity.getSysJobExecuteBean()
                    : entity.getSysJobName();
            long nextFire = nextFireMs(entity, key, now);
            if (nextFire < 0L) {
                continue;
            }
            if (nextFire <= upperBound) {
 // 简单 misfire 补跳: 若内存中记录的上一轮 下一个firems 早于本次 (说明期间错过),
                // 仅补发最后一个被漏掉的 (而不是把所有漏的循环补发, 避免风暴)。
                Long previous = lastFireMs.get(key);
                if (previous != null && previous < nextFire && now - nextFire > 1000L) {
                    log.warn("[job] misfire detected job={} expected={} now={} 补发 1 次",
                            key, nextFire, now);
 // 不修改 下一个fire, 让 铅 正常 发布 一次; 配合 执行器 的幂等去重避免重复执行。
                }
                due.add(toDefinition(entity));
                lastFireMs.put(key, nextFire);
            } else if (lastFireMs.containsKey(key)) {
 // 周期已过 (大写bound 不命中), 同时 最后一个 已存在, 重置避免内存膨胀
                lastFireMs.remove(key);
            }
        }
        if (due.size() > preReadCount) {
            return new ArrayList<>(due.subList(0, preReadCount));
        }
        return due;
    }

    /**
     * 将注解扫描出的任务定义同步到 {@code sys_job} 表 (按 {@code sys_job_execute_bean} 唯一。
     * 已存在则更新调度配置, 不存在则新建触发状态=启用 的本地任务行)。
     */
    @Override
    public void upsert(JobDefinition definition) {
        if (definition == null || definition.getName() == null) {
            return;
        }
        try {
            JobEntity existing = jobMapper.selectOne(Wrappers.<JobEntity>lambdaQuery()
                    .eq(JobEntity::getSysJobExecuteBean, definition.getName()));
            if (existing == null) {
                JobEntity entity = toEntity(definition);
                jobMapper.insert(entity);
                log.info("[job] 注解任务持久化成功 name={}", definition.getName());
            } else {
                // 已存在: 仅当调度配置变化时更新, 避免无意义写库
                JobEntity candidate = toEntity(definition);
                if (!java.util.Objects.equals(candidate.getSysJobScheduleType(), existing.getSysJobScheduleType())
                        || !java.util.Objects.equals(candidate.getSysJobScheduleTime(), existing.getSysJobScheduleTime())) {
                    if (existing.getSysJobNo() == null || existing.getSysJobNo().isBlank()) {
                        existing.setSysJobNo(definition.getName());
                    }
                    existing.setSysJobScheduleType(candidate.getSysJobScheduleType());
                    existing.setSysJobScheduleTime(candidate.getSysJobScheduleTime());
                    existing.setSysJobName(definition.getName());
                    existing.setSysJobDesc(definition.getDesc());
                    existing.setSysJobTriggerStatus(1);
                    existing.setSysJobIsDeleted(0);
                    jobMapper.updateById(existing);
                    log.info("[job] 注解任务调度配置更新 name={} type={} time={}",
                            definition.getName(), candidate.getSysJobScheduleType(),
                            candidate.getSysJobScheduleTime());
                }
            }
        } catch (Exception ex) {
            log.warn("[job] 注解任务持久化失败 name={} cause={}", definition.getName(), ex.getMessage());
        }
    }

    /**
      * 将 作业definition 转换为待持久化的实体。注解模式下无 sys_作业_no, 用 名称 作为 sys_作业_no 以满足 NOT 空 约束。
     *
     * @param definition 任务定义
     * @return 实体
     */
    private JobEntity toEntity(JobDefinition definition) {
        JobEntity entity = new JobEntity();
        entity.setSysJobNo(definition.getName());
        entity.setSysJobName(definition.getName());
        entity.setSysJobExecuteBean(definition.getName());
 // 根据调度配置推导 调度_类型 / 调度_时间 (cron > fixed延迟 > fixedrate)
        if (isNotBlank(definition.getCron())) {
            entity.setSysJobScheduleType("CRON");
            entity.setSysJobScheduleTime(definition.getCron());
        } else if (definition.getFixedDelayMs() > 0L) {
            entity.setSysJobScheduleType("DELAY");
            entity.setSysJobScheduleTime(String.valueOf(definition.getFixedDelayMs()));
        } else if (definition.getFixedRateMs() > 0L) {
            entity.setSysJobScheduleType("FIXED");
            entity.setSysJobScheduleTime(String.valueOf(definition.getFixedRateMs()));
        } else {
            entity.setSysJobScheduleType("NONE");
        }
        entity.setSysJobTriggerStatus(1);
        entity.setSysJobIsDeleted(0);
        entity.setSysJobDesc(definition.getDesc());
        entity.setSysJobGlueType("BEAN");
        return entity;
    }

    /**
     * 计算下一次触发时间 (委托 {@link com.chua.starter.job.support.dispatch.JobTriggering})。
     *
     * @param entity 任务实体
     * @param key    唯一 钥匙 (执行Bean/名称), 用于缓存触发器
     * @param now    当前时间戳
     * @return 下次触发时间, 未配置或计算失败返回 -1
     */
    private long nextFireMs(JobEntity entity, String key, long now) {
        JobDefinition definition = toDefinition(entity);
        com.chua.common.support.task.scheduler.Trigger trigger = triggerCache.get(key);
        if (trigger == null) {
            trigger = com.chua.starter.job.support.dispatch.JobTriggering.createTrigger(definition);
            if (trigger == null) {
                return -1L;
            }
            triggerCache.put(key, trigger);
        }
        Long last = lastFireMs.get(key);
        return com.chua.starter.job.support.dispatch.JobTriggering.nextFireMs(
                definition, trigger, last == null ? 0L : last, now);
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
        definition.setEnabled(entity.getSysJobTriggerStatus() != null
                && entity.getSysJobTriggerStatus() == 1);
        definition.setDesc(entity.getSysJobDesc());
 // 根据 调度_类型 还原调度配置
        String type = entity.getSysJobScheduleType();
        String time = entity.getSysJobScheduleTime();
        if ("CRON".equals(type)) {
            definition.setCron(time);
        } else if ("DELAY".equals(type)) {
            definition.setFixedDelayMs(parseMs(time));
        } else if ("FIXED".equals(type)) {
            definition.setFixedRateMs(parseMs(time));
        } else if (isNotBlank(time)) {
 // 兼容历史脏数据: 无 调度_类型 但 调度_时间 非空, 视作 cron
            definition.setCron(time);
        }
        return definition;
    }

    /**
     * 将毫秒字串解析为 long, 解析失败返回 0。
     *
     * @param value 字串
     * @return 毫秒数
     */
    private long parseMs(String value) {
        if (!isNotBlank(value)) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    /**
     * 文本非空判定。
     *
     * @param value 文本
     * @return 非空返回 true
     */
    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}