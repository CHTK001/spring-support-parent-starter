package com.chua.starter.monitor.support.spi.impl;


import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.utils.NumberUtils;
import com.chua.starter.monitor.support.model.DiskRowVO;
import com.chua.starter.monitor.support.model.HostInfoVO;
import com.chua.starter.monitor.support.service.LocalSystemInfoService;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.enums.HostCollectType;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.chua.starter.server.support.spi.CollectOutcome;
import com.chua.starter.server.support.spi.HostCollectContext;
import com.chua.starter.server.support.spi.HostCollectProvider;
import com.chua.starter.server.support.spi.VirtualThreadSchedulers;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

/**
 * 本机自采集 提供者（LOCAL / OSHI）。
 *
 * <p>主机采集方式标记为「本机」（collect_mode=LOCAL）时命中：monitor 进程所在本机，
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
                .subscribeOn(VirtualThreadSchedulers.virtual());
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
            long t0 = System.nanoTime();
            // 时间轮周期采集：不探测公网 IP（基本不变且需外网往返），公网 IP 只在点击刷新时按需获取
            HostInfoVO info = localService.getHostInfo(false);
            long t1 = System.nanoTime();
            LambdaUpdateWrapper<MsServerHost> write = new LambdaUpdateWrapper<>();
            // 回写 CPU 核数（OSHI 本机逻辑处理器数）
            if (info != null && info.getCpuCores() != null) {
                host.setServerHostCpuCores(info.getCpuCores());
                write.set(MsServerHost::getServerHostCpuCores, host.getServerHostCpuCores());
            }
            // 回写实时使用率：CPU / 内存 / 磁盘（保留 1 位小数，供 collectHook 推送到前端 socket host-metrics）
            double cpuPct = localService.getCpuPercent();
            long t2 = System.nanoTime();
            host.setServerHostCpuUsage(NumberUtils.round(cpuPct, 1).doubleValue());
            write.set(MsServerHost::getServerHostCpuUsage, host.getServerHostCpuUsage());
            if (info != null && info.getMemoryUsage() != null) {
                host.setServerHostMemUsage(NumberUtils.round(info.getMemoryUsage(), 1).doubleValue());
                write.set(MsServerHost::getServerHostMemUsage, host.getServerHostMemUsage());
            }
            List<DiskRowVO> disks = localService.getDisks();
            long t3 = System.nanoTime();
            if (disks != null && !disks.isEmpty()) {
                host.setServerHostDiskUsage(NumberUtils.round(disks.get(0).usage(), 1).doubleValue());
                write.set(MsServerHost::getServerHostDiskUsage, host.getServerHostDiskUsage());
            }
            // 磁盘 IO（KB/s，保留 1 位小数）
            double[] io = localService.getDiskIoKbs();
            long t4 = System.nanoTime();
            double diskRead = NumberUtils.round(io[0], 1).doubleValue();
            double diskWrite = NumberUtils.round(io[1], 1).doubleValue();
            host.setServerHostDiskReadKbs(diskRead);
            host.setServerHostDiskWriteKbs(diskWrite);
            write.set(MsServerHost::getServerHostDiskReadKbs, diskRead);
            write.set(MsServerHost::getServerHostDiskWriteKbs, diskWrite);
            // 网络收发（KB/s）：getHostInfo 内已经采过一次，直接复用同一帧，避免二次采样得到 0
            if (info != null && info.getNetworkRxKb() != null && info.getNetworkTxKb() != null) {
                double netRx = NumberUtils.round(info.getNetworkRxKb(), 1).doubleValue();
                double netTx = NumberUtils.round(info.getNetworkTxKb(), 1).doubleValue();
                host.setServerHostNetRxKbs(netRx);
                host.setServerHostNetTxKbs(netTx);
                write.set(MsServerHost::getServerHostNetRxKbs, netRx);
                write.set(MsServerHost::getServerHostNetTxKbs, netTx);
            }
            // 运行软件（非核心）：识别后 JSON 落库；单独 try/catch，软件识别失败/超时不拖垮核心指标采集
            long t5 = System.nanoTime();
            try {
                List<com.chua.starter.server.support.software.SoftwareInstanceVO> software =
                        localService.getSoftware();
                if (software != null && !software.isEmpty()) {
                    String json = new com.fasterxml.jackson.databind.ObjectMapper()
                            .writeValueAsString(software);
                    host.setServerHostSoftware(json);
                    write.set(MsServerHost::getServerHostSoftware, json);
                }
            } catch (Exception swEx) {
                log.warn("[collect] 软件识别失败，不影响核心采集 hostId={}: {}",
                        host.getId(), swEx.getMessage());
            }
            host.setServerHostOnline(1);
            host.setServerHostOnlineTime(LocalDateTime.now());
            write.set(MsServerHost::getServerHostOnline, host.getServerHostOnline());
            write.set(MsServerHost::getServerHostOnlineTime, host.getServerHostOnlineTime());
            // 只写本次采到的列：updateById 会用这份旧快照覆盖并发路径刚改过的列
            hostMapper.update(null, write.eq(MsServerHost::getId, host.getId()));
            long t6 = System.nanoTime();
            log.info("[collect] LOCAL(OSHI) 本机采集成功 hostId={}, cpu={}%, mem={}%, disk={}%",
                    host.getId(), host.getServerHostCpuUsage(), host.getServerHostMemUsage(), host.getServerHostDiskUsage());
            log.info("[collect-timing] hostInfo={}ms cpu={}ms disks={}ms diskIo={}ms software={}ms update={}ms TOTAL={}ms",
                    (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, (t3 - t2) / 1_000_000,
                    (t4 - t3) / 1_000_000, (t5 - t4) / 1_000_000, (t6 - t5) / 1_000_000,
                    (t6 - t0) / 1_000_000);
            return CollectOutcome.ok("本机 OSHI 采集成功");
        } catch (Exception e) {
            log.warn("[collect] LOCAL(OSHI) 采集失败 hostId={}: {}",
                    context.getHost().getId(), e.getMessage());
            return CollectOutcome.fail(e.getMessage());
        }
    }
}
