// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.manage.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.task.timer.Timer;
import com.chua.common.support.task.timer.TimerTask;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.configuration.health.ChannelProbeGlobalSettingProvider;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderMapper;
import com.chua.starter.ai.support.manage.store.SysAiProvider;
import com.chua.starter.ai.support.service.ChannelHealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 按渠道独立的真实状态探测任务（时间轮驱动）。
 *
 * <p>{@link ChannelHealthProbeTask} 用单一全局间隔统一探全部渠道，但各渠道的上游限流阈值与
 * 凭据寿命差别很大：免费额度渠道需要勤探（额度耗尽就该下线），付费渠道探太勤只会撞限流。
 * 本任务把节奏下沉到「每个渠道一条」，用 {@link Timer}（哈希时间轮）按各自的秒数独立调度，
 * 探针本体仍复用 {@link ChannelHealthService#probeByChannel(String)}，因此状态机、
 * 上下线阈值与人工熔断行为与全量探针完全一致。</p>
 *
 * <h3>间隔来源（优先级从高到低）</h3>
 * <ol>
 *   <li>{@code channel_probe.channel_probe_interval_sec.<channel>}（按渠道，秒）；</li>
 *   <li>{@code health.channel_health_probe_interval_sec}（全局，秒）；</li>
 *   <li>{@code 60} 秒兜底。</li>
 * </ol>
 * <p>每次触发后都重新读取间隔再排下一次，因此设置页改完值下一次触发即生效，无需重启，
 * 也不必取消/重建 {@link TimerTask}（时间轮按「一次性任务 + 自定义重排」使用）。</p>
 *
 * <h3>开关</h3>
 * <p>{@code channel_probe.channel_probe_wheel_enabled}（默认开）。关闭时本任务整体不调度，
 * 全部渠道回落到 {@link ChannelHealthProbeTask} 的全局节奏——两者不会重复探活。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelProbeWheelTask implements ApplicationRunner, DisposableBean {

    /**
     * 按渠道节奏配置分组
     */
    private static final String GROUP = "channel_probe";

    /**
     * 最小间隔（秒）：避免配置误写成 1 秒把上游打爆
     */
    private static final long MIN_INTERVAL_SEC = 30L;

    /**
     * 默认间隔（秒）
     */
    private static final long DEFAULT_INTERVAL_SEC = 60L;

    /**
     * 全局设置读取器
     */
    private final GlobalSettingReader settingReader;

    /**
     * 渠道健康探针服务
     */
    private final ChannelHealthService channelHealthService;

    /**
     * 供应商映射器（发现需要探的渠道）
     */
    private final SysAiProviderMapper providerMapper;

    /**
     * 时间轮（懒建，关闭开关时不创建线程）
     */
    private volatile Timer wheel;

    /**
     * 各渠道当前在轮任务（渠道 → 任务），用于关闭时统一取消
     */
    private final Map<String, TimerTask> scheduled = new ConcurrentHashMap<>();

    /**
     * 启动入口：开关打开时为每个已启用渠道各排一条时间轮任务。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!enabled()) {
            log.debug("[ChannelProbeWheel] 按渠道时间轮未启用，回落全局探针节奏");
            return;
        }
        Set<String> channels = channels();
        if (channels.isEmpty()) {
            log.debug("[ChannelProbeWheel] 暂无可探渠道");
            return;
        }
        for (String channel : channels) {
            scheduleChannel(channel);
        }
        log.info("[ChannelProbeWheel] 按渠道探针已启动 channels={} intervals={}",
                channels, intervals(channels));
    }

    /**
     * 为一个渠道安排下一次探测。
     *
     * @param channel 渠道平台标识
     */
    private void scheduleChannel(String channel) {
        if (StringUtils.isBlank(channel)) {
            return;
        }
        Timer current = wheel();
        if (current == null || !current.isRunning()) {
            return;
        }
        long interval = intervalSec(channel);
        TimerTask task = current.schedule(() -> probeOnce(channel), interval, TimeUnit.SECONDS);
        if (task != null) {
            scheduled.put(channel, task);
        }
    }

    /**
     * 执行一次渠道探测，并按最新间隔排下一次。
     *
     * @param channel 渠道平台标识
     */
    private void probeOnce(String channel) {
        if (!enabled()) {
            scheduled.remove(channel);
            return;
        }
        try {
            int probed = channelHealthService.probeByChannel(channel);
            if (probed > 0) {
                log.debug("[ChannelProbeWheel] 渠道探针完成 channel={} connections={}", channel, probed);
            }
        } catch (Exception e) {
            // 单渠道失败不得让该渠道从时间轮上消失：记录后照常重排
            log.warn("[ChannelProbeWheel] 渠道探针失败 channel={} cause={}", channel, e.getMessage());
        }
        scheduleChannel(channel);
    }

    /**
     * 计算渠道生效间隔（秒）。
     *
     * @param channel 渠道平台标识
     * @return 生效间隔，已钳到 {@value #MIN_INTERVAL_SEC} 秒下限
     */
    long intervalSec(String channel) {
        int perChannel = settingReader.getInt(GROUP,
                ChannelProbeGlobalSettingProvider.FIELD_PREFIX + channel, 0);
        long interval = perChannel > 0 ? perChannel : globalIntervalSec();
        return Math.max(interval, MIN_INTERVAL_SEC);
    }

    /**
     * 全局探针间隔（health 分组）。
     *
     * @return 秒数
     */
    private long globalIntervalSec() {
        int global = settingReader.getInt("health", "channel_health_probe_interval_sec",
                (int) DEFAULT_INTERVAL_SEC);
        return global > 0 ? global : DEFAULT_INTERVAL_SEC;
    }

    /**
     * 全部已启用供应商的渠道集合。
     *
     * @return 渠道平台标识
     */
    private Set<String> channels() {
        List<SysAiProvider> providers = providerMapper.selectList(
                new LambdaQueryWrapper<SysAiProvider>().eq(SysAiProvider::getEnabled, true));
        Set<String> channels = new LinkedHashSet<>();
        if (providers == null) {
            return channels;
        }
        for (SysAiProvider provider : providers) {
            String platform = provider.getPlatform();
            if (StringUtils.isNotBlank(platform)) {
                channels.add(platform.trim());
            }
        }
        return channels;
    }

    /**
     * 各渠道生效间隔（仅用于启动日志）。
     *
     * @param channels 渠道集合
     * @return 渠道 → 秒数
     */
    private Map<String, Long> intervals(Set<String> channels) {
        Map<String, Long> result = new java.util.LinkedHashMap<>(channels.size());
        for (String channel : channels) {
            result.put(channel, intervalSec(channel));
        }
        return result;
    }

    /**
     * 时间轮开关。
     *
     * @return true 表示启用
     */
    private boolean enabled() {
        return settingReader.getBool(GROUP, ChannelProbeGlobalSettingProvider.FIELD_ENABLED, true);
    }

    /**
     * 懒建时间轮（1024 槽 × 1 秒 tick，足以覆盖秒级间隔且无需高频唤醒）。
     *
     * @return 时间轮；已关闭时返回 {@code null}
     */
    private Timer wheel() {
        Timer local = wheel;
        if (local != null) {
            return local.isRunning() ? local : null;
        }
        synchronized (this) {
            local = wheel;
            if (local == null) {
                // 512 槽 × 1s tick：最小间隔 30s，一圈覆盖 512s，长间隔由补扫自动追赶
                local = Timer.newTimer(512, 1, TimeUnit.SECONDS);
                wheel = local;
                log.debug("[ChannelProbeWheel] 时间轮已创建 slots=512 tick=1s");
            }
            return local;
        }
    }

    /**
     * 关闭时间轮（Spring 容器销毁时）。
     */
    @Override
    public void destroy() {
        Timer local = wheel;
        wheel = null;
        scheduled.clear();
        if (local == null) {
            return;
        }
        local.shutdown();
        log.info("[ChannelProbeWheel] 时间轮已关闭");
    }
}
