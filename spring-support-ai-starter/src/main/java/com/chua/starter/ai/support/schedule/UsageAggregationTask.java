package com.chua.starter.ai.support.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.ai.AiUsage;
import com.chua.common.support.datasearch.usage.spi.UsageParser;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.entity.SysAiPricing;
import com.chua.starter.ai.support.entity.SysAiUsage;
import com.chua.starter.ai.support.entity.SysAiUsageDaily;
import com.chua.starter.ai.support.mapper.SysAiUsageDailyMapper;
import com.chua.starter.ai.support.mapper.SysAiUsageMapper;
import com.chua.starter.ai.support.service.SysAiPricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

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
 *   <li>聚合当天用量明细数据到日汇总表（默认只聚合当天，压力小）</li>
 * </ol>
 * <p>历史数据回填请调用 {@link #aggregateAll()}（手动全量同步），由接口异步触发。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UsageAggregationTask implements SchedulingConfigurer {

    /**
     * 全局设置键值分组（用量开关）
     */
    private static final String GROUP = "global";

    /**
     * 串行执行的间隔（毫秒）：上一次同步/聚合完成后，等待该时长再执行下一次。
     * 不再使用可配置的「用量上报间隔」，改为固定短间隔串行执行。
     */
    private static final long SERIAL_GAP_MILLIS = 5000;

    /**
     * 同步日志缓冲上限
     */
    private static final int LOG_BUFFER_LIMIT = 200;

    /**
     * 最近一次同步运行日志（线程安全，供 /v2/usage/logs 读取）
     */
    private static final java.util.concurrent.ConcurrentLinkedDeque<SyncLogEntry> SYNC_LOGS =
            new java.util.concurrent.ConcurrentLinkedDeque<>();

    /**
     * 同步日志条目
     *
     * @param level     日志级别 INFO/WARN/ERROR
     * @param message   日志内容
     * @param timestamp 时间戳（ISO 格式）
     */
    public record SyncLogEntry(String level, String message, String timestamp) {
    }

    /**
     * 全局设置读取工具（分组 global：用量开关与上报间隔）
     */
    private final GlobalSettingReader settingReader;

    /**
     * 用量 Mapper
     */
    private final SysAiUsageMapper usageMapper;

    /**
     * Spring 应用上下文（用于收集 Spring 容器中的 UsageParser 实现）
     */
    @org.springframework.beans.factory.annotation.Autowired
    private ApplicationContext applicationContext;

    /**
     * 自引用：方法内部通过代理调用，确保 {@link Transactional} 与 AOP 生效。
     */
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private UsageAggregationTask self;

    /**
     * 日汇总 Mapper
     */
    private final SysAiUsageDailyMapper dailyMapper;

    /**
     * 定价服务（无价格记录时按官方价格计算费用）
     */
    private final SysAiPricingService pricingService;

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
            recordLog("WARN", "用量分析未启用，跳过本次执行");
            return;
        }
        recordLog("INFO", "开始定时同步与聚合 (" + java.time.LocalTime.now().withNano(0) + ")");
        syncExternalUsage();
        aggregateToday();
    }

    /**
     * 记录一次同步日志到内存缓冲（供前端用量同步日志页读取）。
     *
     * @param level   级别
     * @param message 内容
     */
    private static void recordLog(String level, String message) {
        SYNC_LOGS.addLast(new SyncLogEntry(level, message,
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
        while (SYNC_LOGS.size() > LOG_BUFFER_LIMIT) {
            SYNC_LOGS.pollFirst();
        }
    }

    /**
     * 返回最近同步日志（新→旧），供 /v2/usage/logs 接口读取。
     *
     * @return 日志条目列表
     */
    public static List<SyncLogEntry> getSyncLogs() {
        List<SyncLogEntry> copy = new ArrayList<>(SYNC_LOGS);
        java.util.Collections.reverse(copy);
        return copy;
    }

    /**
     * 计算下次执行时间：固定短间隔串行执行。
     * 上一次运行完成后，再等待 {@link #SERIAL_GAP_MILLIS} 触发下一次，保证不重叠。
     * <p>关键：必须基于 {@code lastCompletionTime} 计算，而非「当前时间+间隔」。
     * 否则任务执行耗时超过间隔时，调度器会按当前时间再次触发，造成并发执行，
     * 多个 {@code @Transactional} 任务同时占用数据库连接，耗尽连接池。</p>
     *
     * @param triggerContext 触发器上下文
     * @return 下次执行时间
     */
    private Instant nextTriggerTime(TriggerContext triggerContext) {
        java.util.Date lastCompletion = triggerContext.lastCompletionTime();
        if (lastCompletion == null) {
            lastCompletion = triggerContext.lastScheduledExecutionTime();
        }
        Instant base = lastCompletion != null ? lastCompletion.toInstant() : Instant.now();
        return base.plusMillis(SERIAL_GAP_MILLIS);
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
        Map<String, UsageParser> parsers = collectParsers();
        if (parsers == null || parsers.isEmpty()) {
            log.debug("[UsageAggregation] 未发现 UsageParser 实现");
            recordLog("WARN", "未发现可用用量数据源，跳过外部同步");
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
                recordLog("INFO", String.format("[%s] 同步 %d 条用量", entry.getKey(), batch.size()));
            } catch (Exception e) {
                log.warn("[UsageAggregation] UsageParser[{}] 同步失败: {}", entry.getKey(), e.getMessage());
                recordLog("ERROR", String.format("[%s] 同步失败: %s", entry.getKey(), e.getMessage()));
            }
        }
        if (total > 0) {
            log.info("[UsageAggregation] 已同步 {} 条外部用量（共 {} 个 Parser）", total, parsers.size());
            recordLog("INFO", String.format("本次共同步 %d 条外部用量（%d 个数据源）", total, parsers.size()));
        } else {
            recordLog("INFO", "本次外部同步无新增数据（本地工具无可用记录）");
        }
    }

    /**
     * 收集所有可用 UsageParser：优先 Spring 容器（如系统默认 SystemUsageParser），
     * 再补 SPI 注册的外部工具解析器，并去重。
     *
     * @return name -> parser 映射
     */
    private Map<String, UsageParser> collectParsers() {
        Map<String, UsageParser> allParsers = new LinkedHashMap<>();

        // 1. Spring 容器中的 UsageParser Bean
        if (applicationContext != null) {
            try {
                Map<String, UsageParser> springBeans = applicationContext.getBeansOfType(UsageParser.class);
                for (UsageParser parser : springBeans.values()) {
                    if (parser != null && parser.name() != null) {
                        allParsers.putIfAbsent(parser.name(), parser);
                    }
                }
            } catch (Exception e) {
                log.warn("[UsageAggregation] 收集 Spring UsageParser 失败: {}", e.getMessage());
            }
        }

        // 2. SPI 注册的外部工具解析器（opencode/codex 等）
        try {
            Map<String, UsageParser> spiParsers = ServiceProvider.of(UsageParser.class).list();
            if (spiParsers != null) {
                for (Map.Entry<String, UsageParser> entry : spiParsers.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        allParsers.putIfAbsent(entry.getKey(), entry.getValue());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[UsageAggregation] SPI 收集 UsageParser 失败: {}", e.getMessage());
        }
        return allParsers;
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
        record.setSysAiUsageProvider(usage.getProvider() != null ? usage.getProvider() : providerName);
        record.setSysAiUsageModel(usage.getModel());
        record.setSysAiUsageCallType("external");
        record.setSysAiUsagePromptTokens(usage.getInputTokens());
        record.setSysAiUsageCompletionTokens(usage.getOutputTokens());
        record.setSysAiUsageTotalTokens(usage.getTotalTokens());
        record.setSysAiUsageInferenceStrength("默认");
        record.setSysAiUsageEndpoint("/v1/chat/completions");
        record.setSysAiUsageIp("127.0.0.1");
        record.setSysAiUsageType("external");
        record.setSysAiUsageBillingMode("默认");
        record.setSysAiUsageRequestId(usage.getRequestId());
        record.setSysAiUsageCallTime(usage.getStartTime() != null
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(usage.getStartTime()), java.time.ZoneId.systemDefault())
                : LocalDateTime.now());

        // 如果外部工具未提供费用，使用官方价格兜底计算
        Double totalCost = usage.getTotalCost() != null ? usage.getTotalCost().doubleValue() : null;
        if (totalCost == null || totalCost == 0) {
            SysAiPricing pricing = pricingService.getLatestPricing(
                    record.getSysAiUsageProvider(), record.getSysAiUsageModel());
            if (pricing != null) {
                int inputTokens = usage.getInputTokens() != null ? usage.getInputTokens() : 0;
                int outputTokens = usage.getOutputTokens() != null ? usage.getOutputTokens() : 0;
                BigDecimal inputPrice = BigDecimal.valueOf(pricing.getInputPricePer1k());
                BigDecimal outputPrice = BigDecimal.valueOf(pricing.getOutputPricePer1k());
                BigDecimal inputCost = BigDecimal.valueOf(inputTokens)
                        .divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP)
                        .multiply(inputPrice);
                BigDecimal outputCost = BigDecimal.valueOf(outputTokens)
                        .divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP)
                        .multiply(outputPrice);
                totalCost = inputCost.add(outputCost).doubleValue();
                record.setSysAiUsageInputCost(inputCost.doubleValue());
                record.setSysAiUsageOutputCost(outputCost.doubleValue());
                record.setSysAiUsageOriginalCost(totalCost);
            } else {
                totalCost = 0.0;
                record.setSysAiUsageInputCost(0.0);
                record.setSysAiUsageOutputCost(0.0);
                record.setSysAiUsageOriginalCost(0.0);
            }
        }
        record.setSysAiUsageTotalCost(totalCost);
        return record;
    }

    /**
     * 聚合执行入口（默认当天）。
     * <p>定时任务与「仅聚合」接口共用：只聚合当天 usage 明细到日汇总表，
     * 触发一次汇总一次，扫描量小、压力小。</p>
     */
    @Transactional
    public void aggregate() {
        aggregateToday();
    }

    /**
     * 聚合当天 usage 明细到日汇总表（当天 00:00:00 ~ 当前时间）。
     */
    @Transactional
    public void aggregateToday() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = LocalDateTime.now();
        long total = aggregateRange(start, end);
        recordLog("INFO", String.format("当日聚合完成 [%s]，共 %d 条记录", today, total));
        log.info("[UsageAggregation] 当日聚合完成: date={}, records={}", today, total);
    }

    /**
     * 手动全量同步：聚合全部历史 usage 明细到日汇总表。
     * <p>从最早一条用量记录所在日期开始，按天窗口逐段聚合，避免一次性扫描全表
     * 造成内存/连接压力；接口调用方负责异步执行。</p>
     */
    @Transactional
    public void aggregateAll() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime earliest = findEarliestUsageTime();
        if (earliest == null) {
            recordLog("INFO", "全量同步：无任何用量明细数据");
            return;
        }
        LocalDate startDay = earliest.toLocalDate();
        LocalDate endDay = now.toLocalDate();
        long total = 0;
        for (LocalDate day = startDay; !day.isAfter(endDay); day = day.plusDays(1)) {
            total += aggregateRange(day.atStartOfDay(), day.plusDays(1).atStartOfDay());
        }
        recordLog("INFO", String.format("全量同步完成 [%s ~ %s]，共 %d 条记录", startDay, endDay, total));
        log.info("[UsageAggregation] 全量同步完成: {} ~ {}, records={}", startDay, endDay, total);
    }

    /**
     * 查询最早的用量明细时间。
     *
     * @return 最早调用时间，无数据返回 null
     */
    private LocalDateTime findEarliestUsageTime() {
        LambdaQueryWrapper<SysAiUsage> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(SysAiUsage::getSysAiUsageCallTime)
                .last("LIMIT 1");
        List<SysAiUsage> list = usageMapper.selectList(wrapper);
        return list.isEmpty() ? null : list.get(0).getSysAiUsageCallTime();
    }

    /**
     * 聚合指定时间范围内的 usage 明细到日汇总表（按天、group、provider、model 分组）。
     * <p>采用「先删后插」策略：同一天重新聚合时先删除旧汇总，避免重复累计。</p>
     *
     * @param start 开始时间（含）
     * @param end   结束时间（不含）
     * @return 聚合的明细条数
     */
    @Transactional
    public long aggregateRange(LocalDateTime start, LocalDateTime end) {
        LocalDateTime queryStart = start;
        LocalDateTime queryEnd = end;
        if (queryStart == null) {
            queryStart = LocalDate.now().atStartOfDay();
        }
        if (queryEnd == null) {
            queryEnd = LocalDateTime.now();
        }

        int batchSize = 10000;
        long totalAggregated = 0;
        Map<LocalDate, Map<String, List<SysAiUsage>>> byDate = new java.util.TreeMap<>();

        Page<SysAiUsage> page;
        int p = 1;
        do {
            LambdaQueryWrapper<SysAiUsage> wrapper = new LambdaQueryWrapper<>();
            wrapper.ge(SysAiUsage::getSysAiUsageCallTime, queryStart)
                    .lt(SysAiUsage::getSysAiUsageCallTime, queryEnd)
                    .orderByAsc(SysAiUsage::getSysAiUsageId);
            page = usageMapper.selectPage(new Page<>(p, batchSize), wrapper);
            List<SysAiUsage> list = page.getRecords();

            if (list.isEmpty()) {
                break;
            }

            for (SysAiUsage u : list) {
                LocalDate d = u.getSysAiUsageCallTime().toLocalDate();
                String key = u.getSysAiUsageGroupName() + "|" + u.getSysAiUsageProvider() + "|" + u.getSysAiUsageModel();
                byDate.computeIfAbsent(d, k -> new LinkedHashMap<>())
                        .computeIfAbsent(key, k -> new ArrayList<>())
                        .add(u);
            }
            totalAggregated += list.size();
            p++;
        } while (page != null && page.hasNext());

        if (totalAggregated == 0) {
            log.info("[UsageAggregation] {} ~ {} 无数据，跳过聚合", queryStart, queryEnd);
            return 0;
        }

        // 先删后插：删除本窗口涉及的日期在日汇总表中的旧数据
        for (LocalDate d : byDate.keySet()) {
            dailyMapper.delete(new LambdaQueryWrapper<SysAiUsageDaily>()
                    .eq(SysAiUsageDaily::getStatDate, d));
        }

        for (Map.Entry<LocalDate, Map<String, List<SysAiUsage>>> dateEntry : byDate.entrySet()) {
            LocalDate statDate = dateEntry.getKey();
            for (Map.Entry<String, List<SysAiUsage>> entry : dateEntry.getValue().entrySet()) {
                String[] keys = entry.getKey().split("\\|");
                String groupName = keys[0];
                String provider = keys[1];
                String model = keys[2];
                List<SysAiUsage> items = entry.getValue();

                BigDecimal totalCachedTokens = BigDecimal.ZERO;
                BigDecimal totalLatency = BigDecimal.ZERO;
                BigDecimal totalCost = BigDecimal.ZERO;
                BigDecimal standardCost = BigDecimal.ZERO;

                for (SysAiUsage item : items) {
                    int promptTokens = item.getSysAiUsagePromptTokens() != null ? item.getSysAiUsagePromptTokens() : 0;
                    int completionTokens = item.getSysAiUsageCompletionTokens() != null ? item.getSysAiUsageCompletionTokens() : 0;
                    int cachedTokens = item.getSysAiUsageCachedTokens() != null ? item.getSysAiUsageCachedTokens() : 0;
                    long latency = item.getSysAiUsageTotalLatency() != null ? item.getSysAiUsageTotalLatency() : 0;
                    totalCachedTokens = totalCachedTokens.add(BigDecimal.valueOf(cachedTokens));
                    totalLatency = totalLatency.add(BigDecimal.valueOf(latency));

                    SysAiPricing pricing = pricingService.getLatestPricing(provider, model);
                    BigDecimal inputPrice = pricing != null && pricing.getInputPricePer1k() != null
                            ? BigDecimal.valueOf(pricing.getInputPricePer1k()) : BigDecimal.ZERO;
                    BigDecimal outputPrice = pricing != null && pricing.getOutputPricePer1k() != null
                            ? BigDecimal.valueOf(pricing.getOutputPricePer1k()) : BigDecimal.ZERO;
                    BigDecimal tokenCost = BigDecimal.valueOf(promptTokens)
                            .divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP)
                            .multiply(inputPrice)
                            .add(BigDecimal.valueOf(completionTokens)
                                    .divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP)
                                    .multiply(outputPrice));
                    standardCost = standardCost.add(tokenCost);
                    Double itemCost = item.getSysAiUsageTotalCost();
                    totalCost = totalCost.add((itemCost != null && itemCost != 0)
                            ? BigDecimal.valueOf(itemCost) : tokenCost);
                }

                SysAiUsageDaily daily = new SysAiUsageDaily();
                daily.setStatDate(statDate);
                daily.setGroupName(groupName);
                daily.setProvider(provider);
                daily.setModel(model);
                daily.setTotalCalls(items.size());
                daily.setTotalPromptTokens(items.stream().mapToInt(u -> u.getSysAiUsagePromptTokens() != null ? u.getSysAiUsagePromptTokens() : 0).sum());
                daily.setTotalCompletionTokens(items.stream().mapToInt(u -> u.getSysAiUsageCompletionTokens() != null ? u.getSysAiUsageCompletionTokens() : 0).sum());
                daily.setTotalTokens(items.stream().mapToInt(u -> u.getSysAiUsageTotalTokens() != null ? u.getSysAiUsageTotalTokens() : 0).sum());
                daily.setTotalCachedTokens(totalCachedTokens.intValueExact());
                daily.setTotalLatency(totalLatency.longValueExact());
                daily.setTotalCost(totalCost.doubleValue());
                daily.setStandardCost(standardCost.doubleValue());

                dailyMapper.insert(daily);
                log.debug("[UsageAggregation] 汇总: date={}, group={}, provider={}, model={}, calls={}",
                        statDate, groupName, provider, model, items.size());
            }
        }

        log.info("[UsageAggregation] {} ~ {} 聚合完成，共 {} 条记录", queryStart, queryEnd, totalAggregated);
        return totalAggregated;
    }
}
