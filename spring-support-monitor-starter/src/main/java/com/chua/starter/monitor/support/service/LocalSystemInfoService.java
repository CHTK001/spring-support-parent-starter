package com.chua.starter.monitor.support.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;
import oshi.software.os.OSService;
import oshi.software.os.InternetProtocolStats;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Locale;
import oshi.software.os.NetworkParams;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 本机系统信息采集服务（OSHI SPI）。
 * 支持 Windows / Linux / macOS 三大平台。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
public class LocalSystemInfoService {

    /**
     * OSHI 句柄，懒加载失败时为 null（降级为基础信息模式）。
    */
    private volatile SystemInfo si;

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
     * 获取本机基本信息（CPU、内存、操作系统、网络接口、包管理器）。
     *
     * @return 本机基本信息键值对；OSHI 不可用时降级为 JVM 系统属性
     */
    public Map<String, Object> getHostInfo() {
        Map<String, Object> info = new HashMap<>();
        try {
            SystemInfo sys = getSi();
            if (sys == null) {
                info.put("hostname", System.getenv("COMPUTERNAME"));
                info.put("osName", System.getProperty("os.name"));
                info.put("osVersion", System.getProperty("os.version"));
                return info;
            }
            HardwareAbstractionLayer hal = sys.getHardware();
            OperatingSystem os = sys.getOperatingSystem();

            CentralProcessor cpu = hal.getProcessor();
            info.put("cpuCores", cpu.getLogicalProcessorCount());
            info.put("cpuName", cpu.getProcessorIdentifier().getName());

            GlobalMemory mem = hal.getMemory();
            long totalMem = mem.getTotal();
            long usedMem = totalMem - mem.getAvailable();
            info.put("memoryTotalMb", totalMem / 1024 / 1024);
            info.put("memoryUsedMb", usedMem / 1024 / 1024);
            info.put("memoryUsage", totalMem > 0 ? (double) usedMem / totalMem * 100 : 0);

            info.put("osName", os.getFamily());
            info.put("osVersion", os.getVersionInfo().getVersion());
            info.put("osBuild", os.getVersionInfo().getBuildNumber());
            info.put("hostname", os.getNetworkParams().getHostName());
            info.put("ipv4DefaultGateway", os.getNetworkParams().getIpv4DefaultGateway());

            List<Map<String, Object>> interfaces = new ArrayList<>();
            for (NetworkIF net : hal.getNetworkIFs()) {
                Map<String, Object> ni = new HashMap<>();
                ni.put("name", net.getName());
                ni.put("displayName", net.getDisplayName());
                ni.put("mac", net.getMacaddr());
                ni.put("ipv4", String.join(",", net.getIPv4addr()));
                ni.put("ipv6", String.join(",", net.getIPv6addr()));
                ni.put("speed", net.getSpeed());
                ni.put("mtu", net.getMTU());
                interfaces.add(ni);
            }
            info.put("networkInterfaces", interfaces);
        } catch (Throwable t) {
            log.warn("OSHI 采集失败，降级基础信息: {}", t.getMessage());
            info.put("hostname", System.getenv("COMPUTERNAME"));
            info.put("osName", System.getProperty("os.name"));
            info.put("osVersion", System.getProperty("os.version"));
        }

        String pkgMgr = "unknown";
        String osFamily = System.getProperty("os.name", "").toLowerCase();
        if (osFamily.contains("win")) {
            pkgMgr = "winget/choco";
        }
        else if (osFamily.contains("linux")) {
            if (new java.io.File("/usr/bin/apt").exists()) {
                pkgMgr = "apt";
            }
            else if (new java.io.File("/usr/bin/yum").exists()) {
                pkgMgr = "yum";
            }
            else if (new java.io.File("/usr/bin/dnf").exists()) {
                pkgMgr = "dnf";
            }
            else if (new java.io.File("/usr/bin/zypper").exists()) {
                pkgMgr = "zypper";
            }
        } else if (osFamily.contains("mac")) {
            pkgMgr = "brew";
        }
        info.put("packageManager", pkgMgr);

        return info;
    }

    /**
     * 获取本机磁盘信息（文件系统挂载点）。
     *
     * @return 磁盘列表（名称、挂载路径、类型、容量与使用率）；采集失败返回空列表
     */
        /**
         * 获取本机 CPU 使用率（%，瞬时）。
         *
         * @return CPU 使用率 0-100；采集失败返回 0
         */
    /**
     * 上次 CPU ticks（getSystemCpuLoadBetweenTicks 需要两次采样）
    */
    private transient long[] lastCpuTicks = null;

    /**
     * 获取CpuPercent。
     *
     * @return 结果数值
     */
    public double getCpuPercent() {
        try {
            SystemInfo sys = getSi();
            if (sys == null) {
                return 0;
            }
            var cpu = sys.getHardware().getProcessor();
            long[] ticks = System.getProperty("os.name").toLowerCase().contains("win")
                    ? cpu.getSystemCpuLoadTicks() : cpu.getSystemCpuLoadTicks();
            double load;
            if (lastCpuTicks == null) {
                load = 0;
            } else {
                load = cpu.getSystemCpuLoadBetweenTicks(lastCpuTicks);
            }
            lastCpuTicks = ticks;
            if (load < 0 || Double.isNaN(load)) {
                return 0;
            }
            return Math.round(load * 1000) / 10.0;
        } catch (Throwable t) {
            return 0;
        }
    }
/**
 * 获取Disks。
 *
 * @return 结果列表，无数据时为空列表
 */
public List<Map<String, Object>> getDisks() {
        List<Map<String, Object>> disks = new ArrayList<>();
        try {
            SystemInfo sys = getSi();
            if (sys == null) {
                return disks;
            }
            FileSystem fs = sys.getOperatingSystem().getFileSystem();
            for (OSFileStore store : fs.getFileStores()) {
                Map<String, Object> d = new HashMap<>();
                d.put("name", store.getName());
                d.put("mount", store.getMount());
                d.put("label", store.getLabel());
                d.put("fsType", store.getType());
                d.put("description", store.getDescription());
                long total = store.getTotalSpace();
                long used = total - store.getUsableSpace();
                d.put("totalGb", Math.round(total / 1024.0 / 1024.0 / 1024.0 * 100) / 100.0);
                d.put("usedGb", Math.round(used / 1024.0 / 1024.0 / 1024.0 * 100) / 100.0);
                d.put("usage", total > 0 ? Math.round((double) used / total * 10000) / 100.0 : 0);
                disks.add(d);
            }
        } catch (Throwable t) {
            log.warn("OSHI 磁盘采集失败: {}", t.getMessage());
        }
        return disks;
    }

    /**
     * 获取本机进程列表（按 CPU 占用倒序）。
     *
     * @param keyword 进程名或命令行关键字，为空则不做过滤
     * @param limit   最大返回条数
     * @return 进程信息列表；采集失败返回空列表
     */
    public List<Map<String, Object>> getProcesses(String keyword, int limit) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            SystemInfo sys = getSi();
            if (sys == null) {
                return result;
            }
            OperatingSystem os = sys.getOperatingSystem();
            List<OSProcess> procs = os.getProcesses(null, OperatingSystem.ProcessSorting.CPU_DESC, Math.max(limit * 3, 200));
            for (OSProcess p : procs) {
                Map<String, Object> m = new HashMap<>();
                m.put("pid", p.getProcessID());
                m.put("name", p.getName());
                m.put("user", p.getUser());
                m.put("path", p.getPath());
                m.put("command", p.getCommandLine());
                m.put("cpu", Math.round(p.getProcessCpuLoadCumulative() * 10000) / 100.0);
                m.put("memory", p.getResidentSetSize());
                m.put("threads", p.getThreadCount());
                m.put("startTime", p.getStartTime());
                result.add(m);
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = keyword.toLowerCase();
                result = result.stream().filter(p ->
                    String.valueOf(p.get("name")).toLowerCase().contains(kw) ||
                    String.valueOf(p.get("command")).toLowerCase().contains(kw)
                ).limit(limit).toList();
            }
        } catch (Throwable t) {
            log.warn("OSHI 进程采集失败: {}", t.getMessage());
        }
        return result;
    }

    /**
     * 获取本机服务列表。
     *
     * @return 服务列表（名称、状态、PID）；采集失败返回空列表
     */
    public List<Map<String, Object>> getServices() {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            SystemInfo sys = getSi();
            if (sys == null) {
                return result;
            }
            for (OSService s : sys.getOperatingSystem().getServices()) {
                Map<String, Object> m = new HashMap<>();
                m.put("name", s.getName());
                m.put("state", s.getState().name());
                m.put("pid", s.getProcessID());
                result.add(m);
            }
        } catch (Throwable t) {
            log.warn("OSHI 服务采集失败: {}", t.getMessage());
        }
        return result;
    }

    /**
     * 获取本机网络接口。
     *
     * @return 网络接口列表（名称、MAC、IPv4/IPv6、速率、MTU）；采集失败返回空列表
     */
    public List<Map<String, Object>> getNetworkInterfaces() {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            SystemInfo sys = getSi();
            if (sys == null) {
                return result;
            }
            for (NetworkIF net : sys.getHardware().getNetworkIFs()) {
                Map<String, Object> m = new HashMap<>();
                m.put("name", net.getName());
                m.put("displayName", net.getDisplayName());
                m.put("mac", net.getMacaddr());
                m.put("ipv4", String.join(",", net.getIPv4addr()));
                m.put("ipv6", String.join(",", net.getIPv6addr()));
                m.put("speed", net.getSpeed());
                m.put("mtu", net.getMTU());
                result.add(m);
            }
        } catch (Throwable t) {
            log.warn("OSHI 网络接口采集失败: {}", t.getMessage());
        }
        return result;
    }

    /**
     * 获取本机网络连接。
     *
     * @return 连接列表（协议类型、本地地址与端口、连接状态）；采集失败返回空列表
     */
    public List<Map<String, Object>> getNetworkConnections() {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            SystemInfo sys = getSi();
            if (sys != null) {
                InternetProtocolStats stats = sys.getOperatingSystem().getInternetProtocolStats();
                for (InternetProtocolStats.IPConnection conn : stats.getConnections()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("type", conn.getType());
                    m.put("localAddr", conn.getLocalAddress());
                    m.put("localPort", conn.getLocalPort());
                    m.put("state", conn.getState().name());
                    result.add(m);
                }
            }
        } catch (Throwable t) {
            log.warn("OSHI 网络连接采集失败: {}", t.getMessage());
        }
        if (result.isEmpty() && System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
            try {
                Process p = new ProcessBuilder("powershell", "-Command",
                        "Get-NetTCPConnection -State Listen,Established | Select-Object LocalAddress,LocalPort,RemoteAddress,RemotePort,State,OwningProcess | ConvertTo-Json -Depth 3")
                        .redirectErrorStream(true).start();
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), Charset.defaultCharset()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                }
                p.waitFor();
                String json = sb.toString().trim();
                if (json.startsWith("[")) {
                    com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
                    for (com.fasterxml.jackson.databind.JsonNode node : root) {
                        Map<String, Object> m = new HashMap<>();
                        m.put("localAddr", node.path("LocalAddress").asText());
                        m.put("localPort", node.path("LocalPort").asInt());
                        m.put("remoteAddr", node.path("RemoteAddress").asText());
                        m.put("remotePort", node.path("RemotePort").asInt());
                        m.put("state", node.path("State").asText());
                        m.put("pid", node.path("OwningProcess").asInt());
                        result.add(m);
                    }
                }
            } catch (Throwable t) {
                log.warn("PowerShell 网络连接采集失败: {}", t.getMessage());
            }
        }
        return result;
    }

    /**
     * 获取本机环境变量。
     *
     * @return 环境变量列表（name / value）
     */
    public List<Map<String, String>> getEnvVars() {
        List<Map<String, String>> result = new ArrayList<>();
        Map<String, String> env = System.getenv();
        for (Map.Entry<String, String> e : env.entrySet()) {
            Map<String, String> m = new HashMap<>();
            m.put("name", e.getKey());
            m.put("value", e.getValue());
            result.add(m);
        }
        return result;
    }
}
