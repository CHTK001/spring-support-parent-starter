package com.chua.starter.monitor.support.config;

import com.chua.starter.monitor.support.service.HostProfileService;
import com.chua.starter.monitor.support.service.alarm.AlarmEngineService;
import com.chua.starter.monitor.support.service.impl.MonitorPushService;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.enums.HostCollectType;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.chua.starter.server.support.scheduler.HostOnlineScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 主机实时状态 → Socket 推送 + 告警规则评估桥接
 * <p>
 * 向 server-starter 的 {@link HostOnlineScheduler} 注册延迟/在线/采集 hooks：
 * <ul>
 *   <li>延迟 hook：实时延迟推送 socket + 喂入告警规则引擎（指标名 host.delay.ms）</li>
 *   <li>在线 hook：在线状态翻转推送 socket + 判定离线告警（指标名 host.online）</li>
 *   <li>采集 hook：采集完成使用率评估（指标名 host.cpu.usage / host.mem.usage / host.disk.usage）</li>
 * </ul>
 * 前端 subscribe("monitor", "host-delay" / "host-online") 接收推送；
 * 告警规则引擎未装配或 socket 未启用时静默跳过，不影响主链路。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class HostRealtimePushConfiguration {

    /** 实时推送服务 */
    private final MonitorPushService monitorPushService;

    /** 告警规则引擎（评估主机延迟/在线/使用率规则） */
    private final ObjectProvider<AlarmEngineService> alarmEngineProvider;

    /** 主机 映射器（采集后重读最新台账） */
    private final MsServerHostMapper hostMapper;

    /** 主机画像服务（AGENT 方式采集时拉 agent 快照，可能未装配） */
    private final ObjectProvider<HostProfileService> profileServiceProvider;

    /** 主机延迟指标名（毫秒，告警规则 GT 阈值） */
    private static final String METRIC_HOST_DELAY_MS = "host.delay.ms";

    /** 主机在线指标名（1 在线 / 0 离线，离线告警 LT 阈值 1） */
    private static final String METRIC_HOST_ONLINE = "host.online";

    /** 主机 CPU 使用率指标名（%，告警规则 GT 阈值） */
    private static final String METRIC_HOST_CPU_USAGE = "host.cpu.usage";

    /** 主机内存使用率指标名（%，告警规则 GT 阈值） */
    private static final String METRIC_HOST_MEM_USAGE = "host.mem.usage";

    /** 主机磁盘使用率指标名（%，告警规则 GT 阈值） */
    private static final String METRIC_HOST_DISK_USAGE = "host.disk.usage";

    /**
     * 注册主机实时状态 hooks（延迟/在线/采集 → socket 推送 + 告警评估）
     *
     * @param onlineSchedulerProvider 在离线/延迟/采集调度器（server-starter，可能未装配）
     * @return 启动时执行的注册器
     */
    @Bean
    public ApplicationRunner hostRealtimePushBinder(ObjectProvider<HostOnlineScheduler> onlineSchedulerProvider) {
        return args -> {
            HostOnlineScheduler scheduler = onlineSchedulerProvider.getIfAvailable();
            if (scheduler == null) {
                log.debug("[monitor] HostOnlineScheduler 未装配，跳过主机实时推送绑定");
                return;
            }
            scheduler.setDelayHook(host -> {
                try {
                    monitorPushService.pushHostDelay(
                            host.getId(), host.getServerHostName(),
                            host.getServerHostAddress(), host.getServerHostPort(),
                            host.getServerHostDelayMs());
                } catch (Exception e) {
                    log.debug("[monitor] 延迟推送跳过 hostId={}: {}", host.getId(), e.getMessage());
                }
                // 延迟告警评估：常规延迟值（>=0）与不可达（-1）均可命中不同规则
                AlarmEngineService alarmEngine = alarmEngineProvider.getIfAvailable();
                if (alarmEngine != null && host.getId() != null) {
                    try {
                        long delay = host.getServerHostDelayMs() == null ? -1 : host.getServerHostDelayMs();
                        alarmEngine.evaluate(host.getId(), host.getServerHostName(),
                                METRIC_HOST_DELAY_MS, delay);
                    } catch (Exception e) {
                        log.debug("[monitor] 延迟告警评估跳过 hostId={}: {}", host.getId(), e.getMessage());
                    }
                }
            });
            scheduler.setOnlineHook(host -> {
                try {
                    boolean online = host.getServerHostOnline() != null && host.getServerHostOnline() == 1;
                    monitorPushService.pushHostOnline(host.getId(), host.getServerHostName(), online);
                    AlarmEngineService alarmEngine = alarmEngineProvider.getIfAvailable();
                    if (alarmEngine != null && host.getId() != null) {
                        // 离线(0)/在线(1)均上送，由规则条件（LT 1）判定离线告警
                        alarmEngine.evaluate(host.getId(), host.getServerHostName(),
                                METRIC_HOST_ONLINE, online ? 1 : 0);
                    }
                } catch (Exception e) {
                    log.debug("[monitor] 在线推送/告警跳过 hostId={}: {}", host.getId(), e.getMessage());
                }
            });
            scheduler.setCollectHook(host -> {
                if (host == null || host.getId() == null) {
                    return;
                }
                MsServerHost snapshot = pullSnapshot(host);
                pushUsage(snapshot);
                AlarmEngineService alarmEngine = alarmEngineProvider.getIfAvailable();
                if (alarmEngine == null) {
                    return;
                }
                try {
                    if (snapshot.getServerHostCpuUsage() != null) {
                        alarmEngine.evaluate(snapshot.getId(), snapshot.getServerHostName(),
                                METRIC_HOST_CPU_USAGE, snapshot.getServerHostCpuUsage());
                    }
                    if (snapshot.getServerHostMemUsage() != null) {
                        alarmEngine.evaluate(snapshot.getId(), snapshot.getServerHostName(),
                                METRIC_HOST_MEM_USAGE, snapshot.getServerHostMemUsage());
                    }
                    if (snapshot.getServerHostDiskUsage() != null) {
                        alarmEngine.evaluate(snapshot.getId(), snapshot.getServerHostName(),
                                METRIC_HOST_DISK_USAGE, snapshot.getServerHostDiskUsage());
                    }
                } catch (Exception e) {
                    log.debug("[monitor] 采集告警评估跳过 hostId={}: {}", snapshot.getId(), e.getMessage());
                }
            });
            log.info("[monitor] 主机实时状态推送与告警评估已绑定（延迟/在线/采集）");
        };
    }

    /**
     * 采集完成后取一份可用的最新台账
     * <p>
     * AGENT 方式下 Provider 只回写在线状态，使用率要靠拉取 agent 画像才会更新，
     * 所以这里按采集方式补一次画像拉取；其余方式（本机 OSHI / SSH 探测）Provider 已把值写进库，
     * 只需重读台账 —— 钩子拿到的是采集前加载的旧对象。
     * </p>
     *
     * @param host 采集前加载的主机
     * @return 最新台账，重读失败时回退出入参
     */
    private MsServerHost pullSnapshot(MsServerHost host) {
        if (HostCollectType.AGENT.getCode().equalsIgnoreCase(nullToEmpty(host.getServerHostCollectMode()))) {
            HostProfileService profileService = profileServiceProvider.getIfAvailable();
            if (profileService != null) {
                try {
                    profileService.refresh(host.getId(), true);
                } catch (Exception e) {
                    log.debug("[monitor] 采集期画像拉取失败 hostId={}: {}", host.getId(), e.getMessage());
                }
            }
        }
        try {
            MsServerHost latest = hostMapper.selectById(host.getId());
            return latest == null ? host : latest;
        } catch (Exception e) {
            log.debug("[monitor] 采集后台账重读失败 hostId={}: {}", host.getId(), e.getMessage());
            return host;
        }
    }

    /**
     * 广播主机使用率（前端主机资产卡片订阅 host-metrics）
     *
     * @param host 最新台账
     */
    private void pushUsage(MsServerHost host) {
        if (host.getServerHostCpuUsage() == null && host.getServerHostMemUsage() == null
                && host.getServerHostDiskUsage() == null) {
            // 三率全空说明这台主机从来没采到过硬件指标，推 0 会被当成真实的 0%
            return;
        }
        try {
            monitorPushService.pushHostMetrics(host.getId(), host.getServerHostName(),
                    host.getServerHostCpuUsage(), host.getServerHostMemUsage(), host.getServerHostDiskUsage());
        } catch (Exception e) {
            log.debug("[monitor] 指标推送跳过 hostId={}: {}", host.getId(), e.getMessage());
        }
    }

    /**
     * 空值转空串
     *
     * @param value 原值
     * @return 文本
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
