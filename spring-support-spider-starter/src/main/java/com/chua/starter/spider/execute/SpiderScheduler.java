package com.chua.starter.spider.execute;

import com.chua.common.support.task.scheduler.CronExpression;
import com.chua.starter.spider.entity.SpiderDefinition;
import com.chua.starter.spider.service.SpiderDefinitionService;import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

/**
 * 爬虫定时调度器。
 *
 * <p>每隔 1 秒扫描全部已启用的爬虫定义：</p>
 * <ul>
 *   <li>执行类型为 {@code SCHEDULE}（定时）且配置了 cron 表达式的定义，
 *       用 {@link CronExpression} 计算下次执行时间，上次执行之后的下一次触发时间
 *       已到达或超过当前时刻时触发执行一次；</li>
 *   <li>执行类型为 {@code ONCE}（一次）且从未执行过的定义，直接触发一次，执行后
 * 回写 最后一个运行at，此后不再重复触发。</li>
 * </ul>
 *
 * <p>避免"无脑死循环"——每次 tick 都基于 lastRunAt 推算，触发后立即回写
 * 最后一个_运行_at，防止同一秒重复触发。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Component
public class SpiderScheduler {

    /**
     * 定时执行类型标识
     */
    private static final String SCHEDULE = "SCHEDULE";

    /**
     * 一次性执行类型标识
     */
    private static final String ONCE = "ONCE";

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(SpiderScheduler.class);

    /**
     * spawn 时间容差（毫秒），防止时钟抖动
     */
    private static final long TOLERANCE_MS = 1000;

    /**
     * 爬虫定义服务
     */
    private final SpiderDefinitionService definitionService;

    /**
     * 流程执行器
     */
    private final SpiderFlowRunner flowRunner;

    /**
     * 构造调度器。
     *
     * @param definitionService 爬虫定义服务
     * @param flowRunner        流程执行器
     */
    public SpiderScheduler(SpiderDefinitionService definitionService, SpiderFlowRunner flowRunner) {
        this.definitionService = definitionService;
        this.flowRunner = flowRunner;
    }

    /**
     * 每秒触发一次，扫描启用定义并按 cron 命中执行。
     */
    @Scheduled(fixedRate = 1000)
    public void tick() {
        List<SpiderDefinition> enabled = definitionService.listEnabled();
        for (SpiderDefinition definition : enabled) {
            if (!shouldSchedule(definition)) {
                continue;
            }
            if (isDue(definition)) {
                /**
                 * trigger。
                 * @param definition definition
                 */
                trigger(definition);
            }
        }
    }

    /**
     * 判断定义是否参与定时调度。
     *
     * <p>规则：</p>
     * <ul>
     *   <li>执行类型为 {@code SCHEDULE} 且 cron 表达式非空 → 参与调度</li>
     *   <li>执行类型为 {@code ONCE} 且从未执行过（lastRunAt 为空）→ 参与调度，执行后自动置为过期</li>
     * </ul>
     *
     * @param definition 爬虫定义
     * @return 参与调度返回 true
     */
    private boolean shouldSchedule(SpiderDefinition definition) {
        if (ONCE.equalsIgnoreCase(definition.getExecutorType())) {
            return definition.getLastRunAt() == null;
        }
        return SCHEDULE.equalsIgnoreCase(definition.getExecutorType())
                && StringUtils.hasText(definition.getCronExpression());
    }

    /**
     * 判断定义当前是否到触发时间。
     *
     * <p>一次性执行从未跑过时直接放行；定时执行计算上次执行之后的下一次 cron
     * 火点，火点到达当前时间即视为到期。</p>
     *
     * @param definition 爬虫定义
     * @return 到期返回 true；cron 解析失败或从未执行时首秒放行一次
     */
    private boolean isDue(SpiderDefinition definition) {
        if (ONCE.equalsIgnoreCase(definition.getExecutorType())) {
            return definition.getLastRunAt() == null;
        }
        try {
            CronExpression cron = new CronExpression(definition.getCronExpression());
            Date from = toDate(definition.getLastRunAt());
            Date next = cron.getNextValidTimeAfter(from);
            return next == null || next.getTime() <= System.currentTimeMillis() + TOLERANCE_MS;
        } catch (Exception e) {
            log.warn("爬虫 [{}] cron 解析失败: {}", definition.getCode(), e.getMessage());
            return false;
        }
    }

    /**
     * 触发执行一次，成功或失败均回写 最后一个_运行_at，防止同一 cron 周期内重复触发。
     *
     * @param definition 爬虫定义
     */
    private void trigger(SpiderDefinition definition) {
        try {
            log.info("爬虫 [{}] 定时器触发执行", definition.getCode());
            flowRunner.run(definition);
        } catch (Exception e) {
            log.error("爬虫 [{}] 定时执行失败: {}", definition.getCode(), e.getMessage());
        } finally {
            /**
             * mark运行at。
             * @param definition definition
             */
            markRunAt(definition);
        }
    }

    /**
     * 回写 最后一个_运行_at，确保该 cron 周期不再重复触发。
     *
     * @param definition 爬虫定义
     */
    private void markRunAt(SpiderDefinition definition) {
        try {
            definitionService.markRunAtOnly(definition.getId(), java.time.LocalDateTime.now());
        } catch (Exception e) {
            log.warn("爬虫 [{}] 回写 last_run_at 失败: {}", definition.getCode(), e.getMessage());
        }
    }

    /**
     * 时间转换：空值转为"上次零时刻"，使从未执行的定义立即到达首次触发。
     *
     * @param value 时间值
     * @return Date
     */
    private Date toDate(LocalDateTime value) {
        if (value == null) {
            /**
             * 日期。
             * @param 0 0
             * @return 日期的结果
             */
            return new Date(0);
        }
        Date date = Date.from(value.atZone(java.time.ZoneId.systemDefault()).toInstant());
        return date;
    }
}
