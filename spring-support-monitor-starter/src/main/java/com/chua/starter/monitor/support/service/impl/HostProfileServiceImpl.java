package com.chua.starter.monitor.support.service.impl;

import com.chua.common.support.lang.code.ReturnResult;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.starter.monitor.support.model.DiskRowVO;
import com.chua.starter.monitor.support.model.EnvVarVO;
import com.chua.starter.monitor.support.model.HostInfoVO;
import com.chua.starter.monitor.support.model.HostMetricsVO;
import com.chua.starter.monitor.support.model.HostProfileVO;
import com.chua.starter.monitor.support.model.NetProcessRowVO;
import com.chua.starter.monitor.support.model.NetworkInterfaceRowVO;
import com.chua.starter.monitor.support.model.ProcessRowVO;
import com.chua.starter.monitor.support.model.ServiceRowVO;
import com.chua.starter.monitor.support.service.AgentHttpClient;
import com.chua.starter.monitor.support.service.HostProfileService;
import com.chua.starter.monitor.support.spi.SystemInfoProvider;
import com.chua.starter.monitor.support.spi.SystemInfoProviderRouter;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;

/**
 * 主机画像服务实现：所有分区数据经 {@link SystemInfoProvider} 通道取得。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HostProfileServiceImpl implements HostProfileService {

    /**
     * Agent 接入的主机台账值：仅该通道回写 Agent 安装状态与端口
     */
    private static final String CHANNEL_AGENT = "AGENT";

    /**
     * 进程/服务分区的默认抓取条数
     */
    private static final int DEFAULT_SECTION_SIZE = 50;

    /**
     * 主机 映射器
     */
    private final MsServerHostMapper hostMapper;

    /**
     * agent HTTP 客户端（仅用于取 Agent 端口）
     */
    private final AgentHttpClient agentClient;

    /**
     * 采集通道路由器
     */
    private final SystemInfoProviderRouter providerRouter;

    @Override
    public ReturnResult<HostProfileVO> refresh(Long hostId, boolean persist) {
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        SystemInfoProvider provider;
        HostInfoVO info;
        try {
            provider = providerRouter.route(host);
            info = provider.hostInfo(host);
        } catch (Exception e) {
            String reason = cut(e.getMessage());
            if (persist) {
                markError(hostId, reason);
            }
            return ReturnResult.error("通道不可达: " + reason);
        }
        if (info == null) {
            if (persist) {
                markError(hostId, "通道返回空的基础信息");
            }
            return ReturnResult.error("通道未返回主机基础信息");
        }
        HostMetricsVO metrics;
        try {
            metrics = metrics(provider, host, info);
        } catch (Exception e) {
            String reason = cut(e.getMessage());
            if (persist) {
                markError(hostId, reason);
            }
            return ReturnResult.error("指标采集失败: " + reason);
        }

        HostProfileVO vo = new HostProfileVO();
        vo.setInfo(info);
        vo.setMetrics(metrics);
        vo.setHost(host);
        if (isAgentChannel(host)) {
            vo.setPort(agentClient.portOf(host));
        }
        vo.setOsMismatch(osMismatch(host, info));
        vo.setCollectedAt(LocalDateTime.now().toString());
        if (persist) {
            persistSnapshot(host, info, metrics);
        } else {
            clearError(hostId);
        }
        return ReturnResult.ok(vo);
    }

    @Override
    public ReturnResult<List<NetProcessRowVO>> ports(Long hostId) {
        return collect(hostId, (provider, host) -> provider.netProcesses(host));
    }

    @Override
    public ReturnResult<List<ProcessRowVO>> processes(Long hostId, Integer page, Integer size) {
        int no = page == null || page < 1 ? 1 : page;
        int limit = size == null || size < 1 ? DEFAULT_SECTION_SIZE : Math.min(size, 500);
        return collect(hostId, (provider, host) -> provider.processes(host, no, limit));
    }

    @Override
    public ReturnResult<List<ServiceRowVO>> services(Long hostId, Integer page, Integer size) {
        int no = page == null || page < 1 ? 1 : page;
        int limit = size == null || size < 1 ? DEFAULT_SECTION_SIZE : Math.min(size, 500);
        return collect(hostId, (provider, host) -> provider.services(host, no, limit));
    }

    @Override
    public ReturnResult<List<DiskRowVO>> disks(Long hostId) {
        return collect(hostId, (provider, host) -> provider.disks(host));
    }

    @Override
    public ReturnResult<List<NetworkInterfaceRowVO>> network(Long hostId) {
        return collect(hostId, (provider, host) -> provider.networkInterfaces(host));
    }

    @Override
    public ReturnResult<List<EnvVarVO>> env(Long hostId) {
        return collect(hostId, (provider, host) -> provider.env(host));
    }

    /**
     * 统一执行一次分区采集：失败原因写回台账，成功清除历史失败。
     *
     * @param hostId 主机标识
     * @param reader 采集动作
     * @param <R>    返回类型
     * @return 分区数据
     */
    private <R> ReturnResult<R> collect(Long hostId, BiFunction<SystemInfoProvider, MsServerHost, R> reader) {
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        try {
            R rows = reader.apply(providerRouter.route(host), host);
            clearError(hostId);
            return ReturnResult.ok(rows);
        } catch (Exception e) {
            String reason = cut(e.getMessage());
            markError(hostId, reason);
            return ReturnResult.error("通道不可达: " + reason);
        }
    }

    /**
     * 由同一次采集的基础信息、磁盘与进程结果推导实时指标。
     *
     * @param provider 采集通道
     * @param host     目标主机
     * @param info     基础信息
     * @return 实时指标
     */
    private HostMetricsVO metrics(SystemInfoProvider provider, MsServerHost host, HostInfoVO info) {
        HostMetricsVO metrics = new HostMetricsVO();
        metrics.setCpuPercent(value(info.getCpuPercent()));
        metrics.setMemoryTotalMb(value(info.getMemoryTotalMb()));
        metrics.setMemoryUsedMb(value(info.getMemoryUsedMb()));
        metrics.setMemoryPercent(value(info.getMemoryUsage()));
        metrics.setUptimeSec(value(info.getUptimeSec()));
        metrics.setCollectTime(LocalDateTime.now().toString());

        double diskTotal = 0.0;
        double diskUsed = 0.0;
        List<DiskRowVO> disks = provider.disks(host);
        if (disks != null) {
            for (DiskRowVO disk : disks) {
                diskTotal += disk.getTotalGb();
                diskUsed += disk.getUsedGb();
            }
        }
        metrics.setDiskTotalGb(round(diskTotal));
        metrics.setDiskUsedGb(round(diskUsed));
        metrics.setDiskPercent(diskTotal <= 0 ? 0.0 : round(diskUsed * 100.0 / diskTotal));

        List<ProcessRowVO> processes = provider.processes(host, 1, 10000);
        if (processes != null) {
            long threads = 0L;
            for (ProcessRowVO process : processes) {
                threads += process.getThreads();
            }
            metrics.setProcessCount(processes.size());
            metrics.setThreadCount((int) threads);
        }
        return metrics;
    }

    /**
     * 把采集到的硬件与用量快照写回主机台账。
     *
     * @param host    主机
     * @param info    基础信息
     * @param metrics 实时指标
     */
    private void persistSnapshot(MsServerHost host, HostInfoVO info, HostMetricsVO metrics) {
        if (notEmpty(info.getOsVersion())) {
            host.setServerHostOsVersion(info.getOsVersion());
        }
        if (notEmpty(info.getArch())) {
            host.setServerHostOsArch(info.getArch());
        }
        if (info.getCpuCores() != null && info.getCpuCores() > 0) {
            host.setServerHostCpuCores(info.getCpuCores());
        }
        if (info.getMemoryMb() != null && info.getMemoryMb() > 0) {
            host.setServerHostMemoryMb(info.getMemoryMb());
        }
        if (metrics.getDiskTotalGb() > 0) {
            host.setServerHostDiskGb((long) Math.round(metrics.getDiskTotalGb()));
        }
        if (notEmpty(info.getPrivateIp())) {
            host.setServerHostPrivateIp(info.getPrivateIp());
        }
        if (notEmpty(info.getPublicIp())) {
            host.setServerHostPublicIp(info.getPublicIp());
        }
        host.setServerHostCpuUsage(metrics.getCpuPercent());
        host.setServerHostMemUsage(metrics.getMemoryPercent());
        host.setServerHostDiskUsage(metrics.getDiskPercent());

        LocalDateTime now = LocalDateTime.now();
        host.setServerHostOnline(1);
        host.setServerHostOnlineTime(now);
        host.setServerHostLastCollectTime(now);
        if (isAgentChannel(host)) {
            host.setServerHostAgentTime(now);
            host.setServerHostAgentInstalled(1);
            host.setServerHostAgentPort(agentClient.portOf(host));
            if (notEmpty(info.getAgentVersion())) {
                host.setServerHostAgentVersion(info.getAgentVersion());
            }
        }
        hostMapper.updateById(host);
        // 采集成功即清掉历史失败原因（updateById 会跳过 null 字段，必须走显式置空）
        clearError(host.getId());
    }

    /**
     * 采集回报的系统族与台账 osType 不一致时给出提示。
     * <p>
     * 不自动改写 osType：它决定走 SSH 还是 WinRM、绑定哪套凭据，静默改写会让下一次连接直接失败。
     * </p>
     *
     * @param host 主机
     * @param info 基础信息
     * @return 不一致时返回提示文案，一致时返回 null
     */
    private String osMismatch(MsServerHost host, HostInfoVO info) {
        String reported = info.getOs() == null ? "" : info.getOs().toUpperCase(Locale.ROOT);
        String recorded = host.getServerHostOsType() == null ? "" : host.getServerHostOsType().toUpperCase(Locale.ROOT);
        if (reported.isEmpty() || recorded.isEmpty() || CHANNEL_AGENT.equals(recorded) || "LOCAL".equals(recorded)) {
            // 台账里存的是接入方式时，osType 本身不表达平台，无从比对
            return null;
        }
        boolean same = (reported.contains("WINDOWS") && recorded.contains("WIN"))
                || (!reported.contains("WINDOWS") && !recorded.contains("WIN"));
        return same ? null : "采集回报系统为 " + info.getOs() + "，台账记录为 "
                + host.getServerHostOsType() + "，请确认远程通道配置";
    }

    /**
     * 主机是否经 Agent 接入。
     *
     * @param host 主机
     * @return 台账 osType 为 AGENT 时返回 true
     */
    private static boolean isAgentChannel(MsServerHost host) {
        return CHANNEL_AGENT.equalsIgnoreCase(host.getServerHostOsType());
    }

    /**
     * 记录通道访问失败原因。
     *
     * @param hostId 主机标识
     * @param reason 原因
     */
    private void markError(Long hostId, String reason) {
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            return;
        }
        host.setServerHostAgentError(cut(reason));
        if (isAgentChannel(host)) {
            host.setServerHostAgentInstalled(0);
        }
        hostMapper.updateById(host);
    }

    /**
     * 清除历史失败原因（必须显式置空：updateById 的字段策略会跳过 null）。
     *
     * @param hostId 主机标识
     */
    private void clearError(Long hostId) {
        if (hostId == null) {
            return;
        }
        hostMapper.update(null, new LambdaUpdateWrapper<MsServerHost>()
                .eq(MsServerHost::getId, hostId)
                .set(MsServerHost::getServerHostAgentError, null));
    }

    /**
     * 文本非空判断。
     *
     * @param value 文本
     * @return 非空白返回 true
     */
    private static boolean notEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 可空数值取值。
     *
     * @param value 原值
     * @return 数值，为空返回 0
     */
    private static double value(Double value) {
        return value == null ? 0.0 : value;
    }

    /**
     * 可空长整取值。
     *
     * @param value 原值
     * @return 数值，为空返回 0
     */
    private static long value(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * 保留一位小数。
     *
     * @param value 原值
     * @return 归一后的值
     */
    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /**
     * 截断过长的错误信息。
     *
     * @param text 原文
     * @return 截断结果
     */
    private String cut(String text) {
        if (text == null || text.isBlank()) {
            return "未知错误";
        }
        String one = text.replaceAll("\\s+", " ").trim();
        return one.length() > 480 ? one.substring(0, 480) : one;
    }
}
