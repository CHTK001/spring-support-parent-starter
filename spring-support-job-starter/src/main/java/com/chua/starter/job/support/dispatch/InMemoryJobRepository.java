package com.chua.starter.job.support.dispatch;

import com.chua.common.support.task.scheduler.CronExpression;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 内存版任务仓库, 默认实现, 适用于单机测试或调试场景。
 *
 * <p>使用 {@link CronExpression} 解析 cron, 计算任务下一次触发时间,
 * 与 {@code (now + preReadMs)} 比较决定是否到期。</p>
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
     * cron 解析缓存, key 为 cron 字串
     */
    private final Map<String, CronExpression> cronCache = new HashMap<>();

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
        cronCache.remove(definition.getCron());
    }

    /**
     * 查询到期任务。
     *
     * @param now         当前时间戳
     * @param preReadMs   预读时间窗
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
            long nextFire = nextFireTime(definition, now);
            if (nextFire < 0L) {
                continue;
            }
            if (nextFire <= now + preReadMs) {
                due.add(definition);
            }
        }
        if (due.size() > preReadCount) {
            return new ArrayList<>(due.subList(0, preReadCount));
        }
        return due;
    }

    /**
     * 计算任务下一次触发时间 (相对 {@code now})。
     *
     * @param definition 任务定义
     * @param now        当前时间戳
     * @return 下次触发时间, 解析失败返回 -1
     */
    private long nextFireTime(JobDefinition definition, long now) {
        String cron = definition.getCron();
        if (cron == null || cron.isBlank()) {
            return -1L;
        }
        try {
            CronExpression expression = cronCache.get(cron);
            if (expression == null) {
                expression = new CronExpression(cron);
                cronCache.put(cron, expression);
            }
            java.util.Date next = expression.getNextValidTimeAfter(new java.util.Date(now));
            return next == null ? -1L : next.getTime();
        } catch (Exception ex) {
            log.warn("[job] cron 解析失败 name={} cron={} cause={}",
                    definition.getName(), cron, ex.getMessage());
            return -1L;
        }
    }
}
