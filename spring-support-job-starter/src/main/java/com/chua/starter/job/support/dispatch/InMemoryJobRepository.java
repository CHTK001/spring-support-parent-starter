package com.chua.starter.job.support.dispatch;

import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.task.scheduler.Trigger;
import com.chua.starter.job.support.handler.JobHandlerFactory;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版任务仓库, 默认实现, 适用于单机测试或调试场景。
 *
 * <p>调度触发时间统一委托给 {@link JobTriggering} (内部复用仓库公共
 * {@link com.chua.common.support.task.scheduler.CronTrigger} / {@link com.chua.common.support.task.scheduler.FixedTrigger}),
 * 支持 cron / fixed延迟 / fixedrate 三种调度方式;</p>
 *
 * <p>每个任务的触发器与最近一次触发时间都在内存中维护, 以实现固定的频率/延迟序列推进。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class InMemoryJobRepository implements JobRepository {

    /**
     * 未指定分组时使用的默认分组
     */
    private static final String DEFAULT_GROUP = "default";

    /**
     * 任务存储表
     */
    private final Map<String, JobDefinition> table = new ConcurrentHashMap<>();

    /**
     * 调度三要素之外的治理元数据 (分组/执行参数/GLUE), Key 为任务名
     */
    private final Map<String, JobSpec> specs = new ConcurrentHashMap<>();

    /**
     * 触发器缓存, Key 为任务名
     */
    private final Map<String, Trigger> triggerCache = new ConcurrentHashMap<>();

    /**
     * 最近一次触发时间, Key 为任务名
     */
    private final Map<String, Long> lastFireMs = new ConcurrentHashMap<>();

    /**
     * 注册任务。
     *
     * @param definition 任务定义
     */
    public void upsert(JobDefinition definition) {
        if (definition == null || definition.getName() == null) {
            return;
        }
        table.put(definition.getName(), definition);
        // 重新初始化该任务的触发器状态, 避免复用旧调度配置
        triggerCache.remove(definition.getName());
        lastFireMs.remove(definition.getName());
    }

    /**
     * 查询到期任务。
     *
     * @param now          当前时间戳
     * @param preReadMs    预读时间窗
     * @param preReadCount 最大返回数
     * @return 任务列表
     */
    @Override
    public List<JobDefinition> findDue(long now, long preReadMs, int preReadCount) {
        List<JobDefinition> due = new ArrayList<>();
        for (JobDefinition definition : table.values()) {
            if (!definition.isEnabled()) {
                continue;
            }
            Trigger trigger = triggerCache.get(definition.getName());
            if (trigger == null) {
                trigger = JobTriggering.createTrigger(definition);
                if (trigger == null) {
                    continue;
                }
                triggerCache.put(definition.getName(), trigger);
            }
            Long last = lastFireMs.get(definition.getName());
            long nextFire = JobTriggering.nextFireMs(definition, trigger,
                    last == null ? 0L : last, now);
            if (nextFire < 0L) {
                continue;
            }
            if (nextFire <= now + preReadMs) {
                due.add(definition);
                lastFireMs.put(definition.getName(), nextFire);
            }
        }
        if (due.size() > preReadCount) {
            return new ArrayList<>(due.subList(0, preReadCount));
        }
        return due;
    }

    @Override
    /**
     * 保存任务规格 (新建或按任务键更新)
    */
    public boolean save(JobSpec spec) {
        if (spec == null || spec.getJobKey() == null || spec.getJobKey().isBlank()) {
            return false;
        }
        String jobKey = spec.getJobKey().trim();
        JobDefinition definition = table.computeIfAbsent(jobKey, key -> {
            JobDefinition created = new JobDefinition();
            created.setName(key);
            return created;
        });
        definition.setDesc(spec.getDesc());
        definition.setJobName(blankToDefault(spec.getJobName(), jobKey));
        applySchedule(definition, spec.getScheduleType(), spec.getScheduleTime());
        if (spec.getEnabled() != null) {
            definition.setEnabled(spec.getEnabled());
        }
        specs.put(jobKey, spec);
        refresh(jobKey);
        return true;
    }

    @Override
    /**
     * 按任务键读取定义
    */
    public JobDefinition find(String jobKey) {
        return jobKey == null ? null : table.get(jobKey.trim());
    }

    @Override
    /**
     * 按任务键读取运行态
    */
    public JobRuntime runtime(String jobKey) {
        JobDefinition definition = find(jobKey);
        return definition == null ? null
                : toRuntime(definition, specs.get(jobKey.trim()));
    }

    @Override
    /**
     * Page任务运行态
    */
    public PageResult<JobRuntime> page(JobQuery query) {
        JobQuery condition = query == null ? new JobQuery() : query;
        List<JobRuntime> matched = listGroup(condition.getGroup());
        String keyword = condition.getKeyword() == null ? "" : condition.getKeyword().trim().toLowerCase();
        List<JobRuntime> filtered = new ArrayList<>();
        for (JobRuntime row : matched) {
            if (condition.getStatus() != null && condition.getStatus() != (row.isEnabled() ? 1 : 0)) {
                continue;
            }
            if (!keyword.isEmpty() && !containsIgnoreCase(row.getJobKey(), keyword)
                    && !containsIgnoreCase(row.getJobName(), keyword)
                    && !containsIgnoreCase(row.getDesc(), keyword)) {
                continue;
            }
            filtered.add(row);
        }
        int pageNum = condition.getPageNum() == null || condition.getPageNum() < 1 ? 1 : condition.getPageNum();
        int pageSize = condition.getPageSize() == null || condition.getPageSize() < 1 ? 10 : condition.getPageSize();
        int from = Math.min((pageNum - 1) * pageSize, filtered.size());
        int to = Math.min(from + pageSize, filtered.size());
        return PageResult.<JobRuntime>builder()
                .pageNo(pageNum)
                .pageSize(pageSize)
                .totalPages((filtered.size() + pageSize - 1) / pageSize)
                .total((long) filtered.size())
                .data(new ArrayList<>(filtered.subList(from, to)))
                .build();
    }

    @Override
    /**
     * 按分组列出运行态快照
    */
    public List<JobRuntime> listGroup(String group) {
        List<JobRuntime> rows = new ArrayList<>();
        for (JobDefinition definition : table.values()) {
            JobSpec spec = specs.get(definition.getName());
            if (group != null && !group.isBlank()
                    && !group.trim().equals(blankToDefault(spec == null ? null : spec.getGroup(), DEFAULT_GROUP))) {
                continue;
            }
            rows.add(toRuntime(definition, spec));
        }
        rows.sort(Comparator.comparing(JobRuntime::getJobKey, Comparator.nullsLast(Comparator.naturalOrder())));
        return rows;
    }

    @Override
    /**
     * 改调度配置并立即生效
    */
    public boolean updateSchedule(String jobKey, String scheduleType, String scheduleTime) {
        JobDefinition definition = find(jobKey);
        if (definition == null) {
            return false;
        }
        applySchedule(definition, scheduleType, scheduleTime);
        refresh(jobKey.trim());
        return true;
    }

    @Override
    /**
     * 启停自动调度
    */
    public boolean changeStatus(String jobKey, boolean enabled) {
        JobDefinition definition = find(jobKey);
        if (definition == null) {
            return false;
        }
        definition.setEnabled(enabled);
        refresh(jobKey.trim());
        return true;
    }

    @Override
    /**
     * 删除任务
    */
    public boolean remove(String jobKey) {
        if (jobKey == null || jobKey.isBlank()) {
            return false;
        }
        String key = jobKey.trim();
        boolean removed = table.remove(key) != null;
        specs.remove(key);
        refresh(key);
        return removed;
    }

    @Override
    /**
     * 失效本地触发器缓存与已派发时刻记录
    */
    public void refresh(String jobKey) {
        if (jobKey == null || jobKey.isBlank()) {
            return;
        }
        String key = jobKey.trim();
        triggerCache.remove(key);
        lastFireMs.remove(key);
    }

    /**
     * 把调度类型/配置落到任务定义的 cron 与 fixed 两个维度上 (cron &gt; fixed延迟 &gt; fixedrate)。
     *
     * @param definition   任务定义
     * @param scheduleType 调度类型
     * @param scheduleTime 调度配置
     */
    private void applySchedule(JobDefinition definition, String scheduleType, String scheduleTime) {
        definition.setCron(null);
        definition.setFixedDelayMs(0L);
        definition.setFixedRateMs(0L);
        String time = scheduleTime == null ? null : scheduleTime.trim();
        if (time == null || time.isEmpty() || JobSpec.TYPE_NONE.equalsIgnoreCase(scheduleType)) {
            return;
        }
        if (JobSpec.TYPE_DELAY.equalsIgnoreCase(scheduleType)) {
            definition.setFixedDelayMs(parseMs(time));
        } else if (JobSpec.TYPE_FIXED.equalsIgnoreCase(scheduleType)) {
            definition.setFixedRateMs(parseMs(time));
        } else {
            definition.setCron(time);
        }
    }

    /**
     * 任务定义 + 治理元数据合成运行态快照。
     *
     * @param definition 任务定义
     * @param spec       治理元数据, 可为空
     * @return 快照
     */
    private JobRuntime toRuntime(JobDefinition definition, JobSpec spec) {
        JobRuntime runtime = new JobRuntime();
        String key = definition.getName();
        runtime.setJobNo(spec == null || spec.getJobNo() == null ? key : spec.getJobNo());
        runtime.setJobKey(key);
        runtime.setJobName(definition.getJobName() == null ? key : definition.getJobName());
        runtime.setDesc(definition.getDesc());
        runtime.setGroup(blankToDefault(spec == null ? null : spec.getGroup(), DEFAULT_GROUP));
        runtime.setScheduleType(scheduleTypeOf(definition));
        runtime.setScheduleTime(scheduleTimeOf(definition));
        runtime.setEnabled(definition.isEnabled());
        runtime.setExecuteParam(spec == null ? null : spec.getExecuteParam());
        Long last = lastFireMs.get(key);
        runtime.setLastFireMs(last == null ? 0L : last);
        runtime.setNextFireMs(definition.isEnabled() ? computeNextFireMs(definition) : 0L);
        runtime.setHandlerRegistered(JobHandlerFactory.getInstance().contains(key));
        return runtime;
    }

    /**
     * 由任务定义反推调度类型。
     *
     * @param definition 任务定义
     * @return 调度类型
     */
    private String scheduleTypeOf(JobDefinition definition) {
        if (definition.getCron() != null && !definition.getCron().isBlank()) {
            return JobSpec.TYPE_CRON;
        }
        if (definition.getFixedDelayMs() > 0L) {
            return JobSpec.TYPE_DELAY;
        }
        if (definition.getFixedRateMs() > 0L) {
            return JobSpec.TYPE_FIXED;
        }
        return JobSpec.TYPE_NONE;
    }

    /**
     * 由任务定义反推调度配置。
     *
     * @param definition 任务定义
     * @return 调度配置, 未配置返回 空
     */
    private String scheduleTimeOf(JobDefinition definition) {
        if (definition.getCron() != null && !definition.getCron().isBlank()) {
            return definition.getCron();
        }
        if (definition.getFixedDelayMs() > 0L) {
            return String.valueOf(definition.getFixedDelayMs());
        }
        if (definition.getFixedRateMs() > 0L) {
            return String.valueOf(definition.getFixedRateMs());
        }
        return null;
    }

    /**
     * 现算下一次触发时刻 (不复用派发序列的触发器, 避免只读操作推进调度)。
     *
     * @param definition 任务定义
     * @return 毫秒时间戳, 未配置或表达式非法返回 -1
     */
    private long computeNextFireMs(JobDefinition definition) {
        try {
            Trigger trigger = JobTriggering.createTrigger(definition);
            return JobTriggering.nextFireMs(definition, trigger, 0L, System.currentTimeMillis());
        } catch (Exception ex) {
            return -1L;
        }
    }

    /**
     * 毫秒字串解析, 非法返回 0。
     *
     * @param value 字串
     * @return 毫秒数
     */
    private long parseMs(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    /**
     * 空值兜底。
     *
     * @param value        原值
     * @param defaultValue 兜底值
     * @return 非空原值或兜底值
     */
    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    /**
     * 忽略大小写的包含判定。
     *
     * @param value   原值
     * @param keyword 已转小写的关键字
     * @return 包含返回 true
     */
    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }
}
