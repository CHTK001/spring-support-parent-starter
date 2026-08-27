package com.chua.starter.ai.support.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.ai.AiUsage;
import com.chua.common.support.datasearch.usage.spi.UsageParser;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.entity.SysAiUsage;
import com.chua.starter.ai.support.entity.SysAiUsageDaily;
import com.chua.starter.ai.support.mapper.SysAiUsageDailyMapper;
import com.chua.starter.ai.support.mapper.SysAiUsageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * AI 用量聚合任务
 * <p>
 * 采用动态 {@link Trigger} 调度，执行开关与间隔从 {@code ai_global_setting} 表读取：
 * <ul>
 *   <li>开关：{@code setting_enable_usage_analysis}（前端"启用用量分析"），关闭时跳过执行</li>
 *   <li>间隔：{@code setting_usage_interval_sec}（前端"上报频率"，最小 10 秒），未配置时默认 1 小时</li>
 * </ul>
 * <p>
 * 每次执行：
 * <ol>
 *   <li>通过 datasearch 的 {@link UsageParser} SPI 拉取所有外部工具用量，写入本地用量表</li>
 *   <li>聚合上次执行时间点至当前时间内的用量明细数据到日汇总表</li>
 * </ol>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UsageAggregationTask implements SchedulingConfigurer {

    /**
     * 全局设置键值分组（用量开关与上报间隔）
     */
    private static final String GROUP = "global";

    /**
     * 未配置时的默认执行间隔（秒）
     */
    private static final long DEFAULT_INTERVAL_SEC = 3600;

    /**
     * 最小执行间隔（秒），防止配置过小导致死循环
     */
    private static final long MIN_INTERVAL_SEC = 10;

    /**
     * 全局设置读取工具（分组 global：用量开关与上报间隔）
     */
    private final GlobalSettingReader settingReader;

    /**
     * 用量 Mapper
     */
    private final SysAiUsageMapper usageMapper;

    /**
     * 日汇总 Mapper
     */
    private final SysAiUsageDailyMapper dailyMapper;

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
        taskRegistrar.addTriggerTask(this::run, trigger);
    }

    /**
     * 调度入口：先校验开关，关闭时跳过。
     */
    public void run() {
        if (!isUsageEnabled()) {
            log.debug("[UsageAggregation] 用量分析未启用，跳过本次执行");
            return;
        }
        syncExternalUsage();
        aggregate();
    }

    /**
     * 计算下次执行时间：读取全局配置间隔（最小 10s），未配置时使用默认值。
     *
     * @param triggerContext 触发器上下文
     * @return 下次执行时间
     */
    private Instant nextTriggerTime(TriggerContext triggerContext) {
        long intervalSec = settingReader.getInt(GROUP, "usage_report_interval", (int) DEFAULT_INTERVAL_SEC);
        if (intervalSec < MIN_INTERVAL_SEC) {
            intervalSec = MIN_INTERVAL_SEC;
        }
        java.util.Date last = triggerContext.lastScheduledExecutionTime();
        if (last == null) {
            return Instant.now().plusMillis(TimeUnit.SECONDS.toMillis(intervalSec));
        }
        return last.toInstant().plusMillis(TimeUnit.SECONDS.toMillis(intervalSec));
    }

    /**
     * 判断用量分析是否启用（global 分组 enable_usage_analysis，默认启用）。
     *
     * @return true 表示启用
     */
    private boolean isUsageEnabled() {
        return settingReader.getBool(GROUP, "enable_usage_analysis", true);
    }

    /**
     * 通过 datasearch UsageParser SPI 解析所有外部工具本地用量，写入本地用量表。
     */
    @Transactional
    public void syncExternalUsage() {
        try {
            Map<String, UsageParser> parsers = ServiceProvider.of(UsageParser.class).list();
            if (parsers == null || parsers.isEmpty()) {
                log.debug("[UsageAggregation] 未发现 UsageParser 实现");
                return;
            }
            int total = 0;
            for (Map.Entry<String, UsageParser> entry : parsers.entrySet()) {
                try {
                    List<AiUsage> batch = new ArrayList<>();
                    // 响应式流：与 LocalToolUsageSyncService 保持一致，限流消费后统一落库
                    entry.getValue().streamAll()
                            .doOnNext(batch::add)
                            .blockLast(java.time.Duration.ofSeconds(120));
                    if (batch.isEmpty()) {
                        continue;
                    }
                    for (AiUsage usage : batch) {
                        SysAiUsage record = toSysAiUsage(entry.getKey(), usage);
                        if (record != null) {
                            usageMapper.insert(record);
                            total++;
                        }
                    }
                } catch (Exception e) {
                    log.warn("[UsageAggregation] UsageParser[{}] 同步失败: {}", entry.getKey(), e.getMessage());
                }
            }
            if (total > 0) {
                log.info("[UsageAggregation] 已同步 {} 条外部用量（共 {} 个 Parser）", total, parsers.size());
            }
        } catch (Exception e) {
            log.warn("[UsageAggregation] 外部用量同步失败: {}", e.getMessage());
        }
    }

    /**
     * 将 common 的 AiUsage 映射为本地用量记录。
     *
     * @param providerName Provider 名称（作为 groupName 前缀）
     * @param usage        AiUsage 用量数据
     * @return 本地用量记录
     */
    private SysAiUsage toSysAiUsage(String providerName, AiUsage usage) {
        if (usage == null) {
            return null;
        }
        SysAiUsage record = new SysAiUsage();
        record.setSysAiUsageGroupName(providerName);
        record.setSysAiUsageProvider(usage.getSysAiUsageProvider() != null ? usage.getSysAiUsageProvider() : providerName);
        record.setSysAiUsageModel(usage.getSysAiUsageModel());
        record.setSysAiUsageCallType("external");
        record.setSysAiUsagePromptTokens(usage.getInputTokens());
        record.setSysAiUsageCompletionTokens(usage.getOutputTokens());
        record.setSysAiUsageTotalTokens(usage.getSysAiUsageTotalTokens());
        record.setSysAiUsageInputCost(usage.getInputCost() != null ? usage.getInputCost().doubleValue() : null);
        record.setSysAiUsageOutputCost(usage.getOutputCost() != null ? usage.getOutputCost().doubleValue() : null);
        record.setSysAiUsageTotalCost(usage.getTotalCost() != null ? usage.getTotalCost().doubleValue() : null);
        record.setSysAiUsageRequestId(usage.getRequestId());
        record.setSysAiUsageCallTime(usage.getStartTime() != null
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(usage.getStartTime()), java.time.ZoneId.systemDefault())
                : LocalDateTime.now());
        return record;
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
                .collect(Collectors.groupingBy(u -> u.getSysAiUsageCallTime().toLocalDate() + "|" + u.getSysAiUsageGroupName()
                        + "|" + u.getSysAiUsageProvider() + "|" + u.getSysAiUsageModel()));

        for (Map.Entry<String, List<SysAiUsage>> entry : grouped.entrySet()) {
            String[] keys = entry.getKey().split("\\|");
            LocalDate statDate = LocalDate.parse(keys[0]);
            String groupName = keys[1];
            String provider = keys[2];
            String model = keys[3];
            List<SysAiUsage> items = entry.getValue();

            SysAiUsageDaily daily = new SysAiUsageDaily();
            daily.setSysAiUsageDailyStatDate(statDate);
            daily.setSysAiUsageGroupName(groupName);
            daily.setSysAiUsageProvider(provider);
            daily.setSysAiUsageModel(model);
            daily.setSysAiUsageDailyTotalCalls(items.size());
            daily.setSysAiUsageDailyTotalPromptTokens(items.stream().mapToInt(u -> u.getSysAiUsagePromptTokens() != null ? u.getSysAiUsagePromptTokens() : 0).sum());
            daily.setSysAiUsageDailyTotalCompletionTokens(items.stream().mapToInt(u -> u.getSysAiUsageCompletionTokens() != null ? u.getSysAiUsageCompletionTokens() : 0).sum());
            daily.setSysAiUsageTotalTokens(items.stream().mapToInt(u -> u.getSysAiUsageTotalTokens() != null ? u.getSysAiUsageTotalTokens() : 0).sum());
            daily.setSysAiUsageTotalCost(items.stream().mapToDouble(u -> u.getTotalCost() != null ? u.getTotalCost() : 0).sum());

            dailyMapper.insert(daily);
            log.debug("[UsageAggregation] 汇总: date={}, group={}, provider={}, model={}, calls={}",
                    statDate, groupName, provider, model, items.size());
        }
        log.info("[UsageAggregation] {} ~ {} 聚合完成，共 {} 条记录，{} 个分组",
                start, end, list.size(), grouped.size());
    }
}
