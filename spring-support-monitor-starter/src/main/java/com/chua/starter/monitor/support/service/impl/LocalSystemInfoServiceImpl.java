package com.chua.starter.monitor.support.service.impl;

import com.chua.starter.monitor.support.model.DiskRowVO;
import com.chua.starter.monitor.support.model.EnvVarVO;
import com.chua.starter.monitor.support.model.HostInfoVO;
import com.chua.starter.monitor.support.model.NetProcessRowVO;
import com.chua.starter.monitor.support.model.NetworkInterfaceRowVO;
import com.chua.starter.monitor.support.model.ProcessRowVO;
import com.chua.starter.monitor.support.model.ServiceRowVO;
import com.chua.starter.monitor.support.service.LocalSystemInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
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
     * 本机是否 Windows，决定服务枚举走 Win32_Service 还是 OSHI
     */
    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    /**
     * PowerShell 输出的 Base64 负载
     */
    private static final Pattern BASE64_PAYLOAD = Pattern.compile("[A-Za-z0-9+/]{40,}={0,2}");

    /**
     * 启动时间展示格式
     */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 一 MB / 一 KB 的字节数
     */
    private static final long BYTES_PER_MB = 1024L * 1024L;

    /**
     * 一 KB 的字节数
     */
    private static final long BYTES_PER_KB = 1024L;

    /**
     * 一 GB 的双精度字节数
     */
    private static final double BYTES_PER_GB_DOUBLE = 1024.0 * 1024.0 * 1024.0;

    /**
     * 进程内存统一单位（KB），与 Agent、SSH 通道对齐
     */
    private static final long MEMORY_UNIT_BYTES = BYTES_PER_KB;

    /**
     * OSHI 句柄，懒加载失败时为 null（降级为基础信息模式）
     */
    private volatile SystemInfo si;

    /**
     * 上次 CPU ticks（getSystemCpuLoadBetweenTicks 需要两次采样）
     */
    private transient long[] lastCpuTicks;

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

    @Override
    public HostInfoVO getHostInfo() {
        HostInfoVO info = new HostInfoVO();
        try {
            SystemInfo sys = getSi();
            if (sys == null) {
                degradeToSystemProperties(info);
                info.setPackageManager(packageManager());
                return info;
            }
            HardwareAbstractionLayer hal = sys.getHardware();
            OperatingSystem os = sys.getOperatingSystem();

            CentralProcessor cpu = hal.getProcessor();
            info.setCpuCores(cpu.getLogicalProcessorCount());
            info.setCpuName(cpu.getProcessorIdentifier().getName());

            GlobalMemory mem = hal.getMemory();
            long totalMem = mem.getTotal();
            long usedMem = totalMem - mem.getAvailable();
            info.setMemoryTotalMb(totalMem / BYTES_PER_MB);
            info.setMemoryUsedMb(usedMem / BYTES_PER_MB);
            info.setMemoryMb(totalMem / BYTES_PER_MB);
            info.setMemoryUsage(totalMem > 0 ? (double) usedMem / totalMem * 100 : 0);

            info.setOsName(os.getFamily());
            info.setOsVersion(os.getVersionInfo().getVersion());
            info.setOsBuild(os.getVersionInfo().getBuildNumber());
            info.setHostname(os.getNetworkParams().getHostName());
            info.setIpv4DefaultGateway(os.getNetworkParams().getIpv4DefaultGateway());

            List<NetworkInterfaceRowVO> interfaces = networkInterfaces(hal);
            info.setNetworkInterfaces(interfaces);
            if (!interfaces.isEmpty()) {
                info.setMac(interfaces.get(0).getMac());
            }
        } catch (Throwable t) {
            log.warn("OSHI 采集失败，降级基础信息: {}", t.getMessage());
            degradeToSystemProperties(info);
        }
        info.setPackageManager(packageManager());
        return info;
    }

    @Override
    public double getCpuPercent() {
        try {
            SystemInfo sys = getSi();
            if (sys == null) {
                return 0;
            }
            CentralProcessor cpu = sys.getHardware().getProcessor();
            long[] ticks = cpu.getSystemCpuLoadTicks();
            double load = lastCpuTicks == null ? 0 : cpu.getSystemCpuLoadBetweenTicks(lastCpuTicks);
            lastCpuTicks = ticks;
            if (load < 0 || Double.isNaN(load)) {
                return 0;
            }
            return Math.round(load * 1000) / 10.0;
        } catch (Throwable t) {
            return 0;
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
                DiskRowVO vo = new DiskRowVO();
                vo.setName(firstText(store.getName(), store.getLabel()));
                vo.setMount(store.getMount());
                vo.setFsType(store.getType());
                vo.setTotalGb(round(total / BYTES_PER_GB_DOUBLE));
                vo.setUsedGb(round(used / BYTES_PER_GB_DOUBLE));
                vo.setAvailGb(round(usable / BYTES_PER_GB_DOUBLE));
                vo.setUsage(total > 0 ? round((double) used / total * 100) : 0);
                disks.add(vo);
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
            String kw = keyword == null || keyword.isBlank() ? null : keyword.toLowerCase(Locale.ROOT);
            for (OSProcess p : procs) {
                if (result.size() >= limit) {
                    break;
                }
                if (kw != null && !matchesKeyword(p, kw)) {
                    continue;
                }
                ProcessRowVO vo = new ProcessRowVO();
                vo.setPid(p.getProcessID());
                vo.setPpid(p.getParentProcessID());
                vo.setName(p.getName());
                vo.setUser(p.getUser());
                vo.setCpu(round(p.getProcessCpuLoadCumulative() * 100));
                vo.setMemory(p.getResidentSetSize() / MEMORY_UNIT_BYTES);
                vo.setThreads(p.getThreadCount());
                vo.setStartTime(formatEpoch(p.getStartTime()));
                vo.setCommand(p.getCommandLine());
                vo.setPath(p.getPath());
                result.add(vo);
            }
        } catch (Throwable t) {
            log.warn("OSHI 进程采集失败: {}", t.getMessage());
        }
        return result;
    }

    @Override
    public List<ServiceRowVO> getServices() {
        if (IS_WINDOWS) {
            List<ServiceRowVO> rows = windowsServiceRows();
            if (rows != null) {
                return rows;
            }
        }
        List<ServiceRowVO> result = new ArrayList<>();
        try {
            SystemInfo sys = getSi();
            if (sys == null) {
                return result;
            }
            for (OSService s : sys.getOperatingSystem().getServices()) {
                ServiceRowVO vo = new ServiceRowVO();
                vo.setName(s.getName());
                vo.setDisplayName(firstText(s.getName(), ""));
                vo.setState(s.getState().name().toUpperCase(Locale.ROOT));
                vo.setStartType("");
                vo.setPid(s.getProcessID());
                result.add(vo);
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
        if (result.isEmpty() && IS_WINDOWS) {
            result.addAll(powerShellNetTcpConnections());
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

    /**
     * 用 Win32_Service 枚举 Windows 服务，取回真正的服务键与启动类型。
     *
     * <p>脚本经 Base64(UTF-16LE) 用 -EncodedCommand 传入：PowerShell 5.1 会二次解析 -Command 的命令行，
     * 内层引号被吞后脚本以退出码 1 结束且无输出。输出侧同样以 Base64(UTF-8) 回传，绕开控制台代码页。</p>
     *
     * @return 服务行；非 Windows 或执行失败返回 null，由调用方回落 OSHI
     */
    private List<ServiceRowVO> windowsServiceRows() {
        String ps = "$t=[char]9; $rows=@(Get-CimInstance -ClassName Win32_Service | ForEach-Object { "
                + "$_.Name + $t + $_.DisplayName + $t + $_.State + $t + $_.StartMode + $t + $_.ProcessId }); "
                + "[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(($rows -join \"`n\")))";
        Process process = null;
        try {
            process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                    "-EncodedCommand", encodePowerShell(ps)).redirectErrorStream(true).start();
            String out = new String(readAll(process.getInputStream()), StandardCharsets.UTF_8);
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                log.warn("Win32_Service 枚举超时，回落 OSHI");
                return null;
            }
            String payload = longestBase64(out);
            if (payload == null) {
                log.warn("Win32_Service 枚举失败 exit={} out={}，回落 OSHI", process.exitValue(),
                        out.length() > 300 ? out.substring(0, 300) : out);
                return null;
            }
            String text = new String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8);
            List<ServiceRowVO> rows = new ArrayList<>();
            for (String line : text.split("\n")) {
                String[] p = line.split("\t", -1);
                if (p.length < 5 || p[0].trim().isEmpty()) {
                    continue;
                }
                ServiceRowVO vo = new ServiceRowVO();
                vo.setName(p[0].trim());
                vo.setDisplayName(p[1].trim());
                vo.setState(p[2].trim().toUpperCase(Locale.ROOT));
                vo.setStartType(p[3].trim());
                try {
                    vo.setPid(Long.parseLong(p[4].trim()));
                } catch (NumberFormatException e) {
                    vo.setPid(0L);
                }
                rows.add(vo);
            }
            return rows.isEmpty() ? null : rows;
        } catch (Throwable t) {
            log.warn("Win32_Service 枚举异常，回落 OSHI: {}", t.getMessage());
            return null;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    /**
     * OSHI 不可用时的 PowerShell 网络连接回落采集。
     *
     * @return 端口占用行，采集失败返回空列表
     */
    private List<NetProcessRowVO> powerShellNetTcpConnections() {
        List<NetProcessRowVO> rows = new ArrayList<>();
        try {
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command",
                    "Get-NetTCPConnection -State Listen,Established | Select-Object LocalAddress,LocalPort,"
                            + "RemoteAddress,RemotePort,State,OwningProcess | ConvertTo-Json -Depth 3")
                    .redirectErrorStream(true).start();
            String json = new String(readAll(p.getInputStream()), Charset.defaultCharset()).trim();
            p.waitFor();
            if (!json.startsWith("[")) {
                return rows;
            }
            com.fasterxml.jackson.databind.JsonNode root =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            for (com.fasterxml.jackson.databind.JsonNode node : root) {
                rows.add(NetProcessRowVO.of("tcp", node.path("LocalAddress").asText(),
                        node.path("LocalPort").asInt(), node.path("RemoteAddress").asText(),
                        node.path("RemotePort").asInt(), node.path("State").asText().toUpperCase(Locale.ROOT),
                        node.path("OwningProcess").asInt(), ""));
            }
        } catch (Throwable t) {
            log.warn("PowerShell 网络连接采集失败: {}", t.getMessage());
        }
        return rows;
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
            vo.setStatus("UP");
            vo.setMac(net.getMacaddr());
            vo.setIpv4(String.join(",", net.getIPv4addr()));
            vo.setIpv6(String.join(",", net.getIPv6addr()));
            vo.setSpeed(gbps(net.getSpeed()));
            vo.setMtu(net.getMTU());
            result.add(vo);
        }
        return result;
    }

    /**
     * 把 OSHI 的比特速率换算成展示文本。
     *
     * @param bits 每秒比特数
     * @return 展示文本，速率非正时返回空串
     */
    private static String gbps(long bits) {
        if (bits <= 0) {
            return "";
        }
        if (bits >= 1_000_000_000L) {
            return round(bits / 1_000_000_000.0) + " Gbps";
        }
        return round(bits / 1_000_000.0) + " Mbps";
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
     * 生成 PowerShell -EncodedCommand 参数（Base64 of UTF-16LE）。
     *
     * @param script PowerShell 脚本
     * @return 编码后的参数值
     */
    private static String encodePowerShell(String script) {
        return Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
    }

    /**
     * 取文本中最长的纯 Base64 串，即脚本负载。
     *
     * @param text 子进程合并输出
     * @return Base64 负载，不存在返回 null
     */
    private static String longestBase64(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        Matcher matcher = BASE64_PAYLOAD.matcher(text);
        String best = null;
        while (matcher.find()) {
            if (best == null || matcher.group().length() > best.length()) {
                best = matcher.group();
            }
        }
        return best;
    }

    /**
     * 读干输入流。
     *
     * @param in 输入流
     * @return 原始字节
     * @throws IOException 读取失败
     */
    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) > 0) {
            bos.write(buf, 0, len);
        }
        return bos.toByteArray();
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
