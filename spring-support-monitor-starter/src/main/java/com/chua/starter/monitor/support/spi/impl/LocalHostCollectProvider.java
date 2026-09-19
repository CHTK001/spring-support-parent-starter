package com.chua.starter.monitor.support.spi.impl;


import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.monitor.support.model.DiskRowVO;
import com.chua.starter.monitor.support.model.HostInfoVO;
import com.chua.starter.monitor.support.service.LocalSystemInfoService;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.enums.HostCollectType;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.chua.starter.server.support.spi.CollectOutcome;
import com.chua.starter.server.support.spi.HostCollectContext;
import com.chua.starter.server.support.spi.HostCollectProvider;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
@Spi(value = "local", order = 100)
public class LocalHostCollectProvider implements HostCollectProvider {

    /**
     * 本机系统信息采集服务（OSHI）
    */
    @Autowired
    private LocalSystemInfoService localService;

    /**
     * 主机 映射器（回写硬件字段）
    */
    @Autowired
    private MsServerHostMapper hostMapper;

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
            HostInfoVO info = localService.getHostInfo();
            LambdaUpdateWrapper<MsServerHost> write = new LambdaUpdateWrapper<>();
            // 回写 CPU 核数（OSHI 本机逻辑处理器数）
            if (info != null && info.getCpuCores() != null) {
                host.setServerHostCpuCores(info.getCpuCores());
                write.set(MsServerHost::getServerHostCpuCores, host.getServerHostCpuCores());
            }
            // 回写实时使用率：CPU / 内存 / 磁盘（供 collectHook 推送到前端 socket host-metrics）
            double cpuPct = localService.getCpuPercent();
            host.setServerHostCpuUsage((double) Math.round(cpuPct));
            write.set(MsServerHost::getServerHostCpuUsage, host.getServerHostCpuUsage());
            if (info != null && info.getMemoryUsage() != null) {
                host.setServerHostMemUsage((double) Math.round(info.getMemoryUsage()));
                write.set(MsServerHost::getServerHostMemUsage, host.getServerHostMemUsage());
            }
            List<DiskRowVO> disks = localService.getDisks();
            if (disks != null && !disks.isEmpty()) {
                host.setServerHostDiskUsage((double) Math.round(disks.get(0).getUsage()));
                write.set(MsServerHost::getServerHostDiskUsage, host.getServerHostDiskUsage());
            }
            host.setServerHostOnline(1);
            host.setServerHostOnlineTime(LocalDateTime.now());
            write.set(MsServerHost::getServerHostOnline, host.getServerHostOnline());
            write.set(MsServerHost::getServerHostOnlineTime, host.getServerHostOnlineTime());
            // 只写本次采到的列：updateById 会用这份旧快照覆盖并发路径刚改过的列
            hostMapper.update(null, write.eq(MsServerHost::getId, host.getId()));
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
