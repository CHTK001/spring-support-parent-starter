// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.schedule;

import com.chua.common.support.task.timer.Timer;
import com.chua.starter.account.channel.service.AccountChannelService;
import com.chua.starter.account.channel.store.SysAccountChannel;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.manage.api.ApiProviderController;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 余额定时刷新任务：每 5 分钟在后台把「已启用且有 Key」的供应商余额真查一遍，
 * 并把读数按 Key 落库（sys_ai_provider_credit，先删后增）。
 *
 * <p>与面板 {@code /credit} 的关系：面板读数优先走进程内 5 分钟缓存，缓存未命中
 * 再回放落库快照；本任务定时强制回源并刷新快照，让面板在无人打开时也保持新鲜，
 * 重启后第一眼就能看到最近一次读数。开关 {@code global.credit_refresh_enabled}
 * 每轮 tick 实时读取，改配置无需重启。</p>
 *
 * <p>调度复用 {@link com.chua.common.support.task.timer.Timer} 时间轮（自续调度：
 * 每轮 tick 执行完再注册下一次，天然不重叠），与 {@link UsageAggregationTask} 的
 * 时间轮口径一致。余额查询逻辑直接复用
 * {@link ApiProviderController#refreshProviderCredit(SysAccountChannel)}——
 * 该方法与「同步」余额分节、面板强制刷新共用同一套候选实现、鉴权与落库口径；
 * 余额链路（候选实现 / 鉴权 / 缓存失效 / 快照落库）都在控制器里闭环，单独抽
 * Service 等于把整条链路搬一遍，收益只有「注入得更体面」。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreditRefreshScheduler {

    /**
     * 全局设置分组：与用量开关同组（sys_ai_global_setting → global）。
     */
    private static final String GROUP = "global";

    /**
     * 余额定时刷新开关键。
     */
    private static final String FIELD_ENABLED = "credit_refresh_enabled";

    /**
     * 刷新周期：5 分钟。
     */
    private static final long INTERVAL_SECONDS = 300;

    /**
     * 启动后首个 tick 的延迟：避开迁移、缓存预热等启动期工作。
     */
    private static final long INITIAL_DELAY_SECONDS = 60;

    /**
     * 渠道服务（取已启用供应商清单）。
     */
    private final AccountChannelService channelService;

    /**
     * 供应商管理 API：余额刷新逻辑（候选实现、鉴权、落库）都在这里，直接复用。
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
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    /**
     * 启动时间轮并注册首个 tick。
     */
    @PostConstruct
    public void start() {
        timeWheel = Timer.newTimer(512, 100, TimeUnit.MILLISECONDS);
        timeWheel.schedule(this::tick, INITIAL_DELAY_SECONDS, TimeUnit.SECONDS);
        log.info("[CreditRefresh] 余额定时刷新已启动：首刷 {} 秒后执行，周期 {} 秒",
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
                log.debug("[CreditRefresh] 时间轮关闭异常: {}", e.getMessage());
            }
        }
    }

    /**
     * 时间轮 tick：查开关 → 防重入 → 逐供应商刷新 → 自续注册下一次。
     */
    private void tick() {
        try {
            if (settingReader.getBool(GROUP, FIELD_ENABLED, true)) {
                if (refreshing.compareAndSet(false, true)) {
                    try {
                        refreshAll();
                    } finally {
                        refreshing.set(false);
                    }
                }
            }
        } catch (Throwable e) {
            log.error("[CreditRefresh] 余额定时刷新执行异常（已捕获，不影响时间轮）: {}", e.getMessage(), e);
        } finally {
            Timer wheel = timeWheel;
            if (wheel != null && wheel.isRunning()) {
                try {
                    wheel.schedule(this::tick, INTERVAL_SECONDS, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.debug("[CreditRefresh] 时间轮注册失败: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 刷新全部已启用供应商的余额；无 Key / 无余额口径的供应商由
     * {@link ApiProviderController#refreshProviderCredit(SysAccountChannel)}
     * 返回 null 跳过，不出网。
     */
    private void refreshAll() {
        List<SysAccountChannel> channels = channelService.listEnabled();
        int touched = 0;
        int succeeded = 0;
        for (SysAccountChannel channel : channels) {
            try {
                ApiProviderController.CreditSyncReport report = providerController.refreshProviderCredit(channel);
                if (report != null) {
                    touched++;
                    if (report.ok()) {
                        succeeded++;
                    }
                }
            } catch (Exception e) {
                log.warn("[CreditRefresh] provider={} 余额刷新失败: {}",
                        channel.getProviderId(), e.getMessage());
            }
        }
        if (touched > 0) {
            log.info("[CreditRefresh] 余额定时刷新完成：{} 个供应商有余额口径，其中 {} 个全部 Key 查到读数",
                    touched, succeeded);
        }
    }
}
