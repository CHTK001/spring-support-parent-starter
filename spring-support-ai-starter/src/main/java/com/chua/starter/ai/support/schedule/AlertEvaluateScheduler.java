// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.task.timer.Timer;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.entity.SysAiAppKey;
import com.chua.starter.ai.support.entity.SysAiServiceAccessLog;
import com.chua.starter.ai.support.entity.SysAiUsage;
import com.chua.starter.ai.support.entity.settings.SysAiAlert;
import com.chua.starter.ai.support.manage.mapper.SysAiAlertRecordMapper;
import com.chua.starter.ai.support.manage.store.SysAiAlertRecord;
import com.chua.starter.ai.support.mapper.SysAiAppKeyMapper;
import com.chua.starter.ai.support.mapper.SysAiServiceAccessLogMapper;
import com.chua.starter.ai.support.mapper.SysAiUsageMapper;
import com.chua.starter.ai.support.mapper.settings.AiAlertMapper;
import com.chua.starter.common.support.service.MailService;
import com.chua.starter.entity.service.SysMessage;
import com.chua.starter.entity.system.SysUser;
import com.chua.starter.system.service.SysMessageService;
import com.chua.starter.system.service.SysUserService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import com.chua.common.support.utils.CollectionUtils;

/**
 * 告警规则定时评估：按周期把启用的告警规则（sys_ai_alert）真算一遍——
 * 当日费用 / 当日 Token / 当日请求数 / 错误率达到阈值时，按规则勾选的渠道
 * （站内信 / 邮件 / Webhook）推送，成功后写触发留痕（sys_ai_alert_record）。
 *
 * <p>去重口径：{@code (alert_id, stat_date)} 唯一——同一条规则同一天只推一次；
 * 投递失败不写留痕，下一轮评估重试。开关 {@code global.alert_evaluate_enabled}
 * 每轮 tick 实时读取，改配置无需重启；周期 {@code global.alert_evaluate_interval}
 * 秒（最小 30 秒）。</p>
 *
 * <p>指标口径：请求数 / Token / 错误率取自 {@code sys_ai_service_access_log}
 * （per-appKey 规则按 app_key 串过滤，全局规则全表，统计当日零点以来）；
 * 当日费用取自 {@code sys_ai_usage}（按 appKey 归属账号汇总，全局规则全表）。
 * 接收人口径：per-appKey 规则按归属用户（appKey.userId = 登录用户名）解析
 * sys_user 定向投递；全局规则或解析不到归属时广播全员。</p>
 *
 * <p>调度复用 {@link com.chua.common.support.task.timer.Timer} 时间轮（自续调度：
 * 每轮 tick 执行完再注册下一次，天然不重叠），与 {@link CreditRefreshScheduler}
 * 口径一致。投递实现与余额告警（ApiProviderController#dispatchCreditAlert）
 * 同款：逐渠道投递任一成功即成功，站内信复用 common-system-starter 的
 * {@link SysMessageService#broadcast}，邮件复用 common-starter 的
 * {@link MailService#sendNoticeMail}，Webhook 按机器人地址主机自适应卡片格式。</p>
 *
 * @since 4.0.0.62
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEvaluateScheduler {

    /**
     * 全局设置分组：与用量开关同组（sys_ai_global_setting → global）。
     */
    private static final String GROUP = "global";

    /**
     * 告警定时评估开关键。
     */
    private static final String FIELD_ENABLED = "alert_evaluate_enabled";

    /**
     * 告警评估周期键（秒）。
     */
    private static final String FIELD_INTERVAL = "alert_evaluate_interval";

    /**
     * 告警邮件收件人键（逗号分隔）。
     */
    private static final String FIELD_EMAILS = "alert_notify_emails";

    /**
     * 告警 Webhook 地址键。
     */
    private static final String FIELD_WEBHOOK = "alert_notify_webhook";

    /**
     * 启动后首个 tick 的延迟：避开迁移、缓存预热等启动期工作。
     */
    private static final long INITIAL_DELAY_SECONDS = 60;

    /**
     * 默认评估周期：60 秒。
     */
    private static final long DEFAULT_INTERVAL_SECONDS = 60;

    /**
     * 最小评估周期：30 秒（防止误配把数据库打爆）。
     */
    private static final long MIN_INTERVAL_SECONDS = 30;

    /**
     * 指标：当日费用（元）。
     */
    private static final String COND_DAILY_COST = "daily_cost";

    /**
     * 指标：当日 Token 用量。
     */
    private static final String COND_DAILY_TOKENS = "daily_tokens";

    /**
     * 指标：当日请求数。
     */
    private static final String COND_DAILY_REQUESTS = "daily_requests";

    /**
     * 指标：错误率（%）。
     */
    private static final String COND_ERROR_RATE = "error_rate";

    /**
     * 告警规则映射器。
     */
    private final AiAlertMapper alertMapper;

    /**
     * AppKey 映射器（解析规则范围与归属人）。
     */
    private final SysAiAppKeyMapper appKeyMapper;

    /**
     * 服务访问日志映射器（请求数 / Token / 错误率）。
     */
    private final SysAiServiceAccessLogMapper accessLogMapper;

    /**
     * 用量映射器（当日费用）。
     */
    private final SysAiUsageMapper usageMapper;

    /**
     * 告警触发留痕映射器（同规则同日去重 + 留痕）。
     */
    private final SysAiAlertRecordMapper alertRecordMapper;

    /**
     * 消息中心服务（宿主未接该依赖时站内信渠道跳过）。
     */
    private final ObjectProvider<SysMessageService> messageServiceProvider;

    /**
     * 邮件服务（宿主未接 spring.mail.* 时邮件渠道跳过）。
     */
    private final ObjectProvider<MailService> mailServiceProvider;

    /**
     * 系统用户服务（归属用户名 → sysUserId 定向投递）。
     */
    private final ObjectProvider<SysUserService> userServiceProvider;

    /**
     * 全局设置读取（开关 / 周期 / 收件配置每轮实时读）。
     */
    private final GlobalSettingReader settingReader;

    /**
     * Spring 环境（判定 spring.mail.host 是否真的配置，避免 Mock 假成功）。
     */
    private final Environment environment;

    /**
     * JSON 序列化（解析渠道清单 / 组装 Webhook 报文）。
     */
    private final ObjectMapper objectMapper;

    /**
     * 时间轮实例（自续调度）。
     */
    private volatile Timer timeWheel;

    /**
     * 防重入：上一轮未跑完时不叠加新的一轮。
     */
    private final AtomicBoolean evaluating = new AtomicBoolean(false);

    /**
     * Webhook 投递专用 HttpClient（10 秒请求超时在请求级设置）。
     */
    private final HttpClient webhookHttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * 启动时间轮并注册首个 tick。
     */
    @PostConstruct
    public void start() {
        timeWheel = Timer.newTimer(512, 100, TimeUnit.MILLISECONDS);
        timeWheel.schedule(this::tick, INITIAL_DELAY_SECONDS, TimeUnit.SECONDS);
        log.info("[AlertEvaluate] 告警规则定时评估已启动：首评 {} 秒后执行，周期 {} 秒",
                INITIAL_DELAY_SECONDS, DEFAULT_INTERVAL_SECONDS);
    }

    /**
     * 关闭时间轮资源。
     */
    @PreDestroy
    public void stop() {
        Timer wheel = timeWheel;
        timeWheel = null;
        if (wheel != null) {
            try {
                wheel.shutdown();
            } catch (Exception e) {
                log.debug("[AlertEvaluate] 时间轮关闭异常: {}", e.getMessage());
            }
        }
    }

    /**
     * 时间轮 tick：查开关 → 防重入 → 逐规则评估 → 自续注册下一次。
     */
    private void tick() {
        try {
            boolean enabled = settingReader.getBool(GROUP, FIELD_ENABLED, true);
            if (enabled && evaluating.compareAndSet(false, true)) {
                try {
                    evaluateAll();
                } finally {
                    evaluating.set(false);
                }
            }
        } catch (Throwable e) {
            log.error("[AlertEvaluate] 告警规则评估执行异常（已捕获，不影响时间轮）: {}", e.getMessage(), e);
        } finally {
            Timer wheel = timeWheel;
            if (wheel != null && wheel.isRunning()) {
                try {
                    wheel.schedule(this::tick, readIntervalSeconds(), TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.debug("[AlertEvaluate] 时间轮注册失败: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 读取评估周期（秒），下限 {@link #MIN_INTERVAL_SECONDS}。
     */
    private long readIntervalSeconds() {
        try {
            int value = settingReader.getInt(GROUP, FIELD_INTERVAL, (int) DEFAULT_INTERVAL_SECONDS);
            return Math.max(MIN_INTERVAL_SECONDS, value);
        } catch (Exception e) {
            return DEFAULT_INTERVAL_SECONDS;
        }
    }

    /**
     * 评估全部启用规则。
     */
    private void evaluateAll() {
        List<SysAiAlert> rules = alertMapper.selectList(new LambdaQueryWrapper<SysAiAlert>()
                .eq(SysAiAlert::getAlertEnabled, true));
        if (CollectionUtils.isEmpty(rules)) {
            return;
        }
        LocalDateTime todayZero = LocalDate.now().atStartOfDay();
        int triggered = 0;
        for (SysAiAlert rule : rules) {
            try {
                if (evaluateRule(rule, todayZero)) {
                    triggered++;
                }
            } catch (Exception e) {
                log.warn("[AlertEvaluate] 规则 {}（{}）评估失败: {}",
                        rule.getAlertId(), rule.getAlertName(), e.getMessage());
            }
        }
        if (triggered > 0) {
            log.info("[AlertEvaluate] 告警评估完成：{} 条规则触发推送（启用规则共 {} 条）", triggered, rules.size());
        }
    }

    /**
     * 评估单条规则：去重 → 算指标 → 比阈值 → 按渠道投递 → 成功留痕。
     *
     * @return 本轮是否触发并成功推送
     */
    private boolean evaluateRule(SysAiAlert rule, LocalDateTime todayZero) {
        // 同规则同日只推一次：当日已有成功留痕直接跳过（投递失败不写留痕，下轮重试）
        Long existed = alertRecordMapper.selectCount(new LambdaQueryWrapper<SysAiAlertRecord>()
                .eq(SysAiAlertRecord::getAlertId, rule.getAlertId())
                .eq(SysAiAlertRecord::getStatDate, LocalDate.now()));
        if (existed != null && existed > 0) {
            return false;
        }

        String condition = rule.getAlertCondition() == null ? "" : rule.getAlertCondition().trim();
        Long threshold = rule.getAlertThreshold();
        if (threshold == null || threshold < 0) {
            return false;
        }

        // 范围解析：appKeyId 空 / 0 / 查不到 = 全局
        SysAiAppKey appKey = null;
        if (rule.getAlertAppKeyId() != null && rule.getAlertAppKeyId() > 0) {
            appKey = appKeyMapper.selectById(rule.getAlertAppKeyId());
        }

        Metrics metrics = collectMetrics(appKey, todayZero);
        if (metrics == null || !metrics.exceeds(condition, threshold)) {
            return false;
        }

        String scopeName = appKey == null ? "全局"
                : (appKey.getName() != null && !appKey.getName().isBlank() ? appKey.getName() : appKey.getAppKey());
        String title = "AI 用量告警：" + rule.getAlertName();
        String content = buildContent(rule.getAlertName(), scopeName, condition, threshold, metrics);

        List<Integer> receivers = resolveReceivers(appKey);
        List<String> channels = parseChannels(rule.getAlertChannels());

        // 逐渠道投递，任一成功即算成功；实际送达渠道写入留痕
        List<String> delivered = new ArrayList<>(channels.size());
        for (String channel : channels) {
            boolean ok = switch (channel) {
                case "inbox" -> deliverInbox(title, content, receivers);
                case "email" -> deliverEmails(title, content);
                case "webhook" -> deliverWebhook(title, content);
                default -> false;
            };
            if (ok) {
                delivered.add(channel);
            }
        }
        if (delivered.isEmpty()) {
            log.info("[AlertEvaluate] 规则 {}（{}）触发但所有渠道投递失败，保留至下轮重试",
                    rule.getAlertId(), rule.getAlertName());
            return false;
        }
        recordAlert(rule, condition, threshold, metrics, scopeName, receivers, title, content,
                String.join(",", delivered));
        return true;
    }

    /**
     * 当日指标采集：请求数 / Token / 失败数取访问日志，费用取用量表（SQL 聚合，不拉明细）。
     */
    private Metrics collectMetrics(SysAiAppKey appKey, LocalDateTime todayZero) {
        QueryWrapper<SysAiServiceAccessLog> accessWrapper = new QueryWrapper<>();
        accessWrapper.select(
                "COUNT(*) AS agg_requests",
                "COALESCE(SUM(total_tokens), 0) AS agg_tokens",
                "COALESCE(SUM(CASE WHEN status >= 200 AND status < 400 THEN 0 ELSE 1 END), 0) AS agg_failures");
        accessWrapper.ge("create_time", todayZero);
        if (appKey != null && appKey.getAppKey() != null && !appKey.getAppKey().isBlank()) {
            accessWrapper.eq("app_key", appKey.getAppKey());
        }
        List<Map<String, Object>> accessRows = accessLogMapper.selectMaps(accessWrapper);
        long requests = numOf(accessRows, "agg_requests");
        long tokens = numOf(accessRows, "agg_tokens");
        long failures = numOf(accessRows, "agg_failures");

        QueryWrapper<SysAiUsage> usageWrapper = new QueryWrapper<>();
        usageWrapper.select("COALESCE(SUM(sys_ai_usage_total_cost), 0) AS agg_cost");
        usageWrapper.ge("sys_ai_usage_call_time", todayZero);
        if (appKey != null && appKey.getUserId() != null && !appKey.getUserId().isBlank()) {
            usageWrapper.eq("sys_ai_usage_user_id", appKey.getUserId());
        }
        List<Map<String, Object>> usageRows = usageMapper.selectMaps(usageWrapper);
        double cost = numOfDouble(usageRows, "agg_cost");

        return new Metrics(requests, tokens, failures, cost);
    }

    /**
     * 当日指标快照。
     *
     * @param requests 当日请求数
     * @param tokens   当日 Token 用量
     * @param failures 当日失败请求数（status 非 2xx）
     * @param cost     当日费用（元）
     */
    private record Metrics(long requests, long tokens, long failures, double cost) {

        /**
         * 错误率（%）：无请求时为 0。
         */
        double errorRate() {
            return requests <= 0 ? 0.0 : failures * 100.0 / requests;
        }

        /**
         * 按指标名取当前值；未知指标返回 NaN（不触发）。
         */
        double valueOf(String condition) {
            return switch (condition) {
                case COND_DAILY_COST -> cost;
                case COND_DAILY_TOKENS -> tokens;
                case COND_DAILY_REQUESTS -> requests;
                case COND_ERROR_RATE -> errorRate();
                default -> Double.NaN;
            };
        }

        /**
         * 是否达到阈值（&gt;=）。
         */
        boolean exceeds(String condition, long threshold) {
            double value = valueOf(condition);
            return !Double.isNaN(value) && value >= threshold;
        }
    }

    /**
     * 聚合结果取长整型（驱动返回类型可能是 Long / BigDecimal / 字符串）。
     */
    private long numOf(List<Map<String, Object>> rows, String key) {
        Object value = rawOf(rows, key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return new BigDecimal(value.toString()).longValue();
            } catch (Exception ignore) {
                // 落到默认 0
            }
        }
        return 0L;
    }

    /**
     * 聚合结果取双精度。
     */
    private double numOfDouble(List<Map<String, Object>> rows, String key) {
        Object value = rawOf(rows, key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return new BigDecimal(value.toString()).doubleValue();
            } catch (Exception ignore) {
                // 落到默认 0
            }
        }
        return 0.0;
    }

    private Object rawOf(List<Map<String, Object>> rows, String key) {
        if (CollectionUtils.isEmpty(rows) || rows.get(0) == null) {
            return null;
        }
        return rows.get(0).get(key);
    }

    /**
     * 组装推送文案（站内信 / 邮件 / Webhook 共用一份摘要）。
     */
    private String buildContent(String ruleName, String scopeName, String condition, long threshold, Metrics metrics) {
        String metricLabel = switch (condition) {
            case COND_DAILY_COST -> "当日费用";
            case COND_DAILY_TOKENS -> "当日 Token 用量";
            case COND_DAILY_REQUESTS -> "当日请求数";
            case COND_ERROR_RATE -> "当日错误率";
            default -> condition;
        };
        String actual = switch (condition) {
            case COND_DAILY_COST -> String.format("%.2f 元", metrics.cost());
            case COND_DAILY_TOKENS -> metrics.tokens() + " tokens";
            case COND_DAILY_REQUESTS -> metrics.requests() + " 次";
            case COND_ERROR_RATE -> String.format("%.2f%%", metrics.errorRate());
            default -> "-";
        };
        String thresholdText = switch (condition) {
            case COND_DAILY_COST -> threshold + " 元";
            case COND_DAILY_TOKENS -> threshold + " tokens";
            case COND_DAILY_REQUESTS -> threshold + " 次";
            case COND_ERROR_RATE -> threshold + "%";
            default -> String.valueOf(threshold);
        };
        return String.format("【%s】范围：%s；%s 当前 %s，达到阈值 %s（时间：%s）。",
                ruleName, scopeName, metricLabel, actual, thresholdText,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }

    /**
     * 解析站内信接收人：per-appKey 规则按归属用户名查 sys_user 定向投递；
     * 全局规则或解析不到归属时返回空清单（= 消息中心全局广播）。
     */
    private List<Integer> resolveReceivers(SysAiAppKey appKey) {
        if (appKey == null || appKey.getUserId() == null || appKey.getUserId().isBlank()) {
            return List.of();
        }
        SysUserService userService = userServiceProvider.getIfAvailable();
        if (userService == null) {
            return List.of();
        }
        try {
            SysUser user = userService.getOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getSysUserUsername, appKey.getUserId()), false);
            if (user != null && user.getSysUserId() != null) {
                return List.of(user.getSysUserId());
            }
        } catch (Exception e) {
            log.debug("[AlertEvaluate] 解析归属用户失败 userId={} err={}", appKey.getUserId(), e.getMessage());
        }
        return List.of();
    }

    /**
     * 解析渠道清单：规则存的是中文标签 JSON 数组（["站内消息","邮件","Webhook"]），
     * 兼容直接存英文标识；缺省回落站内信。
     */
    private List<String> parseChannels(String channelsJson) {
        if (channelsJson == null || channelsJson.isBlank()) {
            return List.of("inbox");
        }
        try {
            List<String> raw = objectMapper.readValue(channelsJson, new TypeReference<List<String>>() {
            });
            LinkedHashSet<String> channels = new LinkedHashSet<>();
            for (String item : raw) {
                if (item == null) {
                    continue;
                }
                switch (item.trim()) {
                    case "站内消息", "inbox" -> channels.add("inbox");
                    case "邮件", "email" -> channels.add("email");
                    case "Webhook", "webhook" -> channels.add("webhook");
                    default -> log.debug("[AlertEvaluate] 忽略未知告警渠道: {}", item);
                }
            }
            return channels.isEmpty() ? List.of("inbox") : new ArrayList<>(channels);
        } catch (Exception e) {
            log.debug("[AlertEvaluate] 渠道清单解析失败（{}），回落站内信", channelsJson);
            return List.of("inbox");
        }
    }

    /**
     * 站内信渠道：写一条消息到消息中心（sys_message）。
     *
     * <p>receivers 为空表示全局广播（全局规则或归属解析不到），非空为定向投递。
     * 消息中心服务拿不到或写库失败只记日志并返回 false。</p>
     */
    private boolean deliverInbox(String title, String content, List<Integer> receivers) {
        SysMessageService service = messageServiceProvider.getIfAvailable();
        if (service == null) {
            log.debug("[AlertEvaluate] 宿主未接入消息中心，站内信渠道跳过");
            return false;
        }
        SysMessage message = new SysMessage();
        message.setSysMessageTitle(title);
        message.setSysMessageContent(content);
        message.setSysMessageType("warning");
        message.setSysMessageLevel("high");
        message.setSysMessageChannel("PLATFORM");
        message.setSysMessageSenderId(0);
        message.setSysMessageSenderName("AI 告警");
        try {
            ReturnResult<Integer> result = service.broadcast(message, receivers);
            boolean ok = result != null && result.isOk();
            if (ok) {
                log.info("[AlertEvaluate] 告警站内信已推送 receivers={}（空=全局广播）", receivers.size());
            } else {
                log.warn("[AlertEvaluate] 告警站内信写入消息中心失败 msg={}",
                        result == null ? "无返回" : result.getMsg());
            }
            return ok;
        } catch (Exception ex) {
            log.warn("[AlertEvaluate] 告警站内信写入消息中心异常 err={}", ex.getMessage());
            return false;
        }
    }

    /**
     * 邮件渠道：给全局设置 alert_notify_emails 里的地址发通知邮件。
     *
     * <p>先用 Environment 判定 spring.mail.host 是否真的配置了——本地 Mock 的
     * JavaMailSender 会让 MailService 存在但发不出信，这里拦掉避免「假成功」污染留痕。
     * 任一地址发送成功即算成功。</p>
     */
    private boolean deliverEmails(String title, String content) {
        List<String> emails = splitCsv(settingReader.getStr(GROUP, FIELD_EMAILS));
        if (emails.isEmpty()) {
            log.debug("[AlertEvaluate] 邮件渠道未配置收件邮箱，跳过");
            return false;
        }
        MailService service = mailServiceProvider.getIfAvailable();
        if (service == null) {
            log.debug("[AlertEvaluate] 宿主未接入邮件服务，邮件渠道跳过");
            return false;
        }
        if (!environment.containsProperty("spring.mail.host")) {
            log.debug("[AlertEvaluate] 宿主未配置 spring.mail.host，邮件渠道跳过");
            return false;
        }
        String html = "<p>" + content.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;") + "</p>";
        boolean any = false;
        for (String to : emails) {
            try {
                ReturnResult<Void> result = service.sendNoticeMail(to, title, html);
                if (result != null && result.isOk()) {
                    any = true;
                } else {
                    log.warn("[AlertEvaluate] 告警邮件发送失败 to={} msg={}",
                            to, result == null ? "无返回" : result.getMsg());
                }
            } catch (Exception ex) {
                log.warn("[AlertEvaluate] 告警邮件发送异常 to={} err={}", to, ex.getMessage());
            }
        }
        return any;
    }

    /**
     * Webhook 渠道：向全局设置 alert_notify_webhook 的机器人地址 POST 一条卡片消息。
     *
     * <p>按地址主机自动适配卡片格式（飞书 / 企微 / 钉钉与自建）。10 秒超时，
     * 2xx 视为成功；失败只记日志，不打断其它渠道。</p>
     */
    private boolean deliverWebhook(String title, String content) {
        String webhook = settingReader.getStr(GROUP, FIELD_WEBHOOK);
        if (webhook == null || webhook.isBlank()) {
            log.debug("[AlertEvaluate] Webhook 渠道未配置地址，跳过");
            return false;
        }
        String url = webhook.trim();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(webhookBody(url, title, content),
                            StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = webhookHttpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
            if (ok) {
                log.info("[AlertEvaluate] 告警 Webhook 已推送 status={}", response.statusCode());
            } else {
                log.warn("[AlertEvaluate] 告警 Webhook 投递失败 status={} body={}",
                        response.statusCode(), response.body());
            }
            return ok;
        } catch (Exception ex) {
            log.warn("[AlertEvaluate] 告警 Webhook 投递异常 err={}", ex.getMessage());
            return false;
        }
    }

    /**
     * 组装机器人 Webhook 卡片报文，按地址主机适配三种常见格式：
     * 飞书（open.feishu.cn）走 {@code msg_type/text}；企业微信（qyapi.weixin.qq.com）
     * 走 {@code msgtype/markdown}；其余（含钉钉 oapi.dingtalk.com 与自建机器人）
     * 走钉钉的 {@code msgtype/markdown}（带 title，@真人时需要）。
     */
    private String webhookBody(String webhook, String title, String content) {
        URI uri = URI.create(webhook);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String text = "**" + title + "**\n\n" + content;
        Map<String, Object> body = new LinkedHashMap<>();
        if (host.contains("feishu") || host.contains("larksuite")) {
            body.put("msg_type", "text");
            body.put("content", Map.of("text", title + "\n" + content));
        } else if (host.contains("qyapi.weixin.qq.com") || host.contains("weixin") || host.contains("wecom")) {
            body.put("msgtype", "markdown");
            body.put("markdown", Map.of("content", text));
        } else {
            body.put("msgtype", "markdown");
            Map<String, Object> markdown = new LinkedHashMap<>();
            markdown.put("title", title);
            markdown.put("text", text);
            body.put("markdown", markdown);
        }
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception ex) {
            // 理论不可达（纯 Map 序列化）；兜底成最小文本报文
            return "{\"msg_type\":\"text\",\"content\":{\"text\":\"" + title + "\"}}";
        }
    }

    /**
     * 触发留痕：投递成功后追加一行（同规则同日唯一，兼作去重闸）。
     */
    private void recordAlert(SysAiAlert rule, String condition, long threshold, Metrics metrics,
                             String scopeName, List<Integer> receivers, String title, String content,
                             String channels) {
        try {
            SysAiAlertRecord row = new SysAiAlertRecord();
            row.setAlertId(rule.getAlertId());
            row.setAlertName(rule.getAlertName());
            row.setStatDate(LocalDate.now());
            row.setMetric(condition);
            row.setThreshold(threshold);
            row.setActual(metrics.valueOf(condition));
            row.setScope(scopeName);
            row.setChannels(channels);
            row.setReceivers(receivers.stream().map(String::valueOf).collect(Collectors.joining(",")));
            row.setTitle(title);
            row.setContent(content);
            long now = System.currentTimeMillis();
            row.setPushedAt(now);
            row.setCreateTime(now);
            alertRecordMapper.insert(row);
            log.info("[AlertEvaluate] 规则 {}（{}）告警留痕完成，送达渠道: {}",
                    rule.getAlertId(), rule.getAlertName(), channels);
        } catch (Exception ex) {
            log.warn("[AlertEvaluate] 告警留痕失败 alertId={} err={}", rule.getAlertId(), ex.getMessage());
        }
    }

    /**
     * 逗号分隔字符串 → 非空清单（去空白、去空项）。
     */
    private List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(csv.split("[,;，；]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
