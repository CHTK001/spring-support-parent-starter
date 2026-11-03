package com.chua.starter.ai.support.manage.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderConnectionMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderMapper;
import com.chua.starter.ai.support.manage.store.SysAiProvider;
import com.chua.starter.ai.support.manage.store.SysAiProviderConnection;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.ai.support.spi.AccountChannelAuthManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 账号渠道自动签到任务。
 *
 * <p>按 {@code ModelMetricsPushTask} 的动态 {@link Trigger} 调度范式：开关与间隔从
 * {@code sys_ai_global_setting} 的 {@code global} 分组读取：</p>
 * <ul>
 *   <li>开关：{@code enable_channel_checkin}（默认打开）</li>
 *   <li>间隔：{@code channel_checkin_interval_sec}（秒，最小 300s，默认 3600s）</li>
 *   <li>每日首次签到（UTC+8 当日 00:30 之后才允许），用 {@code channel_checkin_last_date}
 *       保存上次签到的日期（yyyy-MM-dd）做去重</li>
 * </ul>
 *
 * <p>执行逻辑：</p>
 * <ol>
 *   <li>遍历 {@code ServiceProvider.of(AccountChannelAuthManager.class).list()}，
 *       仅保留 {@code supportsCheckin()==true} 的渠道实现；</li>
 *   <li>每个渠道取 {@code sys_ai_provider} 下所有 {@code enabled=true} 的
 *       {@code sys_ai_provider_connection} 行；</li>
 *   <li>逐账号串行调用 {@link AccountChannelAuthManager#checkin(Map)}（单条失败不中断批次）；</li>
 *   <li>把更新后的凭据（含 credits/message 等）回写 connection 行。</li>
 * </ol>
 *
 * <p>与 {@code auto-checkin.ts} 的「UTC+8 日期守卫 + 首日 30s 延迟」对齐：本任务由
 * 动态间隔驱动（默认 1h 一次），每次触发时先判定「今天是否已签到」，只有首次触发
 * 才真正调上游。该判定在内存里做（{@code channel_checkin_last_date} 落全局设置），
 * 避免同一日内重复请求。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelCheckinTask implements SchedulingConfigurer {

    /**
     * 全局设置分组
     */
    private static final String GROUP = "global";

    /**
     * 签到开关设置键
     */
    private static final String SETTING_ENABLE = "enable_channel_checkin";

    /**
     * 签到间隔设置键
     */
    private static final String SETTING_INTERVAL_SEC = "channel_checkin_interval_sec";

    /**
     * 上次签到日期（UTC+8，yyyy-MM-dd）设置键
     */
    private static final String SETTING_LAST_DATE = "channel_checkin_last_date";

    /**
     * 默认间隔（秒）
     */
    private static final long DEFAULT_INTERVAL_SEC = 3600;

    /**
     * 最小间隔（秒）
     */
    private static final long MIN_INTERVAL_SEC = 300;

    /**
     * 时区：签到按 UTC+8 的「自然日」判定
     */
    private static final ZoneId CHECKIN_ZONE = ZoneId.of("UTC+8");

    /**
     * 日期格式化（yyyy-MM-dd）
     */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 全局设置读取器
     */
    private final GlobalSettingReader settingReader;

    /**
     * 全局设置服务（写入当日签到日期，避免同日重复触发）
     */
    private final SysAiGlobalSettingService globalSettingService;

    /**
     * 渠道 provider 映射器
     */
    private final SysAiProviderMapper providerMapper;

    /**
     * 渠道 connection 映射器
     */
    private final SysAiProviderConnectionMapper connectionMapper;

    /**
     * 渠道鉴权 SPI 实现映射（懒加载，首次调用时从 SPI 注册表拉取）
     */
    private volatile Map<String, AccountChannelAuthManager> channelManagers;

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
     * 调度入口：校验开关 + 当日去重，命中后逐渠道逐账号串行签到。
     */
    public void run() {
        if (!isEnabled()) {
            log.debug("[ChannelCheckin] 渠道自动签到未启用，跳过");
            return;
        }
        String today = LocalDate.now(CHECKIN_ZONE).format(DATE_FORMAT);
        String lastDate = settingReader.getStr(GROUP, SETTING_LAST_DATE);
        if (today.equals(lastDate)) {
            log.debug("[ChannelCheckin] 当日已签到（{}），跳过", today);
            return;
        }

        Map<String, AccountChannelAuthManager> managers = managers();
        int totalChannels = 0;
        int totalAccounts = 0;
        int okAccounts = 0;
        int failedAccounts = 0;

        for (Map.Entry<String, AccountChannelAuthManager> entry : managers.entrySet()) {
            String channel = entry.getKey();
            AccountChannelAuthManager impl = entry.getValue();
            if (!impl.supportsCheckin()) {
                continue;
            }
            totalChannels++;
            List<SysAiProviderConnection> connections = connectionsOf(channel);
            if (connections.isEmpty()) {
                continue;
            }
            for (SysAiProviderConnection conn : connections) {
                totalAccounts++;
                if (!hasCredentials(conn)) {
                    continue;
                }
                try {
                    Map<String, Object> credentials = new LinkedHashMap<>(conn.getCredentials());
                    Map<String, Object> after = impl.checkin(credentials);
                    if (after != null && after != credentials) {
                        Map<String, Object> merged = new LinkedHashMap<>(after);
                        merged.put("channel", channel);
                        conn.setCredentials(merged);
                        conn.setUpdatedAt(System.currentTimeMillis());
                        connectionMapper.updateById(conn);
                        okAccounts++;
                        log.info("[ChannelCheckin] 签到成功 channel={} label={}",
                                channel, conn.getLabel());
                    } else {
                        okAccounts++;
                        log.debug("[ChannelCheckin] 签到无变化 channel={} label={}",
                                channel, conn.getLabel());
                    }
                } catch (ApiException e) {
                    failedAccounts++;
                    log.warn("[ChannelCheckin] 签到失败 channel={} label={} code={} cause={}",
                            channel, conn.getLabel(),
                            e.getErrorCode() == null ? "UNKNOWN" : e.getErrorCode().getCode(),
                            e.getMessage());
                } catch (Exception e) {
                    failedAccounts++;
                    log.warn("[ChannelCheckin] 签到异常 channel={} label={} cause={}",
                            channel, conn.getLabel(), e.getMessage());
                }
            }
        }

        if (totalAccounts > 0) {
            // 把「今天」落库，让下一轮触发跳过；即使部分账号失败也标记（避免反复重试
            // 已确认失败的渠道在当日继续打上游）。
            globalSettingService.upsertValue(GROUP, SETTING_LAST_DATE, today, "渠道自动签到：上次完成签到的日期（UTC+8）");
            log.info("[ChannelCheckin] 当日签到完成 date={} channels={} accounts={} ok={} failed={}",
                    today, totalChannels, totalAccounts, okAccounts, failedAccounts);
        } else {
            log.debug("[ChannelCheckin] 无可签到账号，跳过当日标记");
        }
    }

    /**
     * 取指定渠道下全部「有凭据」的 connection（该渠道 provider 行必须 enabled）。
     *
     * @param channel 渠道标识
     * @return 连接列表（可能为空）
     */
    private List<SysAiProviderConnection> connectionsOf(String channel) {
        SysAiProvider provider = providerMapper.selectById(channel);
        if (provider == null || !Boolean.TRUE.equals(provider.getEnabled())) {
            return List.of();
        }
        List<SysAiProviderConnection> connections = connectionMapper.selectList(
                new LambdaQueryWrapper<SysAiProviderConnection>()
                        .eq(SysAiProviderConnection::getProviderId, channel));
        return connections == null ? List.of() : connections;
    }

    /**
     * 该连接是否持有非空凭据（缺凭据的账号签到必失败，跳过以省上游调用）。
     *
     * @param conn 连接行
     * @return 是否可签到
     */
    private static boolean hasCredentials(SysAiProviderConnection conn) {
        return conn.getCredentials() != null && !conn.getCredentials().isEmpty();
    }

    /**
     * 懒加载渠道实现映射（幂等）。
     *
     * @return channel → 实现
     */
    private Map<String, AccountChannelAuthManager> managers() {
        Map<String, AccountChannelAuthManager> local = channelManagers;
        if (local == null) {
            synchronized (this) {
                local = channelManagers;
                if (local == null) {
                    local = ServiceProvider.of(AccountChannelAuthManager.class).list();
                    channelManagers = local;
                }
            }
        }
        return local;
    }

    /**
     * 计算下次执行时间：读全局配置间隔（最小 300s），未配置时用默认值。
     *
     * @param triggerContext 触发器上下文
     * @return 下次执行时间
     */
    private Instant nextTriggerTime(TriggerContext triggerContext) {
        long intervalSec = settingReader.getInt(GROUP, SETTING_INTERVAL_SEC,
                (int) DEFAULT_INTERVAL_SEC);
        if (intervalSec < MIN_INTERVAL_SEC) {
            intervalSec = MIN_INTERVAL_SEC;
        }
        Date last = triggerContext.lastScheduledExecutionTime();
        if (last == null) {
            return Instant.now().plusMillis(TimeUnit.SECONDS.toMillis(intervalSec));
        }
        return last.toInstant().plusMillis(TimeUnit.SECONDS.toMillis(intervalSec));
    }

    /**
     * 渠道自动签到是否启用（全局分组 {@code enable_channel_checkin}，默认启用）。
     *
     * @return true 表示启用
     */
    private boolean isEnabled() {
        return settingReader.getBool(GROUP, SETTING_ENABLE, true);
    }
}
