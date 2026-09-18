package com.chua.starter.monitor.support.spi.impl;

import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.enums.HostCollectType;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.chua.starter.server.support.spi.CollectOutcome;
import com.chua.starter.server.support.spi.HostCollectContext;
import com.chua.starter.server.support.spi.HostCollectProvider;
import com.chua.starter.monitor.support.service.LocalSystemInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 本机自采集 提供者（LOCAL / OSHI）。
 *
 * <p>主机平台标记为「本机」（osType=LOCAL）时命中：monitor 进程所在本机，
 * 经 OSHI 直接读取本机 CPU/内存/系统信息回写，无需 Agent 上报或 SSH 连接。
 * 数据来源 {@link LocalSystemInfoService}（OSHI 本机系统信息服务）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalHostCollectProvider implements HostCollectProvider {

    /** 本机系统信息采集服务（OSHI） */
    private final LocalSystemInfoService localService;

    /** 主机 映射器（回写硬件字段） */
    private final MsServerHostMapper hostMapper;

    @Override
    public HostCollectType type() {
        return HostCollectType.LOCAL;
    }

    @Override
    public Mono<CollectOutcome> collect(HostCollectContext context) {
        return Mono.fromCallable(() -> doCollect(context))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
    * 同步执行本机 OSHI 采集并回写主机硬件字段
    *
    * @param context 上下文
    * @return 采集结果
    */
    private CollectOutcome doCollect(HostCollectContext context) {
        if (context == null || context.getHost() == null || context.getHost().getId() == null) {
            return CollectOutcome.fail("主机数据不完整");
        }
        try {
            MsServerHost host = context.getHost();
            Map<String, Object> info = localService.getHostInfo();
            // 回写 CPU 核数（OSHI 本机逻辑处理器数）
            Object cores = info == null ? null : info.get("cpuCores");
            if (cores instanceof Number n) {
                host.setServerHostCpuCores(n.intValue());
            }
            // 回写实时使用率：CPU / 内存 / 磁盘（供 collectHook 推送到前端 socket host-metrics）
            double cpuPct = localService.getCpuPercent();
            host.setServerHostCpuUsage((double) Math.round(cpuPct));
            Object memUsage = info == null ? null : info.get("memoryUsage");
            if (memUsage instanceof Number n) {
                host.setServerHostMemUsage((double) Math.round(n.doubleValue()));
            }
            java.util.List<java.util.Map<String, Object>> disks = localService.getDisks();
            if (disks != null && !disks.isEmpty()) {
                Object du = disks.get(0).get("usage");
                if (du instanceof Number n) {
                    host.setServerHostDiskUsage((double) Math.round(n.doubleValue()));
                }
            }
            host.setServerHostOnline(1);
            host.setServerHostOnlineTime(LocalDateTime.now());
            hostMapper.updateById(host);
            log.info("[collect] LOCAL(OSHI) 本机采集成功 hostId={}, cpu={}%, mem={}%, disk={}%",
                    host.getId(), host.getServerHostCpuUsage(), host.getServerHostMemUsage(), host.getServerHostDiskUsage());
            return CollectOutcome.ok("本机 OSHI 采集成功");
        } catch (Exception e) {
            log.warn("[collect] LOCAL(OSHI) 采集失败 hostId={}: {}",
                    context.getHost().getId(), e.getMessage());
            return CollectOutcome.fail(e.getMessage());
        }
    }
}
