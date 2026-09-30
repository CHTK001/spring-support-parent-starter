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
 *   <li>按 {@code sys_job_trigger_status=1, sys_job_is_deleted=0} 粗筛, 并在指定时按
 *   {@code sys_job_group} 收敛到本节点分组；</li>
 *   <li><b>不分发模式</b>：{@code dispatchMode} 不参与扫描, LOCAL 与 REMOTE 行一视同仁,
 *   都由本节点 Leader 下发（REMOTE 经同步通道转在线执行器）。</li>
 *   <li>加载候选后用 {@link CronExpression} 计算下一次触发时间, 与 {@code now + preReadMs} 比较;</li>
 *   <li>若 {@code now - lastFireMs} 超过一个周期, 补全错过的触发 (misfire),
 * 仍返回单个 作业definition (Leader 一次性 发布, 由 执行器 串行执行).</li>
 *   <li>返回到期的 JobDefinition 列表给 LeaderLoop。</li>
 * </ol>
 *
 * <p><b>misfire 补跳:</b> 仅在内存 {@link #lastFireMs} 中记录每个任务的最近一次
 * Leader循环 通知过的下次触发时间, 若当前 {@code nextFire} 早于 {@code lastFire},
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

    /**
     * 未指定分组时的落地分组：取本节点的 {@code plugin.job.cluster.group}。
     *
     * <p>Leader 扫描只处理 {@code sys_job_group = cluster.group} 的行，留空若落 {@code default}
     * 会得到"登记成功但永不被自动派发"的任务（手动触发却能跑，极难排查）。这里统一落到本节点分组，
     * 使"不填分组 = 归属本调度中心"，与 {@code upsert} 的注解任务口径一致。</p>
     */
    private final String defaultGroup;

    @Override
    public List<JobDefinition> findDue(long now, long preReadMs, int preReadCount) {
        return findDue(now, preReadMs, preReadCount, null);
    }

    @Override
    public List<JobDefinition> findDue(long now, long preReadMs, int preReadCount, String group) {
        // 分发模式不参与扫描：远程行同样由本节点 Leader 经同步通道下发到在线执行器，
        // LOCAL/REMOTE 只影响展示归属，不再决定"能不能跑"。
        var query = Wrappers.<JobEntity>lambdaQuery()
                .eq(JobEntity::getSysJobTriggerStatus, 1)
                .eq(JobEntity::getSysJobIsDeleted, 0);
        if (group != null && !group.isBlank()) {
            query.eq(JobEntity::getSysJobGroup, group.trim());
        }
        // 多机部署时各节点本机时钟可能漂移，而"到点没有""错过没有"全靠本机 now 判定；
        // 统一改用数据库时钟，触发相位不再随当前 leader 是哪台机器而平移。
        now = currentDbTimeMs(now);
        long upperBound = now + Math.max(preReadMs, 0L);
        query.and(w -> w.isNull(JobEntity::getSysJobTriggerNextTime)
                .or().eq(JobEntity::getSysJobTriggerNextTime, 0L)
                .or().le(JobEntity::getSysJobTriggerNextTime, upperBound));
        query.orderByAsc(JobEntity::getSysJobTriggerNextTime)
                .orderByAsc(JobEntity::getSysJobId)
                .last("LIMIT " + Math.max(preReadCount, 1));
        List<JobEntity> candidates = jobMapper.selectList(query);

        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<JobDefinition> due = new ArrayList<>();
        for (JobEntity entity : candidates) {
            String key = stateKey(entity);
            if (key == null) {
                continue;
            }
            long nextFire = nextFireMs(entity, key, now);
            if (nextFire < 0L) {
                continue;
            }
            if (nextFire <= upperBound) {
                // 错过的判定: 上一轮已派发的触发点早于本轮计算出的触发点, 且已过 1s 容差。
                // 基线同样要走 lastFireBaseline: Leader 切换后进程内 map 是空的, 只读 map 会让
                // missed 恒为 false, 故障切换期间错过的槽位就再也不会被识别为 misfire。
                // 基线为 0 表示"从未触发过"（新任务）, 此时不能算 missed, 否则首拍就会被当成错过。
                long previous = lastFireBaseline(entity, key);
                boolean missed = previous > 0L && previous < nextFire && now - nextFire > 1000L;
                // 默认（未配置）保持历史行为补跑一次；显式 DO_NOTHING 时错过的触发不补跑，
                // 只推进派发序列与展示用下次时刻，避免重启后集中补跑风暴。
                boolean doNothing = JobSpec.MISFIRE_DO_NOTHING.equalsIgnoreCase(
                        trimToNull(entity.getSysJobExecuteMisfireStrategy()));
                if (missed && doNothing) {
                    log.info("[job] misfire 跳过 job={} expected={} now={} strategy=DO_NOTHING",
                            key, nextFire, now);
                    // 必须跳到「再下一个槽位」并同时推进持久化基线, 否则派发序列不前进:
                    // ① next_time 此时通常已经等于 nextFire, 写它是空写(且 Connector/J 默认
                    //    带 CLIENT_FOUND_ROWS, 空写也返回匹配行数, 看起来"成功"了);
                    // ② 跳过分支只写 next_time 不写 last_time, 持久化基线仍停在被跳过的槽位,
                    //    于是换个进程重算 nextFire 仍是同一个过期槽位 —— 该任务就永久不再触发,
                    //    表现为日志里反复出现同一 expected 的「misfire 跳过」。
                    // 把 last_time 推到被跳过的槽位、next_time 推到下一个槽位, 跨进程才能收敛,
                    // 追平未来后恢复正常派发。
                    long pushed = nextFireAfter(entity, key, nextFire, now);
                    if (pushed > 0L && pushed != nextFire && persistSkippedSlot(entity, nextFire, pushed)) {
                        // 进程内基线必须与 persistSkippedSlot 写入的 last_time 保持一致（都是被跳过的
                        // 那个槽位 nextFire），不能记成 pushed。记成 pushed 会让下一拍从 pushed 之后
                        // 重新起算, 于是 pushed 这个槽位既没触发也没被跳过, 永远轮不到 —— 任务再也不跑。
                        lastFireMs.put(key, nextFire);
                    } else {
                        log.warn("[job] misfire 跳过但无法推进派发序列 job={} expected={} pushed={}",
                                key, nextFire, pushed);
                    }
                    continue;
                }
                if (missed) {
                    log.warn("[job] misfire 补发 1 次 job={} expected={} now={}", key, nextFire, now);
                }
                if (persistFireTimes(entity, now, nextFire)) {
                    due.add(toDefinition(entity));
                    lastFireMs.put(key, nextFire);
                }
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
                syncNextFireTime(definition.getName());
                log.info("[job] 注解任务持久化成功 name={}", definition.getName());
            } else {
                // 已存在: 仅当调度配置或分组变化时更新, 避免无意义写库
                JobEntity candidate = toEntity(definition);
                boolean scheduleChanged =
                        !java.util.Objects.equals(candidate.getSysJobScheduleType(), existing.getSysJobScheduleType())
                                || !java.util.Objects.equals(candidate.getSysJobScheduleTime(), existing.getSysJobScheduleTime());
                // 分组也要比对: 存量库里这一行可能还挂在旧分组上（例如后来才在注解里补了 group）,
                // 不同步的话 Leader 按 plugin.job.cluster.group 扫描时永远捞不到它
                boolean groupChanged =
                        !java.util.Objects.equals(candidate.getSysJobGroup(), existing.getSysJobGroup());
                if (scheduleChanged || groupChanged) {
                    if (existing.getSysJobNo() == null || existing.getSysJobNo().isBlank()) {
                        existing.setSysJobNo(definition.getName());
                    }
                    existing.setSysJobScheduleType(candidate.getSysJobScheduleType());
                    existing.setSysJobScheduleTime(candidate.getSysJobScheduleTime());
                    existing.setSysJobGroup(candidate.getSysJobGroup());
                    existing.setSysJobName(definition.getName());
                    existing.setSysJobDesc(definition.getDesc());
                    existing.setSysJobTriggerStatus(1);
                    existing.setSysJobIsDeleted(0);
                    // 注解侧改了调度/分组也是"台账被改过"：推进版本，让同时在编辑的人保存时被拦下
                    existing.setSysJobVersion(
                            existing.getSysJobVersion() == null ? 1L : existing.getSysJobVersion() + 1);
                    jobMapper.updateById(existing);
                    syncNextFireTime(definition.getName());
                    log.info("[job] 注解任务调度配置更新 name={} type={} time={} group={}",
                            definition.getName(), candidate.getSysJobScheduleType(),
                            candidate.getSysJobScheduleTime(), candidate.getSysJobGroup());
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
        String dispatchMode = JobSpec.DISPATCH_REMOTE.equalsIgnoreCase(trimToNull(spec.getDispatchMode()))
                ? JobSpec.DISPATCH_REMOTE : JobSpec.DISPATCH_LOCAL;
        // 任务键重命名（编辑时改键）：目标键未被占用才迁移台账行，随后按新键走常规更新
        String originKey = trimToNull(spec.getOriginJobKey());
        if (originKey != null && !originKey.equals(jobKey)
                && selectByKey(jobKey) == null) {
            JobEntity origin = selectByKey(originKey);
            if (origin != null) {
                jobMapper.update(null, Wrappers.<JobEntity>lambdaUpdate()
                        .eq(JobEntity::getSysJobId, origin.getSysJobId())
                        .set(JobEntity::getSysJobExecuteBean, jobKey)
                        .set(JobEntity::getSysJobNo, jobKey));
                log.info("[job] 任务键变更 {} -> {}", originKey, jobKey);
            }
        }
        JobEntity existing = selectByKey(jobKey);
        // 执行类型: BEAN 走本机处理器; 其它值为脚本引擎名, 正文变更时刷新版本时刻以失效执行器缓存
        String glueType = normalizeGlueType(spec.getGlueType());
        boolean scriptJob = !JobSpec.GLUE_BEAN.equals(glueType);
        String glueSource = scriptJob ? spec.getGlueSource() : null;
        Long glueAt = null;
        if (scriptJob) {
            String existingSource = existing == null ? null : existing.getSysJobGlueSource();
            Long existingAt = existing == null || existing.getSysJobGlueUpdatetime() == null
                    ? null : existing.getSysJobGlueUpdatetime().getTime();
            glueAt = glueSource != null && glueSource.equals(existingSource) && existingAt != null
                    ? existingAt : System.currentTimeMillis();
        }
        if (existing == null) {
            // 未删除的行都没有: 再认一次逻辑删除的墓碑行, 走下面的更新分支把它复活（含 set isDeleted=0）
            existing = selectByKey(jobKey, false);
        }
        // 乐观锁基准版本: 任何更新都把版本 +1, 否则"不校验版本"的调用方会悄悄钝化 CAS 判据
        long currentVersion = existing == null || existing.getSysJobVersion() == null
                ? 0L : existing.getSysJobVersion();
        if (existing == null) {
            JobEntity entity = new JobEntity();
            entity.setSysJobNo(isBlank(spec.getJobNo()) ? jobKey : spec.getJobNo().trim());
            entity.setSysJobExecuteBean(jobKey);
            entity.setSysJobName(isBlank(spec.getJobName()) ? jobKey : spec.getJobName());
            entity.setSysJobDesc(spec.getDesc());
            entity.setSysJobGroup(groupOrDefault(spec.getGroup()));
            entity.setSysJobScheduleType(scheduleType);
            entity.setSysJobScheduleTime(scheduleTime);
            entity.setSysJobTriggerStatus(enabled ? 1 : 0);
            entity.setSysJobExecuteParam(spec.getExecuteParam());
            entity.setSysJobDispatchMode(dispatchMode);
            entity.setSysJobRemoteExecutorAddress(trimToNull(spec.getRemoteExecutorAddress()));
            entity.setSysJobAuthor(trimToNull(spec.getAuthor()));
            entity.setSysJobFailRetry(spec.getFailRetry() == null ? 0 : spec.getFailRetry());
            entity.setSysJobRetryInterval(spec.getRetryInterval() == null ? 0 : spec.getRetryInterval());
            entity.setSysJobExecuteTimeout(spec.getExecuteTimeout() == null ? 0 : spec.getExecuteTimeout());
            entity.setSysJobExecuteMisfireStrategy(trimToNull(spec.getMisfireStrategy()));
            entity.setSysJobGlueType(glueType);
            entity.setSysJobGlueSource(glueSource);
            entity.setSysJobGlueUpdatetime(glueAt == null ? null : new java.util.Date(glueAt));
            entity.setSysJobRetryCallbackBean(trimToNull(spec.getRetryCallbackBean()));
            entity.setSysJobExceptionCallbackBean(trimToNull(spec.getExceptionCallbackBean()));
            entity.setSysJobAlarmEmail(trimToNull(spec.getAlarmEmail()));
            entity.setSysJobAlarmType(trimToNull(spec.getAlarmType()));
            entity.setSysJobIsDeleted(0);
            entity.setSysJobVersion(0L);
            // 新建行: 未显式指定并发策略时落 SKIP (安全默认), 与建表列默认值一致。
            // 存量行为 NULL, 读侧按 ALLOW 兼容, 见 readConcurrentPolicy。
            entity.setSysJobConcurrentPolicy(
                    isBlank(spec.getConcurrentPolicy())
                            ? JobSpec.CONCURRENT_SKIP
                            : JobSpec.normalizeConcurrentPolicy(spec.getConcurrentPolicy()));
            try {
                jobMapper.insert(entity);
            } catch (Exception ex) {
                throw new IllegalStateException("任务登记失败 job=" + jobKey + ": " + ex.getMessage(), ex);
            }
            log.info("[job] 任务登记成功 job={} type={} time={} enabled={} dispatch={}",
                    jobKey, scheduleType, scheduleTime, enabled, dispatchMode);
        } else {
            LambdaUpdateWrapper<JobEntity> update = rowFilter(existing)
                    .set(JobEntity::getSysJobName, isBlank(spec.getJobName()) ? jobKey : spec.getJobName())
                    .set(JobEntity::getSysJobDesc, spec.getDesc())
                    .set(JobEntity::getSysJobGroup, groupOrDefault(spec.getGroup()))
                    .set(JobEntity::getSysJobScheduleType, scheduleType)
                    .set(JobEntity::getSysJobScheduleTime, scheduleTime)
                    .set(JobEntity::getSysJobTriggerStatus, enabled ? 1 : 0)
                    .set(JobEntity::getSysJobExecuteParam, spec.getExecuteParam())
                    .set(JobEntity::getSysJobDispatchMode, dispatchMode)
                    .set(JobEntity::getSysJobRemoteExecutorAddress, trimToNull(spec.getRemoteExecutorAddress()))
                    .set(JobEntity::getSysJobAuthor, trimToNull(spec.getAuthor()))
                    .set(JobEntity::getSysJobFailRetry, spec.getFailRetry() == null ? 0 : spec.getFailRetry())
                    .set(JobEntity::getSysJobRetryInterval,
                            spec.getRetryInterval() == null ? 0 : spec.getRetryInterval())
                    .set(JobEntity::getSysJobExecuteTimeout,
                            spec.getExecuteTimeout() == null ? 0 : spec.getExecuteTimeout())
                    .set(JobEntity::getSysJobExecuteMisfireStrategy, trimToNull(spec.getMisfireStrategy()))
                    .set(JobEntity::getSysJobGlueType, glueType)
                    .set(JobEntity::getSysJobGlueSource, glueSource)
                    .set(JobEntity::getSysJobGlueUpdatetime, glueAt == null ? null : new java.util.Date(glueAt))
                    .set(JobEntity::getSysJobRetryCallbackBean, trimToNull(spec.getRetryCallbackBean()))
                    .set(JobEntity::getSysJobExceptionCallbackBean, trimToNull(spec.getExceptionCallbackBean()))
                    .set(JobEntity::getSysJobAlarmEmail, trimToNull(spec.getAlarmEmail()))
                    .set(JobEntity::getSysJobAlarmType, trimToNull(spec.getAlarmType()))
                    // 更新时未显式给并发策略则保持原值, 不覆盖存量行既定的 ALLOW
                    .set(isBlank(spec.getConcurrentPolicy()), JobEntity::getSysJobConcurrentPolicy,
                            JobSpec.normalizeConcurrentPolicy(spec.getConcurrentPolicy()))
                    .set(JobEntity::getSysJobIsDeleted, 0)
                    // 版本前进: 让带版本的调用方下次拿到的基准一定变化
                    .set(JobEntity::getSysJobVersion, currentVersion + 1);
            // 调用方回传了编辑前读到的版本才做 CAS; 不回传则保持历史行为(后写覆盖), 兼容未升级调用方
            if (spec.getVersion() != null) {
                update.eq(JobEntity::getSysJobVersion, spec.getVersion());
            }
            if (jobMapper.update(null, update) == 0 && spec.getVersion() != null) {
                throw new IllegalArgumentException("配置已被修改，请刷新后重试");
            }
            log.info("[job] 任务更新成功 job={} type={} time={} enabled={} dispatch={}",
                    jobKey, scheduleType, scheduleTime, enabled, dispatchMode);
        }
        syncNextFireTime(jobKey);
        refresh(jobKey);
        return true;
    }

    /**
     * 按当前落库配置回写展示用的下次触发时刻。
     *
     * <p>口径与 {@link #toRuntime(JobEntity)} 完全一致 (停用、无调度类型或表达式永不匹配都落 0)，
     * 保证「直读台账的列」与「接口现算的 nextFireTime」不会出现两份账。同一任务键可能挂着多条台账行
     * (各自不同的调度配置)，所以逐行按主键回写，不按任务键一次性覆盖。</p>
     *
     * @param jobKey 任务键
     */
    private void syncNextFireTime(String jobKey) {
        List<JobEntity> entities = jobMapper.selectList(Wrappers.<JobEntity>lambdaQuery()
                .and(w -> w.eq(JobEntity::getSysJobExecuteBean, jobKey)
                        .or().eq(JobEntity::getSysJobName, jobKey))
                .eq(JobEntity::getSysJobIsDeleted, 0));
        if (entities == null || entities.isEmpty()) {
            return;
        }
        for (JobEntity entity : entities) {
            boolean enabled = entity.getSysJobTriggerStatus() != null && entity.getSysJobTriggerStatus() == 1;
            long next = enabled
                    ? computeNextFireMs(toDefinition(entity),
                            entity.getSysJobTriggerLastTime() == null ? 0L : entity.getSysJobTriggerLastTime())
                    : 0L;
            // 运行态回写：刻意不推进 sys_job_version（每拍都会写，推进会让版本疯涨、编辑恒冲突）
            jobMapper.update(null, Wrappers.<JobEntity>lambdaUpdate()
                    .eq(JobEntity::getSysJobId, entity.getSysJobId())
                    .set(JobEntity::getSysJobTriggerNextTime, Math.max(next, 0L)));
        }
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
        if (!isBlank(condition.getDispatchMode())) {
            wrapper.eq(JobEntity::getSysJobDispatchMode, condition.getDispatchMode().trim().toUpperCase());
        }
        if (!isBlank(condition.getKeyword())) {
            String keyword = condition.getKeyword().trim();
            wrapper.and(w -> w.like(JobEntity::getSysJobName, keyword)
                    .or().like(JobEntity::getSysJobExecuteBean, keyword)
                    .or().like(JobEntity::getSysJobNo, keyword)
                    .or().like(JobEntity::getSysJobDesc, keyword)
                    .or().like(JobEntity::getSysJobAuthor, keyword));
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
        if (isBlank(jobKey)) {
            return false;
        }
        JobEntity row = selectByKey(jobKey.trim());
        if (row == null) {
            return false;
        }
        String type = isBlank(scheduleType) ? JobSpec.TYPE_NONE : scheduleType.trim();
        String time = isBlank(scheduleTime) ? null : scheduleTime.trim();
        jobMapper.update(null, rowFilterNextVersion(row)
                .set(JobEntity::getSysJobScheduleType, type)
                .set(JobEntity::getSysJobScheduleTime, time));
        syncNextFireTime(jobKey.trim());
        refresh(jobKey.trim());
        log.info("[job] 调度配置已更新并失效缓存 job={} type={} time={}", jobKey, type, time);
        return true;
    }

    @Override
    /**
     * 启停自动调度
    */
    public boolean changeStatus(String jobKey, boolean enabled) {
        if (isBlank(jobKey)) {
            return false;
        }
        String key = jobKey.trim();
        JobEntity row = selectByKey(key);
        if (row == null) {
            return false;
        }
        jobMapper.update(null, rowFilterNextVersion(row)
                .set(JobEntity::getSysJobTriggerStatus, enabled ? 1 : 0));
        syncNextFireTime(key);
        refresh(key);
        log.info("[job] 调度状态已切换 job={} enabled={}", jobKey, enabled);
        return true;
    }

    @Override
    /**
     * 逻辑删除任务
    */
    public boolean remove(String jobKey) {
        if (isBlank(jobKey)) {
            return false;
        }
        JobEntity row = selectByKey(jobKey.trim());
        if (row == null) {
            return false;
        }
        jobMapper.update(null, rowFilterNextVersion(row)
                .set(JobEntity::getSysJobIsDeleted, 1)
                .set(JobEntity::getSysJobTriggerStatus, 0)
                .set(JobEntity::getSysJobTriggerNextTime, 0L));
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
        // 序列键是台账主键，只能按任务键反查出行才删得掉；含已删除行，避免留下无人认领的缓存。
        List<JobEntity> rows = jobMapper.selectList(Wrappers.<JobEntity>lambdaQuery()
                .select(JobEntity::getSysJobId, JobEntity::getSysJobName)
                .and(w -> w.eq(JobEntity::getSysJobExecuteBean, key)
                        .or().eq(JobEntity::getSysJobName, key)));
        if (rows == null) {
            return;
        }
        for (JobEntity row : rows) {
            triggerCache.remove(stateKey(row));
            lastFireMs.remove(stateKey(row));
        }
    }

    /**
     * 按任务键定位任务行: 先认「任务编号就是这个键」的行，未命中再按执行器 Bean 名、任务名反查。
     *
     * <p>编号优先是必要的：一个执行器 Bean 可以挂多条台账行（采集流程镜像行 + 调度中心为该 Bean
     * 另建的作业行），只按 Bean 名反查会随机落到别的作业上，让流程登记改写了不属于它的行。</p>
     *
     * @param jobKey 任务键
     * @return 实体, 不存在返回 空
     */
    private JobEntity selectByKey(String jobKey) {
        return selectByKey(jobKey, true);
    }

    /**
     * 按任务键定位任务行, 可选择连逻辑删除的墓碑行一起看。
     *
     * <p>登记路径需要看到墓碑行：台账行被删掉后再登记同一把任务键, 只查未删除的行会走插入分支、
     * 撞执行器名的唯一键, 于是流程侧「保存即可补回调度行」永远失败。</p>
     *
     * @param jobKey   任务键
     * @param onlyLive true 只看未删除的行; false 连逻辑删除的行一起看
     * @return 实体, 不存在返回 空
     */
    private JobEntity selectByKey(String jobKey, boolean onlyLive) {
        JobEntity entity = jobMapper.selectOne(Wrappers.<JobEntity>lambdaQuery()
                .eq(JobEntity::getSysJobNo, jobKey)
                .eq(onlyLive, JobEntity::getSysJobIsDeleted, 0)
                .orderByAsc(JobEntity::getSysJobId)
                .last("LIMIT 1"));
        if (entity != null) {
            return entity;
        }
        entity = jobMapper.selectOne(Wrappers.<JobEntity>lambdaQuery()
                .eq(JobEntity::getSysJobExecuteBean, jobKey)
                .eq(onlyLive, JobEntity::getSysJobIsDeleted, 0)
                .orderByAsc(JobEntity::getSysJobId)
                .last("LIMIT 1"));
        if (entity != null) {
            return entity;
        }
        return jobMapper.selectOne(Wrappers.<JobEntity>lambdaQuery()
                .eq(JobEntity::getSysJobName, jobKey)
                .eq(onlyLive, JobEntity::getSysJobIsDeleted, 0)
                .orderByAsc(JobEntity::getSysJobId)
                .last("LIMIT 1"));
    }

    /**
     * 构造按主键限定单行的更新条件。
     *
     * @param row 目标行
     * @return 更新条件
     */
    private LambdaUpdateWrapper<JobEntity> rowFilter(JobEntity row) {
        return Wrappers.<JobEntity>lambdaUpdate().eq(JobEntity::getSysJobId, row.getSysJobId());
    }

    /**
     * 按主键限定单行，并在同一次更新里推进乐观锁版本。
     *
     * <p><b>为什么每条写路径都要推进版本</b>：编辑保存靠 {@code version} 做 CAS，
     * 只要有任何一条写路径不推进，持旧版本的编辑弹窗就会在下一次保存时仍然比对成功，
     * 把别人的启停 / 调度 / 删除改动静默覆盖——这正是乐观锁要拦的事。</p>
     *
     * <p><b>反例（刻意不推进）</b>：{@link #syncNextFireTime}、{@link #persistFireTimes} 等
     * 是每拍调度回写运行态（下次触发时刻 / 已派发时刻），一秒可能写多次。
     * 给它们推进版本会让版本无意义地疯涨，所有编辑一保存就"冲突"，
     * 所以运行态回写一律<b>不</b>参与版本推进。</p>
     *
     * @param row 目标行
     * @return 已带上版本推进的按主键更新条件
     */
    private LambdaUpdateWrapper<JobEntity> rowFilterNextVersion(JobEntity row) {
        long current = row.getSysJobVersion() == null ? 0L : row.getSysJobVersion();
        return rowFilter(row).set(JobEntity::getSysJobVersion, current + 1);
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
        runtime.setDispatchMode(isBlank(entity.getSysJobDispatchMode())
                ? JobSpec.DISPATCH_LOCAL : entity.getSysJobDispatchMode());
        runtime.setRemoteExecutorAddress(entity.getSysJobRemoteExecutorAddress());
        runtime.setAuthor(entity.getSysJobAuthor());
        runtime.setFailRetry(entity.getSysJobFailRetry());
        runtime.setRetryInterval(entity.getSysJobRetryInterval());
        runtime.setExecuteTimeout(entity.getSysJobExecuteTimeout());
        runtime.setMisfireStrategy(entity.getSysJobExecuteMisfireStrategy());
        runtime.setConcurrentPolicy(readConcurrentPolicy(entity));
        runtime.setVersion(entity.getSysJobVersion() == null ? 0L : entity.getSysJobVersion());
        runtime.setGlueType(normalizeGlueType(entity.getSysJobGlueType()));
        runtime.setGlueSource(entity.getSysJobGlueSource());
        runtime.setGlueUpdatetimeMs(entity.getSysJobGlueUpdatetime() == null
                ? 0L : entity.getSysJobGlueUpdatetime().getTime());
        runtime.setRetryCallbackBean(entity.getSysJobRetryCallbackBean());
        runtime.setExceptionCallbackBean(entity.getSysJobExceptionCallbackBean());
        runtime.setAlarmEmail(entity.getSysJobAlarmEmail());
        runtime.setAlarmType(entity.getSysJobAlarmType());
        runtime.setLastFireMs(persistedLastFireMs(entity));
        // 停用任务不预告触发时刻：按 cron 现算会让前端显示"下次执行"，但它永远不会跑
        boolean enabled = entity.getSysJobTriggerStatus() != null && entity.getSysJobTriggerStatus() == 1;
        runtime.setNextFireMs(enabled
                ? computeNextFireMs(toDefinition(entity),
                        entity.getSysJobTriggerLastTime() == null ? 0L : entity.getSysJobTriggerLastTime())
                : 0L);
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
        String key = stateKey(entity);
        Long memory = key == null ? null : lastFireMs.get(key);
        return memory == null ? 0L : memory;
    }

    /**
     * 现算下一次触发时刻。
     *
     * @param definition 任务定义
     * @param lastFireMs 上次触发时刻（0 表示无历史，基准回落到当前时间）
     * @return 毫秒时间戳, 未配置或表达式非法返回 -1
     */
    /**
     * 取数据库当前时刻（毫秒）。
     *
     * <p>多机部署时各节点本机时钟可能漂移，而"任务是否到点""是否错过"全部由本机 now 判定；
     * 统一改用数据库时钟后，触发相位不再随当前 leader 是哪台机器而平移，
     * 多机部署对 NTP 的依赖由硬要求降级为可选。</p>
     *
     * @param fallback 取不到数据库时间时的回落值（本机时钟）
     * @return 数据库当前毫秒时间戳
     */
    private long currentDbTimeMs(long fallback) {
        try {
            var dbNow = jobMapper.selectDbNow();
            return dbNow == null ? fallback : dbNow.getTime();
        } catch (Exception ex) {
            log.warn("[job] 取数据库时间失败, 回落到本机时钟 cause={}", ex.getMessage());
            return fallback;
        }
    }

    private long computeNextFireMs(JobDefinition definition, long lastFireMs) {
        try {
            com.chua.common.support.task.scheduler.Trigger trigger = JobTriggering.createTrigger(definition);
            return JobTriggering.nextFireMs(definition, trigger, lastFireMs, System.currentTimeMillis());
        } catch (Exception ex) {
            return -1L;
        }
    }

    /**
     * 把本次派发与下次触发时刻写回任务行 (簿记性质, 失败只告警, 绝不影响派发)。
     *
     * <p>按主键定位而不是按任务键，是因为多条台账行可以共用一个执行器 Bean；按 Bean 回写会把其中一行的
     * 触发时刻盖到另一行上，两条各自独立的调度就互相看见对方的排队时间。</p>
     *
     * @param entity   本次派发的任务行
     * @param fireMs   本次派发时刻
     * @param nextFire 下次触发时刻
     */
    private boolean persistFireTimes(JobEntity entity, long fireMs, long nextFire) {
        try {
            LambdaUpdateWrapper<JobEntity> wrapper = Wrappers.<JobEntity>lambdaUpdate()
                    .set(JobEntity::getSysJobTriggerLastTime, fireMs)
                    .set(JobEntity::getSysJobTriggerNextTime, Math.max(nextFire, 0L));
            if (entity.getSysJobId() != null) {
                wrapper.eq(JobEntity::getSysJobId, entity.getSysJobId());
            } else {
                wrapper.and(w -> w.eq(JobEntity::getSysJobExecuteBean, entity.getSysJobExecuteBean())
                        .or().eq(JobEntity::getSysJobName, entity.getSysJobName()));
            }
            addExpectedNextFilter(wrapper, entity);
            return jobMapper.update(null, wrapper) > 0;
        } catch (Exception ex) {
            log.warn("[job] 触发时刻回写失败 job={} cause={}", stateKey(entity), ex.getMessage());
            return false;
        }
    }

    /**
     * 仅回写展示用的下次触发时刻（misfire 跳过时用，不伪造成"本次已触发"）。
     *
     * @param entity   任务行
     * @param nextFire 下次触发时刻
     */
    private boolean persistNextFire(JobEntity entity, long nextFire) {
        try {
            LambdaUpdateWrapper<JobEntity> wrapper = Wrappers.<JobEntity>lambdaUpdate()
                    .set(JobEntity::getSysJobTriggerNextTime, Math.max(nextFire, 0L));
            if (entity.getSysJobId() != null) {
                wrapper.eq(JobEntity::getSysJobId, entity.getSysJobId());
            } else {
                wrapper.and(w -> w.eq(JobEntity::getSysJobExecuteBean, entity.getSysJobExecuteBean())
                        .or().eq(JobEntity::getSysJobName, entity.getSysJobName()));
            }
            addExpectedNextFilter(wrapper, entity);
            return jobMapper.update(null, wrapper) > 0;
        } catch (Exception ex) {
            log.warn("[job] 下次触发时刻回写失败 job={} cause={}", stateKey(entity), ex.getMessage());
            return false;
        }
    }

    /**
     * 写入「跳过某个槽位」的结果: 基线推到被跳过的槽位, 下次触发点推到下一个槽位。
     *
     * <p>与 {@link #persistFireTimes(JobEntity, long, long)} 的区别是不写派发墙钟 ——
     * 被跳过的槽位并未真正执行, 若把 {@code now} 写进 last_time 就等于谎报执行时刻;
     * 但基线必须前进, 否则换进程重算仍是同一个过期槽位。</p>
     *
     * @param entity      任务行
     * @param skippedSlot 被跳过的触发点
     * @param nextSlot    顺延后的下次触发点
     * @return 是否写入成功
     */
    private boolean persistSkippedSlot(JobEntity entity, long skippedSlot, long nextSlot) {
        try {
            LambdaUpdateWrapper<JobEntity> wrapper = Wrappers.<JobEntity>lambdaUpdate()
                    .set(JobEntity::getSysJobTriggerLastTime, skippedSlot)
                    .set(JobEntity::getSysJobTriggerNextTime, Math.max(nextSlot, 0L));
            if (entity.getSysJobId() != null) {
                wrapper.eq(JobEntity::getSysJobId, entity.getSysJobId());
            } else {
                wrapper.and(w -> w.eq(JobEntity::getSysJobExecuteBean, entity.getSysJobExecuteBean())
                        .or().eq(JobEntity::getSysJobName, entity.getSysJobName()));
            }
            addExpectedNextFilter(wrapper, entity);
            return jobMapper.update(null, wrapper) > 0;
        } catch (Exception ex) {
            log.warn("[job] 跳过槽位回写失败 job={} cause={}", stateKey(entity), ex.getMessage());
            return false;
        }
    }

    /**
     * 以读取时的下次触发时刻作为乐观领取条件，避免多个 Leader 同时派发同一任务。
     */
    private void addExpectedNextFilter(LambdaUpdateWrapper<JobEntity> wrapper, JobEntity entity) {
        if (entity.getSysJobTriggerNextTime() == null) {
            wrapper.isNull(JobEntity::getSysJobTriggerNextTime);
        } else {
            wrapper.eq(JobEntity::getSysJobTriggerNextTime, entity.getSysJobTriggerNextTime());
        }
    }

    /**
     * 派发序列键: 台账主键优先，让同一 Bean 上的多条调度各自推进；无主键时退回执行器 Bean 名。
     *
     * @param entity 任务行
     * @return 序列键
     */
    private String stateKey(JobEntity entity) {
        if (entity.getSysJobId() != null) {
            return "#" + entity.getSysJobId();
        }
        return !isBlank(entity.getSysJobExecuteBean()) ? entity.getSysJobExecuteBean() : entity.getSysJobName();
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
        // 分组留空时与建表默认值保持一致，仍是 default；显式指定时按注解值落库，
        // 需与本机 plugin.job.cluster.group 对齐，否则 Leader 扫描捞不到这一行
        entity.setSysJobGroup(groupOrDefault(definition.getGroup()));
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
     * <p><b>基线必须能跨进程存活</b>: 「上次已派发的触发点」先查进程内 {@code lastFireMs},
     * 查不到时回落到台账已持久化的 {@code sys_job_trigger_last_time}。只用进程内 map 时,
     * Leader 切换后的新节点 map 是空的, 基线退化成 0, 后果有两处:
     * 固定间隔任务 (FIXED/DELAY) 会<b>立刻多触发一次</b>;
     * cron 任务的 misfire 判定 (上一触发点早于本轮触发点) 恒为假,
     * <b>故障切换后错过的补发能力直接失效</b>。回落到持久化列后, 派发序列随库一起转移。</p>
     *
     * @param entity 任务实体
     * @param key    唯一 Key (执行Bean/名称), 用于缓存触发器
     * @param now    当前时间戳
     * @return 下次触发时间, 未配置或计算失败返回 -1
     */
    private long nextFireMs(JobEntity entity, String key, long now) {
        return nextFireAfter(entity, key, lastFireBaseline(entity, key), now);
    }

    /**
     * 以指定基线计算下一次触发时间。
     *
     * @param entity   任务实体
     * @param key      派发序列键
     * @param baseline 上次已派发触发点, 0 表示无历史
     * @param now      当前时间戳
     * @return 下次触发时间, 未配置或计算失败返回 -1
     */
    private long nextFireAfter(JobEntity entity, String key, long baseline, long now) {
        JobDefinition definition = toDefinition(entity);
        com.chua.common.support.task.scheduler.Trigger trigger = triggerCache.get(key);
        if (trigger == null) {
            trigger = com.chua.starter.job.support.dispatch.JobTriggering.createTrigger(definition);
            if (trigger == null) {
                return -1L;
            }
            triggerCache.put(key, trigger);
        }
        return com.chua.starter.job.support.dispatch.JobTriggering.nextFireMs(definition, trigger, baseline, now);
    }

    /**
     * 取「上次已派发的触发点」基线: 进程内快路径优先, 未命中回落到台账持久化列。
     *
     * @param entity 任务实体
     * @param key    派发序列键
     * @return 上次触发点毫秒时间戳, 无任何可用基线时返回 0
     */
    private long lastFireBaseline(JobEntity entity, String key) {
        Long cached = lastFireMs.get(key);
        if (cached != null) {
            return cached;
        }
        // Leader 切换 / 进程重启后进程内 map 为空, 此时以库里的持久化值为准
        Long persisted = entity.getSysJobTriggerLastTime();
        return persisted == null || persisted < 0L ? 0L : persisted;
    }

    /**
     * 读取台账行的并发策略。
     *
     * <p><b>该列为空时按 {@link JobSpec#CONCURRENT_ALLOW} 处理, 而不是默认的 SKIP</b>:
     * 这一列是后加的, 存量行全为 NULL。若把 NULL 当成 SKIP, 升级瞬间所有「周期短于耗时」的
     * 长任务会集体被跳过, 表现为任务静默不跑, 比并发堆积更难排查。
     * 缺省一律保持历史行为 (ALLOW), 由新建行 (建表默认值 SKIP / 保存时显式写入) 决定新任务的策略。</p>
     *
     * @param entity 任务实体
     * @return SKIP / ALLOW (非空)
     */
    private String readConcurrentPolicy(JobEntity entity) {
        String stored = entity.getSysJobConcurrentPolicy();
        if (isBlank(stored)) {
            return JobSpec.CONCURRENT_ALLOW;
        }
        return JobSpec.normalizeConcurrentPolicy(stored);
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
        definition.setFailRetry(entity.getSysJobFailRetry());
        definition.setRetryIntervalSec(entity.getSysJobRetryInterval());
        definition.setExecuteTimeoutSec(entity.getSysJobExecuteTimeout());
        definition.setGlueType(normalizeGlueType(entity.getSysJobGlueType()));
        definition.setConcurrentPolicy(readConcurrentPolicy(entity));
        definition.setGlueSource(entity.getSysJobGlueSource());
        definition.setGlueUpdatetimeMs(entity.getSysJobGlueUpdatetime() == null
                ? 0L : entity.getSysJobGlueUpdatetime().getTime());
        definition.setRetryCallbackBean(entity.getSysJobRetryCallbackBean());
        definition.setExceptionCallbackBean(entity.getSysJobExceptionCallbackBean());
        definition.setAlarmEmail(entity.getSysJobAlarmEmail());
        definition.setAlarmType(entity.getSysJobAlarmType());
        definition.setExecuteParam(entity.getSysJobExecuteParam());
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
     * 归一执行类型: 空值按 BEAN 处理。
     *
     * @param glueType 原始执行类型
     * @return 归一后的执行类型
     */
    private String normalizeGlueType(String glueType) {
        String value = trimToNull(glueType);
        return value == null ? JobSpec.GLUE_BEAN : value;
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

    /**
     * 去掉首尾空白, 全空白归一为 {@code null}, 避免把空串写进可选列。
     *
     * @param value 取值
     * @return 规整后的取值
     */
    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    /**
     * 分组兜底：留空落到本节点的调度分组。
     *
     * @param group 提交的分组
     * @return 非空分组
     */
    private String groupOrDefault(String group) {
        String value = trimToNull(group);
        if (value != null) {
            return value;
        }
        String configured = trimToNull(defaultGroup);
        return configured == null ? DEFAULT_GROUP : configured;
    }
}
