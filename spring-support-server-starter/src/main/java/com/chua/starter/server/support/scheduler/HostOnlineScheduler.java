package com.chua.starter.server.support.scheduler;

import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.enums.HostCollectType;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.chua.starter.server.support.spi.CollectOutcome;
import com.chua.starter.server.support.spi.HostCollectContext;
import com.chua.starter.server.support.spi.HostCollectProvider;
import com.chua.starter.server.support.spi.HostCollectProviderRegistry;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 主机在离线定时检测器 / 定时采集器（主机级配置）
 * <p>
 * 由 {@link ServerTaskScheduler} 固定细粒度扫描驱动。
 * 每台主机的在离线检测开关/间隔、采集开关/方式/间隔均独立配置（存于主机字段）：
 * <ul>
 *   <li>在离线：仅探测 {@code onlineEnabled=1} 且到达 {@code onlineInterval} 的主机</li>
 *   <li>采集：仅采集 {@code collectEnabled=1} 且到达 {@code collectInterval} 的主机，
 *       经采集 SPI 按主机自身 {@code collectMode} 路由</li>
 * </ul>
 * 127.0.0.1 / localhost 视为本机直接在线（hairpin NAT 特判）。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HostOnlineScheduler {

    /** 主机 Mapper */
    private final MsServerHostMapper hostMapper;

    /** 采集 Provider 注册中心（SPI 按 mode 路由） */
    private final HostCollectProviderRegistry collectRegistry;

    /** 延迟检测结果钩子（monitor 侧注册，推送 socket 实时延迟；未注册时不推送） */
    @Setter
    private Consumer<MsServerHost> delayHook;

    /** 在线状态变更钩子（monitor 侧注册；仅状态翻转时回调） */
    @Setter
    private Consumer<MsServerHost> onlineHook;

    /** TCP 连接超时(毫秒) */
    private static final int CONNECT_TIMEOUT_MS = 3000;

    /** 默认在离线间隔(秒) */
    private static final int DEFAULT_ONLINE_INTERVAL = 60;

    /** 默认采集间隔(秒) */
    private static final int DEFAULT_COLLECT_INTERVAL = 3600;

    /** 默认延迟检测间隔(秒) */
    private static final int DEFAULT_DELAY_INTERVAL = 60;

    /** 最小间隔(秒) */
    private static final int MIN_INTERVAL = 5;

    /** 状态：启用 */
    private static final int ENABLED = 1;

    /**
     * 在离线检测：扫描所有启用主机，按各自开关/间隔决定是否探测
     */
    public void tick() {
        try {
            List<MsServerHost> hosts = hostMapper.selectList(
                    new LambdaQueryWrapper<MsServerHost>().eq(MsServerHost::getServerHostStatus, 1));
            LocalDateTime now = LocalDateTime.now();
            int online = 0;
            int probed = 0;
            for (MsServerHost host : hosts) {
                // 主机级在离线开关
                if (!isEnabled(host.getServerHostOnlineEnabled())) {
                    continue;
                }
                int interval = normalizeInterval(
                        host.getServerHostOnlineInterval() == null ? DEFAULT_ONLINE_INTERVAL : host.getServerHostOnlineInterval());
                // 到达间隔才探测
                if (!isDue(host.getServerHostOnlineTime(), interval, now)) {
                    continue;
                }
                probed++;
                boolean reachable = isReachable(host);
                if (reachable) {
                    online++;
                }
                boolean prevOnline = isEnabled(host.getServerHostOnline());
                updateOnline(host.getId(), reachable);
                if (onlineHook != null && prevOnline != reachable) {
                    host.setServerHostOnline(reachable ? 1 : 0);
                    try {
                        onlineHook.accept(host);
                    } catch (Exception hookEx) {
                        log.debug("[online] 在线状态钩子异常 hostId={}: {}", host.getId(), hookEx.getMessage());
                    }
                }
            }
            if (probed > 0) {
                log.info("[online] 在离线检测完成: 探测 {} 台, {} 台在线", probed, online);
            }
        } catch (Exception e) {
            log.warn("[online] 在离线检测异常: {}", e.getMessage());
        }
    }

    /**
     * 硬件定时采集：扫描所有启用主机，按各自开关/间隔/方式经 SPI 采集
     */
    public void collectTick() {
        try {
            List<MsServerHost> hosts = hostMapper.selectList(
                    new LambdaQueryWrapper<MsServerHost>().eq(MsServerHost::getServerHostStatus, 1));
            LocalDateTime now = LocalDateTime.now();
            // 过滤出到期的采集主机
            List<MsServerHost> dueHosts = hosts.stream()
                    .filter(h -> isEnabled(h.getServerHostCollectEnabled()))
                    .filter(h -> {
                        int interval = normalizeInterval(h.getServerHostCollectInterval() == null
                                ? DEFAULT_COLLECT_INTERVAL : h.getServerHostCollectInterval());
                        return isDue(h.getServerHostLastCollectTime(), interval, now);
                    })
                    .toList();
            if (dueHosts.isEmpty()) {
                return;
            }
            long[] ok = {0};
            Flux.fromIterable(dueHosts)
                    .map(host -> {
                        HostCollectType mode = HostCollectType.of(
                                host.getServerHostCollectMode() == null ? "SCHEDULED" : host.getServerHostCollectMode());
                        return new HostCollectContext(mode, host);
                    })
                    .filter(ctx -> ctx.getMode() != null)
                    .flatMap(context -> {
                        Optional<HostCollectProvider> providerOpt = collectRegistry.resolve(context.getMode());
                        if (providerOpt.isEmpty()) {
                            log.warn("[collect] 未找到采集 Provider: mode={}", context.getMode());
                            markCollected(context.getHost());
                            return Mono.empty();
                        }
                        return providerOpt.get().collect(context)
                                .doOnNext(outcome -> {
                                    if (outcome.isSuccess()) {
                                        ok[0]++;
                                    }
                                })
                                .doFinally(signal -> markCollected(context.getHost()))
                                .onErrorResume(e -> {
                                    log.warn("[collect] 采集失败 hostId={}: {}", context.getHost().getId(), e.getMessage());
                                    return Mono.just(CollectOutcome.fail(e.getMessage()));
                                });
                    })
                    .subscribe(
                            outcome -> {
                            },
                            error -> log.warn("[collect] 采集流异常: {}", error.getMessage()),
                            () -> log.info("[collect] 采集完成: {} 台成功, 共 {} 台", ok[0], dueHosts.size()));
        } catch (Exception e) {
            log.warn("[collect] 定时采集异常: {}", e.getMessage());
        }
    }

    /**
     * 延迟检测：扫描启用主机，按各自开关/间隔 TCP 连接测 RTT 毫秒并回写
     */
    public void delayTick() {
        try {
            List<MsServerHost> hosts = hostMapper.selectList(
                    new LambdaQueryWrapper<MsServerHost>().eq(MsServerHost::getServerHostStatus, 1));
            LocalDateTime now = LocalDateTime.now();
            int probed = 0;
            for (MsServerHost host : hosts) {
                if (!isEnabled(host.getServerHostDelayEnabled())) {
                    continue;
                }
                int interval = normalizeInterval(
                        host.getServerHostDelayInterval() == null ? DEFAULT_DELAY_INTERVAL : host.getServerHostDelayInterval());
                if (!isDue(host.getServerHostLastDelayTime(), interval, now)) {
                    continue;
                }
                probed++;
                updateDelay(host.getId());
            }
            if (probed > 0) {
                log.info("[delay] 延迟检测完成: 探测 {} 台", probed);
            }
        } catch (Exception e) {
            log.warn("[delay] 延迟检测异常: {}", e.getMessage());
        }
    }

    /**
     * TCP 连接测 RTT 延迟并回写（连不上则置 -1）
     *
     * @param hostId 主机 ID
     */
    private void updateDelay(Long hostId) {
        try {
            MsServerHost host = hostMapper.selectById(hostId);
            if (host == null) {
                return;
            }
            long delay = measureDelayMs(host);
            host.setServerHostDelayMs(delay < 0 ? -1 : delay);
            host.setServerHostLastDelayTime(LocalDateTime.now());
            hostMapper.updateById(host);
            if (delayHook != null) {
                try {
                    delayHook.accept(host);
                } catch (Exception hookEx) {
                    log.debug("[delay] 延迟钩子异常 hostId={}: {}", hostId, hookEx.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[delay] 更新延迟状态失败 hostId={}", hostId, e);
        }
    }

    /**
     * 测量 TCP 连接延迟（毫秒），不可达返回 -1
     *
     * @param host 主机
     * @return 延迟毫秒或 -1
     */
    private long measureDelayMs(MsServerHost host) {
        String address = host.getServerHostAddress();
        if (address == null || "127.0.0.1".equals(address.trim()) || "localhost".equalsIgnoreCase(address.trim())) {
            return 0;
        }
        int port = host.getServerHostPort() == null || host.getServerHostPort() <= 0 ? 22 : host.getServerHostPort();
        try (Socket socket = new Socket()) {
            long start = System.nanoTime();
            socket.connect(new InetSocketAddress(address, port), CONNECT_TIMEOUT_MS);
            long cost = (System.nanoTime() - start) / 1_000_000;
            return cost;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 回写最近采集时间（无论成功失败，防止每次扫描重复采集同一主机）
     *
     * @param host 主机
     */
    private void markCollected(MsServerHost host) {
        try {
            host.setServerHostLastCollectTime(LocalDateTime.now());
            hostMapper.updateById(host);
        } catch (Exception e) {
            log.warn("[collect] 回写采集时间失败 hostId={}", host.getId(), e);
        }
    }

    /**
     * 更新主机在线状态
     *
     * @param hostId   主机 ID
     * @param reachable 是否可达
     */
    private void updateOnline(Long hostId, boolean reachable) {
        try {
            MsServerHost host = hostMapper.selectById(hostId);
            if (host == null) {
                return;
            }
            host.setServerHostOnline(reachable ? 1 : 0);
            if (reachable) {
                host.setServerHostOnlineTime(java.time.LocalDateTime.now());
            }
            hostMapper.updateById(host);
        } catch (Exception e) {
            log.warn("[online] 更新在线状态失败 hostId={}", hostId, e);
        }
    }

    /**
     * TCP 探测主机端口连通性
     * <p>127.0.0.1 / localhost 视为本机，直接在线（避免 hairpin NAT 自连失败误报离线）。</p>
     *
     * @param host 主机
     * @return 是否可达
     */
    private boolean isReachable(MsServerHost host) {
        String address = host.getServerHostAddress();
        if (address == null || "127.0.0.1".equals(address.trim()) || "localhost".equalsIgnoreCase(address.trim())) {
            return true;
        }
        int port = host.getServerHostPort() == null || host.getServerHostPort() <= 0 ? 22 : host.getServerHostPort();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host.getServerHostAddress(), port), CONNECT_TIMEOUT_MS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断开关是否启用（null 视为启用）
     *
     * @param flag 开关值
     * @return 是否启用
     */
    private boolean isEnabled(Integer flag) {
        return flag == null || flag == ENABLED;
    }

    /**
     * 判断是否到达执行间隔（lastTime 为空视为到期）
     *
     * @param lastTime 上次执行时间
     * @param interval 间隔（秒）
     * @param now      当前时间
     * @return 是否到期
     */
    private boolean isDue(LocalDateTime lastTime, int interval, LocalDateTime now) {
        if (lastTime == null) {
            return true;
        }
        return Duration.between(lastTime, now).getSeconds() >= interval;
    }

    /**
     * 归一化间隔（不小于最小间隔）
     *
     * @param interval 间隔（秒）
     * @return 归一化间隔
     */
    private int normalizeInterval(int interval) {
        return interval < MIN_INTERVAL ? MIN_INTERVAL : interval;
    }
}
