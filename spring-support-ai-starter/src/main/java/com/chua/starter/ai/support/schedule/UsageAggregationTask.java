package com.chua.starter.ai.support.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.entity.SysAiUsage;
import com.chua.starter.ai.support.entity.SysAiUsageDaily;
import com.chua.starter.ai.support.entity.settings.AiGlobalSetting;
import com.chua.starter.ai.support.mapper.SysAiUsageDailyMapper;
import com.chua.starter.ai.support.mapper.SysAiUsageMapper;
import com.chua.starter.ai.support.mapper.settings.AiGlobalSettingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * AI 用量聚合任务
 * <p>
 * 采用动态 {@link Trigger} 调度，执行间隔从 {@code ai_global_setting} 表的
 * {@code setting_usage_interval_sec} 字段读取（前端"上报间隔"配置），
 * 未配置时兜底为 1 小时一次。
 * <p>
 * 每次执行聚合上次执行时间点至当前时间内的用量明细数据。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UsageAggregationTask implements SchedulingConfigurer {

    /**
     * 全局设置单条记录主键
     */
    private static final Long SETTING_ID = 1L;

    /**
     * 未配置时的默认执行间隔（秒）
     */
    private static final long DEFAULT_INTERVAL_SEC = 3600;

    /**
     * 用量 Mapper
     */
    private final SysAiUsageMapper usageMapper;

    /**
     * 日汇总 Mapper
     */
    private final SysAiUsageDailyMapper dailyMapper;

    /**
     * 全局设置 Mapper
     */
    private final AiGlobalSettingMapper globalSettingMapper;

    /**
     * 上次聚合开始时间，用于增量聚合
     */
    private volatile LocalDateTime lastRunTime = null;

    /**
     * 注册动态调度任务，每次执行后按配置间隔计算下次执行时间。
     *
     * @param taskRegistrar 调度任务注册器
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        Trigger trigger = this::nextTriggerTime;
        taskRegistrar.addTriggerTask(this::aggregate, trigger);
    }

    /**
     * 计算下次执行时间：读取全局配置间隔，未配置时使用默认值。
     *
     * @param triggerContext 触发器上下文
     * @return 下次执行时间
     */
    private Instant nextTriggerTime(TriggerContext triggerContext) {
        long intervalSec = DEFAULT_INTERVAL_SEC;
        try {
            AiGlobalSetting setting = globalSettingMapper.selectById(SETTING_ID);
            if (setting != null && setting.getSettingUsageIntervalSec() != null
                    && setting.getSettingUsageIntervalSec() > 0) {
                intervalSec = setting.getSettingUsageIntervalSec();
            }
        } catch (Exception e) {
            log.debug("[UsageAggregation] 读取全局设置失败，使用默认间隔 {}", e.getMessage());
        }
        java.util.Date last = triggerContext.lastScheduledExecutionTime();
        if (last == null) {
            return Instant.now().plusMillis(TimeUnit.SECONDS.toMillis(intervalSec));
        }
        return last.toInstant().plusMillis(TimeUnit.SECONDS.toMillis(intervalSec));
    }
    /**
     * 聚合执行入口。
     */
    @Transactional
    public void aggregate() {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = lastRunTime;
        if (start == null) {
            // 首次执行聚合最近一天数据
            start = end.minusDays(1);
        }
        lastRunTime = end;

        LambdaQueryWrapper<SysAiUsage> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(SysAiUsage::getCallTime, start)
                .lt(SysAiUsage::getCallTime, end);
        List<SysAiUsage> list = usageMapper.selectList(wrapper);

        if (list.isEmpty()) {
            log.info("[UsageAggregation] {} ~ {} 无用量数据", start, end);
            return;
        }

        // 按日期、group, provider, model 分组聚合
        Map<String, List<SysAiUsage>> grouped = list.stream()
                .collect(Collectors.groupingBy(u -> u.getCallTime().toLocalDate() + "|" + u.getGroupName()
                        + "|" + u.getProvider() + "|" + u.getModel()));

        for (Map.Entry<String, List<SysAiUsage>> entry : grouped.entrySet()) {
            String[] keys = entry.getKey().split("\\|");
            LocalDate statDate = LocalDate.parse(keys[0]);
            String groupName = keys[1];
            String provider = keys[2];
            String model = keys[3];
            List<SysAiUsage> items = entry.getValue();

            SysAiUsageDaily daily = new SysAiUsageDaily();
            daily.setStatDate(statDate);
            daily.setGroupName(groupName);
            daily.setProvider(provider);
            daily.setModel(model);
            daily.setTotalCalls(items.size());
            daily.setTotalPromptTokens(items.stream().mapToInt(u -> u.getPromptTokens() != null ? u.getPromptTokens() : 0).sum());
            daily.setTotalCompletionTokens(items.stream().mapToInt(u -> u.getCompletionTokens() != null ? u.getCompletionTokens() : 0).sum());
            daily.setTotalTokens(items.stream().mapToInt(u -> u.getTotalTokens() != null ? u.getTotalTokens() : 0).sum());
            daily.setTotalCost(items.stream().mapToDouble(u -> u.getTotalCost() != null ? u.getTotalCost() : 0).sum());

            dailyMapper.insert(daily);
            log.debug("[UsageAggregation] 汇总: date={}, group={}, provider={}, model={}, calls={}",
                    statDate, groupName, provider, model, items.size());
        }
        log.info("[UsageAggregation] {} ~ {} 聚合完成，共 {} 条记录，{} 个分组",
                start, end, list.size(), grouped.size());
    }
}
