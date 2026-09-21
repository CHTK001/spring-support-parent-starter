package com.chua.starter.job.support.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.job.support.dispatch.JobDefinition;
import com.chua.starter.job.support.dispatch.JobQuery;
import com.chua.starter.job.support.dispatch.JobRepository;
import com.chua.starter.job.support.dispatch.JobRuntime;
import com.chua.starter.job.support.dispatch.JobSpec;
import com.chua.starter.job.support.dispatch.JobTriggering;
import com.chua.starter.job.support.handler.JobHandlerFactory;
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
     * 未指定分组时使用的默认分组
     */
    private static final String DEFAULT_GROUP = "default";

    /**
     * 单实例内存记录每个 作业 上一次已发布的 下一个firems (用于 misfire 去重与补跳决策).
     */
    private final ConcurrentMap<String, Long> lastFireMs = new ConcurrentHashMap<>();

    /**
     * 触发器缓存, Key 为 执行Bean/名称 (复用固定序列推进)
     */
    private final ConcurrentMap<String, com.chua.common.support.task.scheduler.Trigger> triggerCache = new ConcurrentHashMap<>();

    /**
     * 任务 映射器
     */
    private final JobMapper jobMapper;

    @Override
    /**
     * 查找Due
    */
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
                persistFireTimes(key, now, nextFire);
            }
            // 未到期时保留已派发的触发时刻：清掉会让下一拍以 now 重算出同一个触发点，
            // 落在预读窗内就被反复派发（一个周期最多 preReadMs/tick 次）。调度配置变更后
            // 由该时刻自然推进对齐，无需在此清理。
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

    @Override
    /**
     * 保存任务规格 (新建或按任务键更新), 写入后立即失效触发器缓存
    */
    public boolean save(JobSpec spec) {
        if (spec == null || isBlank(spec.getJobKey())) {
            return false;
        }
        String jobKey = spec.getJobKey().trim();
        String scheduleType = isBlank(spec.getScheduleType()) ? JobSpec.TYPE_NONE : spec.getScheduleType().trim();
        String scheduleTime = isBlank(spec.getScheduleTime()) ? null : spec.getScheduleTime().trim();
        boolean enabled = spec.getEnabled() == null || spec.getEnabled();
        JobEntity existing = selectByKey(jobKey);
        if (existing == null) {
            JobEntity entity = new JobEntity();
            entity.setSysJobNo(isBlank(spec.getJobNo()) ? jobKey : spec.getJobNo().trim());
            entity.setSysJobExecuteBean(jobKey);
            entity.setSysJobName(isBlank(spec.getJobName()) ? jobKey : spec.getJobName());
            entity.setSysJobDesc(spec.getDesc());
            entity.setSysJobGroup(isBlank(spec.getGroup()) ? DEFAULT_GROUP : spec.getGroup());
            entity.setSysJobScheduleType(scheduleType);
            entity.setSysJobScheduleTime(scheduleTime);
            entity.setSysJobTriggerStatus(enabled ? 1 : 0);
            entity.setSysJobExecuteParam(spec.getExecuteParam());
            entity.setSysJobDispatchMode("LOCAL");
            entity.setSysJobIsDeleted(0);
            try {
                jobMapper.insert(entity);
            } catch (Exception ex) {
                throw new IllegalStateException("任务登记失败 job=" + jobKey + ": " + ex.getMessage(), ex);
            }
            log.info("[job] 任务登记成功 job={} type={} time={} enabled={}",
                    jobKey, scheduleType, scheduleTime, enabled);
        } else {
            jobMapper.update(null, keyFilter(jobKey)
                    .set(JobEntity::getSysJobName, isBlank(spec.getJobName()) ? jobKey : spec.getJobName())
                    .set(JobEntity::getSysJobDesc, spec.getDesc())
                    .set(JobEntity::getSysJobGroup, isBlank(spec.getGroup()) ? DEFAULT_GROUP : spec.getGroup())
                    .set(JobEntity::getSysJobScheduleType, scheduleType)
                    .set(JobEntity::getSysJobScheduleTime, scheduleTime)
                    .set(JobEntity::getSysJobTriggerStatus, enabled ? 1 : 0)
                    .set(JobEntity::getSysJobExecuteParam, spec.getExecuteParam())
                    .set(JobEntity::getSysJobIsDeleted, 0));
            log.info("[job] 任务更新成功 job={} type={} time={} enabled={}",
                    jobKey, scheduleType, scheduleTime, enabled);
        }
        if (!enabled) {
            clearNextFireTime(jobKey);
        } else if (!JobSpec.TYPE_NONE.equals(scheduleType)) {
            seedNextFireTime(jobKey);
        }
        refresh(jobKey);
        return true;
    }

    /**
     * 把启用作业的下次触发时刻补种到台账行。
     *
     * <p>派发判定用触发器现算，不依赖这一列；但控制台的「下次触发」只读它，
     * 不补种就要等第一轮派发才有值，刚启用的作业在页面上看着像没排上队。</p>
     *
     * @param jobKey 任务键
     */
    private void seedNextFireTime(String jobKey) {
        JobEntity entity = selectByKey(jobKey);
        if (entity == null) {
            return;
        }
        long next = computeNextFireMs(toDefinition(entity));
        if (next > 0L) {
            jobMapper.update(null, keyFilter(jobKey).set(JobEntity::getSysJobTriggerNextTime, next));
        }
    }

    /**
     * 抹掉停用作业残留的下次触发时刻。
     *
     * <p>派发判定看 {@code sys_job_trigger_status} 与现算的触发器，这一列只供控制台展示。
     * 停用时不清掉，页面上就会拿着启用期种下的旧时刻显示"下次执行"，而它永远不会推进，
     * 停掉的任务看着像还在排队。</p>
     *
     * @param jobKey 任务键
     */
    private void clearNextFireTime(String jobKey) {
        jobMapper.update(null, keyFilter(jobKey).set(JobEntity::getSysJobTriggerNextTime, 0L));
    }

    @Override
    /**
     * 按任务键读取定义
    */
    public JobDefinition find(String jobKey) {
        JobEntity entity = selectByKey(jobKey);
        return entity == null ? null : toDefinition(entity);
    }

    @Override
    /**
     * 按任务键读取运行态
    */
    public JobRuntime runtime(String jobKey) {
        JobEntity entity = selectByKey(jobKey);
        return entity == null ? null : toRuntime(entity);
    }

    @Override
    /**
     * Page任务运行态
    */
    public PageResult<JobRuntime> page(JobQuery query) {
        JobQuery condition = query == null ? new JobQuery() : query;
        int pageNum = condition.getPageNum() == null || condition.getPageNum() < 1 ? 1 : condition.getPageNum();
        int pageSize = condition.getPageSize() == null || condition.getPageSize() < 1 ? 10 : condition.getPageSize();
        LambdaQueryWrapper<JobEntity> wrapper = Wrappers.<JobEntity>lambdaQuery()
                .eq(JobEntity::getSysJobIsDeleted, 0);
        if (!isBlank(condition.getGroup())) {
            wrapper.eq(JobEntity::getSysJobGroup, condition.getGroup().trim());
        }
        if (condition.getStatus() != null) {
            wrapper.eq(JobEntity::getSysJobTriggerStatus, condition.getStatus());
        }
        if (!isBlank(condition.getKeyword())) {
            String keyword = condition.getKeyword().trim();
            wrapper.and(w -> w.like(JobEntity::getSysJobName, keyword)
                    .or().like(JobEntity::getSysJobExecuteBean, keyword)
                    .or().like(JobEntity::getSysJobDesc, keyword));
        }
        wrapper.orderByDesc(JobEntity::getSysJobTriggerStatus).orderByAsc(JobEntity::getSysJobExecuteBean);
        IPage<JobEntity> result = jobMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<JobRuntime> rows = new ArrayList<>();
        for (JobEntity entity : result.getRecords()) {
            rows.add(toRuntime(entity));
        }
        return PageResult.<JobRuntime>builder()
                .pageNo((int) result.getCurrent())
                .pageSize((int) result.getSize())
                .totalPages((int) result.getPages())
                .total(result.getTotal())
                .data(rows)
                .build();
    }

    @Override
    /**
     * 按分组列出运行态快照
    */
    public List<JobRuntime> listGroup(String group) {
        LambdaQueryWrapper<JobEntity> wrapper = Wrappers.<JobEntity>lambdaQuery()
                .eq(JobEntity::getSysJobIsDeleted, 0);
        if (!isBlank(group)) {
            wrapper.eq(JobEntity::getSysJobGroup, group.trim());
        }
        wrapper.orderByAsc(JobEntity::getSysJobExecuteBean);
        List<JobEntity> entities = jobMapper.selectList(wrapper);
        List<JobRuntime> rows = new ArrayList<>();
        if (entities != null) {
            for (JobEntity entity : entities) {
                rows.add(toRuntime(entity));
            }
        }
        return rows;
    }

    @Override
    /**
     * 改调度配置并立即生效
    */
    public boolean updateSchedule(String jobKey, String scheduleType, String scheduleTime) {
        if (isBlank(jobKey) || selectByKey(jobKey) == null) {
            return false;
        }
        String type = isBlank(scheduleType) ? JobSpec.TYPE_NONE : scheduleType.trim();
        String time = isBlank(scheduleTime) ? null : scheduleTime.trim();
        jobMapper.update(null, keyFilter(jobKey.trim())
                .set(JobEntity::getSysJobScheduleType, type)
                .set(JobEntity::getSysJobScheduleTime, time));
        refresh(jobKey.trim());
        log.info("[job] 调度配置已更新并失效缓存 job={} type={} time={}", jobKey, type, time);
        return true;
    }

    @Override
    /**
     * 启停自动调度
    */
    public boolean changeStatus(String jobKey, boolean enabled) {
        if (isBlank(jobKey) || selectByKey(jobKey) == null) {
            return false;
        }
        String key = jobKey.trim();
        jobMapper.update(null, keyFilter(key)
                .set(JobEntity::getSysJobTriggerStatus, enabled ? 1 : 0));
        if (enabled) {
            seedNextFireTime(key);
        } else {
            clearNextFireTime(key);
        }
        refresh(key);
        log.info("[job] 调度状态已切换 job={} enabled={}", jobKey, enabled);
        return true;
    }

    @Override
    /**
     * 逻辑删除任务
    */
    public boolean remove(String jobKey) {
        if (isBlank(jobKey) || selectByKey(jobKey) == null) {
            return false;
        }
        jobMapper.update(null, keyFilter(jobKey.trim())
                .set(JobEntity::getSysJobIsDeleted, 1)
                .set(JobEntity::getSysJobTriggerStatus, 0));
        refresh(jobKey.trim());
        log.info("[job] 任务已删除 job={}", jobKey);
        return true;
    }

    @Override
    /**
     * 失效本地触发器缓存与已派发时刻记录
    */
    public void refresh(String jobKey) {
        if (isBlank(jobKey)) {
            return;
        }
        String key = jobKey.trim();
        triggerCache.remove(key);
        lastFireMs.remove(key);
    }

    /**
     * 按任务键定位任务行: 先匹配执行器 Bean 名, 未命中再匹配任务名 (注解任务两列同值)。
     *
     * @param jobKey 任务键
     * @return 实体, 不存在返回 空
     */
    private JobEntity selectByKey(String jobKey) {
        JobEntity entity = jobMapper.selectOne(Wrappers.<JobEntity>lambdaQuery()
                .eq(JobEntity::getSysJobExecuteBean, jobKey)
                .eq(JobEntity::getSysJobIsDeleted, 0)
                .last("LIMIT 1"));
        if (entity != null) {
            return entity;
        }
        return jobMapper.selectOne(Wrappers.<JobEntity>lambdaQuery()
                .eq(JobEntity::getSysJobName, jobKey)
                .eq(JobEntity::getSysJobIsDeleted, 0)
                .last("LIMIT 1"));
    }

    /**
     * 构造与 {@link #selectByKey(String)} 同口径的更新条件。
     *
     * @param jobKey 任务键
     * @return 更新条件
     */
    private LambdaUpdateWrapper<JobEntity> keyFilter(String jobKey) {
        return Wrappers.<JobEntity>lambdaUpdate()
                .and(w -> w.eq(JobEntity::getSysJobExecuteBean, jobKey)
                        .or().eq(JobEntity::getSysJobName, jobKey));
    }

    /**
     * 实体转运行态快照, 下次触发时间按当前配置现算 (不复用派发序列的触发器, 避免只读操作推进调度)。
     *
     * @param entity 实体
     * @return 快照
     */
    private JobRuntime toRuntime(JobEntity entity) {
        JobRuntime runtime = new JobRuntime();
        String key = !isBlank(entity.getSysJobExecuteBean()) ? entity.getSysJobExecuteBean() : entity.getSysJobName();
        runtime.setJobNo(entity.getSysJobNo());
        runtime.setJobKey(key);
        runtime.setJobName(entity.getSysJobName());
        runtime.setDesc(entity.getSysJobDesc());
        runtime.setGroup(entity.getSysJobGroup());
        runtime.setScheduleType(entity.getSysJobScheduleType());
        runtime.setScheduleTime(entity.getSysJobScheduleTime());
        runtime.setEnabled(entity.getSysJobTriggerStatus() != null && entity.getSysJobTriggerStatus() == 1);
        runtime.setExecuteParam(entity.getSysJobExecuteParam());
        runtime.setLastFireMs(persistedLastFireMs(entity));
        // 停用任务不预告触发时刻：按 cron 现算会让前端显示"下次执行"，但它永远不会跑
        boolean enabled = entity.getSysJobTriggerStatus() != null && entity.getSysJobTriggerStatus() == 1;
        runtime.setNextFireMs(enabled ? computeNextFireMs(toDefinition(entity)) : 0L);
        runtime.setHandlerRegistered(!isBlank(key) && JobHandlerFactory.getInstance().contains(key));
        return runtime;
    }

    /**
     * 读取上次派发时刻: 优先落库值 (跨重启), 回退到本进程内存记录。
     *
     * @param entity 实体
     * @return 毫秒时间戳, 无记录返回 0
     */
    private long persistedLastFireMs(JobEntity entity) {
        Long persisted = entity.getSysJobTriggerLastTime();
        if (persisted != null && persisted > 0L) {
            return persisted;
        }
        String key = !isBlank(entity.getSysJobExecuteBean()) ? entity.getSysJobExecuteBean() : entity.getSysJobName();
        Long memory = key == null ? null : lastFireMs.get(key);
        return memory == null ? 0L : memory;
    }

    /**
     * 现算下一次触发时刻。
     *
     * @param definition 任务定义
     * @return 毫秒时间戳, 未配置或表达式非法返回 -1
     */
    private long computeNextFireMs(JobDefinition definition) {
        try {
            com.chua.common.support.task.scheduler.Trigger trigger = JobTriggering.createTrigger(definition);
            return JobTriggering.nextFireMs(definition, trigger, 0L, System.currentTimeMillis());
        } catch (Exception ex) {
            return -1L;
        }
    }

    /**
     * 把本次派发与下次触发时刻写回任务行 (簿记性质, 失败只告警, 绝不影响派发)。
     *
     * @param jobKey   任务键
     * @param fireMs   本次派发时刻
     * @param nextFire 下次触发时刻
     */
    private void persistFireTimes(String jobKey, long fireMs, long nextFire) {
        try {
            LambdaUpdateWrapper<JobEntity> wrapper = keyFilter(jobKey)
                    .set(JobEntity::getSysJobTriggerLastTime, fireMs);
            if (nextFire > 0L) {
                wrapper.set(JobEntity::getSysJobTriggerNextTime, nextFire);
            }
            jobMapper.update(null, wrapper);
        } catch (Exception ex) {
            log.warn("[job] 触发时刻回写失败 job={} cause={}", jobKey, ex.getMessage());
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
     * @param key    唯一 Key (执行Bean/名称), 用于缓存触发器
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
        definition.setJobName(entity.getSysJobName());
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
     * 文本空白判定。
     *
     * @param value 文本
     * @return 空白返回 true
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 文本非空判定。
     *
     * @param value 文本
     * @return 非空返回 true
     */
    private boolean isNotBlank(String value) {
        return !isBlank(value);
    }
}
