package com.chua.starter.job.support.dispatch;

import com.chua.common.support.task.scheduler.Trigger;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 内存版任务仓库, 默认实现, 适用于单机测试或调试场景。
 *
 * <p>调度触发时间统一委托给 {@link JobTriggering} (内部复用仓库公共
 * {@link com.chua.common.support.task.scheduler.CronTrigger} / {@link com.chua.common.support.task.scheduler.FixedTrigger}),
 * 支持 cron / fixedDelay / fixedRate 三种调度方式;</p>
 *
 * <p>每个任务的触发器与最近一次触发时间都在内存中维护, 以实现固定的频率/延迟序列推进。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class InMemoryJobRepository implements JobRepository {

    /**
     * 任务存储表
     */
    private final Map<String, JobDefinition> table = new HashMap<>();

    /**
     * 触发器缓存, key 为任务名
     */
    private final Map<String, Trigger> triggerCache = new HashMap<>();

    /**
     * 最近一次触发时间, key 为任务名
     */
    private final Map<String, Long> lastFireMs = new HashMap<>();

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
}
