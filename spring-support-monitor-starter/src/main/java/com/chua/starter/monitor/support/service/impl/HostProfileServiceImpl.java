package com.chua.starter.monitor.support.service.impl;

import com.chua.common.support.lang.code.ReturnResult;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.starter.monitor.support.model.DiskRowVO;
import com.chua.starter.monitor.support.model.EnvVarVO;
import com.chua.starter.monitor.support.model.FirewallRuleRowVO;
import com.chua.starter.monitor.support.model.HostInfoVO;
import com.chua.starter.monitor.support.model.HostMetricsVO;
import com.chua.starter.monitor.support.model.HostProfileVO;
import com.chua.starter.monitor.support.model.NetProcessRowVO;
import com.chua.starter.monitor.support.model.NetworkInterfaceRowVO;
import com.chua.starter.monitor.support.model.ProcessRowVO;
import com.chua.starter.monitor.support.model.ProcessSummaryVO;
import com.chua.starter.monitor.support.model.ServiceRowVO;
import com.chua.starter.monitor.support.service.AgentHttpClient;
import com.chua.starter.monitor.support.service.HostProfileService;
import com.chua.starter.monitor.support.spi.SystemInfoProvider;
import com.chua.starter.monitor.support.spi.SystemInfoProviderRouter;
import com.chua.starter.monitor.support.spi.impl.AgentProvider;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.chua.starter.server.support.util.HostPlatforms;
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
     * 进程/服务分区单次允许的最大条数
     */
    private static final int MAX_SECTION_SIZE = 500;

    /**
     * 关键字检索时向通道取的宽页条数：候选集截断只会少命中，不影响无关键字时的通道分页
     */
    private static final int KEYWORD_SCAN_LIMIT = 2000;

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

    /**
     * JSON 序列化（网卡列表落库）
     */
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

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
        if (isAgentProvider(provider)) {
            vo.setPort(agentClient.portOf(host));
        }
        vo.setOsMismatch(osMismatch(host, info));
        vo.setCollectedAt(LocalDateTime.now().toString());
        if (persist) {
            persistSnapshot(host, info, metrics, provider);
        } else {
            clearError(hostId);
        }
        return ReturnResult.ok(vo);
    }

    @Override
    public ReturnResult<HostProfileVO> snapshot(Long hostId) {
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        return ReturnResult.ok(fromStored(host));
    }

    /**
     * 由台账行还原画像视图（不触发任何通道采集）。
     *
     * @param h 台账主机行
     * @return 表中保存的画像视图，从未采集过的字段为空 / 0
     */
    private HostProfileVO fromStored(MsServerHost h) {
        double memTotal = h.getServerHostMemoryMb() == null ? 0.0 : h.getServerHostMemoryMb();
        double memPct = h.getServerHostMemUsage() == null ? 0.0 : h.getServerHostMemUsage();
        double diskTotal = h.getServerHostDiskGb() == null ? 0.0 : h.getServerHostDiskGb();
        double diskPct = h.getServerHostDiskUsage() == null ? 0.0 : h.getServerHostDiskUsage();
        long memUsed = Math.round(memTotal * memPct / 100.0);
        double diskUsed = round(diskTotal * diskPct / 100.0);

        HostInfoVO info = new HostInfoVO();
        info.setHostname(h.getServerHostOsHostname());
        info.setOs(h.getServerHostOsType());
        info.setOsName(h.getServerHostOsType());
        info.setOsVersion(h.getServerHostOsVersion());
        info.setArch(h.getServerHostOsArch());
        info.setCpuCores(h.getServerHostCpuCores());
        info.setMemoryMb((long) memTotal);
        info.setMemoryTotalMb((long) memTotal);
        info.setMemoryUsedMb(memUsed);
        info.setMemoryUsage(round(memPct));
        info.setCpuPercent(h.getServerHostCpuUsage() == null ? 0.0 : round(h.getServerHostCpuUsage()));
        info.setDiskTotalGb(diskTotal);
        info.setDiskUsedGb(diskUsed);
        info.setDiskReadKbs(h.getServerHostDiskReadKbs());
        info.setDiskWriteKbs(h.getServerHostDiskWriteKbs());
        info.setPublicIp(h.getServerHostPublicIp());
        info.setPrivateIp(h.getServerHostPrivateIp());
        info.setMac(h.getServerHostMac());
        info.setHostCode(h.getServerHostCode());
        info.setJavaVersion(h.getServerHostJavaVersion());
        info.setUptimeSec(h.getServerHostUptimeSec());
        info.setAgentVersion(h.getServerHostAgentVersion());
        info.setNetworkInterfaces(parseStoredNetworks(h.getServerHostNetworks()));

        HostMetricsVO metrics = new HostMetricsVO();
        metrics.setCpuPercent(info.getCpuPercent());
        metrics.setMemoryTotalMb((long) memTotal);
        metrics.setMemoryUsedMb(memUsed);
        metrics.setMemoryPercent(round(memPct));
        metrics.setDiskTotalGb(diskTotal);
        metrics.setDiskUsedGb(diskUsed);
        metrics.setDiskPercent(round(diskPct));
        metrics.setDiskReadKbs(h.getServerHostDiskReadKbs() == null ? 0.0 : round(h.getServerHostDiskReadKbs()));
        metrics.setDiskWriteKbs(h.getServerHostDiskWriteKbs() == null ? 0.0 : round(h.getServerHostDiskWriteKbs()));
        metrics.setUptimeSec(h.getServerHostUptimeSec() == null ? 0L : h.getServerHostUptimeSec());
        metrics.setCollectTime(h.getServerHostLastCollectTime() == null ? null
                : h.getServerHostLastCollectTime().toString());

        HostProfileVO vo = new HostProfileVO();
        vo.setInfo(info);
        vo.setMetrics(metrics);
        vo.setHost(h);
        vo.setPort(h.getServerHostAgentPort());
        vo.setCollectedAt(h.getServerHostLastCollectTime() == null ? null
                : h.getServerHostLastCollectTime().toString());
        return vo;
    }

    /**
     * 解析台账里保存的网卡 JSON，失败 / 为空返回空列表。
     *
     * @param json 网卡列表 JSON
     * @return 网卡行列表
     */
    private List<NetworkInterfaceRowVO> parseStoredNetworks(String json) {
        if (json == null || json.isBlank()) {
            return java.util.Collections.emptyList();
        }
        try {
            List<NetworkInterfaceRowVO> list = objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, NetworkInterfaceRowVO.class));
            return list == null ? java.util.Collections.emptyList() : list;
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }

    @Override
    public ReturnResult<List<NetProcessRowVO>> ports(Long hostId, Integer page, Integer size, String keyword) {
        // 「监听端口」只展示处于监听态的 TCP(LISTEN) 与 UDP(UNCONN) 端点，
        // 不混入 ESTABLISHED / TIME_WAIT 等活动连接；后端按页切片，前端只渲染当前页。
        int no = pageOf(page);
        int limit = sizeOf(size);
        String needle = lower(keyword);
        return collect(hostId, (provider, host) -> {
            List<NetProcessRowVO> filtered = provider.netProcesses(host).stream()
                    .filter(row -> {
                        String state = String.valueOf(row.state()).toUpperCase(Locale.ROOT);
                        return "LISTEN".equals(state) || "UNCONN".equals(state);
                    })
                    .filter(row -> needle.isEmpty() || matches(needle,
                            String.valueOf(row.localPort()), row.local(), row.process(),
                            row.protocol(), row.state()))
                    .toList();
            return slice(filtered, no, limit);
        });
    }

    @Override
    public ReturnResult<List<FirewallRuleRowVO>> firewall(Long hostId, Integer page, Integer size, String keyword) {
        // 防火墙规则后端分页：规则名/链/方向/动作/协议/端口任一命中即保留，再按页切片。
        int no = pageOf(page);
        int limit = sizeOf(size);
        String needle = lower(keyword);
        return collect(hostId, (provider, host) -> {
            List<FirewallRuleRowVO> filtered = provider.firewallRules(host).stream()
                    .filter(row -> needle.isEmpty() || matches(needle,
                            row.getName(), row.getChain(), row.getDirection(), row.getAction(),
                            row.getProfile(), row.getProtocol(), String.valueOf(row.getLocalPort()),
                            row.getTarget(), row.getSource(), row.getDestination()))
                    .toList();
            return slice(filtered, no, limit);
        });
    }

    @Override
    public ReturnResult<List<ProcessRowVO>> processes(Long hostId, Integer page, Integer size, String keyword) {
        int no = pageOf(page);
        int limit = sizeOf(size);
        String needle = lower(keyword);
        if (needle.isEmpty()) {
            return collect(hostId, (provider, host) -> provider.processes(host, no, limit));
        }
        return collect(hostId, (provider, host) -> slice(provider.processes(host, 1, KEYWORD_SCAN_LIMIT).stream()
                .filter(row -> matches(needle, String.valueOf(row.getPid()), row.getName(), row.getUser(),
                        row.getCommand(), row.getPath()))
                .toList(), no, limit));
    }

    @Override
    public ReturnResult<List<ServiceRowVO>> services(Long hostId, Integer page, Integer size, String keyword) {
        int no = pageOf(page);
        int limit = sizeOf(size);
        String needle = lower(keyword);
        if (needle.isEmpty()) {
            return collect(hostId, (provider, host) -> provider.services(host, no, limit));
        }
        return collect(hostId, (provider, host) -> slice(provider.services(host, 1, KEYWORD_SCAN_LIMIT).stream()
                .filter(row -> matches(needle, row.getName(), row.getDisplayName(), row.getState(),
                        row.getStartType(), String.valueOf(row.getPid())))
                .toList(), no, limit));
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
     * 归一化页码，非法值按第一页处理。
     *
     * @param page 页码，可为 null
     * @return 从 1 开始的页码
     */
    private static int pageOf(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    /**
     * 归一化每页条数，避免无上限抓取打爆通道。
     *
     * @param size 每页条数，可为 null
     * @return 生效条数
     */
    private static int sizeOf(Integer size) {
        return size == null || size < 1 ? DEFAULT_SECTION_SIZE : Math.min(size, MAX_SECTION_SIZE);
    }

    /**
     * 关键字归一化：两端空白去掉并转小写，空串表示不过滤。
     *
     * @param keyword 原始关键字，可为 null
     * @return 可直接比对的关键字
     */
    private static String lower(String keyword) {
        return keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 任一展示字段命中关键字即保留（忽略大小写）。
     *
     * @param needle 已归一化的关键字
     * @param fields 参与匹配的字段，可为 null
     * @return 是否命中
     */
    private static boolean matches(String needle, String... fields) {
        for (String field : fields) {
            if (field != null && field.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 对内存候选集按请求页切片。
     *
     * @param rows 过滤后的全部命中行
     * @param page 页码，从 1 开始
     * @param size 每页条数
     * @param <T>  行类型
     * @return 当前页数据，越界返回空列表
     */
    private static <T> List<T> slice(List<T> rows, int page, int size) {
        int from = (page - 1) * size;
        if (from >= rows.size()) {
            return List.of();
        }
        return rows.subList(from, Math.min(from + size, rows.size()));
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
        // 百分比 / 速率统一保留 1 位小数，避免下发长浮点（总量 / 计数为整数不收敛）
        metrics.setCpuPercent(round(value(info.getCpuPercent())));
        metrics.setMemoryTotalMb(value(info.getMemoryTotalMb()));
        metrics.setMemoryUsedMb(value(info.getMemoryUsedMb()));
        metrics.setMemoryPercent(round(value(info.getMemoryUsage())));
        metrics.setUptimeSec(value(info.getUptimeSec()));
        metrics.setNetworkPercent(round(value(info.getNetworkPercent())));
        metrics.setNetworkRxKb(round(value(info.getNetworkRxKb())));
        metrics.setNetworkTxKb(round(value(info.getNetworkTxKb())));
        metrics.setDiskReadKbs(round(value(info.getDiskReadKbs())));
        metrics.setDiskWriteKbs(round(value(info.getDiskWriteKbs())));
        metrics.setCollectTime(LocalDateTime.now().toString());

        double diskTotal = 0.0;
        double diskUsed = 0.0;
        if (info.getDiskTotalGb() != null && info.getDiskTotalGb() > 0 && info.getDiskUsedGb() != null) {
            // 合并采集通道（Windows SSH）已随概览一次返回磁盘汇总，避免再发一次往返
            diskTotal = info.getDiskTotalGb();
            diskUsed = info.getDiskUsedGb();
        } else {
            List<DiskRowVO> disks = provider.disks(host);
            if (disks != null) {
                for (DiskRowVO disk : disks) {
                    diskTotal += disk.getTotalGb();
                    diskUsed += disk.getUsedGb();
                }
            }
        }
        metrics.setDiskTotalGb(round(diskTotal));
        metrics.setDiskUsedGb(round(diskUsed));
        metrics.setDiskPercent(diskTotal <= 0 ? 0.0 : round(diskUsed * 100.0 / diskTotal));

        if (info.getProcessCount() != null) {
            // 合并采集通道已随概览返回进程/线程总数
            metrics.setProcessCount(info.getProcessCount());
            metrics.setThreadCount(info.getThreadCount() == null ? 0 : info.getThreadCount());
        } else {
            ProcessSummaryVO summary = provider.processSummary(host);
            if (summary != null) {
                metrics.setProcessCount(summary.processCount());
                metrics.setThreadCount((int) summary.threadCount());
            }
        }
        // 句柄数：Windows 汇总各进程 HandleCount，POSIX 计打开文件句柄数；取不到留 -1
        metrics.setHandleCount(handleCount(provider, host));
        return metrics;
    }

    /**
     * 句柄数短期缓存：hostId → [采集时刻(ms), 值]。
     *
     * <p>{@link #refresh} 会被「画像手动刷新」与「AGENT 采集循环」
     * （{@code HostRealtimePushConfiguration} → pullSnapshot）两条路径调用；句柄数变化很慢，
     * 加一层短 TTL 免去采集循环每轮都多跑一条 PowerShell / {@code /proc} 命令。</p>
     */
    private final java.util.Map<Long, long[]> handleCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 句柄数缓存有效期（毫秒）
     */
    private static final long HANDLE_CACHE_TTL_MS = 15_000L;

    /**
     * 取主机句柄数（带短 TTL 缓存）；取不到返回 -1，绝不抛异常打断画像采集。
     *
     * @param provider 采集通道
     * @param host     目标主机
     * @return 句柄数
     */
    private long handleCount(SystemInfoProvider provider, MsServerHost host) {
        Long id = host.getId();
        if (id == null) {
            return provider.handleCount(host);
        }
        long now = System.currentTimeMillis();
        long[] hit = handleCache.get(id);
        if (hit != null && now - hit[0] < HANDLE_CACHE_TTL_MS) {
            return hit[1];
        }
        long value = provider.handleCount(host);
        handleCache.put(id, new long[]{now, value});
        return value;
    }

    /**
     * 把采集到的硬件与用量快照写回主机台账。
     *
     * @param host    主机
     * @param info    基础信息
     * @param metrics 实时指标
     */
    private void persistSnapshot(MsServerHost host, HostInfoVO info, HostMetricsVO metrics, SystemInfoProvider provider) {
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
        if (notEmpty(info.getHostCode())) {
            host.setServerHostCode(info.getHostCode());
        }
        if (notEmpty(info.getMac())) {
            host.setServerHostMac(info.getMac());
        }
        if (notEmpty(info.getHostname())) {
            host.setServerHostOsHostname(info.getHostname());
        }
        if (notEmpty(info.getJavaVersion())) {
            host.setServerHostJavaVersion(info.getJavaVersion());
        }
        if (info.getUptimeSec() != null && info.getUptimeSec() > 0) {
            host.setServerHostUptimeSec(info.getUptimeSec());
        }
        // 网卡完整列表落库：本机通道已内嵌，其余通道补一次采集
        List<NetworkInterfaceRowVO> nics = info.getNetworkInterfaces();
        if (nics == null || nics.isEmpty()) {
            try {
                nics = provider.networkInterfaces(host);
            } catch (Exception ignore) {
                nics = java.util.Collections.emptyList();
            }
        }
        if (nics != null && !nics.isEmpty()) {
            try {
                host.setServerHostNetworks(objectMapper.writeValueAsString(nics));
            } catch (com.fasterxml.jackson.core.JsonProcessingException ignore) {
                // 序列化失败不阻断主流程
            }
        }
        host.setServerHostCpuUsage(metrics.getCpuPercent());
        host.setServerHostMemUsage(metrics.getMemoryPercent());
        host.setServerHostDiskUsage(metrics.getDiskPercent());
        host.setServerHostDiskReadKbs(metrics.getDiskReadKbs());
        host.setServerHostDiskWriteKbs(metrics.getDiskWriteKbs());
        // 软件实例：由进程列表按软件特征派生（同软件多实例按 PID），JSON 落库
        String softwareJson = deriveSoftware(provider, host);
        if (softwareJson != null) {
            host.setServerHostSoftware(softwareJson);
        }

        LocalDateTime now = LocalDateTime.now();
        host.setServerHostOnline(1);
        host.setServerHostOnlineTime(now);
        host.setServerHostLastCollectTime(now);
        if (isAgentProvider(provider)) {
            host.setServerHostAgentTime(now);
            host.setServerHostAgentInstalled(1);
            host.setServerHostAgentPort(agentClient.portOf(host));
            if (notEmpty(info.getAgentVersion())) {
                host.setServerHostAgentVersion(info.getAgentVersion());
            }
        }
        // 关键：不能把采集开始时加载的陈旧快照整行写回。该快照里的「用户/配置开关」
        // （SSH 开关、在线检测开关、采集开关/方式/间隔、延迟开关、远控开关、Agent 开关、启停状态）
        // 可能已被并发路径（用户保存、探测回写 sshEnabled 等）改过；整行写回会用旧值覆盖，
        // 实测表现为「用户把 win101 的 SSH 打开后，采集每 10~60s 又把它改回 0」，
        // 于是所有发布操作被「未开启 SSH」闸门周期性拦住。
        // 这里把这些不属于采集产物的列显式置 null，借助 updateById 的 null 跳过策略保留库中现值。
        host.setServerHostSshEnabled(null);
        host.setServerHostOnlineEnabled(null);
        host.setServerHostCollectEnabled(null);
        host.setServerHostCollectMode(null);
        host.setServerHostCollectInterval(null);
        host.setServerHostDelayEnabled(null);
        host.setServerHostDelayInterval(null);
        host.setServerHostRemoteEnabled(null);
        host.setServerHostAgentEnabled(null);
        host.setServerHostStatus(null);
        hostMapper.updateById(host);
        // 采集成功即清掉历史失败原因（updateById 会跳过 null 字段，必须走显式置空）
        clearError(host.getId());
    }

    /**
     * 由通道进程列表派生软件实例 JSON。
     *
     * @param provider 采集通道
     * @param host     主机
     * @return 软件实例 JSON；无已知软件返回 null
     */
    private String deriveSoftware(SystemInfoProvider provider, MsServerHost host) {
        try {
            List<ProcessRowVO> procs = provider.processes(host, 1, 300);
            if (procs == null || procs.isEmpty()) {
                return null;
            }
            List<com.chua.starter.server.support.software.SoftwareInstanceVO> list =
                    new java.util.ArrayList<>();
            for (ProcessRowVO p : procs) {
                String cmd = p.getCommand() != null && !p.getCommand().isBlank()
                        ? p.getCommand() : p.getPath();
                String type = com.chua.starter.server.support.software.SoftwareCatalog
                        .match(p.getName(), cmd);
                if (type == null) {
                    continue;
                }
                list.add(com.chua.starter.server.support.software.SoftwareCatalog.instance(type,
                        (int) p.getPid(), p.getCpu(), p.getMemory(),
                        uptimeFromStart(p.getStartTime()), cmd == null ? p.getName() : cmd));
            }
            return list.isEmpty() ? null : objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            log.debug("[profile] 软件派生失败 hostId={}: {}", host.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * 由启动时间文本（yyyy-MM-dd HH:mm:ss）换算运行秒数，解析失败为 0。
     *
     * @param startTime 启动时间
     * @return 运行秒数
     */
    private static long uptimeFromStart(String startTime) {
        if (startTime == null || startTime.isBlank()) {
            return 0;
        }
        try {
            LocalDateTime start = LocalDateTime.parse(startTime,
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return Math.max(0, java.time.Duration.between(start, LocalDateTime.now()).getSeconds());
        } catch (Exception e) {
            return 0;
        }
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
     * @return 采集方式为 AGENT 时返回 true
     */
    private static boolean isAgentChannel(MsServerHost host) {
        return CHANNEL_AGENT.equalsIgnoreCase(host.getServerHostCollectMode());
    }

    /**
     * 实际服务本次采集的是否为 Agent 通道（按 Provider 实例判定，而非配置值）。
     *
     * @param provider 实际命中的采集实现
     * @return 为 AgentProvider 时返回 true
     */
    private static boolean isAgentProvider(SystemInfoProvider provider) {
        return provider instanceof AgentProvider;
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
        // 不在此把 agent_installed 置 0：installed 是部署生命周期状态（仅由部署=1/卸载=0 改变），
        // 一次瞬时采集超时/分区失败只记录原因，否则健康 Agent 会因偶发抖动被整体判不可用。
        // 只写自己负责的一列：整行 updateById 会把这份快照里的 SSH 开关/采集开关等
        // 用户配置按旧值写回（用户刚改过就被覆盖），进而周期性阻断发布等操作。
        hostMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MsServerHost>()
                .eq(MsServerHost::getId, hostId)
                .set(MsServerHost::getServerHostAgentError, host.getServerHostAgentError()));
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
