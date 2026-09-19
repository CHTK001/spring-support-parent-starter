package com.chua.starter.job.support.dispatch;

import com.chua.common.support.task.scheduler.CronTrigger;
import com.chua.common.support.task.scheduler.FixedTrigger;
import com.chua.common.support.task.scheduler.Trigger;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

/**
 * 任务触发时间计算助手 (复用仓库公共 {@link Trigger} 族)。
 *
 * <p>根据 {@link JobDefinition} 的调度类型 (cron / fixedDelay / fixedRate) 选择并复用对应的
 * {@link CronTrigger} / {@link FixedTrigger}, 供 {@link JobRepository} 实现统一计算 "下一次触发时间点",
 * 避免各仓库实现各自重复造轮子。</p>
 *
 * <p>核心方法 {@link #nextFireMs(JobDefinition, Trigger, long, long)} 接受一个可复用的触发器实例与
 * 最近一次触发时间戳, 语义如下:</p>
 * <ul>
 *   <li>cron: 返回 {@code max(now, lastFire)} 之后的下一个 cron 匹配点;</li>
 *   <li>fixedRate / fixedDelay: 复用 {@link FixedTrigger}, 基于 lastFire 推进到下一拍。
 * (fixed延迟 在单机调度下近似 间隔-从-最后一个-fire 语义)</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
public final class JobTriggering {

    /** 创建 作业triggering 实例 */
    private JobTriggering() {
    }

    /**
     * 判断任务是否配置了可调度的触发策略。
     *
     * @param definition 任务定义
     * @return 任一调度配置有效返回 true
     */
    public static boolean hasTrigger(JobDefinition definition) {
        if (definition == null) {
            return false;
        }
        return notBlank(definition.getCron())
                || definition.getFixedDelayMs() > 0L
                || definition.getFixedRateMs() > 0L;
    }

    /**
     * 根据任务定义创建一次性的触发器实例。
     *
     * <p>返回的触发器应在创建后长期复用 (勿每次重建), 以保证 fixed 序列基于一致的 startTime 推进。</p>
     *
     * @param definition 任务定义 (cron / fixed延迟 / fixedrate 三选一, 优先级 cron &gt; fixed延迟 &gt; fixedrate)
     * @return 触发器, 没有任何调度配置时返回 空
     */
    public static Trigger createTrigger(JobDefinition definition) {
        if (definition == null) {
            return null;
        }
        if (notBlank(definition.getCron())) {
            /**
             * crontrigger。
             * @param definition.getCron( definition.获取cron(
             * @return CronTrigger的结果
             */
            return new CronTrigger(definition.getCron());
        }
        if (definition.getFixedDelayMs() > 0L) {
            /**
             * fixedtrigger。
             * @param definition.getFixedDelayMs( definition.getfixed延迟ms(
             * @return FixedTrigger的结果
             */
            return new FixedTrigger(definition.getFixedDelayMs(), TimeUnit.MILLISECONDS);
        }
        if (definition.getFixedRateMs() > 0L) {
            /**
             * fixedtrigger。
             * @param definition.getFixedRateMs( definition.获取fixedratems(
             * @return FixedTrigger的结果
             */
            return new FixedTrigger(definition.getFixedRateMs(), TimeUnit.MILLISECONDS);
        }
        return null;
    }

    /**
     * 计算任务下一次触发时间点 (轮次 毫秒)。
     *
     * @param definition 任务定义
     * @param trigger    复用的触发器实例, 可为 空 (此时视为未配置)
     * @param lastFireMs 上一次触发时间戳, 无历史则为 0 / 任意参考值
     * @param now        当前时间戳
     * @return 下一次触发时间, 未配置或计算失败返回 -1
     */
    public static long nextFireMs(JobDefinition definition, Trigger trigger, long lastFireMs, long now) {
        if (definition == null || trigger == null) {
            return -1L;
        }
        long reference = lastFireMs > 0L ? lastFireMs : now;
        try {
            LocalDateTime from = Instant.ofEpochMilli(reference)
                    .atZone(ZoneId.systemDefault()).toLocalDateTime();
            LocalDateTime next = trigger.nextExecutionTime(from);
            return next == null ? -1L : next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception ex) {
            return -1L;
        }
    }

    /**
     * 文本非空判定。
     *
     * @param value 文本
     * @return 非空返回 true
     */
    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
