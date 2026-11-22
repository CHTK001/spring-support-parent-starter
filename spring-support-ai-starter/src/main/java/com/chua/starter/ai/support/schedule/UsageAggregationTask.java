// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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
import com.chua.starter.ai.support.service.SysAiUsageService;
import com.chua.starter.ai.support.manage.service.SysAiModelMetricsService;
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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
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
     * 日汇总表缺口天数超过该值时改走一次全量回填（逐天补太多次往返）。
     */
    private static final int DAILY_GAP_FULL_BACKFILL_DAYS = 30;

    /**
     * 用量状态：成功
     */
    private static final String STATUS_SUCCESS = "success";

    /**
     * 用量状态：失败（耗时超过会话超时上限被截断，数据保留）
     */
    private static final String STATUS_FAILED = "failed";

    /**
     * 会话超时默认值（分钟）：配置缺失/非法时，入库侧耗时截断的兜底上限。
     */
    private static final int DEFAULT_SESSION_TIMEOUT_MINUTES = 5;

    /**
     * 会话超时配置所在分组（设置中心 → 会话配置）
     */
    private static final String GROUP_SESSION = "session";

    /**
     * 会话超时配置字段名
     */
    private static final String FIELD_SESSION_IDLE_TIMEOUT = "stream_idle_timeout_minutes";

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
     * 当前生效的会话超时上限（毫秒）：入库侧脏耗时截断的依据。
     *
     * <p>配置口径：{@code session.stream_idle_timeout_minutes}（分钟）。
     * 缺失/非法时用默认 5 分钟；显式配置为 0 表示不限制（0=不截断）。
     * 每轮同步开始时刷新一次，配置变更下一轮即生效。</p>
     */
    private volatile long sessionTimeoutMillis;

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
     * 模型指标库服务（按 provider+model 回填本地平均输出速度）
     */
    private final SysAiModelMetricsService sysAiModelMetricsService;

    /**
     * 日汇总表历史自愈是否已在本进程内检查过（置位后不再重复触发全量回填）。
     */
    private final java.util.concurrent.atomic.AtomicBoolean dailyHistoryChecked =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * 待重试的入库失败记录，按数据源分组。
     *
     * <p><b>为什么需要它。</b>有 requestId 的记录下一轮一定会被重新扫到（去重是幂等的），
     * 但没有 requestId 的（opencode 约 40.8 万条、claude-code 约 4.4 万条）走的是
     * 「时间水位」去重：水位取自本组<b>已入库</b>的最大调用时间。一旦某条插入失败，
     * 同批次里更晚的成功记录就会把水位推过它，此后每一轮都在水位判定里被跳过 ——
     * <b>永久丢失，且不报错</b>。队列把这类记录留在内存里跨轮重试。</p>
     *
     * <p>只在内存里，进程重启即失效：这是刻意的取舍 —— 把失败明细落库需要新增表，
     * 而绝大多数失败（缺价）靠重试并不会自愈，重启后大概率还是失败。
     * 真正的兜底是上面那条 ERROR 日志，它让人知道「缺了多少」。</p>
     */
    private final Map<String, Deque<AiUsage>> retryQueue = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 单个数据源的重试队列上限，防止持续失败时把内存吃光。
     *
     * <p>达到上限后丢弃最旧的记录：宁可少救几条，也不能让同步任务本身 OOM。</p>
     */
    private static final int RETRY_QUEUE_LIMIT = 2000;

    /**
     * 单轮外部同步的时间预算（秒）。
     *
     * <p>预算用尽即截断本轮流：解析器按游标续传，下一轮从「上一整页末尾」接着扫。
     * 取 240 秒是为了在「一轮 5 分钟」的调度周期内留出落库与聚合的余量，
     * 同时让 opencode 的旧库（约 150~170 秒/全量）也能在 2~3 轮内补齐。</p>
     */
    private static final int SYNC_BUDGET_SECONDS = 240;

    /**
     * 阻塞等待的宽限（秒）：预算到点会主动取消上游，这里只做兜底，
     * 避免解析器实现不响应取消时把调度线程永久挂住。
     */
    private static final int SYNC_SLACK_SECONDS = 60;

    /**
     * 单页消费条数：与 opencode 解析器的分页大小一致，
     * 保证「一整页消费完 → 游标推进到页末 → 这一页才落库」三者对齐。
     */
    private static final int SYNC_PAGE_SIZE = 2000;

    /**
     * 单次批量 INSERT 的行数：34 列 × 500 = 17000 个占位符，远低于 MySQL 65535 上限。
     */
    private static final int SYNC_INSERT_CHUNK = 500;

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
        boolean timeWheelEnabled = false;
        try {
            timeWheelEnabled = isTimeWheelEnabled();
        } catch (Throwable e) {
            log.warn("[UsageAggregation] 读取时间轮开关失败，回退 Trigger 调度: {}", e.getMessage());
        }
        log.info("[UsageAggregation] configureTasks 注册调度：timewheelEnabled={}", timeWheelEnabled);
        if (timeWheelEnabled) {
            log.info("[UsageAggregation] 用量同步使用后台时间轮调度");
            /**
             * 启动时间wheel。
             */
            startTimeWheel();
            return;
        }
        int interval = settingReader.getInt(GROUP, SETTING_REPORT_INTERVAL, DEFAULT_REPORT_INTERVAL_SEC);
        log.info("[UsageAggregation] 用量同步使用 Trigger 调度，间隔 {} 秒", interval);
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
     * <p>只有控制台「手动触发」或显式打开时间轮开关才会走到这里；定时任务只做当天轻量聚合
     * （DB 端 GROUP BY，秒级、低压力），重的外部工具用量全量拉取 {@link #syncExternalUsage()}
     * 避免长连接把数据库连接池耗尽。</p>
     */
    public void run() {
        try {
            if (!isUsageEnabled()) {
                log.debug("[UsageAggregation] 用量分析未启用，跳过本次执行");
                recordLog(LOG_LEVEL_WARN, "用量分析未启用，跳过本次执行");
                return;
            }
            recordLog(LOG_LEVEL_INFO, "开始定时同步与聚合 (" + java.time.LocalTime.now().withNano(0) + ")");
            self.syncExternalUsage();
            self.aggregateCurrentHour();
            ensureDailyHistoryBackfilled();
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
     * 本地用量解析根目录键（逗号分隔的附加用户主目录；当前用户主目录始终参与）。
     */
    private static final String SETTING_PARSE_DIRS = "usage_parse_dirs";

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
     * 刷新会话超时上限（毫秒），作为本轮入库侧耗时截断的依据。
     *
     * <p>读取 {@code session.stream_idle_timeout_minutes}（分钟）：缺失/非法时
     * 用默认 {@value #DEFAULT_SESSION_TIMEOUT_MINUTES} 分钟；显式配置为 0
     * 表示不限制，此时不截断。每轮同步开始时刷新一次，配置变更下一轮即生效。</p>
     */
    private void refreshSessionTimeout() {
        try {
            Integer minutes = settingReader.getIntOrNull(GROUP_SESSION, FIELD_SESSION_IDLE_TIMEOUT);
            // 0=不限制：不截断任何耗时（但仍标记状态列，便于明细区分）
            sessionTimeoutMillis = (minutes != null && minutes > 0) ? minutes * 60_000L : 0L;
        } catch (Exception e) {
            log.warn("[UsageAggregation] 读取会话超时配置失败，按默认 {} 分钟处理: {}",
                    DEFAULT_SESSION_TIMEOUT_MINUTES, e.getMessage());
            sessionTimeoutMillis = DEFAULT_SESSION_TIMEOUT_MINUTES * 60_000L;
        }
    }

    /**
     * 通过 datasearch usageparser SPI 解析所有外部工具本地用量，写入本地用量表。
     * <p>注意：不使用 {@link Transactional}——外部解析可能耗时（每个 Parser 最多阻塞 120s），
     * 若包在事务里会长时间占用数据库连接导致连接池耗尽。逐条插入由 MyBatis 自动提交。</p>
     * <p><b>消费方式：逐页限流。</b>解析器按游标惰性分页（每页
     * {@value #SYNC_PAGE_SIZE} 行），本方法每消费满一页就批量落库，
     * 单轮时间预算 {@value #SYNC_BUDGET_SECONDS} 秒；预算用尽只是取消本轮，
     * 下一轮从上一整页末尾续扫。旧实现一次性全量装载后 300 秒超时，整批丢弃，
     * 于是「每轮都超时、数据永远入不了库」—— 这是本机 100 万行用量一条都没同步进来的根因。</p>
     */
    public void syncExternalUsage() {
        // 先刷新会话超时上限，本批所有耗时截断判定都用这一轮的值
        refreshSessionTimeout();
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
                GroupSyncContext ctx = new GroupSyncContext(entry.getKey(), maxCallTimeOfGroup(entry.getKey()));
                // 上轮失败的重试批先落一次：它们大多没有 requestId，按时间水位必然被跳过，
                // 必须先于本次流的去重判断执行（retrySet 用**恒等**比较，只豁免重试记录本身）
                List<AiUsage> retryBatch = pollRetry(entry.getKey());
                ctx.retrySet.addAll(retryBatch);
                if (!retryBatch.isEmpty()) {
                    flushBatch(ctx, retryBatch);
                }
                // 单轮时间预算：解析器按游标续传，预算用尽即取消本轮流，
                // 下一轮从「上一整页末尾」接着扫 —— 不会像整批装载那样 300 秒超时后全丢。
                // 截断只对可续传（游标）解析器安全，当前仅 opencode 走这条路。
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(SYNC_BUDGET_SECONDS);
                entry.getValue().streamAll()
                        .takeWhile(ignored -> System.nanoTime() - deadline < 0L)
                        // 页大小与解析器分页一致：一整页消费完，游标恰好推进到页末
                        .buffer(SYNC_PAGE_SIZE)
                        .doOnNext(page -> {
                            ctx.delivered += page.size();
                            try {
                                flushBatch(ctx, page);
                            } catch (Throwable ex) {
                                // 兜底：游标在本页交付时已经推进，异常若逃逸会中断整轮流（后续页不再消费），
                                // 而这一页也不会再被扫到。所以除了留痕，还要整页放回重试队列给第二次机会。
                                // 失败数按整页计 —— 可能高估，因为回退逐条插入时已入库的那部分不会重复插入
                                // （下一轮按 requestId 去重），只是计数偏大。
                                ctx.failed += page.size();
                                for (AiUsage usage : page) {
                                    offerRetry(entry.getKey(), usage);
                                }
                                log.error("[UsageAggregation] [{}] 本页落库异常（{} 条），已整页转入重试队列: {}",
                                        entry.getKey(), page.size(), ex.getMessage(), ex);
                            }
                        })
                        .blockLast(java.time.Duration.ofSeconds(SYNC_BUDGET_SECONDS + SYNC_SLACK_SECONDS));
                /**
                 * record日志。
                 * @param inserted) 日志水平仪信息
                 * @param entry.getKey( entry.获取键(
                 */
                recordLog(LOG_LEVEL_INFO, String.format("[%s] 同步 %d 条用量（新增 %d，跳过 %d）",
                        entry.getKey(), ctx.delivered + retryBatch.size(), ctx.inserted, ctx.skipped));
                if (ctx.failed > 0) {
                    // 失败数必须进日志、且提到 ERROR 级别。此前这里只有一条 WARN，
                    // 且「同步 N 条（新增 M）」看不出 N-M 去哪了：
                    // 对无 requestId 的数据源，这些记录会被后续成功记录推高的水位
                    // 永久跳过 —— 也就是说失败一次，这段用量就从账单上消失了，
                    // 界面上却毫无异常。重试队列能救回内存期内的那部分，
                    // 进程重启后仍未救回的，这里就是唯一的线索。
                    recordLog(LOG_LEVEL_ERROR, String.format(
                            "[%s] 有 %d 条用量入库失败（已入重试队列，重启后仍未入库的即为永久缺失）",
                            entry.getKey(), ctx.failed));
                }
                total += ctx.inserted;
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
     * 落库一页用量：去重 → 映射 → 分批插入。
     *
     * <p>去重是<b>页内集合 + 库内回查</b>两段，不跨页累积：每页落库后，
     * 后续页的回查天然能看到前面页刚插入的行，因此无需把整轮（百万级）的 requestId
     * 全留在内存里；页内集合只用来挡住同一页里的重复行。</p>
     *
     * @param ctx  本轮数据源状态（时间水位、重试集合与计数）
     * @param page 本页记录（解析器交付的一页，或上轮失败的重试批）
     */
    private void flushBatch(GroupSyncContext ctx, List<AiUsage> page) {
        if (page == null || page.isEmpty()) {
            return;
        }
        // 按 requestId 去重：库内已存在的跳过，新记录（含历史缺口/在途完成）照插
        Set<String> existingIds = existingRequestIds(ctx.groupName, page);
        Set<String> seenIds = new HashSet<>(page.size());
        List<AiUsage> accepted = new ArrayList<>(page.size());
        List<SysAiUsage> records = new ArrayList<>(page.size());
        for (AiUsage usage : page) {
            String requestId = usage.getRequestId();
            if (requestId != null && !requestId.isBlank()) {
                // 有稳定请求号：按 requestId 去重，历史缺口（在途/漏采）可在任一次同步补齐
                if (existingIds.contains(requestId) || !seenIds.add(requestId)) {
                    ctx.skipped++;
                    continue;
                }
            } else if (!ctx.retrySet.contains(usage)) {
                // 无请求号：退回时间水位，避免全量重复插入。
                // 重试记录豁免此判断 —— 它们上一轮就是被水位挡下来的，
                // 再走同一道判断等于原地踏步。
                LocalDateTime callTime = usage.getStartTime() != null
                        ? LocalDateTime.ofInstant(Instant.ofEpochMilli(usage.getStartTime()), java.time.ZoneId.systemDefault())
                        : null;
                if (ctx.since != null && (callTime == null || !callTime.isAfter(ctx.since))) {
                    ctx.skipped++;
                    continue;
                }
            }
            SysAiUsage record;
            try {
                record = toSysAiUsage(ctx.groupName, usage);
            } catch (Throwable ex) {
                // 单条失败（缺价/字段异常等）不应拖垮整页
                ctx.failed++;
                offerRetry(ctx.groupName, usage);
                log.warn("[UsageAggregation] [{}] 单条用量转换失败: {} -> {}",
                        ctx.groupName, requestId, ex.getMessage());
                continue;
            }
            if (record == null) {
                // 构造不出记录。这条**必须留痕**：无 requestId 的记录没有第二次机会
                ctx.failed++;
                offerRetry(ctx.groupName, usage);
                continue;
            }
            accepted.add(usage);
            records.add(record);
        }
        insertChunks(ctx, accepted, records);
    }

    /**
     * 分批写入（单批 {@value #SYNC_INSERT_CHUNK} 行）；整批失败时回退逐条插入。
     *
     * <p>回退的意义是保留「单条失败不拖垮整批」的语义：整批失败往往是某一行字段超长，
     * 逐条才能把失败行挑出来进重试队列，其余行照常入库。</p>
     *
     * @param ctx      本轮数据源状态
     * @param accepted 通过去重的原始记录（与 {@code records} 同下标）
     * @param records  待插入的实体（与 {@code accepted} 同下标）
     */
    private void insertChunks(GroupSyncContext ctx, List<AiUsage> accepted, List<SysAiUsage> records) {
        for (int from = 0; from < records.size(); from += SYNC_INSERT_CHUNK) {
            int to = Math.min(from + SYNC_INSERT_CHUNK, records.size());
            List<SysAiUsage> chunk = records.subList(from, to);
            try {
                usageMapper.insertBatch(chunk);
                ctx.inserted += chunk.size();
            } catch (Throwable ex) {
                log.warn("[UsageAggregation] [{}] 批量入库失败（{} 条），回退逐条插入: {}",
                        ctx.groupName, chunk.size(), ex.getMessage());
                for (int i = from; i < to; i++) {
                    AiUsage usage = accepted.get(i);
                    try {
                        usageMapper.insert(records.get(i));
                        ctx.inserted++;
                    } catch (Throwable one) {
                        ctx.failed++;
                        offerRetry(ctx.groupName, usage);
                        log.warn("[UsageAggregation] [{}] 单条用量入库失败: {} -> {}",
                                ctx.groupName, usage.getRequestId(), one.getMessage());
                    }
                }
            }
        }
    }

    /**
     * 单个数据源在一轮同步内的去重集合与计数。
     *
     * <p>为什么需要它：解析器按页惰性交付，而 lambda 里改不了局部变量；
     * 这里只保留<b>与页数无关</b>的状态（时间水位、这一轮的重试记录），
     * 逐行的去重集合留在 {@link #flushBatch} 的页作用域内，避免百万行全量扫描时把内存打满。</p>
     *
     * <p>重试集合用**恒等**比较（{@code IdentityHashMap}）而非 equals：不同调用可能产出
     * 内容相同但时间不同的记录，用 equals 会把「本次真正的新记录」误当成重试项放过水位。</p>
     */
    private static final class GroupSyncContext {

        /**
         * 数据源名称（写入用量分组名）。
         */
        private final String groupName;

        /**
         * 上轮失败待重试的记录。
         */
        private final Set<AiUsage> retrySet =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

        /**
         * 该分组已入库的最大调用时间，用于无 requestId 记录的水位兜底。
         */
        private final LocalDateTime since;

        /**
         * 本轮交付的行数（重试批由调用方补加）。
         */
        private int delivered;

        /**
         * 成功入库行数。
         */
        private int inserted;

        /**
         * 去重跳过行数。
         */
        private int skipped;

        /**
         * 入库失败行数。
         */
        private int failed;

        /**
         * 构造分组同步上下文。
         *
         * @param groupName 分组名称
         * @param since     增量起始时间
         */
        GroupSyncContext(String groupName, LocalDateTime since) {
            this.groupName = groupName;
            this.since = since;
        }
    }

    /**
     * 批量查询指定分组下已存在的 request_id，用于按请求号去重（替代「仅大于时间水位」的增量过滤）。
     * <p>分批 IN 查询，避免一次装载过多绑定参数；查询失败时退化为空集合（后续按 id 去重会重复插入，
     * 但由时间水位兜底的方式仍可控制），不阻断主流程。</p>
     *
     * @param groupName 分组名称
     * @param batch     本批解析出的用量记录
     * @return 已入库的 request_id 集合
     */
    private Set<String> existingRequestIds(String groupName, List<AiUsage> batch) {
        Set<String> ids = new HashSet<>();
        List<String> batchIds = new ArrayList<>();
        for (AiUsage usage : batch) {
            String requestId = usage.getRequestId();
            if (requestId != null && !requestId.isBlank()) {
                batchIds.add(requestId);
            }
        }
        if (batchIds.isEmpty()) {
            return ids;
        }
        int chunk = 500;
        for (int i = 0; i < batchIds.size(); i += chunk) {
            List<String> part = batchIds.subList(i, Math.min(i + chunk, batchIds.size()));
            try {
                List<SysAiUsage> rows = usageMapper.selectList(new LambdaQueryWrapper<SysAiUsage>()
                        .select(SysAiUsage::getSysAiUsageRequestId)
                        .eq(SysAiUsage::getSysAiUsageGroupName, groupName)
                        .in(SysAiUsage::getSysAiUsageRequestId, part));
                for (SysAiUsage row : rows) {
                    if (row.getSysAiUsageRequestId() != null) {
                        ids.add(row.getSysAiUsageRequestId());
                    }
                }
            } catch (Exception e) {
                log.warn("[UsageAggregation] 查询已存在 requestId 失败(group={}): {}", groupName, e.getMessage());
            }
        }
        return ids;
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

        // 2. SPI 注册的外部工具解析器（opencode/codex 等），按配置的解析根目录实例化
        try {
            Map<String, UsageParser> spiParsers = ServiceProvider.of(UsageParser.class).list(resolveParseHomes());
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
     * 解析本地用量根目录配置（{@code usage_parse_dirs}）。
     *
     * <p>配置值为逗号分隔的附加用户主目录；当前用户主目录（{@code user.home}）
     * 始终参与解析并排在首位，无需在配置中重复填写。去重后以逗号分隔串返回，
     * 供 {@code UsageParser} SPI 的 csv 构造参数使用。</p>
     *
     * @return 逗号分隔的解析根目录列表（至少含当前用户主目录）
     */
    private String resolveParseHomes() {
        java.util.LinkedHashSet<String> homes = new java.util.LinkedHashSet<>();
        homes.add(System.getProperty("user.home", "."));
        try {
            String raw = settingReader.getStr(GROUP, SETTING_PARSE_DIRS);
            if (raw != null && !raw.isBlank()) {
                for (String part : raw.split("[,，\\n\\r]+")) {
                    String dir = part.trim();
                    if (!dir.isEmpty()) {
                        homes.add(dir);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[UsageAggregation] 读取解析目录配置失败，仅解析当前用户主目录: {}", e.getMessage());
        }
        return String.join(",", homes);
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
        record.setSysAiUsageCachedTokens(usage.getCacheTokens());
        // 脏耗时收口：耗时（总耗时/首字耗时）超过「会话超时」上限的记录，
        // 一律截断为上限值并标记为失败（数据保留，不丢弃）；正常记录标记成功。
        // 无耗时数据（null）无从判定，按成功处理。
        Long latency = usage.getDurationMillis();
        Long firstLatency = usage.getFirstTokenLatencyMillis();
        boolean timeout = false;
        long effLatency = latency != null ? latency : 0L;
        if (latency != null && sessionTimeoutMillis > 0L && latency > sessionTimeoutMillis) {
            effLatency = sessionTimeoutMillis;
            timeout = true;
        }
        if (firstLatency != null && sessionTimeoutMillis > 0L
                && firstLatency > sessionTimeoutMillis) {
            // 首字耗时超过会话超时本身即失真（首字 ≤ 总耗时，物理上不可能）
            timeout = true;
        }
        record.setSysAiUsageTotalLatency(timeout && latency != null ? sessionTimeoutMillis : latency);
        record.setSysAiUsageFirstTokenLatency(
                timeout && firstLatency != null ? sessionTimeoutMillis : firstLatency);
        record.setSysAiUsageStatus(timeout ? STATUS_FAILED : STATUS_SUCCESS);
        // 解码速度 / 端到端吞吐：用截断后的耗时重算，避免脏耗时派生出不可信速率；
        // 外部已显式填充的速率优先（不覆盖）
        record.setSysAiUsageTokensPerSecond(usage.tokensPerSecond(effLatency));
        record.setSysAiUsageTotalTokensPerSecond(usage.totalTokensPerSecond(effLatency));
        record.setSysAiUsageInferenceStrength("默认");
        record.setSysAiUsageEndpoint("/v1/chat/completions");
        record.setSysAiUsageIp("127.0.0.1");
        record.setSysAiUsageType("external");
        record.setSysAiUsageBillingMode("默认");
        record.setSysAiUsageRequestId(usage.getRequestId());
        // 三方导入记录归属执行同步的当前登录账号（服务端写）
        // 同步跑在调度线程上，没有登录态，currentUserId() 必为 null —— 直接写 null
        // 会让这批数据对所有账号不可见（user_id = 'x' 匹配不到 NULL）。统一兜底到 admin。
        record.setSysAiUsageUserId(com.chua.starter.ai.support.service.SysAiUsageService.resolveOwner(
                com.chua.starter.ai.support.util.AiUserContext.currentUserId()));
        // 标记是否为估算 token
        if (usage.isEstimated()) {
            record.setSysAiUsageEstimatedTokens(1);
        }
        record.setSysAiUsageSource("third_party");
        record.setSysAiUsageCallTime(usage.getStartTime() != null
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(usage.getStartTime()), java.time.ZoneId.systemDefault())
                : LocalDateTime.now());

        // 如果外部工具未提供费用，使用官方价格兜底计算
        Double totalCost = usage.getTotalCost() != null ? usage.getTotalCost().doubleValue() : null;
        if (totalCost == null || totalCost == 0) {
            SysAiPricing pricing = null;
            try {
                pricing = pricingService.getLatestPricing(
                        record.getSysAiUsageProvider(), record.getSysAiUsageModel());
            } catch (Exception e) {
                log.warn("[UsageAggregation] 定价查询失败，按 0 计: provider={}, model={}, err={}",
                        record.getSysAiUsageProvider(), record.getSysAiUsageModel(), e.getMessage());
            }
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
     * 把一条入库失败的记录放进重试队列。
     *
     * @param groupName 数据源
     * @param usage     失败记录
     */
    private void offerRetry(String groupName, AiUsage usage) {
        if (usage == null) {
            return;
        }
        try {
            Deque<AiUsage> q = retryQueue.computeIfAbsent(groupName,
                    k -> new java.util.concurrent.ConcurrentLinkedDeque<>());
            if (q.size() >= RETRY_QUEUE_LIMIT) {
                // 丢最旧的一条并记账：超限本身就说明这个数据源在持续失败，
                // 静默截断会让人以为「只是重试队列满了」
                AiUsage dropped = q.pollFirst();
                if (dropped != null && dropped != usage) {
                    log.warn("[UsageAggregation] [{}] 重试队列已满（{}），丢弃最早的一条失败记录，重启后永久缺失",
                            groupName, RETRY_QUEUE_LIMIT);
                }
            }
            q.addLast(usage);
        } catch (Throwable ex) {
            // 重试队列本身出问题绝不能影响同步主流程
            log.debug("[UsageAggregation] 重试入队失败: {}", ex.getMessage());
        }
    }

    /**
     * 取出并清空某数据源待重试的记录。
     *
     * <p>取走即清空：本轮若仍失败，会由 {@link #offerRetry} 重新入队，
     * 无需在这里保留副本。</p>
     *
     * @param groupName 数据源
     * @return 待重试记录；没有则返回空列表
     */
    private List<AiUsage> pollRetry(String groupName) {
        Deque<AiUsage> q = retryQueue.get(groupName);
        if (q == null || q.isEmpty()) {
            return List.of();
        }
        List<AiUsage> out = new ArrayList<>(q);
        q.clear();
        if (!out.isEmpty()) {
            log.info("[UsageAggregation] [{}] 重试 {} 条上次入库失败的用量", groupName, out.size());
        }
        return out;
    }

    /**
     * 聚合执行入口（默认当前小时）。
     * <p>定时任务与「仅聚合」接口共用：只聚合当前小时 usage 明细到日汇总表，
     * 触发一次汇总一次，扫描量小、压力小。</p>
     */
    public void aggregate() {
        self.aggregateCurrentHour();
    }

    /**
     * 一次性自愈：日汇总表缺历史数据时补齐。
     *
     * <p><b>为什么需要。</b>{@link #run()} 每次只聚合<b>当前小时</b>，历史区间从不回补。
     * 服务停过一天、或某天聚合任务没跑，那天在日汇总表里就是<b>整天空洞</b>；
     * 而用量统计/分布/每日模型趋势现在都以日汇总为主源 —— 空洞会被读成「那天没有用量」，
     * 比慢更糟（静默少算，页面上看不出异常）。</p>
     *
     * <p><b>判据。</b>明细里有、日汇总里没有的日期视为缺口（今天除外：当天还没结束，
     * 由小时聚合持续写入）。只查一次「明细出现过哪些日期 + 汇总出现过哪些日期」，
     * 两者都是小结果集，比逐天 count 更省。</p>
     *
     * <p><b>补法。</b>缺口少时逐天 {@link #aggregateRange}（每次只重算一天，压力可控）；
     * 缺口超过 {@value #DAILY_GAP_FULL_BACKFILL_DAYS} 天时走一次 {@link #aggregateAll()}，
     * 避免上百次往返。</p>
     *
     * <p><b>每进程只跑一次</b>（{@code dailyHistoryChecked} 置位）。回填是重活，
     * 放在调度线程里可接受（不在请求路径上），但绝不该每个周期都跑。</p>
     */
    private void ensureDailyHistoryBackfilled() {
        if (!dailyHistoryChecked.compareAndSet(false, true)) {
            return;
        }
        try {
            List<LocalDate> rawDays = usageMapper.selectDistinctUsageDays();
            if (rawDays == null || rawDays.isEmpty()) {
                return;
            }
            Set<LocalDate> dailyDays = new HashSet<>();
            List<SysAiUsageDaily> dailyRows = dailyMapper.selectList(new LambdaQueryWrapper<SysAiUsageDaily>()
                    .select(SysAiUsageDaily::getStatDate)
                    .groupBy(SysAiUsageDaily::getStatDate));
            for (SysAiUsageDaily row : dailyRows) {
                if (row.getStatDate() != null) {
                    dailyDays.add(row.getStatDate());
                }
            }
            LocalDate today = LocalDate.now();
            List<LocalDate> missing = new ArrayList<>();
            for (LocalDate day : rawDays) {
                // 今天还没结束，由小时聚合持续写入，不算缺口
                if (day != null && !day.equals(today) && !dailyDays.contains(day)) {
                    missing.add(day);
                }
            }
            if (missing.isEmpty()) {
                return;
            }
            missing.sort(LocalDate::compareTo);
            if (missing.size() > DAILY_GAP_FULL_BACKFILL_DAYS) {
                log.info("[UsageAggregation] 日汇总表缺 {} 天（最早 {}），触发一次全量回填",
                        missing.size(), missing.getFirst());
                recordLog(LOG_LEVEL_INFO, String.format("日汇总表缺 %d 天，自动触发全量回填", missing.size()));
                aggregateAll();
                return;
            }
            log.info("[UsageAggregation] 日汇总表缺 {} 天，按天补齐: {}", missing.size(), missing);
            recordLog(LOG_LEVEL_INFO, String.format("日汇总表缺 %d 天，按天补齐", missing.size()));
            for (LocalDate day : missing) {
                self.aggregateRange(day.atStartOfDay(), day.plusDays(1).atStartOfDay());
            }
        } catch (Throwable e) {
            log.warn("[UsageAggregation] 日汇总表历史自愈检查失败（不影响本轮聚合）: {}", e.getMessage());
        }
    }

    /**
     * 聚合当前小时 usage 明细到日汇总表（当前小时 00分00秒 ~ 当前时间）。
     */
    @Transactional
    public void aggregateCurrentHour() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = now;
        long total = self.aggregateRange(start, end);
        /**
         * record日志。
         * @param total) 日志水平仪信息
         * @param total total
         */
        recordLog(LOG_LEVEL_INFO, String.format("小时聚合完成 [%s ~ %s]，共 %d 条记录", start, end, total));
        log.info("[UsageAggregation] 小时聚合完成: range={} ~ {}, records={}", start, end, total);
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

        // 日汇总表按自然日存，只能整天重建：窗口不对齐到整天时，重插只覆盖窗口内几小时，
        // 会把同一天已汇总的其它小时抹掉，统计出口随之少算
        LocalDateTime dayStart = start.toLocalDate().atStartOfDay();
        LocalDateTime dayEnd = LocalTime.MIDNIGHT.equals(end.toLocalTime())
                ? end : end.toLocalDate().plusDays(1).atStartOfDay();

 // 数据库端 群体 BY 聚合，一次返回全部日汇总数据，避免全量装载到 JVM
        List<SysAiUsageMapper.DailyAggregateRow> rows =
                usageMapper.selectDailyAggregate(dayStart, dayEnd, null, false);
        if (rows.isEmpty()) {
            log.info("[UsageAggregation] {} ~ {} 无数据，跳过聚合", dayStart, dayEnd);
            return 0;
        }

        // 先删后插：删除本窗口涉及的「日期 + 账号」在日汇总表中的旧数据。
        // 维度必须是 (date, userId) 而不是只按 date —— 只按日期删会把其它账号当天的
        // 汇总一起删掉，等于每跑一次就清空别人那一天的数据。
        Set<String> dateUserKeys = new TreeSet<>();
        for (SysAiUsageMapper.DailyAggregateRow row : rows) {
            dateUserKeys.add(row.statDate() + GROUP_KEY_SEPARATOR + row.userId());
        }
        for (String key : dateUserKeys) {
            int sep = key.lastIndexOf(GROUP_KEY_SEPARATOR);
            LocalDate d = LocalDate.parse(key.substring(0, sep));
            String uid = key.substring(sep + GROUP_KEY_SEPARATOR.length());
            dailyMapper.delete(new LambdaQueryWrapper<SysAiUsageDaily>()
                    .eq(SysAiUsageDaily::getStatDate, d)
                    .eq(SysAiUsageDaily::getUserId, uid));
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
            daily.setUserId(SysAiUsageService.resolveOwner(row.userId()));
            daily.setTotalCalls(row.totalCalls() != null ? row.totalCalls() : 0L);
            daily.setTotalPromptTokens(promptTokens);
            daily.setTotalCompletionTokens(completionTokens);
            daily.setTotalTokens(row.totalTokens() != null ? row.totalTokens() : 0L);
            daily.setTotalCachedTokens(row.totalCachedTokens() != null ? row.totalCachedTokens() : 0L);
            daily.setTotalLatency(row.totalLatency() != null ? row.totalLatency() : 0L);
            daily.setTotalFirstTokenLatency(row.totalFirstTokenLatency() != null ? row.totalFirstTokenLatency() : 0L);
            // 速度存「合计 + 样本数」，读取侧用 合计/样本数 还原均值（AVG 不可跨行相加）
            daily.setTotalTokensPerSecond(row.totalTokensPerSecond() != null ? row.totalTokensPerSecond() : 0D);
            daily.setTpsSamples(row.tpsSamples() != null ? row.tpsSamples() : 0L);
            // 实际费用只累加明细里真实记录的金额，缺失（未记录 / 免费模型）就是 0。
            // 这里曾用「actualCost != 0 ? actualCost : standardCost」兜底，把实付为 0 的分组
            // 按当前定价估成钱：日汇总合计因此比明细多出约 1,397 元（免费模型被计价），
            // 页面上「总消费」和明细页脚对不上，用户无法核对。估算值只放 standardCost，
            // 由「标准价 / 相对标准价 / 节省」那几处读数单独展示，两者不混。
            double actualCost = row.totalCost() != null ? row.totalCost() : 0.0;
            daily.setTotalCost(actualCost);
            daily.setStandardCost(standardCost.doubleValue());

            dailyMapper.insert(daily);
            totalAggregated += row.totalCalls() != null ? row.totalCalls() : 0;
            log.debug("[UsageAggregation] 汇总: date={}, group={}, provider={}, model={}, calls={}",
                    row.statDate(), row.groupName(), row.provider(), row.model(), row.totalCalls());
        }

        // 按 provider+model 回填本地平均输出速度：有本地用量的写平均值，无本地用量的
        // 保持 NULL（前端/计费回退到 Artificial Analysis 输出速度）——
        // 不抛异常，避免一次回填失败拖垮整次聚合。
        try {
            int backfilled = sysAiModelMetricsService.syncAvgOutputSpeedTps();
            if (backfilled > 0) {
                log.info("[UsageAggregation] 本地平均输出速度回填完成：{} 行", backfilled);
            }
        } catch (Throwable e) {
            log.warn("[UsageAggregation] 回填本地平均输出速度失败: {}", e.getMessage());
        }

        log.info("[UsageAggregation] {} ~ {} 聚合完成，共 {} 条记录", dayStart, dayEnd, totalAggregated);
        return totalAggregated;
    }
}