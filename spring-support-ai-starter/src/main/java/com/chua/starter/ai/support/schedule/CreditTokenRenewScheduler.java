// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.schedule;

import com.chua.common.support.task.timer.Timer;
import com.chua.starter.account.channel.service.AccountChannelService;
import com.chua.starter.account.channel.store.SysAccountChannel;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.manage.api.ApiProviderController;
import com.chua.starter.ai.support.manage.service.ProviderCreditLoginService;
import com.chua.starter.datasearch.credit.support.CreditTokens;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 余额令牌自动续期任务：巡检「账密登录」供应商的余额令牌，到期前用无头浏览器
 * 回访上游门户静默续期，避免令牌过期后反复人工重登。
 *
 * <p>与 {@link CreditRefreshScheduler} 的分工：余额刷新保「读数新鲜」，本任务保
 * 「令牌活着」。巡检周期 60 秒，逐供应商判定是否到点，到点才出网回访（无头、
 * 无人值守）；判定口径见 {@link #isDue(Map, long)}：</p>
 * <ul>
 *   <li>JWT 令牌（解得出 exp）：到期前 {@code credit_renew_ahead_minutes}
 *       （默认 30 分钟）即续；</li>
 *   <li>非 JWT 令牌（如 Moss 的不透明 Cookie）：按上次续期时间 +
 *       {@code credit_renew_interval_minutes}（默认 720 分钟）周期回访保活。</li>
 * </ul>
 *
 * <p>续期执行与状态落库在 {@link ApiProviderController#renewProviderCreditToken}——
 * 与余额刷新同一条复用路径（候选实现 / 截获钩子 / extra 回写 / 告警管线都在
 * 控制器闭环），本任务只负责「何时该续」与运行计数。全局开关
 * {@code global.credit_renew_enabled} 每轮 tick 实时读取，改配置无需重启；
 * 渠道级开关 {@code extra.credit_renew_enabled} 缺省视为开启。</p>
 *
 * <p>调度复用 {@link com.chua.common.support.task.timer.Timer} 时间轮（自续调度：
 * 每轮 tick 执行完再注册下一次，天然不重叠），与余额刷新任务同一套口径。</p>
 *
 * @since 4.0.0.61
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreditTokenRenewScheduler {

    /**
     * 全局设置分组：与余额刷新开关同组（sys_ai_global_setting → global）。
     */
    private static final String GROUP = "global";

    /**
     * 余额令牌自动续期开关键。
     */
    private static final String FIELD_ENABLED = "credit_renew_enabled";

    /**
     * 到期前提前续期窗口（分钟）：extra.credit_renew_ahead_minutes 缺省值。
     */
    public static final long DEFAULT_AHEAD_MINUTES = 30;

    /**
     * 无到期时间令牌的保活回访周期（分钟）：extra.credit_renew_interval_minutes 缺省值。
     */
    public static final long DEFAULT_INTERVAL_MINUTES = 720;

    /**
     * 巡检周期：60 秒。判定本身只是读内存 extra，出网回访只在到点时发生。
     */
    private static final long INTERVAL_SECONDS = 60;

    /**
     * 启动后首个 tick 的延迟：避开迁移、缓存预热等启动期工作。
     */
    private static final long INITIAL_DELAY_SECONDS = 30;

    /**
     * 渠道服务（取已启用供应商清单）。
     */
    private final AccountChannelService channelService;

    /**
     * 供应商管理 API：续期执行、状态回写与告警管线都在控制器里，直接复用。
     */
    private final ApiProviderController providerController;

    /**
     * 全局设置读取（开关每轮实时读）。
     */
    private final GlobalSettingReader settingReader;

    /**
     * 时间轮实例（自续调度）。
     */
    private volatile Timer timeWheel;

    /**
     * 防重入：上一轮未跑完时不叠加新的一轮。
     */
    private final AtomicBoolean renewing = new AtomicBoolean(false);

    /**
     * 最近一轮开关是否放行（实时开关状态以 {@link #status()} 现读为准）。
     */
    private volatile boolean lastEnabled = true;

    /**
     * 最近一轮开始时间（epoch 毫秒；0 = 尚未执行过）。
     */
    private volatile long lastRunAt;

    /**
     * 最近一轮耗时（毫秒）。
     */
    private volatile long lastDurationMs;

    /**
     * 最近一轮扫描的已启用供应商数。
     */
    private volatile int lastProviders;

    /**
     * 最近一轮到点（真正出网回访）的供应商数。
     */
    private volatile int lastDue;

    /**
     * 最近一轮续期成功（平台轮换令牌）的供应商数。
     */
    private volatile int lastRenewed;

    /**
     * 最近一轮续期失败（需人工重登）的供应商数。
     */
    private volatile int lastRejected;

    /**
     * 下一次计划执行时间（epoch 毫秒；0 = 未排定 / 已停止）。
     */
    private volatile long nextRunAt;

    /**
     * 定时续期运行状态快照（供 {@code /credit/renew-status} 可观测端点回显）。
     *
     * @param enabled        开关当前是否打开（实时读设置，改配置立即反映）
     * @param running        是否正在执行一轮巡检
     * @param intervalSeconds 巡检周期（秒）
     * @param lastRunAt      最近一轮开始时间（epoch 毫秒；0 = 尚未执行过）
     * @param lastDurationMs 最近一轮耗时（毫秒）
     * @param nextRunAt      下一次计划执行时间（epoch 毫秒；0 = 未排定 / 已停止）
     * @param providers      最近一轮扫描的已启用供应商数
     * @param due            最近一轮到点出网回访的供应商数
     * @param renewed        最近一轮续期成功（轮换令牌）的供应商数
     * @param rejected       最近一轮续期失败（需人工重登）的供应商数
     */
    public record RenewStatus(boolean enabled, boolean running, int intervalSeconds,
                              long lastRunAt, long lastDurationMs, long nextRunAt,
                              int providers, int due, int renewed, int rejected) {
    }

    /**
     * 当前运行状态快照。
     *
     * @return 状态快照
     */
    public RenewStatus status() {
        return new RenewStatus(settingReader.getBool(GROUP, FIELD_ENABLED, true), renewing.get(),
                (int) INTERVAL_SECONDS, lastRunAt, lastDurationMs, nextRunAt,
                lastProviders, lastDue, lastRenewed, lastRejected);
    }

    /**
     * 启动时间轮并注册首个 tick。
     */
    @PostConstruct
    public void start() {
        timeWheel = Timer.newTimer(512, 100, TimeUnit.MILLISECONDS);
        timeWheel.schedule(this::tick, INITIAL_DELAY_SECONDS, TimeUnit.SECONDS);
        log.info("[CreditRenew] 余额令牌自动续期已启动：首巡 {} 秒后执行，巡检周期 {} 秒",
                INITIAL_DELAY_SECONDS, INTERVAL_SECONDS);
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
                log.debug("[CreditRenew] 时间轮关闭异常: {}", e.getMessage());
            }
        }
    }

    /**
     * 时间轮 tick：查开关 → 防重入 → 逐供应商判定到点续期 → 自续注册下一次。
     */
    private void tick() {
        try {
            lastEnabled = settingReader.getBool(GROUP, FIELD_ENABLED, true);
            if (lastEnabled && renewing.compareAndSet(false, true)) {
                try {
                    renewAll();
                } finally {
                    renewing.set(false);
                }
            }
        } catch (Throwable e) {
            log.error("[CreditRenew] 余额令牌续期执行异常（已捕获，不影响时间轮）: {}", e.getMessage(), e);
        } finally {
            Timer wheel = timeWheel;
            if (wheel != null && wheel.isRunning()) {
                try {
                    wheel.schedule(this::tick, INTERVAL_SECONDS, TimeUnit.SECONDS);
                    nextRunAt = System.currentTimeMillis() + INTERVAL_SECONDS * 1000L;
                } catch (Exception e) {
                    log.debug("[CreditRenew] 时间轮注册失败: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 巡检全部已启用供应商：账密登录口径、渠道级开关未关、令牌到点才出网回访。
     * 本轮的扫描 / 到点 / 成功 / 失败计数写入可观测字段。
     */
    private void renewAll() {
        long start = System.currentTimeMillis();
        lastRunAt = start;
        List<SysAccountChannel> channels = channelService.listEnabled();
        lastProviders = channels.size();
        int due = 0;
        int renewed = 0;
        int rejected = 0;
        for (SysAccountChannel channel : channels) {
            try {
                Map<String, Object> extra = channel.getExtra();
                if (!isRenewalCandidate(extra)
                        || !isDue(extra, System.currentTimeMillis())) {
                    continue;
                }
                due++;
                ApiProviderController.CreditRenewReport report =
                        providerController.renewProviderCreditToken(channel);
                if (report == null) {
                    continue;
                }
                if (ApiProviderController.CreditRenewReport.RENEWED.equals(report.status())) {
                    renewed++;
                } else if (ApiProviderController.CreditRenewReport.REJECTED.equals(report.status())) {
                    rejected++;
                }
            } catch (Exception e) {
                log.warn("[CreditRenew] provider={} 余额令牌续期失败: {}",
                        channel.getProviderId(), e.getMessage());
            }
        }
        lastDue = due;
        lastRenewed = renewed;
        lastRejected = rejected;
        lastDurationMs = System.currentTimeMillis() - start;
        if (due > 0) {
            log.info("[CreditRenew] 余额令牌续期巡检完成：{} 个供应商到点回访，{} 个续期成功，{} 个失败（耗时 {} ms）",
                    due, renewed, rejected, lastDurationMs);
        }
    }

    /**
     * 是否为「可参与续期」的供应商：账密登录口径 + 渠道级续期开关未显式关闭。
     *
     * <p>渠道级开关 {@code credit_renew_enabled} 缺省视为开启；是否真正可续期
     * 还要看 SPI 能力声明，那一步在执行器（控制器）里判定，这里只做粗筛——
     * 不可续期的供应商在这里被放行后，执行器会返回 skipped，不出网。</p>
     *
     * @param extra 供应商扩展配置
     * @return 是否参与续期巡检
     */
    private boolean isRenewalCandidate(Map<String, Object> extra) {
        if (extra == null || !ProviderCreditLoginService.KIND.equals(str(extra, "credit_kind"))) {
            return false;
        }
        Object enabled = extra.get("credit_renew_enabled");
        return enabled == null || Boolean.parseBoolean(String.valueOf(enabled));
    }

    /**
     * 判断令牌是否「该续期了」。
     *
     * <p>JWT 令牌（解得出 exp）：到期前 {@code credit_renew_ahead_minutes}（默认 30
     * 分钟）即判到点；已过期的令牌也判到点——门户会话可能仍活着，回访一次还有救，
     * 救不回来会走告警转人工。非 JWT 令牌（解不出 exp）：按上次续期时间 +
     * {@code credit_renew_interval_minutes}（默认 720 分钟）周期回访保活；从未续期过
     * 的判为立即到点（首次巡检即验证一次续期链路）。</p>
     *
     * @param extra 供应商扩展配置
     * @param now   当前时间（epoch 毫秒）
     * @return 是否该续期
     */
    public static boolean isDue(Map<String, Object> extra, long now) {
        if (extra == null) {
            return false;
        }
        String token = str(extra, "credit_key");
        if (token == null) {
            return false;
        }
        Long expireAt = CreditTokens.expireAtMs(token);
        if (expireAt != null) {
            long ahead = minutes(extra, "credit_renew_ahead_minutes", DEFAULT_AHEAD_MINUTES);
            return now >= expireAt - ahead * 60_000L;
        }
        long interval = minutes(extra, "credit_renew_interval_minutes", DEFAULT_INTERVAL_MINUTES);
        Long last = longOf(extra.get("credit_last_renew_at"));
        return last == null || now >= last + interval * 60_000L;
    }

    /**
     * 读扩展配置里的字符串值。
     *
     * @param map 扩展配置
     * @param key 键名
     * @return 非空字符串；空为 null
     */
    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v);
        return s.isBlank() ? null : s;
    }

    /**
     * 读扩展配置里的分钟数（非法 / 非正值回落缺省）。
     *
     * @param map      扩展配置
     * @param key      键名
     * @param fallback 缺省值
     * @return 分钟数
     */
    private static long minutes(Map<String, Object> map, String key, long fallback) {
        Object v = map.get(key);
        if (v instanceof Number n) {
            return n.longValue() <= 0 ? fallback : n.longValue();
        }
        try {
            long parsed = v == null ? fallback : Long.parseLong(String.valueOf(v).trim());
            return parsed <= 0 ? fallback : parsed;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * 读扩展配置里的长整数（非法回落 null）。
     *
     * @param v 原值
     * @return 长整数；解析失败为 null
     */
    private static Long longOf(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return v == null ? null : Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
