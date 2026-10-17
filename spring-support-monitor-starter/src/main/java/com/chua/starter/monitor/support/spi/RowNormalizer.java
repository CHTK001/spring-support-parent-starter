package com.chua.starter.monitor.support.spi;

import com.chua.starter.monitor.support.model.DiskRowVO;
import com.chua.starter.monitor.support.model.EnvVarVO;
import com.chua.starter.monitor.support.model.FileEntryVO;
import com.chua.starter.monitor.support.model.HostInfoVO;
import com.chua.starter.monitor.support.model.NetProcessRowVO;
import com.chua.starter.monitor.support.model.NetworkInterfaceRowVO;
import com.chua.starter.monitor.support.model.ProcessRowVO;
import com.chua.starter.monitor.support.model.ServiceRowVO;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 系统信息行的字段契约归一层。
 *
 * <p>进程与服务各有四类数据来源（OSHI 本机、Agent HTTP、SSH 命令、WinRM PowerShell），
 * 每家的键名大小写、量纲与取值都不一致。这里把通道原始报文（{@code row}）映射成强类型视图，
 * 前端只需认一份契约，新增通道也不必改前端。</p>
 *
 * <p>本类是通道内部适配层：入参 {@code Map} 只承载"外部报文"这一通用结构，不跨 Service
 * 或 Controller 边界传播。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class RowNormalizer {

    /**
     * 一 GB 的字节数
     */
    private static final double BYTES_PER_GB = 1024.0 * 1024.0 * 1024.0;

    /**
     * 工具类，禁止实例化。
     */
    private RowNormalizer() {
    }

    /**
     * 批量归一服务行。
     *
     * @param rows 任意来源的服务行，可为 null
     * @return 归一后的行列表，永不为 null
     */
    public static List<ServiceRowVO> services(List<Map<String, Object>> rows) {
        List<ServiceRowVO> out = new ArrayList<>();
        if (rows == null) {
            return out;
        }
        for (Map<String, Object> row : rows) {
            ServiceRowVO vo = service(row);
            if (vo != null) {
                out.add(vo);
            }
        }
        return out;
    }

    /**
     * 归一单行服务。
     *
     * @param row 原始行
     * @return 服务视图；state 统一大写，取不到名称时返回 null
     */
    public static ServiceRowVO service(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        String name = text(row, "name", "Name", "serviceName", "ServiceName");
        if (name.isEmpty()) {
            return null;
        }
        ServiceRowVO vo = new ServiceRowVO(name,
                first(text(row, "displayName", "DisplayName", "description", "Description"), name),
                serviceState(row),
                serviceStartType(row),
                number(row, "pid", "PID", "processId", "ProcessId"));
        return vo;
    }

    /**
     * 批量归一进程行。
     *
     * @param rows 任意来源的进程行，可为 null
     * @return 归一后的行列表，永不为 null
     */
    public static List<ProcessRowVO> processes(List<Map<String, Object>> rows) {
        List<ProcessRowVO> out = new ArrayList<>();
        if (rows == null) {
            return out;
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            out.add(new ProcessRowVO(
                    number(row, "pid", "PID", "ProcessId"),
                    number(row, "ppid", "PPID", "parentPid", "ParentProcessId"),
                    text(row, "name", "Name", "imageName", "processName"),
                    text(row, "user", "User", "username", "UserName"),
                    decimal(row, "cpu", "CPU", "cpuUsage"),
                    memoryMb(row),
                    number(row, "threads", "Threads", "threadCount"),
                    first(text(row, "startTime", "StartTime", "started"), ""),
                    first(text(row, "command", "Command", "args", "cmdline", "CommandLine"),
                            text(row, "path", "Path", "executablePath")),
                    text(row, "path", "Path", "executablePath", "ExecutablePath")));
        }
        return out;
    }

    /**
     * 批量归一磁盘行。
     *
     * <p>各通道单位不同：Agent 给 "465.3 G" 字符串，SSH 的 df 给字节，本机 OSHI 给 GB 浮点，
     * 统一换算成 GB 数值并补算使用率。</p>
     *
     * @param rows 任意来源的磁盘行，可为 null
     * @return 归一后的行列表，永不为 null
     */
    public static List<DiskRowVO> disks(List<Map<String, Object>> rows) {
        List<DiskRowVO> out = new ArrayList<>();
        if (rows == null) {
            return out;
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            double total = gb(row, new String[] {"totalGb", "CapacityGb"},
                    new String[] {"size", "totalBytes", "total"}, new String[] {"size"});
            double used = gb(row, new String[] {"usedGb"},
                    new String[] {"used", "usedBytes"}, new String[] {"used"});
            double avail = gb(row, new String[] {"availGb", "freeGb", "SizeRemaining"},
                    new String[] {"avail", "free", "freeBytes"}, new String[] {"avail", "free"});
            if (total <= 0 && avail > 0) {
                total = avail + used;
            }
            double usage = percent(row, "usage", "use%", "Usage", "usePercent");
            if (usage <= 0 && total > 0) {
                usage = (total - avail) * 100.0 / total;
            }
            out.add(new DiskRowVO(
                    first(text(row, "name", "Name", "FriendlyName", "label", "filesystem"),
                            text(row, "mount", "Mounted on", "mountpoint")),
                    text(row, "mount", "Mounted on", "mountpoint"),
                    text(row, "fsType", "type", "FileSystem", "filesystem"),
                    round1(total),
                    round1(used),
                    round1(avail),
                    round1(usage)));
        }
        return out;
    }

    /**
     * 批量归一网卡行。
     *
     * @param rows 任意来源的网卡行，可为 null
     * @return 归一后的行列表，永不为 null
     */
    public static List<NetworkInterfaceRowVO> interfaces(List<Map<String, Object>> rows) {
        List<NetworkInterfaceRowVO> out = new ArrayList<>();
        if (rows == null) {
            return out;
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            String name = first(text(row, "name", "Name", "ifname", "ifName", "interface", "Index"),
                    text(row, "displayName", "Description", "InterfaceDescription"));
            NetworkInterfaceRowVO vo = new NetworkInterfaceRowVO();
            vo.setName(name);
            vo.setDisplayName(first(text(row, "displayName", "InterfaceDescription", "Description"), name));
            vo.setStatus(first(text(row, "status", "Status", "operstate", "ifOperStatus", "state"), "UNKNOWN"));
            vo.setMac(text(row, "mac", "MacAddress", "macaddr", "address"));
            vo.setIpv4(text(row, "ipv4", "IPAddress", "IPv4", "addr"));
            vo.setIpv6(text(row, "ipv6", "IPv6"));
            vo.setMtu(number(row, "mtu", "MTU", "MaxMtu"));
            vo.setSpeed(speedText(row));
            out.add(vo);
        }
        return out;
    }

    /**
     * 批量归一环境变量报文。
     *
     * @param rows 任意来源的变量行，可为 null
     * @return 变量列表，永不为 null
     */
    public static List<EnvVarVO> envVars(List<Map<String, Object>> rows) {
        List<EnvVarVO> out = new ArrayList<>();
        if (rows == null) {
            return out;
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            String name = text(row, "name", "Name", "key");
            if (name.isEmpty()) {
                continue;
            }
            out.add(EnvVarVO.of(name, text(row, "value", "Value")));
        }
        return out;
    }

    /**
     * 归一主机基础信息报文。
     *
     * <p>Agent 与 WinRM 的应答是外部 JSON/文本，数值以字符串下发，这里统一收敛为强类型；
     * 解析不出的字段保持 null，由前端按"该通道不提供"处理。</p>
     *
     * @param row 原始报文，可为 null
     * @return 主机信息视图；row 为 null 时返回 null
     */
    public static HostInfoVO hostInfo(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        HostInfoVO vo = new HostInfoVO();
        vo.setHostname(text(row, "hostname", "Hostname", "hostName"));
        vo.setOs(text(row, "os", "Os"));
        vo.setOsName(text(row, "osName", "OSName", "Caption"));
        vo.setOsVersion(text(row, "osVersion", "version"));
        vo.setOsBuild(text(row, "osBuild", "BuildNumber"));
        vo.setArch(text(row, "arch", "OSArchitecture"));
        vo.setCpuName(text(row, "cpuName", "processorId"));
        vo.setPackageManager(text(row, "packageManager"));
        vo.setGpu(text(row, "gpu"));
        vo.setPublicIp(text(row, "publicIp"));
        vo.setHostCode(text(row, "hostCode", "code", "machineId"));
        vo.setPrivateIp(text(row, "privateIp"));
        vo.setMac(text(row, "mac", "macAddress"));
        vo.setIpv4DefaultGateway(text(row, "ipv4DefaultGateway"));
        vo.setJavaVersion(text(row, "javaVersion"));
        vo.setAgentVersion(text(row, "agentVersion"));
        vo.setCollectTime(text(row, "collectTime"));
        vo.setUptimeSec(nullableNumber(row, "uptimeSec"));
        vo.setMemoryMb(nullableNumber(row, "memoryMb"));
        vo.setMemoryTotalMb(nullableNumber(row, "memoryTotalMb"));
        vo.setMemoryUsedMb(nullableNumber(row, "memoryUsedMb"));
        vo.setMemoryUsed(nullableNumber(row, "memoryUsed"));
        vo.setPid((int) number(row, "pid"));
        vo.setAgentPort((int) number(row, "agentPort"));
        int cores = (int) number(row, "cpuCores", "logicalProcessorCount");
        vo.setCpuCores(cores > 0 ? cores : null);
        vo.setCpuPercent(nullableDecimal(row, "cpuPercent"));
        vo.setMemoryUsage(nullableDecimal(row, "memoryUsage", "memoryPercent"));
        vo.setDiskTotalGb(nullableDecimal(row, "diskTotalGb"));
        vo.setDiskUsedGb(nullableDecimal(row, "diskUsedGb"));
        vo.setDiskReadKbs(nullableDecimal(row, "diskReadKbs"));
        vo.setDiskWriteKbs(nullableDecimal(row, "diskWriteKbs"));
        vo.setNetworkRxKb(nullableDecimal(row, "networkRxKb", "networkRxKbs"));
        vo.setNetworkTxKb(nullableDecimal(row, "networkTxKb", "networkTxKbs"));
        vo.setNetworkPercent(nullableDecimal(row, "networkPercent"));
        int procCount = (int) number(row, "processCount");
        vo.setProcessCount(procCount > 0 ? procCount : null);
        int threadCount = (int) number(row, "threadCount");
        vo.setThreadCount(threadCount > 0 ? threadCount : null);
        return vo;
    }

    /**
     * 批量归一网络端口占用行（外部报文形态）。
     *
     * <p>兼容两种形态：Agent 的 {@code netProc()} 已给出四元组，WinRM/{@code ports} 只有
     * {@code proto}/{@code local}/{@code port}。本地地址带端口时拆开，否则从 port 取。</p>
     *
     * @param rows 原始行，可为 null
     * @return 归一后的监听行列表，永不为 null
     */
    public static List<NetProcessRowVO> netProcs(List<Map<String, Object>> rows) {
        List<NetProcessRowVO> out = new ArrayList<>();
        if (rows == null) {
            return out;
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            String localAddr = first(text(row, "localAddr", "LocalAddress"), stripPort(text(row, "local", "Local")));
            int localPort = (int) firstLong(number(row, "localPort"), number(row, "port", "LocalPort"),
                    extractPort(text(row, "local")));
            String remoteAddr = first(text(row, "remoteAddr", "RemoteAddress"), stripPort(text(row, "remote")));
            int remotePort = (int) firstLong(number(row, "remotePort"), number(row, "RemotePort"),
                    extractPort(text(row, "remote")));
            String protocol = first(text(row, "protocol", "proto", "type"), "tcp");
            NetProcessRowVO vo = NetProcessRowVO.of(protocol, localAddr, localPort, remoteAddr, remotePort,
                    first(text(row, "state", "State"), protocol.startsWith("udp") ? "UNCONN" : "UNKNOWN"),
                    (int) number(row, "pid", "OwningProcess"),
                    first(text(row, "process", "pidName", "ProcessName"), ""));
            out.add(vo);
        }
        return out;
    }

    /**
     * 去掉 {@code addr:port} 的地址部分，兼容 IPv6 方括号写法。
     *
     * @param text 原始地址文本
     * @return 地址部分；无冒号时返回原文
     */
    private static String stripPort(String text) {
        int i = text.lastIndexOf(':');
        return i < 0 ? text : text.substring(0, i).replace("[", "").replace("]", "");
    }

    /**
     * 取 {@code addr:port} 的端口部分。
     *
     * @param text 原始地址文本
     * @return 端口；解析不出返回 0
     */
    private static long extractPort(String text) {
        int i = text.lastIndexOf(':');
        if (i < 0 || i == text.length() - 1) {
            return 0L;
        }
        try {
            return Long.parseLong(text.substring(i + 1).replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 返回第一个非零值。
     *
     * @param values 候选值
     * @return 首个 &gt; 0 的值，全是 0 时返回 0
     */
    private static long firstLong(long... values) {
        for (long value : values) {
            if (value > 0) {
                return value;
            }
        }
        return 0L;
    }

    /**
     * 按候选键取数值，取不到返回 null（区别于 {@link #number} 的 0 回落）。
     *
     * @param row  原始行
     * @param keys 候选键
     * @return 数值，全部缺失时返回 null
     */
    private static Long nullableNumber(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object v = row.get(key);
            if (v instanceof Number) {
                return ((Number) v).longValue();
            }
            if (v != null) {
                try {
                    return (long) Double.parseDouble(String.valueOf(v).trim());
                } catch (NumberFormatException ignored) {
                    // 继续尝试下一个候选键
                }
            }
        }
        return null;
    }

    /**
     * 按候选键取浮点值，取不到返回 null。
     *
     * @param row  原始行
     * @param keys 候选键
     * @return 浮点值，全部缺失时返回 null
     */
    private static Double nullableDecimal(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object v = row.get(key);
            if (v instanceof Number) {
                return ((Number) v).doubleValue();
            }
            if (v != null) {
                try {
                    return Double.parseDouble(String.valueOf(v).trim().replace("%", ""));
                } catch (NumberFormatException ignored) {
                    // 继续尝试下一个候选键
                }
            }
        }
        return null;
    }

    /**
     * 批量归一文件条目。
     *
     * <p>{@code size} 在 SSH/WinRM 通道是文本，在这里收敛为字节数；缺位的属主/权限/属组补空串。</p>
     *
     * @param rows 任意来源的文件行，可为 null
     * @return 归一后的条目列表，永不为 null
     */
    public static List<FileEntryVO> files(List<Map<String, Object>> rows) {
        List<FileEntryVO> out = new ArrayList<>();
        if (rows == null) {
            return out;
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            out.add(new FileEntryVO(
                    text(row, "name", "Name", "FileName"),
                    bool(row, "isDir", "IsDir", "directory", "PSIsContainer"),
                    number(row, "size", "Size", "Length"),
                    text(row, "modified", "Modified", "LastModified", "CreationTime"),
                    text(row, "owner", "Owner", "User"),
                    text(row, "perms", "Perms", "Permission", "mode"),
                    text(row, "group", "Group")));
        }
        out.sort((a, b) -> {
            if (a.directory() != b.directory()) {
                return a.directory() ? -1 : 1;
            }
            return a.name().compareToIgnoreCase(b.name());
        });
        return out;
    }

    /**
     * 按三级单位来源读取磁盘容量并换算为 GB。
     *
     * @param row       原始行
     * @param gbKeys    值已是 GB 的键
     * @param byteKeys  值是字节的键
     * @param humanKeys 值是 "465.3 G" 形式的键
     * @return GB 值，识别不到时返回 0
     */
    private static double gb(Map<String, Object> row, String[] gbKeys, String[] byteKeys, String[] humanKeys) {
        double gb = number(row, gbKeys);
        if (gb > 0) {
            return gb;
        }
        double bytes = number(row, byteKeys);
        if (bytes > 0) {
            return bytes / BYTES_PER_GB;
        }
        for (String key : humanKeys) {
            double parsed = humanToGb(text(row, key));
            if (parsed > 0) {
                return parsed;
            }
        }
        return 0d;
    }

    /**
     * 解析 "465.3 G" / "1.2T" / "900 M" 形式的人类可读容量。
     *
     * @param text 原始文本
     * @return GB 值，无法解析时返回 0
     */
    private static double humanToGb(String text) {
        if (text == null || text.isBlank()) {
            return 0d;
        }
        String s = text.trim().toUpperCase(Locale.ROOT).replace("IB", "").replace("B", "");
        String num = s.replaceAll("[^0-9.\\-]", "");
        String unit = s.replaceAll("[0-9.\\-]", "").trim();
        double value;
        try {
            value = Double.parseDouble(num);
        } catch (NumberFormatException e) {
            return 0d;
        }
        return switch (unit) {
            case "T" -> value * 1024;
            case "M" -> value / 1024;
            case "K" -> value / 1024 / 1024;
            case "G", "" -> value;
            default -> 0d;
        };
    }

    /**
     * 进程内存量纲归一。
     *
     * <p>Agent 的 {@code rss} 是 KB，WinRM 的 {@code workingSet} 是字节，本机 OSHI 直接给 MB；
     * 前端表头写死「内存(MB)」，故统一折算成 MB，取不到时返回 0。</p>
     *
     * @param row 原始行
     * @return MB 值
     */
    private static long memoryMb(Map<String, Object> row) {
        long bytes = number(row, "workingSet", "WorkingSetSize", "residentSetSizeBytes");
        if (bytes > 0) {
            return bytes / (1024L * 1024L);
        }
        long kb = number(row, "rss", "RSS", "residentSetSize");
        if (kb > 0) {
            return kb / 1024L;
        }
        return number(row, "memory", "Memory");
    }

    /**
     * 归一网卡速率展示值。
     *
     * @param row 原始行
     * @return 可直接展示的速率文本，取不到返回空串
     */
    private static String speedText(Map<String, Object> row) {
        Object raw = firstNonNull(row.get("speed"), firstNonNull(row.get("Speed"), row.get("LinkSpeed")));
        if (raw == null) {
            return "";
        }
        if (raw instanceof Number) {
            return speedText(((Number) raw).longValue());
        }
        String text = String.valueOf(raw).trim();
        return "0".equals(text) ? "" : text;
    }

    /**
     * 把链路速率（bps）格式化为展示文本。
     *
     * @param bits 每秒比特数
     * @return 展示文本，如 {@code "1.0 Gbps"}；非正值返回空串
     */
    public static String speedText(long bits) {
        if (bits <= 0) {
            return "";
        }
        if (bits >= 1_000_000_000L) {
            return round1(bits / 1_000_000_000.0) + " Gbps";
        }
        return round1(bits / 1_000_000.0) + " Mbps";
    }

    /**
     * 取第一个非 null 值。
     *
     * @param primary  首选
     * @param fallback 回落
     * @return 首个非 null 的参数
     */
    private static Object firstNonNull(Object primary, Object fallback) {
        return primary != null ? primary : fallback;
    }

    /**
     * 保留一位小数。
     *
     * @param value 原值
     * @return 一位小数的值
     */
    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    /**
     * 归一服务状态取值。
     *
     * @param row 原始行
     * @return RUNNING / STOPPED / 其他大写原值；取不到返回 UNKNOWN
     */
    private static String serviceState(Map<String, Object> row) {
        String raw = text(row, "state", "State", "status", "Status", "tcpState");
        String key = raw.trim().toLowerCase(Locale.ROOT);
        switch (key) {
            case "running":
            case "active":
            case "up":
            case "1":
                return "RUNNING";
            case "stopped":
            case "inactive":
            case "down":
            case "4":
            case "":
                return key.isEmpty() ? "UNKNOWN" : "STOPPED";
            case "starting":
                return "STARTING";
            case "stopping":
                return "STOPPING";
            default:
                return raw.trim().toUpperCase(Locale.ROOT);
        }
    }

    /**
     * 归一服务启动类型取值。
     *
     * @param row 原始行
     * @return AUTOMATIC / MANUAL / DISABLED / AUTO；无法识别时返回原值大写
     */
    private static String serviceStartType(Map<String, Object> row) {
        String raw = text(row, "startType", "StartType", "startup", "startupType", "StartupType");
        String key = raw.trim().toLowerCase(Locale.ROOT);
        switch (key) {
            case "auto":
            case "automatic":
            case "2":
                return "AUTOMATIC";
            case "manual":
            case "demand":
            case "3":
                return "MANUAL";
            case "disabled":
            case "4":
                return "DISABLED";
            case "":
                return "";
            default:
                return raw.trim().toUpperCase(Locale.ROOT);
        }
    }

    /**
     * 按候选键取第一个非空字符串值。
     *
     * @param row  原始行
     * @param keys 候选键
     * @return 字符串值；都没有时返回空串
     */
    private static String text(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object v = row.get(key);
            if (v != null && !String.valueOf(v).isBlank()) {
                return String.valueOf(v);
            }
        }
        return "";
    }

    /**
     * 按候选键取第一个可解析的布尔值。
     *
     * @param row  原始行
     * @param keys 候选键
     * @return 布尔值；都没有时返回 false
     */
    private static boolean bool(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object v = row.get(key);
            if (v instanceof Boolean) {
                return (Boolean) v;
            }
            if (v != null) {
                String s = String.valueOf(v).trim();
                if ("true".equalsIgnoreCase(s) || "1".equals(s)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 按候选键取第一个可解析的数值。
     *
     * @param row  原始行
     * @param keys 候选键
     * @return 数值；都没有时返回 0
     */
    private static long number(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object v = row.get(key);
            if (v instanceof Number) {
                return ((Number) v).longValue();
            }
            if (v != null) {
                try {
                    return (long) Double.parseDouble(String.valueOf(v).trim());
                } catch (NumberFormatException ignored) {
                    // 非数值字段继续尝试下一个候选键
                }
            }
        }
        return 0L;
    }

    /**
     * 按候选键取第一个可解析的浮点值，不做整数截断。
     *
     * @param row  原始行
     * @param keys 候选键
     * @return 浮点值；都没有时返回 0
     */
    private static double decimal(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object v = row.get(key);
            if (v instanceof Number) {
                return ((Number) v).doubleValue();
            }
            if (v != null) {
                try {
                    return Double.parseDouble(String.valueOf(v).trim());
                } catch (NumberFormatException ignored) {
                    // 非数值字段继续尝试下一个候选键
                }
            }
        }
        return 0d;
    }

    /**
     * 按候选键取百分比数值，兼容 "45%" 这类带百分号的文本。
     *
     * @param row  原始行
     * @param keys 候选键
     * @return 百分比数值；都没有时返回 0
     */
    private static double percent(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object v = row.get(key);
            if (v instanceof Number) {
                return ((Number) v).doubleValue();
            }
            if (v != null) {
                String s = String.valueOf(v).trim().replace("%", "");
                try {
                    return Double.parseDouble(s);
                } catch (NumberFormatException ignored) {
                    // 非数值字段继续尝试下一个候选键
                }
            }
        }
        return 0d;
    }

    /**
     * 取第一个非空候选值。
     *
     * @param primary  首选
     * @param fallback 回落
     * @return 首选非空则用首选
     */
    private static String first(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }
}
