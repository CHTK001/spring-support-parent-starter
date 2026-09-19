package com.chua.starter.ai.support.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.ai.AiUsage;
import com.chua.common.support.datasearch.usage.spi.UsageParser;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.entity.SysAiPricing;
import com.chua.starter.ai.support.entity.SysAiUsage;
import com.chua.starter.ai.support.entity.SysAiUsageDaily;
import com.chua.starter.ai.support.entity.SysAiUsageSyncLog;
import com.chua.starter.ai.support.mapper.SysAiUsageDailyMapper;
import com.chua.starter.ai.support.mapper.SysAiUsageMapper;
import com.chua.starter.ai.support.mapper.SysAiUsageSyncLogMapper;
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

import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

/**
 * AI 用量聚合任务
 * <p>
 * 采用动态 {@link Trigger} 调度（默认），执行开关与间隔从 {@code ai_global_setting} 表读取：
 * <ul>
 *   <li>开关：{@code setting_enable_usage_analysis}（前端"启用用量分析"），关闭时跳过执行</li>
 *   <li>间隔：{@code setting_usage_interval_sec}（前端"上报频率"，最小 10 秒），未配置时默认 1 小时</li>
 *   <li>数据源白名单：{@code usage_sync_providers}（Provider SPI 开关，逗号分隔，留空 = 全部）</li>
 *   <li>后台时间轮：{@code usage_timewheel_enabled}（开启后改用时间轮调度）</li>
 * </ul>
 * <p>
 * 每次执行：
 * <ol>
 *   <li>通过 datasearch 的 {@link UsageParser} SPI 拉取所有外部工具用量，写入本地用量表</li>
 *   <li>聚合当天用量明细数据到日汇总表（默认只聚合当天，压力小）</li>
 *   <li>同步过程日志写入 {@code sys_ai_usage_sync_log} 表（前端用量同步日志页持久化查询）</li>
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
     * 同步日志缓冲上限（前端展示最新 100 条）
     */
    private static final int LOG_BUFFER_LIMIT = 100;

    /**
     * 分组键分隔符
     */
    private static final String GROUP_KEY_SEPARATOR = "|";

    /**
     * 同步日志级别：信息
     */
    private static final String LOG_LEVEL_INFO = "INFO";

    /**
     * 同步日志级别：WARN
     */
    private static final String LOG_LEVEL_WARN = "WARN";

    /**
     * 同步日志级别：错误
     */
    private static final String LOG_LEVEL_ERROR = "ERROR";

    /**
     * 最近一次同步运行日志（线程安全，供 /v2/usage/日志 读取）
     */
    private static final java.util.concurrent.ConcurrentLinkedDeque<SyncLogEntry> SYNC_LOGS =
            new java.util.concurrent.ConcurrentLinkedDeque<>();

    /**
     * 同步日志条目
     *
     * @param level     日志级别 信息/WARN/错误
     * @param message   日志内容
     * @param timestamp 时间戳（ISO 格式）
     * @return 同步日志entry的结果
     */
    public record SyncLogEntry(String level, String message, String timestamp) {
    }

    /**
     * 全局设置读取工具（分组 全局：用量开关与上报间隔）
     */
    private final GlobalSettingReader settingReader;

    /**
     * 用量 映射器
     */
    private final SysAiUsageMapper usageMapper;

    /**
     * 日汇总 映射器
     */
    private final SysAiUsageDailyMapper dailyMapper;

    /**
     * 定价服务（无价格记录时按官方价格计算费用）
     */
    private final SysAiPricingService pricingService;

    /**
     * 用量同步日志 映射器（落库持久化）
     */
    private final SysAiUsageSyncLogMapper syncLogMapper;

    /**
     * Spring 应用上下文（用于收集 Spring 容器中的 usageparser 实现）
     */
    @org.springframework.beans.factory.annotation.Autowired
    private ApplicationContext applicationContext; // application上下文

    /**
     * 自引用：方法内部通过代理调用，确保 {@link Transactional} 与 AOP 生效。
     */
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private UsageAggregationTask self; // self

    /**
     * 后台时间轮实例（usage_timewheel_已启用 时启用）
     */
    private volatile com.chua.common.support.task.timer.Timer timeWheel;

    /**
     * 注册动态调度任务：默认 Trigger 调度；开启后台时间轮时改用时间轮。
     *
     * @param taskRegistrar 调度任务注册器
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        if (isTimeWheelEnabled()) {
            log.info("[UsageAggregation] 用量同步使用后台时间轮调度");
            /**
             * 启动时间wheel。
             */
            startTimeWheel();
            return;
        }
        Trigger trigger = this::nextTriggerTime;
        taskRegistrar.addTriggerTask(this::run, trigger);
    }

    /**
     * 关闭时间轮资源。
     */
    @PreDestroy
    public void destroyTimeWheel() {
        if (timeWheel != null) {
            try {
                timeWheel.shutdown();
            } catch (Exception e) {
                log.debug("[UsageAggregation] 时间轮关闭异常: {}", e.getMessage());
            }
            timeWheel = null;
        }
    }

    /**
     * 调度入口：先校验开关，关闭时跳过。
     * <p>定时任务只做当天轻量聚合（DB 端 GROUP BY，秒级、低压力）；
     * 重的外部工具用量全量拉取 {@link #syncExternalUsage()} 仅由手动接口触发，
     * 避免定时任务长连接把数据库连接池耗尽。</p>
     */
    public void run() {
        try {
            // 临时禁用用量分析定时任务，防止JVM崩溃
            boolean disabled = true;
            if (disabled) {
                log.debug("[UsageAggregation] 用量分析定时任务已临时禁用");
                return;
            }
            
            if (!isUsageEnabled()) {
                log.debug("[UsageAggregation] 用量分析未启用，跳过本次执行");
                recordLog(LOG_LEVEL_WARN, "用量分析未启用，跳过本次执行");
                return;
            }
            recordLog(LOG_LEVEL_INFO, "开始定时同步与聚合 (" + java.time.LocalTime.now().withNano(0) + ")");
            self.syncExternalUsage();
            self.aggregateToday();
        } catch (Throwable e) {
            log.error("[UsageAggregation] 定时任务执行异常（已捕获，不影响JVM）: {}", e.getMessage(), e);
            recordLog(LOG_LEVEL_WARN, "定时任务异常: " + e.getMessage());
        }
    }

    /**
     * 记录一次同步日志：写入内存缓冲（前端 /v2/usage/日志 读取）并落库持久化。
     *
     * @param level   级别
     * @param message 内容
     */
    private void recordLog(String level, String message) {
        SYNC_LOGS.addLast(new SyncLogEntry(level, message,
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
        while (SYNC_LOGS.size() > LOG_BUFFER_LIMIT) {
            SYNC_LOGS.pollFirst();
        }
        /**
         * persist日志。
         * @param level 水平仪
         * @param "schedule" "调度"
         * @param message 消息
         */
        persistLog(level, "schedule", message);
    }

    /**
     * 同步日志落库（幂等容错：失败不影响主流程）。
     *
     * @param level   日志级别
     * @param source  来源（调度 / manual / parser）
     * @param message 日志内容
     */
    private void persistLog(String level, String source, String message) {
        try {
            if (syncLogMapper == null) {
                return;
            }
            SysAiUsageSyncLog entry = new SysAiUsageSyncLog();
            entry.setLevel(level);
            entry.setSource(source);
            entry.setMessage(message);
            entry.setCreateTime(LocalDateTime.now());
            syncLogMapper.insert(entry);
        } catch (Exception e) {
            log.debug("[UsageAggregation] 同步日志落库失败: {}", e.getMessage());
        }
    }

    /**
     * 返回最近同步日志（新→旧），供 /v2/usage/日志 接口读取。
     *
     * @return 日志条目列表
     */
    public static List<SyncLogEntry> getSyncLogs() {
        List<SyncLogEntry> copy = new ArrayList<>(SYNC_LOGS);
        java.util.Collections.reverse(copy);
        return copy;
    }

    /**
     * 用量上报间隔键（秒），从全局设置读取；未配置时默认 {@link #DEFAULT_REPORT_INTERVAL_SEC}。
     */
    private static final String SETTING_REPORT_INTERVAL = "usage_report_interval";

    /**
     * 后台时间轮开关键。
     */
    private static final String SETTING_TIMEWHEEL = "usage_timewheel_enabled";

    /**
     * 提供者 SPI 白名单键（逗号分隔，留空 = 全部启用）。
     */
    private static final String SETTING_SYNC_PROVIDERS = "usage_sync_providers";

    /**
     * 默认用量上报间隔（秒）——未配置时取 300 秒（5 分钟）。
     */
    private static final int DEFAULT_REPORT_INTERVAL_SEC = 300;

    /**
     * 计算下次执行时间：按「用量上报间隔（秒）」串行执行。
     * <p>间隔取自全局设置 {@code usage_report_interval}，驱动一次同步/聚合的节奏；
     * 上一次运行完成后，再等待该间隔触发下一次，保证不重叠。
     * 关键：必须基于 {@code lastCompletionTime} 计算，而非「当前时间+间隔」。
     * 否则任务执行耗时超过间隔时，调度器会按当前时间再次触发，造成并发执行，
     * 多个 {@code @Transactional} 任务同时占用数据库连接，耗尽连接池。</p>
     *
     * @param triggerContext 触发器上下文
     * @return 下次执行时间
     */
    private Instant nextTriggerTime(TriggerContext triggerContext) {
        int intervalSec = settingReader.getInt(GROUP, SETTING_REPORT_INTERVAL, DEFAULT_REPORT_INTERVAL_SEC);
        long gapMillis = Math.max(intervalSec, 1) * 1000L;
        java.util.Date lastCompletion = triggerContext.lastCompletionTime();
        if (lastCompletion == null) {
            lastCompletion = triggerContext.lastScheduledExecutionTime();
        }
        Instant base = lastCompletion != null ? lastCompletion.toInstant() : Instant.now();
        return base.plusMillis(gapMillis);
    }

    /**
     * 判断用量分析是否启用（全局 分组 enable_usage_analysis，默认启用）。
     *
     * @return true 表示启用
     */
    private boolean isUsageEnabled() {
        return settingReader.getBool(GROUP, "enable_usage_analysis", true);
    }

    /**
     * 判断是否启用后台时间轮（全局 分组 usage_timewheel_已启用，默认关闭）。
     *
     * @return true 表示启用时间轮
     */
    private boolean isTimeWheelEnabled() {
        return settingReader.getBool(GROUP, SETTING_TIMEWHEEL, false);
    }

    /**
     * 启动时间轮并注册首个 tick。
     */
    private void startTimeWheel() {
        if (timeWheel != null) {
            return;
        }
        timeWheel = com.chua.common.support.task.timer.Timer.newTimer(512, 100, TimeUnit.MILLISECONDS);
        /**
         * 调度下一个wheeltick。
         */
        scheduleNextWheelTick();
    }

    /**
     * 时间轮自续调度：每次执行后按最新配置间隔重新注册下一次。
     */
    private void scheduleNextWheelTick() {
        com.chua.common.support.task.timer.Timer wheel = timeWheel;
        if (wheel == null || !wheel.isRunning()) {
            return;
        }
        int intervalSec = Math.max(settingReader.getInt(GROUP, SETTING_REPORT_INTERVAL, DEFAULT_REPORT_INTERVAL_SEC), 1);
        try {
            wheel.schedule(this::wheelTick, intervalSec, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("[UsageAggregation] 时间轮注册失败: {}", e.getMessage());
        }
    }

    /**
     * 时间轮 tick 执行体。
     */
    private void wheelTick() {
        try {
            /**
             * 运行。
             */
            run();
        } catch (Throwable e) {
            log.error("[UsageAggregation] 时间轮执行失败: {}", e.getMessage(), e);
            /**
             * record日志。
             * @param e.getMessage() 日志水平仪错误
             * @param e.getMessage( e.获取消息(
             */
            recordLog(LOG_LEVEL_ERROR, "时间轮执行失败: " + e.getMessage());
        }
        /**
         * 调度下一个wheeltick。
         */
        scheduleNextWheelTick();
    }

    /**
     * 提供者 SPI 白名单集合（小写去空格）；空集合 = 全部启用。
     *
     * @return 允许同步的数据源名称集合
     */
    private Set<String> allowedProviders() {
        String raw = settingReader.getStr(GROUP, SETTING_SYNC_PROVIDERS);
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        Set<String> allowed = new java.util.LinkedHashSet<>();
        for (String part : raw.split("[,，\\s]+")) {
            String name = part.trim().toLowerCase(Locale.ROOT);
            if (!name.isEmpty()) {
                allowed.add(name);
            }
        }
        return allowed;
    }

    /**
     * 通过 datasearch usageparser SPI 解析所有外部工具本地用量，写入本地用量表。
     * <p>注意：不使用 {@link Transactional}——外部解析可能耗时（每个 Parser 最多阻塞 120s），
     * 若包在事务里会长时间占用数据库连接导致连接池耗尽。逐条插入由 MyBatis 自动提交。</p>
     */
    public void syncExternalUsage() {
        Map<String, UsageParser> parsers = collectParsers();
        if (parsers == null || parsers.isEmpty()) {
            log.debug("[UsageAggregation] 未发现 UsageParser 实现");
            /**
             * record日志。
             * @param LOG_LEVEL_WARN 日志水平仪warn
             * @param "未发现可用用量数据源，跳过外部同步" "未发现可用用量数据源，跳过外部同步"
             */
            recordLog(LOG_LEVEL_WARN, "未发现可用用量数据源，跳过外部同步");
            return;
        }
        Set<String> allowed = allowedProviders();
        if (!allowed.isEmpty()) {
            /**
             * record日志。
             * @param allowed) 日志水平仪信息
             * @param allowed allowed
             */
            recordLog(LOG_LEVEL_INFO, "Provider SPI 白名单生效：" + String.join(", ", allowed));
        }
        int total = 0;
        for (Map.Entry<String, UsageParser> entry : parsers.entrySet()) {
 // 跳过 "系统" parser ——它从 sys_AI_usage 读取（写入目标表），不拉外部数据，避免循环阻塞
            if ("system".equals(entry.getKey())) {
                continue;
            }
 // 提供者 SPI 配置开关：白名单外数据源跳过
            if (!allowed.isEmpty() && !allowed.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                /**
                 * record日志。
                 * @param 白名单中，已跳过" 日志水平仪信息
                 * @param entry.getKey( entry.获取键(
                 */
                recordLog(LOG_LEVEL_INFO, "[" + entry.getKey() + "] 未在 Provider SPI 白名单中，已跳过");
                continue;
            }
            try {
                List<AiUsage> batch = new ArrayList<>();
                // 响应式流：限流消费，避免大表全量装载把内存打满
                entry.getValue().streamAll()
                        .doOnNext(batch::add)
                        .blockLast(java.time.Duration.ofSeconds(300));
                if (batch.isEmpty()) {
                    continue;
                }
 // 增量同步：仅插入 call_时间 晚于库内该 群体 最大时间的记录，
                // 避免全量重复插入拖慢同步，也保证新数据能进来（旧实现按 ASC 排，新数据永远被超时截断）
                LocalDateTime since = maxCallTimeOfGroup(entry.getKey());
                int inserted = 0;
                for (AiUsage usage : batch) {
                    LocalDateTime callTime = usage.getStartTime() != null
                            ? LocalDateTime.ofInstant(Instant.ofEpochMilli(usage.getStartTime()), java.time.ZoneId.systemDefault())
                            : null;
                    if (since != null && (callTime == null || !callTime.isAfter(since))) {
                        continue;
                    }
                    SysAiUsage record = toSysAiUsage(entry.getKey(), usage);
                    if (record != null) {
                        usageMapper.insert(record);
                        inserted++;
                        total++;
                    }
                }
                /**
                 * record日志。
                 * @param inserted) 日志水平仪信息
                 * @param entry.getKey( entry.获取键(
                 */
                recordLog(LOG_LEVEL_INFO, String.format("[%s] 同步 %d 条用量（新增 %d）", entry.getKey(), batch.size(), inserted));
            } catch (Throwable e) {
 // 用 抛出 而非 异常：单个 Parser 的 错误（如缺依赖 no类deffound错误）
                // 也会捕获，避免中断后续 parser 的同步
                log.warn("[UsageAggregation] UsageParser[{}] 同步失败: {}", entry.getKey(), e.getMessage(), e);
                /**
                 * record日志。
                 * @param e.getMessage()) 日志水平仪错误
                 * @param entry.getKey( entry.获取键(
                 */
                recordLog(LOG_LEVEL_ERROR, String.format("[%s] 同步失败: %s", entry.getKey(), e.getMessage()));
            }
        }
        if (total > 0) {
            log.info("[UsageAggregation] 已同步 {} 条外部用量（共 {} 个 Parser）", total, parsers.size());
            /**
             * record日志。
             * @param parsers.size()) 日志水平仪信息
             * @param parsers.size( parsers.大小(
             */
            recordLog(LOG_LEVEL_INFO, String.format("本次共同步 %d 条外部用量（%d 个数据源）", total, parsers.size()));
        } else {
            /**
             * record日志。
             * @param LOG_LEVEL_INFO 日志水平仪信息
             * @param "本次外部同步无新增数据（本地工具无可用记录）" "本次外部同步无新增数据（本地工具无可用记录）"
             */
            recordLog(LOG_LEVEL_INFO, "本次外部同步无新增数据（本地工具无可用记录）");
        }
    }

    /**
     * 查询指定 群体 在 `sys_ai_usage` 中已入库的最大调用时间。
     *
     * @param groupName 分组名称
     * @return 最大调用时间；无记录时返回 空
     */
    private LocalDateTime maxCallTimeOfGroup(String groupName) {
        List<SysAiUsage> list = usageMapper.selectList(new LambdaQueryWrapper<SysAiUsage>()
                .select(SysAiUsage::getSysAiUsageCallTime)
                .eq(SysAiUsage::getSysAiUsageGroupName, groupName)
                .isNotNull(SysAiUsage::getSysAiUsageCallTime)
                .orderByDesc(SysAiUsage::getSysAiUsageCallTime)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.getFirst().getSysAiUsageCallTime();
    }

    /**
     * 收集所有可用 usageparser：优先 Spring 容器（如系统默认 系统usageparser），
     * 再补 SPI 注册的外部工具解析器，并去重。
     *
     * @return name -> parser 映射
     */
    private Map<String, UsageParser> collectParsers() {
        Map<String, UsageParser> allParsers = new LinkedHashMap<>();

 // 1. Spring 容器中的 usageparser Bean
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
     * 将 通用 的 aiusage 映射为本地用量记录。
     *
     * @param providerName 提供者 名称（作为 群体名称 前缀）
     * @param usage        aiusage 用量数据
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
            if (pricing != null && pricing.getInputPricePer1k() != null
                    && pricing.getOutputPricePer1k() != null) {
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
    public void aggregate() {
        self.aggregateToday();
    }

    /**
     * 聚合当天 usage 明细到日汇总表（当天 00:00:00 ~ 当前时间）。
     */
    @Transactional
    public void aggregateToday() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = LocalDateTime.now();
        long total = self.aggregateRange(start, end);
        /**
         * record日志。
         * @param total) 日志水平仪信息
         * @param total total
         */
        recordLog(LOG_LEVEL_INFO, String.format("当日聚合完成 [%s]，共 %d 条记录", today, total));
        log.info("[UsageAggregation] 当日聚合完成: date={}, records={}", today, total);
    }

    /**
     * 手动全量同步：聚合全部历史 usage 明细到日汇总表。
     * <p>一次遍历全部历史数据（分页），按天分组后先删后插；
     * 接口调用方负责异步执行。</p>
     */
    public void aggregateAll() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime earliest = findEarliestUsageTime();
        if (earliest == null) {
            /**
             * record日志。
             * @param LOG_LEVEL_INFO 日志水平仪信息
             * @param "全量同步：无任何用量明细数据" "全量同步：无任何用量明细数据"
             */
            recordLog(LOG_LEVEL_INFO, "全量同步：无任何用量明细数据");
            return;
        }
        LocalDate startDay = earliest.toLocalDate();
        LocalDate endDay = now.toLocalDate();
        LocalDateTime start = startDay.atStartOfDay();
        LocalDateTime end = endDay.plusDays(1).atStartOfDay();
        long total = self.aggregateRange(start, end);
        /**
         * record日志。
         * @param total) 日志水平仪信息
         * @param total total
         */
        recordLog(LOG_LEVEL_INFO, String.format("全量同步完成 [%s ~ %s]，共 %d 条记录", startDay, endDay, total));
        log.info("[UsageAggregation] 全量同步完成: {} ~ {}, records={}", startDay, endDay, total);
    }

    /**
     * 查询最早的用量明细时间。
     *
     * @return 最早调用时间，无数据返回 空
     */
    private LocalDateTime findEarliestUsageTime() {
        LambdaQueryWrapper<SysAiUsage> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(SysAiUsage::getSysAiUsageCallTime)
                .last("LIMIT 1");
        List<SysAiUsage> list = usageMapper.selectList(wrapper);
        return list.isEmpty() ? null : list.getFirst().getSysAiUsageCallTime();
    }

    /**
     * 聚合指定时间范围内的 usage 明细到日汇总表（按天、群体、提供者、模型 分组）。
     * <p>采用「先删后插」策略：同一天重新聚合时先删除旧汇总，避免重复累计。</p>
     *
     * @param start 开始时间（含），不能为 空
     * @param end   结束时间（不含），不能为 空
     * @return 聚合的明细条数
     * @throws NullPointerException 当 启动 或 结束 为 空 时
     */
    @Transactional
    public long aggregateRange(LocalDateTime start, LocalDateTime end) {
        Objects.requireNonNull(start, "start must not be null");
        Objects.requireNonNull(end, "end must not be null");

 // 数据库端 群体 BY 聚合，一次返回全部日汇总数据，避免全量装载到 JVM
        List<SysAiUsageMapper.DailyAggregateRow> rows = usageMapper.selectDailyAggregate(start, end);
        if (rows.isEmpty()) {
            log.info("[UsageAggregation] {} ~ {} 无数据，跳过聚合", start, end);
            return 0;
        }

        // 先删后插：删除本窗口涉及的日期在日汇总表中的旧数据
        Set<LocalDate> dates = new TreeSet<>();
        for (SysAiUsageMapper.DailyAggregateRow row : rows) {
            dates.add(row.statDate());
        }
        for (LocalDate d : dates) {
            dailyMapper.delete(new LambdaQueryWrapper<SysAiUsageDaily>()
                    .eq(SysAiUsageDaily::getStatDate, d));
        }

 // 提供者|模型 -> 定价 缓存，避免对每个分组重复查询（N+1）
        Map<String, SysAiPricing> pricingCache = new LinkedHashMap<>();
        long totalAggregated = 0;
        for (SysAiUsageMapper.DailyAggregateRow row : rows) {
            String pricingKey = row.provider() + GROUP_KEY_SEPARATOR + row.model();
            SysAiPricing pricing = pricingCache.computeIfAbsent(pricingKey,
                    k -> pricingService.getLatestPricing(row.provider(), row.model()));
            BigDecimal inputPrice = pricing != null && pricing.getInputPricePer1k() != null
                    ? BigDecimal.valueOf(pricing.getInputPricePer1k()) : BigDecimal.ZERO;
            BigDecimal outputPrice = pricing != null && pricing.getOutputPricePer1k() != null
                    ? BigDecimal.valueOf(pricing.getOutputPricePer1k()) : BigDecimal.ZERO;

            long promptTokens = row.totalPromptTokens() != null ? row.totalPromptTokens() : 0L;
            long completionTokens = row.totalCompletionTokens() != null ? row.totalCompletionTokens() : 0L;
            BigDecimal standardCost = BigDecimal.valueOf(promptTokens)
                    .divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP)
                    .multiply(inputPrice)
                    .add(BigDecimal.valueOf(completionTokens)
                            .divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP)
                            .multiply(outputPrice));

            SysAiUsageDaily daily = new SysAiUsageDaily();
            daily.setStatDate(row.statDate());
            daily.setGroupName(row.groupName());
            daily.setProvider(row.provider());
            daily.setModel(row.model());
            daily.setTotalCalls(row.totalCalls() != null ? row.totalCalls() : 0L);
            daily.setTotalPromptTokens(promptTokens);
            daily.setTotalCompletionTokens(completionTokens);
            daily.setTotalTokens(row.totalTokens() != null ? row.totalTokens() : 0L);
            daily.setTotalCachedTokens(row.totalCachedTokens() != null ? row.totalCachedTokens() : 0L);
            daily.setTotalLatency(row.totalLatency() != null ? row.totalLatency() : 0L);
            daily.setTotalFirstTokenLatency(row.totalFirstTokenLatency() != null ? row.totalFirstTokenLatency() : 0L);
            // 实际费用缺失（未记录）时用标准价兜底，保证统计口径一致
            double actualCost = row.totalCost() != null ? row.totalCost() : 0.0;
            daily.setTotalCost(actualCost != 0 ? actualCost : standardCost.doubleValue());
            daily.setStandardCost(standardCost.doubleValue());

            dailyMapper.insert(daily);
            totalAggregated += row.totalCalls() != null ? row.totalCalls() : 0;
            log.debug("[UsageAggregation] 汇总: date={}, group={}, provider={}, model={}, calls={}",
                    row.statDate(), row.groupName(), row.provider(), row.model(), row.totalCalls());
        }

        log.info("[UsageAggregation] {} ~ {} 聚合完成，共 {} 条记录", start, end, totalAggregated);
        return totalAggregated;
    }
}
