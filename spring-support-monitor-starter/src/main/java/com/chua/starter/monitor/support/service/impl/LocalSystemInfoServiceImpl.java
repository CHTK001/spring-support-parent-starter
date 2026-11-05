// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.monitor.support.service.impl;

import com.chua.starter.monitor.support.model.DiskRowVO;
import com.chua.starter.monitor.support.model.EnvVarVO;
import com.chua.starter.monitor.support.model.HostInfoVO;
import com.chua.starter.monitor.support.model.NetProcessRowVO;
import com.chua.starter.monitor.support.model.NetworkInterfaceRowVO;
import com.chua.starter.monitor.support.model.ProcessRowVO;
import com.chua.starter.monitor.support.model.ProcessSummaryVO;
import com.chua.starter.monitor.support.model.ServiceRowVO;
import com.chua.starter.monitor.support.service.LocalSystemInfoService;
import com.chua.starter.monitor.support.spi.HostOperationsRegistry;
import com.chua.starter.monitor.support.spi.RowNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.FileSystem;
import oshi.software.os.InternetProtocolStats;
import oshi.software.os.OSFileStore;
import oshi.software.os.OSProcess;
import oshi.software.os.OSService;
import oshi.software.os.OperatingSystem;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本机系统信息采集服务实现（OSHI + Windows 服务枚举）。
 *
 * <p>支持 Windows / Linux / macOS 三大平台。OSHI 不可用（缺 native 库、权限受限）时逐方法降级，
 * 不整体失败。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
public class LocalSystemInfoServiceImpl implements LocalSystemInfoService {

    /**
     * 启动时间展示格式
     */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 一 MB 的字节数
     */
    private static final long BYTES_PER_MB = 1024L * 1024L;

    /**
     * 一 GB 的双精度字节数
     */
    private static final double BYTES_PER_GB_DOUBLE = 1024.0 * 1024.0 * 1024.0;

    /**
     * 进程内存下发单位：MB，与 Agent、SSH、WinRM 三类通道一致
     */
    private static final long MEMORY_UNIT_BYTES = BYTES_PER_MB;

    /**
     * OSHI 句柄，懒加载失败时为 null（降级为基础信息模式）
     */
    private volatile SystemInfo si;

    /**
     * 上次 CPU ticks（getSystemCpuLoadBetweenTicks 需要两次采样）
     */
    private transient long[] lastCpuTicks;

    /**
     * 上次 CPU 采样时刻（毫秒）
     */
    private transient long lastCpuAt;

    /**
     * 上次进程快照（进程瞬时 CPU 需两次采样做 between-ticks），按 pid 匹配。
     * 用瞬时值替代 lifetime 累计值，避免 Idle 进程跨核累计显示超过 100%。
     */
    private final java.util.concurrent.atomic.AtomicReference<List<OSProcess>> lastProcesses =
            new java.util.concurrent.atomic.AtomicReference<>(List.of());

    /**
     * 上次磁盘 IO 累计字节与采样时刻：[readBytes, writeBytes, epochMs]，首帧为 null
     */
    private final java.util.concurrent.atomic.AtomicReference<long[]> lastDiskIo =
            new java.util.concurrent.atomic.AtomicReference<>(null);

    /**
     * 上次网卡吞吐累计字节与采样时刻：[recvBytes, sentBytes, epochMs]，首帧为 null
     */
    private final java.util.concurrent.atomic.AtomicReference<long[]> lastNetIo =
            new java.util.concurrent.atomic.AtomicReference<>(null);

    /**
     * 上次算出的 CPU 使用率，窗口不足时沿用
     */
    private transient double lastCpuPercent;

    /**
     * CPU 采样最小窗口（毫秒）：窗口短于此值增量无意义，冷启动时阻塞补足
     */
    private static final long CPU_WINDOW_MS = 500L;

    /**
     * 懒加载并缓存 OSHI {@link SystemInfo} 实例（双重检查锁）。
     *
     * @return SystemInfo 实例；初始化失败时返回 null
     */
    private SystemInfo getSi() {
        if (si == null) {
            synchronized (this) {
                if (si == null) {
                    try {
                        si = new SystemInfo();
                    } catch (Throwable t) {
                        log.warn("OSHI 初始化失败，降级为基础信息模式: {}", t.getMessage());
                        si = null;
                    }
                }
            }
        }
        return si;
    }

    /**
     * 静态主机信息快照（OS 版本/构建、CPU 型号与核数、总内存、网卡列表、网关、主机编码、包管理器等，
     * 基本不变）。这些字段在 Windows 上依赖慢 WMI/网卡枚举（getNetworkParams、getNetworkIFs、
     * getComputerSystem、版本信息），周期采集每轮重跑会造成秒级抖动。仅在按需画像刷新
     * （includePublicIp=true，即点击「刷新」）或快照缺失时重建；时间轮周期采集复用快照。
     */
    private final java.util.concurrent.atomic.AtomicReference<HostInfoVO> staticBase =
            new java.util.concurrent.atomic.AtomicReference<>();

    @Override
    public HostInfoVO getHostInfo(boolean includePublicIp) {
        HostInfoVO info;
        SystemInfo sys = getSi();
        if (sys == null) {
            info = new HostInfoVO();
            degradeToSystemProperties(info);
            info.setPackageManager(packageManager());
            return info;
        }
        try {
            HardwareAbstractionLayer hal = sys.getHardware();
            OperatingSystem os = sys.getOperatingSystem();
            // 静态部分：按需刷新强制重建，周期采集复用缓存（慢 WMI/网卡枚举不每轮跑）
            HostInfoVO base = staticBase.get();
            if (includePublicIp || base == null) {
                base = buildStaticBase(hal, os);
                staticBase.set(base);
            }
            info = new HostInfoVO();
            org.springframework.beans.BeanUtils.copyProperties(base, info);
            // 动态部分：CPU%、内存已用、运行时长（廉价原生调用，无 WMI 枚举）
            applyDynamic(info, hal, os);
            // 公网 IP：仅按需画像刷新（点击「刷新」）时探测；时间轮周期采集不探测，公网 IP 留空
            if (includePublicIp) {
                info.setPublicIp(detectPublicIp());
            }
        } catch (Throwable t) {
            log.warn("OSHI 采集失败，降级基础信息: {}", t.getMessage());
            info = new HostInfoVO();
            degradeToSystemProperties(info);
            info.setPackageManager(packageManager());
        }
        // 磁盘IO / 网络吞吐（KB/s）：复用周期采样帧，首帧可能为 0，待 socket 推送纠正
        double[] io = getDiskIoKbs();
        info.setDiskReadKbs(io[0]);
        info.setDiskWriteKbs(io[1]);
        double[] nf = getNetworkThroughputKbs();
        info.setNetworkRxKb(nf[0]);
        info.setNetworkTxKb(nf[1]);
        return info;
    }

    /**
     * 采集显卡型号名称（多卡用逗号连接）。
     *
     * <p>用 {@link oshi.hardware.GraphicsCard} 而不是自己拼 shell：OSHI 把
     * Windows 的 WMI、Linux 的 sysfs、macOS 的 system_profiler 都封在同一个 API 后面，
     * 新增平台不用再改命令。</p>
     *
     * <p>逐卡 try-catch：部分环境（容器、精简内核、远程桌面会话）枚举显卡会抛异常，
     * 一块坏卡不该让整份主机画像降级。拿不到名称时退而用 vendor 名，至少能看出是 Intel / NVIDIA / AMD。</p>
     *
     * @param hal 硬件层
     * @return 显卡名称；无显卡或全部采集失败时返回 {@code "unknown"}
     */
    private String graphicsAdapterNames(HardwareAbstractionLayer hal) {
        List<String> names = new ArrayList<>();
        try {
            for (oshi.hardware.GraphicsCard gpu : hal.getGraphicsCards()) {
                String name = null;
                try {
                    name = gpu.getName();
                } catch (Throwable t) {
                    log.debug("显卡名称读取失败: {}", t.getMessage());
                }
                if (name == null || name.isBlank()) {
                    try {
                        name = gpu.getVendor();
                    } catch (Throwable ignore) {
                        // vendor 也拿不到就跳过这块卡
                    }
                }
                if (name != null && !name.isBlank()) {
                    names.add(name.trim());
                }
            }
        } catch (Throwable t) {
            log.warn("显卡枚举失败: {}", t.getMessage());
        }
        if (names.isEmpty()) {
            return "unknown";
        }
        return String.join(", ", names);
    }

    /**
     * 组装静态/低频主机信息（含慢 WMI、网卡枚举、主机编码、版本信息等），结果缓存复用。
     *
     * @param hal 硬件层
     * @param os  操作系统
     * @return 含静态字段的主机信息
     */
    private HostInfoVO buildStaticBase(HardwareAbstractionLayer hal, OperatingSystem os) {
        HostInfoVO info = new HostInfoVO();
        CentralProcessor cpu = hal.getProcessor();
        info.setCpuCores(cpu.getLogicalProcessorCount());
        info.setCpuName(cpu.getProcessorIdentifier().getName());

        // 显卡：走 OSHI 的 GraphicsAdapter，Windows/Linux/macOS 同一份代码。
        // 原来这块是三套平台各一条 shell 命令（Windows 走 WMI、Linux 走 lspci、
        // macOS 走 system_profiler），Linux 那条 `lspci | grep -i vga` 还漏掉
        // NVIDIA 的「3D controller」与 AMD 的「Display controller」，带独显的机器反而取不到名字。
        info.setGpu(graphicsAdapterNames(hal));

        GlobalMemory mem = hal.getMemory();
        long totalMem = mem.getTotal();
        info.setMemoryTotalMb(totalMem / BYTES_PER_MB);
        info.setMemoryMb(totalMem / BYTES_PER_MB);

        info.setOsName(os.getFamily());
        info.setOsVersion(os.getVersionInfo().getVersion());
        info.setOsBuild(os.getVersionInfo().getBuildNumber());
        // getNetworkParams 在 Windows 较慢，只调一次并复用
        oshi.software.os.NetworkParams params = os.getNetworkParams();
        info.setHostname(params.getHostName());
        info.setIpv4DefaultGateway(params.getIpv4DefaultGateway());

        List<NetworkInterfaceRowVO> interfaces = networkInterfaces(hal);
        info.setNetworkInterfaces(interfaces);
        if (!interfaces.isEmpty()) {
            info.setMac(interfaces.get(0).getMac());
        }
        // 本机 JVM 信息与架构，零网络开销
        info.setJavaVersion(System.getProperty("java.version"));
        info.setArch(System.getProperty("os.arch"));
        info.setOs(os.getFamily());
        // 内网 IP：取首个非 loopback 的 IPv4（多地址以分号/逗号分隔时取第一个）
        for (NetworkInterfaceRowVO ni : interfaces) {
            String v4 = ni.getIpv4();
            if (v4 != null && !v4.isBlank() && !v4.startsWith("127.")) {
                int sep = Math.min(v4.indexOf(';') < 0 ? Integer.MAX_VALUE : v4.indexOf(';'),
                        v4.indexOf(',') < 0 ? Integer.MAX_VALUE : v4.indexOf(','));
                info.setPrivateIp((sep == Integer.MAX_VALUE ? v4 : v4.substring(0, sep)).trim());
                break;
            }
        }
        // 主机编码：优先硬件 UUID（Windows 多为 MachineGuid 形态，Linux 为产品 UUID）
        String code = hal.getComputerSystem().getHardwareUUID();
        if (code != null && !code.isBlank() && !"N/A".equalsIgnoreCase(code)) {
            info.setHostCode(code.trim());
        }
        info.setPackageManager(packageManager());
        return info;
    }

    /**
     * 覆盖动态/每帧变化的值（CPU%、内存已用、运行时长），均为廉价原生调用，无 WMI 枚举。
     *
     * @param info 待填充主机信息
     * @param hal  硬件层
     * @param os   操作系统
     */
    private void applyDynamic(HostInfoVO info, HardwareAbstractionLayer hal, OperatingSystem os) {
        info.setCpuPercent(getCpuPercent());
        info.setUptimeSec(os.getSystemUptime());
        GlobalMemory mem = hal.getMemory();
        long totalMem = mem.getTotal();
        long usedMem = totalMem - mem.getAvailable();
        info.setMemoryUsedMb(usedMem / BYTES_PER_MB);
        info.setMemoryUsage(totalMem > 0 ? (double) usedMem / totalMem * 100 : 0);
    }

    @Override
    public double getCpuPercent() {
        try {
            SystemInfo sys = getSi();
            if (sys == null) {
                return 0;
            }
            CentralProcessor cpu = sys.getHardware().getProcessor();
            synchronized (this) {
                long now = System.currentTimeMillis();
                if (lastCpuTicks == null) {
                    lastCpuTicks = cpu.getSystemCpuLoadTicks();
                    lastCpuAt = now;
                    sleepQuietly(CPU_WINDOW_MS);
                    now = System.currentTimeMillis();
                }
                if (now - lastCpuAt < CPU_WINDOW_MS) {
                    return lastCpuPercent;
                }
                double load = cpu.getSystemCpuLoadBetweenTicks(lastCpuTicks);
                lastCpuTicks = cpu.getSystemCpuLoadTicks();
                lastCpuAt = System.currentTimeMillis();
                if (load < 0 || Double.isNaN(load)) {
                    return lastCpuPercent;
                }
                lastCpuPercent = Math.round(load * 1000) / 10.0;
                return lastCpuPercent;
            }
        } catch (Throwable t) {
            return lastCpuPercent;
        }
    }

    /**
     * 静默等待，仅用于冷启动补足 CPU 采样窗口。
     *
     * @param millis 毫秒
     */
    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public List<DiskRowVO> getDisks() {
        List<DiskRowVO> disks = new ArrayList<>();
        try {
            SystemInfo sys = getSi();
            if (sys == null) {
                return disks;
            }
            FileSystem fs = sys.getOperatingSystem().getFileSystem();
            for (OSFileStore store : fs.getFileStores()) {
                long total = store.getTotalSpace();
                long usable = store.getUsableSpace();
                long used = total - usable;
                disks.add(new DiskRowVO(
                        firstText(store.getName(), store.getLabel()),
                        store.getMount(),
                        store.getType(),
                        round(total / BYTES_PER_GB_DOUBLE),
                        round(used / BYTES_PER_GB_DOUBLE),
                        round(usable / BYTES_PER_GB_DOUBLE),
                        total > 0 ? round((double) used / total * 100) : 0));
            }
        } catch (Throwable t) {
            log.warn("OSHI 磁盘采集失败: {}", t.getMessage());
        }
        return disks;
    }

    @Override
    public List<ProcessRowVO> getProcesses(String keyword, int limit) {
        List<ProcessRowVO> result = new ArrayList<>();
        try {
            SystemInfo sys = getSi();
            if (sys == null) {
                return result;
            }
            OperatingSystem os = sys.getOperatingSystem();
            List<OSProcess> procs = os.getProcesses(null, OperatingSystem.ProcessSorting.CPU_DESC,
                    Math.max(limit * 3, 200));
            // 上一次进程快照按 pid 索引，用于 between-ticks 瞬时 CPU（归一化 0..1）
            Map<Integer, OSProcess> priorByPid = new HashMap<>();
            for (OSProcess prev : lastProcesses.get()) {
                priorByPid.put(prev.getProcessID(), prev);
            }
            String kw = keyword == null || keyword.isBlank() ? null : keyword.toLowerCase(Locale.ROOT);
            for (OSProcess p : procs) {
                if (result.size() >= limit) {
                    break;
                }
                if (kw != null && !matchesKeyword(p, kw)) {
                    continue;
                }
                // 瞬时 CPU：有上一帧用 between-ticks，首帧/缓存未命中回退 lifetime 累计；统一夹到 0..100
                OSProcess prev = priorByPid.get(p.getProcessID());
                double load = prev != null
                        ? p.getProcessCpuLoadBetweenTicks(prev)
                        : p.getProcessCpuLoadCumulative();
                if (Double.isNaN(load) || load < 0) {
                    load = 0;
                }
                if (load > 1) {
                    load = 1;
                }
                result.add(new ProcessRowVO(
                        p.getProcessID(),
                        p.getParentProcessID(),
                        p.getName(),
                        p.getUser(),
                        round(load * 100),
                        p.getResidentSetSize() / MEMORY_UNIT_BYTES,
                        p.getThreadCount(),
                        formatEpoch(p.getStartTime()),
                        p.getCommandLine(),
                        p.getPath()));
            }
            // 缓存本次快照供下一周期 between-ticks
            lastProcesses.set(procs);
        } catch (Throwable t) {
            log.warn("OSHI 进程采集失败: {}", t.getMessage());
        }
        return result;
    }

    @Override
    public ProcessSummaryVO getProcessSummary() {
        try {
            SystemInfo sys = getSi();
            if (sys == null) {
                return new ProcessSummaryVO(0, 0L);
            }
            long threads = 0L;
            int count = 0;
            for (OSProcess p : sys.getOperatingSystem().getProcesses(null, null, 0)) {
                count++;
                threads += p.getThreadCount();
            }
            return new ProcessSummaryVO(count, threads);
        } catch (Throwable t) {
            log.warn("OSHI 进程计数采集失败: {}", t.getMessage());
            return new ProcessSummaryVO(0, 0L);
        }
    }

    @Override
    public List<ServiceRowVO> getServices() {
        // 平台专属枚举交给 SPI：Windows 实现走 Win32_Service，POSIX 返回 null 由 OSHI 兜底
        List<ServiceRowVO> platformRows = HostOperationsRegistry.localServices();
        if (platformRows != null && !platformRows.isEmpty()) {
            return platformRows;
        }
        List<ServiceRowVO> result = new ArrayList<>();
        try {
            SystemInfo sys = getSi();
            if (sys == null) {
                return result;
            }
            for (OSService s : sys.getOperatingSystem().getServices()) {
                result.add(new ServiceRowVO(
                        s.getName(),
                        firstText(s.getName(), ""),
                        s.getState().name().toUpperCase(Locale.ROOT),
                        "",
                        s.getProcessID()));
            }
        } catch (Throwable t) {
            log.warn("OSHI 服务采集失败: {}", t.getMessage());
        }
        return result;
    }

    @Override
    public List<NetworkInterfaceRowVO> getNetworkInterfaces() {
        try {
            SystemInfo sys = getSi();
            if (sys == null) {
                return new ArrayList<>();
            }
            return networkInterfaces(sys.getHardware());
        } catch (Throwable t) {
            log.warn("OSHI 网络接口采集失败: {}", t.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public List<NetProcessRowVO> getNetworkConnections() {
        List<NetProcessRowVO> result = new ArrayList<>();
        SystemInfo sys = getSi();
        if (sys != null) {
            try {
                InternetProtocolStats stats = sys.getOperatingSystem().getInternetProtocolStats();
                Map<Integer, String> nameByPid = processNameSnapshot(sys);
                for (InternetProtocolStats.IPConnection conn : stats.getConnections()) {
                    int pid = conn.getowningProcessId();
                    String protocol = String.valueOf(conn.getType()).toLowerCase(Locale.ROOT);
                    String localAddr = hostAddress(conn.getLocalAddress());
                    String remoteAddr = hostAddress(conn.getForeignAddress());
                    NetProcessRowVO row = NetProcessRowVO.of(protocol, localAddr, conn.getLocalPort(),
                            remoteAddr, conn.getForeignPort(), conn.getState().name().toUpperCase(Locale.ROOT),
                            pid, nameByPid.getOrDefault(pid, ""));
                    result.add(row);
                }
            } catch (Throwable t) {
                log.warn("OSHI 网络连接采集失败: {}", t.getMessage());
            }
        }
        if (result.isEmpty()) {
            // 同上：平台专属补采（Windows 的 Get-NetTCPConnection），POSIX 实现返回 null 不参与
            List<NetProcessRowVO> platformRows = HostOperationsRegistry.localNetConnections();
            if (platformRows != null) {
                result.addAll(platformRows);
            }
        }
        return result;
    }

    @Override
    public List<EnvVarVO> getEnvVars() {
        List<EnvVarVO> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            result.add(EnvVarVO.of(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    @Override
    public double[] getDiskIoKbs() {
        double[] zero = {0.0, 0.0};
        try {
            SystemInfo sys = getSi();
            if (sys == null) {
                return zero;
            }
            long readBytes = 0L, writeBytes = 0L;
            for (HWDiskStore disk : sys.getHardware().getDiskStores()) {
                // getReadBytes/getWriteBytes 为累计值，需先 updateAttributes 刷新
                if (disk.updateAttributes()) {
                    readBytes += disk.getReadBytes();
                    writeBytes += disk.getWriteBytes();
                }
            }
            long now = System.currentTimeMillis();
            long[] prev = lastDiskIo.get();
            if (prev != null) {
                double dtSec = (now - prev[2]) / 1000.0;
                if (dtSec > 0) {
                    zero[0] = round(Math.max(0, (readBytes - prev[0])) / 1024.0 / dtSec);
                    zero[1] = round(Math.max(0, (writeBytes - prev[1])) / 1024.0 / dtSec);
                }
            }
            lastDiskIo.set(new long[]{readBytes, writeBytes, now});
            return zero;
        } catch (Throwable t) {
            log.warn("OSHI 磁盘IO采集失败: {}", t.getMessage());
            return zero;
        }
    }

    @Override
    public double[] getNetworkThroughputKbs() {
        double[] zero = {0.0, 0.0};
        try {
            SystemInfo sys = getSi();
            if (sys == null) {
                return zero;
            }
            long recvBytes = 0L, sentBytes = 0L;
            for (NetworkIF net : sys.getHardware().getNetworkIFs()) {
                // getBytesRecv/getBytesSent 为累计值，需先 updateAttributes 刷新
                if (net.updateAttributes()) {
                    recvBytes += net.getBytesRecv();
                    sentBytes += net.getBytesSent();
                }
            }
            long now = System.currentTimeMillis();
            long[] prev = lastNetIo.get();
            if (prev != null) {
                double dtSec = (now - prev[2]) / 1000.0;
                if (dtSec > 0) {
                    zero[0] = round(Math.max(0, (recvBytes - prev[0])) / 1024.0 / dtSec);
                    zero[1] = round(Math.max(0, (sentBytes - prev[1])) / 1024.0 / dtSec);
                }
            }
            lastNetIo.set(new long[]{recvBytes, sentBytes, now});
            return zero;
        } catch (Throwable t) {
            log.warn("OSHI 网络吞吐采集失败: {}", t.getMessage());
            return zero;
        }
    }

    @Override
    public List<com.chua.starter.server.support.software.SoftwareInstanceVO> getSoftware() {
        List<com.chua.starter.server.support.software.SoftwareInstanceVO> list = new ArrayList<>();
        try {
            SystemInfo sys = getSi();
            if (sys == null) {
                return list;
            }
            OperatingSystem os = sys.getOperatingSystem();
            // 第一步：快速枚举全量进程（不读命令行）；准备 CPU between-ticks 上一帧
            List<OSProcess> procs = os.getProcesses(null, null, 0);
            Map<Integer, OSProcess> priorByPid = new HashMap<>();
            for (OSProcess prev : lastProcesses.get()) {
                priorByPid.put(prev.getProcessID(), prev);
            }
            long nowMs = System.currentTimeMillis();
            // 第二步：仅对"进程名已知"的候选读取命令行（Windows 单条命令行约 30+ms，全量读取会到十几秒）
            for (OSProcess p : procs) {
                if (!com.chua.starter.server.support.software.SoftwareCatalog
                        .nameKnown(p.getName())) {
                    continue;
                }
                String cmd = p.getCommandLine();
                if (cmd == null || cmd.isBlank()) {
                    cmd = p.getPath();
                }
                String type = com.chua.starter.server.support.software.SoftwareCatalog
                        .match(p.getName(), cmd);
                if (type == null) {
                    continue;
                }
                OSProcess prev = priorByPid.get(p.getProcessID());
                double load = prev != null
                        ? p.getProcessCpuLoadBetweenTicks(prev)
                        : p.getProcessCpuLoadCumulative();
                if (Double.isNaN(load) || load < 0) {
                    load = 0;
                }
                if (load > 1) {
                    load = 1;
                }
                long startMs = p.getStartTime();
                long uptimeSec = startMs > 0 ? Math.max(0, (nowMs - startMs) / 1000L) : 0L;
                list.add(com.chua.starter.server.support.software.SoftwareCatalog.instance(type,
                        p.getProcessID(), load * 100, p.getResidentSetSize() / MEMORY_UNIT_BYTES,
                        uptimeSec, cmd == null ? p.getName() : cmd));
            }
            // 缓存本次全量快照，供下一周期 between-ticks 与进程页复用
            lastProcesses.set(procs);
        } catch (Throwable t) {
            log.warn("OSHI 软件识别失败: {}", t.getMessage());
        }
        return list;
    }

    /**
     * 由启动时间文本（yyyy-MM-dd HH:mm:ss）换算已运行秒数，解析失败为 0。
     *
     * @param startTime 启动时间文本
     * @return 运行秒数
     */
    private static long uptimeFromStart(String startTime) {
        if (startTime == null || startTime.isBlank()) {
            return 0;
        }
        try {
            LocalDateTime start = LocalDateTime.parse(startTime, TIME_FORMAT);
            long sec = java.time.Duration.between(start, LocalDateTime.now()).getSeconds();
            return Math.max(0, sec);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 采集网卡列表，供 {@code getNetworkInterfaces} 与 {@code getHostInfo} 复用。
     *
     * @param hal OSHI 硬件层
     * @return 网卡视图列表
     */
    private List<NetworkInterfaceRowVO> networkInterfaces(HardwareAbstractionLayer hal) {
        List<NetworkInterfaceRowVO> result = new ArrayList<>();
        for (NetworkIF net : hal.getNetworkIFs()) {
            NetworkInterfaceRowVO vo = new NetworkInterfaceRowVO();
            vo.setName(net.getName());
            vo.setDisplayName(net.getDisplayName());
            vo.setStatus(operStatus(net));
            vo.setMac(net.getMacaddr());
            vo.setIpv4(String.join(";", net.getIPv4addr()));
            vo.setIpv6(String.join(";", net.getIPv6addr()));
            vo.setSpeed(RowNormalizer.speedText(net.getSpeed()));
            vo.setMtu(net.getMTU());
            result.add(vo);
        }
        return result;
    }

    /**
     * 网卡操作态归一：确证在线才报 UP，确证不可用才报 DOWN，其余留给 UNKNOWN。
     *
     * @param net OSHI 网卡
     * @return {@code UP}、{@code DOWN} 或 {@code UNKNOWN}
     */
    private static String operStatus(NetworkIF net) {
        NetworkIF.IfOperStatus op = net.getIfOperStatus();
        if (op == null) {
            return "UNKNOWN";
        }
        switch (op) {
            case UP:
                return "UP";
            case DOWN:
            case NOT_PRESENT:
            case LOWER_LAYER_DOWN:
                return "DOWN";
            default:
                return "UNKNOWN";
        }
    }

    /**
     * 关键字过滤：进程名或命令行命中即可。
     *
     * @param p  进程
     * @param kw 已小写的关键字
     * @return 命中返回 true
     */
    private static boolean matchesKeyword(OSProcess p, String kw) {
        String name = p.getName() == null ? "" : p.getName().toLowerCase(Locale.ROOT);
        String command = p.getCommandLine() == null ? "" : p.getCommandLine().toLowerCase(Locale.ROOT);
        return name.contains(kw) || command.contains(kw);
    }

    /**
     * 一次性取回全量进程的 pid→名称映射。
     *
     * <p>逐连接调用 {@code getProcess(pid)} 实测约 75ms，千余条连接会把接口拖到分钟级并被 MVC
     * 异步超时切断；整表快照实测约 150ms。</p>
     *
     * @param sys OSHI 句柄
     * @return pid 到进程名的映射，采集失败返回空映射
     */
    private static Map<Integer, String> processNameSnapshot(SystemInfo sys) {
        Map<Integer, String> nameByPid = new HashMap<>();
        try {
            for (OSProcess process : sys.getOperatingSystem().getProcesses(null, null, 0)) {
                nameByPid.put(process.getProcessID(), process.getName());
            }
        } catch (Throwable t) {
            log.warn("进程名快照采集失败: {}", t.getMessage());
        }
        return nameByPid;
    }

    /**
     * 把 OSHI 的字节地址解码为可读 IP。
     *
     * @param bytes 4 字节 IPv4 或 16 字节 IPv6，可为空
     * @return 点分地址；字节长度非法或为空时返回空串
     */
    private static String hostAddress(byte[] bytes) {
        if (bytes == null || (bytes.length != 4 && bytes.length != 16)) {
            return "";
        }
        try {
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * OSHI 缺失时用 JVM 系统属性兜底基础信息。
     *
     * @param info 待填充视图
     */
    private static void degradeToSystemProperties(HostInfoVO info) {
        String hostname = System.getenv("COMPUTERNAME");
        info.setHostname(hostname == null ? localHostName() : hostname);
        info.setOsName(System.getProperty("os.name"));
        info.setOsVersion(System.getProperty("os.version"));
        // 这两项不依赖 OSHI，降级也能给
        info.setJavaVersion(System.getProperty("java.version"));
        info.setArch(System.getProperty("os.arch"));
    }

    /**
     * 按需探测本机出口公网 IP：多源并发竞速，首个返回合法 IPv4 的源即采用，整体最多等待约 6s。
     *
     * <p>仅在点击「刷新」的画像接口调用，不进入时间轮周期采集。注意必须用返回纯 IP 的端点：
     * ifconfig.me 根路径在非 curl UA 下返回 HTML（校验不通过），故用 {@code /ip}；本机默认不走系统
     * 代理时 api.ipify.org 可能被连接重置，多源互为兜底。</p>
     *
     * @return 公网 IPv4，全部失败返回空串
     */
    private static String detectPublicIp() {
        String[] sources = {"https://ifconfig.me/ip", "https://api.ipify.org"};
        java.util.concurrent.atomic.AtomicReference<String> winner =
                new java.util.concurrent.atomic.AtomicReference<>("");
        CountDownLatch latch = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();
        for (String url : sources) {
            Thread t = Thread.ofVirtual().start(() -> {
                try {
                    java.net.HttpURLConnection conn =
                            (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(3000);
                    conn.setRequestMethod("GET");
                    try (var in = conn.getInputStream()) {
                        String ip = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
                        if (isIPv4(ip) && winner.compareAndSet("", ip)) {
                            latch.countDown();
                        }
                    }
                } catch (Exception ignore) {
                    // 该源失败，等待其他源
                }
            });
            workers.add(t);
        }
        try {
            latch.await(6, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return winner.get();
    }

    /**
     * 严格判断是否为合法 IPv4（四段、每段 0-255）。
     *
     * @param value 待判定文本
     * @return 合法返回 true
     */
    private static boolean isIPv4(String value) {
        if (value == null) {
            return false;
        }
        String[] parts = value.trim().split("\\.");
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3 || !part.matches("\\d+")) {
                return false;
            }
            if (Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return true;
    }

    /**
     * 按本机操作系统族推断包管理器。
     *
     * @return apt / yum / dnf / zypper / brew / winget-choco / unknown
     */
    private static String packageManager() {
        String osFamily = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osFamily.contains("win")) {
            return "winget/choco";
        }
        if (osFamily.contains("mac")) {
            return "brew";
        }
        if (osFamily.contains("linux")) {
            if (new File("/usr/bin/apt").exists()) {
                return "apt";
            }
            if (new File("/usr/bin/dnf").exists()) {
                return "dnf";
            }
            if (new File("/usr/bin/yum").exists()) {
                return "yum";
            }
            if (new File("/usr/bin/zypper").exists()) {
                return "zypper";
            }
        }
        return "unknown";
    }

    /**
     * 格式化 epoch 毫秒为展示时间。
     *
     * @param epochMs 毫秒时间戳，非正返回空串
     * @return yyyy-MM-dd HH:mm:ss 文本
     */
    private static String formatEpoch(long epochMs) {
        if (epochMs <= 0) {
            return "";
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault()).format(TIME_FORMAT);
    }

    /**
     * 保留两位小数。
     *
     * @param value 原值
     * @return 两位小数的值
     */
    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    /**
     * 取第一个非空文本。
     *
     * @param primary  首选
     * @param fallback 回落
     * @return 首选非空则用首选
     */
    private static String firstText(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }

    /**
     * 取本机主机名，作为 OSHI 缺失时的回退值。
     *
     * @return 主机名，取不到返回空串
     */
    private static String localHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "";
        }
    }
}

