package com.chua.starter.server.support.scheduler;

import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.enums.HostCollectType;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.chua.starter.server.support.service.ServerConfigService;
import com.chua.starter.server.support.spi.CollectOutcome;
import com.chua.starter.server.support.spi.HostCollectContext;
import com.chua.starter.server.support.spi.HostCollectProvider;
import com.chua.starter.server.support.spi.HostCollectProviderRegistry;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Optional;

/**
 * 主机在离线定时检测器
 * <p>
 * 由 {@link ServerTaskScheduler} 按系统配置的间隔周期驱动：
 * 探测所有启用主机的 TCP 连通性并更新在线状态；
 * 按 {@code host.collect.mode}（scheduled）执行硬件采集。
 * 探测开关/间隔均可通过系统设置配置，配置变更后由时间轮动态重排。
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

    /** 系统配置服务 */
    private final ServerConfigService configService;

    /** 采集 Provider 注册中心（SPI 按 mode 路由） */
    private final HostCollectProviderRegistry collectRegistry;

    /** TCP 连接超时(毫秒) */
    private static final int CONNECT_TIMEOUT_MS = 3000;

    /**
     * 在离线检测（由时间轮按配置间隔驱动）
     */
    public void tick() {
        try {
            List<MsServerHost> hosts = hostMapper.selectList(
                    new LambdaQueryWrapper<MsServerHost>().eq(MsServerHost::getServerHostStatus, 1));
            int online = 0;
            for (MsServerHost host : hosts) {
                boolean reachable = isReachable(host);
                if (reachable) {
                    online++;
                }
                updateOnline(host.getId(), reachable);
            }
            log.info("[online] 在离线检测完成: {} 台启用主机, {} 台在线", hosts.size(), online);
        } catch (Exception e) {
            log.warn("[online] 在离线检测异常: {}", e.getMessage());
        }
    }

    /**
     * 硬件定时采集（由时间轮按配置间隔驱动）
     * <p>按配置的采集方式（SCHEDULED/AGENT/HTTP/SNMP/PROMETHEUS...）经 SPI 路由到
     * 具体 Provider 执行，统一以响应式（Flux）消费采集结果，屏蔽主动/被动差异。</p>
     */
    public void collectTick() {
        try {
            HostCollectType mode = HostCollectType.of(
                    configService.getString("host.collect.mode", "SCHEDULED"));
            if (mode == null) {
                log.warn("[collect] 未识别的采集方式: {}", mode);
                return;
            }
            Optional<HostCollectProvider> providerOpt = collectRegistry.resolve(mode);
            if (providerOpt.isEmpty()) {
                log.warn("[collect] 未找到采集 Provider: mode={}", mode);
                return;
            }
            HostCollectProvider provider = providerOpt.get();
            List<MsServerHost> hosts = hostMapper.selectList(
                    new LambdaQueryWrapper<MsServerHost>().eq(MsServerHost::getServerHostStatus, 1));
            long[] ok = {0};
            Flux.fromIterable(hosts)
                    .map(host -> HostCollectContext.builder().mode(mode).host(host).build())
                    .filter(provider::supports)
                    .flatMap(context -> provider.collect(context).onErrorResume(e ->
                            Mono.just(CollectOutcome.fail(e.getMessage()))))
                    .doOnNext(outcome -> {
                        if (outcome != null && outcome.isSuccess()) {
                            ok[0]++;
                        }
                    })
                    .subscribe(
                            outcome -> {
                            },
                            error -> log.warn("[collect] 采集流异常: {}", error.getMessage()),
                            () -> log.info("[collect] 采集完成: {} 台成功, 共 {} 台, mode={}", ok[0], hosts.size(), mode));
        } catch (Exception e) {
            log.warn("[collect] 定时采集异常: {}", e.getMessage());
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
}
