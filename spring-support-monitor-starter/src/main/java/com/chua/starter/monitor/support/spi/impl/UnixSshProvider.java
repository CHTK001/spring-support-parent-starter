package com.chua.starter.monitor.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.monitor.support.model.DiskRowVO;
import com.chua.starter.monitor.support.model.EnvVarVO;
import com.chua.starter.monitor.support.model.FileEntryVO;
import com.chua.starter.monitor.support.model.HostInfoVO;
import com.chua.starter.monitor.support.model.NetProcessRowVO;
import com.chua.starter.monitor.support.model.NetworkInterfaceRowVO;
import com.chua.starter.monitor.support.model.ProcessRowVO;
import com.chua.starter.monitor.support.model.ServiceRowVO;
import com.chua.starter.monitor.support.spi.SshConnectionCache;
import com.chua.starter.monitor.support.spi.SystemInfoProvider;
import com.chua.spring.support.configuration.SpringBeanUtils;
import com.chua.starter.server.support.entity.MsServerHost;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unix SSH 系统信息 Provider（Linux + macOS）。
 *
 * <p>复用 utils-support-ssh-starter 的连接池，命令按操作系统族区分。所有采集结果在
 * 本类内部收敛成 SPI 的强类型视图，字符串形式的命令输出不会外溢到调用方。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Spi(value = "ssh", order = 200)
public class UnixSshProvider implements SystemInfoProvider {

    /**
     * SSH 连接缓存，首次使用时从 Spring 容器解析
     */
    private volatile SshConnectionCache resolvedCache;

    /**
     * 懒取 SSH 连接缓存。
     *
     * <p>Provider 实例由 SPI 框架创建、不在容器内，依赖只能按类型查找。</p>
     *
     * @return SSH 连接缓存
     */
    private SshConnectionCache sshCache() {
        SshConnectionCache cache = resolvedCache;
        if (cache == null) {
            cache = SpringBeanUtils.getBean(SshConnectionCache.class);
            resolvedCache = cache;
        }
        return cache;
    }

    /**
     * {@code ss} 输出里的进程段：{@code users:(("java",pid=1234,fd=80))}，捕获进程名与 PID
     */
    private static final Pattern SS_USERS = Pattern.compile("\\(\"([^\"]+)\",.*?pid=(\\d+)");

    /**
     * macOS {@code ifconfig} 块里的 MTU
     */
    private static final Pattern IFCONFIG_MTU = Pattern.compile("mtu\\s+(\\d+)");

    /**
     * JSON 命令输出解析器
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 一 KB 的字节数
     */
    private static final long KB = 1024L;

    /**
     * 一 GB 的字节数（双精度，避免整型截断）
     */
    private static final double GB = 1024.0 * 1024.0 * 1024.0;

    @Override
    public boolean supports(MsServerHost host) {
        String os = host == null ? null : host.getServerHostOsType();
        return "LINUX".equalsIgnoreCase(os) || "MAC".equalsIgnoreCase(os)
                || "MACOS".equalsIgnoreCase(os);
    }

    /**
     * 目标主机是否为 macOS。
     *
     * @param host 目标主机
     * @return 是 macOS 返回 true
     */
    private boolean isMac(MsServerHost host) {
        String os = host.getServerHostOsType();
        return "MAC".equalsIgnoreCase(os) || "MACOS".equalsIgnoreCase(os);
    }

    @Override
    public HostInfoVO hostInfo(MsServerHost host) {
        HostInfoVO info = new HostInfoVO();
        boolean mac = isMac(host);
        String cpuCmd = mac ? "sysctl -n hw.ncpu" : "nproc";
        String memCmd = mac ? "sysctl -n hw.memsize" : "grep MemTotal /proc/meminfo | awk '{print $2}'";
        String hostCmd = mac ? "scutil --get ComputerName" : "hostname";
        String ipCmd = mac ? "ipconfig getifaddr en0" : "hostname -I | awk '{print $1}'";
        String macCmd = mac
                ? "ifconfig en0 | grep ether | awk '{print $2}'"
                : "cat /sys/class/net/$(ip route show default | awk '/default/ {print $5; exit}')/address";
        String osCmd = mac ? "sw_vers -productVersion" : "grep PRETTY_NAME /etc/os-release | cut -d'\"' -f2";

        info.setCpuCores(toInt(sshCache().exec(host, cpuCmd, 10000)));
        info.setCpuName(sshCache().exec(host, mac
                ? "sysctl -n machdep.cpu.brand_string"
                : "grep 'model name' /proc/cpuinfo | head -1 | cut -d: -f2 | xargs", 10000));
        info.setHostname(sshCache().exec(host, hostCmd, 10000));
        info.setPrivateIp(sshCache().exec(host, ipCmd, 10000));
        info.setMac(sshCache().exec(host, macCmd, 10000));
        info.setOsVersion(sshCache().exec(host, osCmd, 10000));
        info.setOsName(mac ? "macOS" : "Linux");
        info.setPackageManager(sshCache().exec(host,
                "which apt >/dev/null 2>&1 && echo apt || (which dnf >/dev/null 2>&1 && echo dnf"
                        + " || (which yum >/dev/null 2>&1 && echo yum || (which brew >/dev/null 2>&1"
                        + " && echo brew || echo unknown)))", 10000));
        info.setGpu(sshCache().exec(host, mac
                ? "system_profiler SPDisplaysDataType 2>/dev/null | grep 'Chipset Model' | cut -d: -f2 | xargs"
                : "lspci 2>/dev/null | grep -i vga | cut -d: -f3 | xargs || echo 'unknown'", 10000));
        info.setMemoryMb(parseMemoryMb(sshCache().exec(host, memCmd, 10000), mac));
        info.setPublicIp(sshCache().exec(host, "curl -s --connect-timeout 5 ifconfig.me 2>/dev/null", 10000));
        info.setCpuPercent(parseCpuPercent(sshCache().exec(host,
                "top -bn1 | grep 'Cpu(s)' | awk '{print 100 - $8}'", 10000)));
        info.setMemoryUsed(parseMemoryUsedMb(host, mac));
        return info;
    }

    /**
     * 把 {@code /proc/meminfo}（KB）或 {@code hw.memsize}（字节）统一换算为 MB。
     *
     * @param out 命令输出
     * @param mac 是否 macOS
     * @return MB 值，解析失败返回 null
     */
    private static Long parseMemoryMb(String out, boolean mac) {
        if (out == null || out.trim().isEmpty()) {
            return null;
        }
        try {
            long raw = Long.parseLong(out.trim());
            return mac ? raw / KB / KB : raw / KB;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析 top 输出的 CPU 使用率。
     *
     * @param out 命令输出
     * @return 使用率，解析失败返回 null
     */
    private static Double parseCpuPercent(String out) {
        if (out == null || out.trim().isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(out.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 用 MemTotal 与 MemAvailable 计算已用内存（MB）。
     *
     * @param host 目标主机
     * @param mac  是否 macOS，macOS 无 meminfo 时返回 null
     * @return 已用 MB，无法计算返回 null
     */
    private Long parseMemoryUsedMb(MsServerHost host, boolean mac) {
        if (mac) {
            return null;
        }
        try {
            long availKb = Long.parseLong(sshCache().exec(host,
                    "grep MemAvailable /proc/meminfo | awk '{print $2}'", 10000).trim());
            long totalKb = Long.parseLong(sshCache().exec(host,
                    "grep MemTotal /proc/meminfo | awk '{print $2}'", 10000).trim());
            return totalKb > 0 ? (totalKb - availKb) / KB : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<ProcessRowVO> processes(MsServerHost host, int page, int pageSize) {
        String out = sshCache().exec(host, "ps -eo pid,ppid,pcpu,rss,comm,args --no-headers", 30000);
        List<ProcessRowVO> all = new ArrayList<>();
        if (out == null || out.isBlank()) {
            return all;
        }
        for (String line : out.split("\\r?\\n")) {
            String[] p = line.trim().split("\\s+", 6);
            if (p.length < 6) {
                continue;
            }
            ProcessRowVO vo = new ProcessRowVO();
            vo.setPid(toLong(p[0]));
            vo.setPpid(toLong(p[1]));
            vo.setCpu(toDouble(p[2]));
            vo.setMemory(toLong(p[3]));
            vo.setName(p[4]);
            vo.setCommand(p[5]);
            all.add(vo);
        }
        return pageOf(all, page, pageSize);
    }

    @Override
    public List<ServiceRowVO> services(MsServerHost host, int page, int pageSize) {
        boolean mac = isMac(host);
        String cmd = mac ? "launchctl list"
                : "systemctl list-units --type=service --no-pager --no-legend --plain";
        String out = sshCache().exec(host, cmd, 30000);
        List<ServiceRowVO> all = new ArrayList<>();
        if (out == null || out.isBlank()) {
            return all;
        }
        for (String line : out.split("\\r?\\n")) {
            String[] p = line.trim().split("\\s+");
            if (p.length < 3) {
                continue;
            }
            ServiceRowVO vo = new ServiceRowVO();
            if (mac) {
                boolean running = !"-".equals(p[0]);
                vo.setName(p[2]);
                vo.setPid(running ? toLong(p[0]) : 0L);
                vo.setState(running ? "RUNNING" : "STOPPED");
            } else {
                if (!p[0].endsWith(".service") && !p[0].contains(".")) {
                    continue;
                }
                vo.setName(p[0]);
                vo.setState("active".equalsIgnoreCase(p[2]) ? "RUNNING" : "STOPPED");
                vo.setPid(0L);
            }
            vo.setDisplayName(vo.getName());
            vo.setStartType("");
            all.add(vo);
        }
        return pageOf(all, page, pageSize);
    }

    @Override
    public List<DiskRowVO> disks(MsServerHost host) {
        String out = sshCache().exec(host, "df -Pk", 15000);
        List<DiskRowVO> list = new ArrayList<>();
        if (out == null || out.isBlank()) {
            return list;
        }
        for (String line : out.split("\\r?\\n")) {
            if (line.startsWith("Filesystem") || line.isBlank()) {
                continue;
            }
            String[] p = line.trim().split("\\s+");
            if (p.length < 6) {
                continue;
            }
            double total = toLong(p[1]) * KB / GB;
            double used = toLong(p[2]) * KB / GB;
            double avail = toLong(p[3]) * KB / GB;
            DiskRowVO vo = new DiskRowVO();
            vo.setName(p[0]);
            vo.setMount(p[5]);
            vo.setFsType(fstypeOf(p[0]));
            vo.setTotalGb(round1(total));
            vo.setUsedGb(round1(used));
            vo.setAvailGb(round1(avail));
            vo.setUsage(total > 0 ? round1((double) toLong(p[2]) / toLong(p[1]) * 100) : 0);
            list.add(vo);
        }
        return list;
    }

    @Override
    public List<NetworkInterfaceRowVO> networkInterfaces(MsServerHost host) {
        return isMac(host) ? macNetworkInterfaces(host) : linuxNetworkInterfaces(host);
    }

    /**
     * 解析 Linux 的 {@code ip -j addr} 与 {@code ip -j link} 输出并按网卡合并。
     *
     * @param host 目标主机
     * @return 网卡视图列表
     */
    private List<NetworkInterfaceRowVO> linuxNetworkInterfaces(MsServerHost host) {
        Map<String, NetworkInterfaceRowVO> byName = new LinkedHashMap<>();
        for (JsonNode node : readJsonArray(sshCache().exec(host, "ip -j addr", 15000))) {
            String name = node.path("ifname").asText("");
            if (name.isEmpty()) {
                continue;
            }
            NetworkInterfaceRowVO vo = byName.computeIfAbsent(name, UnixSshProvider::newInterface);
            vo.setMtu(node.path("mtu").asLong(0));
            String operstate = node.path("operstate").asText("");
            if (!operstate.isEmpty()) {
                vo.setStatus(operstate.toUpperCase(Locale.ROOT));
            }
            for (JsonNode addr : node.path("addr_info")) {
                if ("inet".equals(addr.path("family").asText())) {
                    appendIpv4(vo, addr.path("local").asText(""));
                } else if ("inet6".equals(addr.path("family").asText())) {
                    appendIpv6(vo, addr.path("local").asText(""));
                }
            }
        }
        for (JsonNode node : readJsonArray(sshCache().exec(host, "ip -j link", 15000))) {
            String name = node.path("ifname").asText("");
            NetworkInterfaceRowVO vo = byName.get(name);
            if (vo != null) {
                vo.setMac(node.path("address").asText(""));
            }
        }
        return new ArrayList<>(byName.values());
    }

    /**
     * 解析 macOS 的 {@code ifconfig -a} 文本块。
     *
     * @param host 目标主机
     * @return 网卡视图列表
     */
    private List<NetworkInterfaceRowVO> macNetworkInterfaces(MsServerHost host) {
        List<NetworkInterfaceRowVO> list = new ArrayList<>();
        String out = sshCache().exec(host, "ifconfig -a", 15000);
        if (out == null || out.isBlank()) {
            return list;
        }
        NetworkInterfaceRowVO current = null;
        for (String line : out.split("\\r?\\n")) {
            if (line.isEmpty()) {
                continue;
            }
            if (!Character.isWhitespace(line.charAt(0))) {
                int colon = line.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                String name = line.substring(0, colon);
                if (name.indexOf('(') >= 0) {
                    continue;
                }
                current = newInterface(name);
                current.setDisplayName(name);
                Matcher mtu = IFCONFIG_MTU.matcher(line);
                if (mtu.find()) {
                    current.setMtu(toLong(mtu.group(1)));
                }
                current.setStatus(line.contains("<UP") || line.contains(",UP,") || line.contains("UP>") ? "UP" : "DOWN");
                list.add(current);
                continue;
            }
            if (current == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("ether ")) {
                current.setMac(trimmed.substring(6).trim());
            } else if (trimmed.startsWith("inet ")) {
                appendIpv4(current, trimmed.split("\\s+")[1]);
            } else if (trimmed.startsWith("inet6 ")) {
                appendIpv6(current, trimmed.split("\\s+")[1].split("%")[0]);
            } else if (trimmed.startsWith("status:")) {
                current.setStatus(trimmed.substring(7).trim().contains("active") ? "UP" : "DOWN");
            }
        }
        return list;
    }

    /**
     * 构造一张空白网卡视图。
     *
     * @param name 网卡标识名
     * @return 预置名称与显示名的视图
     */
    private static NetworkInterfaceRowVO newInterface(String name) {
        NetworkInterfaceRowVO vo = new NetworkInterfaceRowVO();
        vo.setName(name);
        vo.setDisplayName(name);
        vo.setStatus("UP");
        vo.setMac("");
        vo.setIpv4("");
        vo.setIpv6("");
        vo.setSpeed("");
        return vo;
    }

    /**
     * 追加一个 IPv4 地址（逗号连接）。
     *
     * @param vo   目标网卡
     * @param addr 地址，空值忽略
     */
    private static void appendIpv4(NetworkInterfaceRowVO vo, String addr) {
        if (addr != null && !addr.isEmpty()) {
            vo.setIpv4(vo.getIpv4() == null || vo.getIpv4().isEmpty() ? addr : vo.getIpv4() + "," + addr);
        }
    }

    /**
     * 追加一个 IPv6 地址（逗号连接）。
     *
     * @param vo   目标网卡
     * @param addr 地址，空值忽略
     */
    private static void appendIpv6(NetworkInterfaceRowVO vo, String addr) {
        if (addr != null && !addr.isEmpty()) {
            vo.setIpv6(vo.getIpv6() == null || vo.getIpv6().isEmpty() ? addr : vo.getIpv6() + "," + addr);
        }
    }

    /**
     * 把命令输出的 JSON 数组读成节点列表，非数组或解析失败返回空列表。
     *
     * @param out 命令输出
     * @return JSON 节点列表
     */
    private static List<JsonNode> readJsonArray(String out) {
        List<JsonNode> nodes = new ArrayList<>();
        if (out == null || out.isBlank()) {
            return nodes;
        }
        try {
            JsonNode root = MAPPER.readTree(out.trim());
            if (root.isArray()) {
                root.forEach(nodes::add);
            }
        } catch (Exception e) {
            log.warn("解析 ip 命令 JSON 输出失败: {}", e.getMessage());
        }
        return nodes;
    }

    @Override
    public List<NetProcessRowVO> netProcesses(MsServerHost host) {
        boolean mac = isMac(host);
        String cmd = mac ? "lsof -i -n -P" : "ss -tulnp";
        String out = sshCache().exec(host, cmd, 15000);
        List<NetProcessRowVO> list = new ArrayList<>();
        if (out == null || out.isBlank()) {
            return list;
        }
        for (String line : out.split("\\r?\\n")) {
            if (line.isBlank()) {
                continue;
            }
            NetProcessRowVO row = mac ? parseLsofLine(line) : parseSsLine(line);
            if (row != null) {
                list.add(row);
            }
        }
        return list;
    }

    /**
     * 解析 {@code ss -tulnp} 的一行。
     *
     * @param line 原始输出行
     * @return 结构化行；表头或不完整行返回 null
     */
    private static NetProcessRowVO parseSsLine(String line) {
        String[] p = line.trim().split("\\s+");
        if (p.length < 6 || !(p[0].startsWith("tcp") || p[0].startsWith("udp"))) {
            return null;
        }
        String[] local = splitAddrPort(p[4]);
        String[] peer = splitAddrPort(p[5]);
        String process = "";
        int pid = 0;
        Matcher m = SS_USERS.matcher(line);
        if (m.find()) {
            process = m.group(1);
            pid = toInt(m.group(2));
        }
        return NetProcessRowVO.of(p[0], local[0], toInt(local[1]), peer[0], toInt(peer[1]),
                "LISTEN".equalsIgnoreCase(p[1]) || "*".equals(p[1]) ? "LISTEN" : p[1].toUpperCase(Locale.ROOT),
                pid, process);
    }

    /**
     * 解析 {@code lsof -i -n -P} 的一行。
     *
     * @param line 原始输出行
     * @return 结构化行；表头、COMMAND 行或无网络地址的行返回 null
     */
    private static NetProcessRowVO parseLsofLine(String line) {
        String[] p = line.trim().split("\\s+");
        if (p.length < 9 || "COMMAND".equals(p[0])) {
            return null;
        }
        String type = p[7];
        if (!type.equalsIgnoreCase("TCP") && !type.equalsIgnoreCase("UDP")) {
            return null;
        }
        String name = p[p.length - 1];
        int paren = name.indexOf('(');
        String state = paren < 0 ? (type.equalsIgnoreCase("TCP") ? "ESTABLISHED" : "UNCONN")
                : name.substring(paren + 1, name.length() - 1).replace("LISTENING", "LISTEN");
        String addr = (paren < 0 ? name : name.substring(0, paren).trim());
        String[] parts = splitAddrPort(addr);
        return NetProcessRowVO.of(type.toLowerCase(Locale.ROOT), parts[0], toInt(parts[1]), "", 0,
                state.toUpperCase(Locale.ROOT), toInt(p[1]), p[0]);
    }

    /**
     * 拆分 {@code addr:port}，兼容 IPv6 的方括号写法。
     *
     * @param text 地址字符串
     * @return 长度为 2 的数组，[0] 为地址、[1] 为端口
     */
    private static String[] splitAddrPort(String text) {
        String s = text == null ? "" : text;
        int i = s.lastIndexOf(':');
        if (i < 0) {
            return new String[]{s, "0"};
        }
        String digits = s.substring(i + 1).replaceAll("[^0-9]", "");
        String addr = s.substring(0, i).replace("[", "").replace("]", "*");
        return new String[]{addr, digits.isEmpty() ? "0" : digits};
    }

    @Override
    public List<FileEntryVO> files(MsServerHost host, String path) {
        String out = sshCache().exec(host, "ls -la --time-style=long-iso \"" + path.replace("\"", "") + "\"", 15000);
        List<FileEntryVO> list = new ArrayList<>();
        if (out == null || out.isBlank()) {
            return list;
        }
        String[] lines = out.split("\\r?\\n");
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            String[] p = line.split("\\s+", 9);
            if (p.length < 9) {
                continue;
            }
            FileEntryVO vo = new FileEntryVO();
            vo.setName(p[8].split(" -> ")[0]);
            vo.setDirectory(p[0].startsWith("d"));
            vo.setPerms(p[0]);
            vo.setSize(toLong(p[4]));
            vo.setModified(p[5] + " " + p[6]);
            vo.setOwner(p[2]);
            vo.setGroup(p[3]);
            list.add(vo);
        }
        return list;
    }

    @Override
    public boolean uploadFile(MsServerHost host, String remotePath, InputStream data, long size) {
        File tmp = null;
        try {
            tmp = File.createTempFile("up_", "_tmp");
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = data.read(buf)) > 0) {
                    fos.write(buf, 0, n);
                }
            }
            sshCache().getSftp(host).upload().local(tmp.getAbsolutePath()).remote(remotePath).exec();
            return true;
        } catch (Exception e) {
            log.error("upload failed", e);
            return false;
        } finally {
            if (tmp != null) {
                tmp.delete();
            }
        }
    }

    @Override
    public InputStream downloadFile(MsServerHost host, String remotePath) {
        try {
            File tmp = File.createTempFile("dl_", "_tmp");
            tmp.deleteOnExit();
            sshCache().getSftp(host).download().remote(remotePath).local(tmp.getAbsolutePath()).exec();
            return new FileInputStream(tmp);
        } catch (Exception e) {
            log.error("download failed", e);
            return null;
        }
    }

    @Override
    public String exec(MsServerHost host, String cmd) {
        return sshCache().exec(host, cmd, 30000);
    }

    @Override
    public List<EnvVarVO> env(MsServerHost host) {
        String out = sshCache().exec(host, "env", 10000);
        List<EnvVarVO> list = new ArrayList<>();
        if (out == null) {
            return list;
        }
        for (String line : out.split("\\r?\\n")) {
            int eq = line.indexOf('=');
            if (eq > 0) {
                list.add(EnvVarVO.of(line.substring(0, eq), line.substring(eq + 1)));
            }
        }
        return list;
    }

    /**
     * 对整表结果做内存分页。
     *
     * @param all      全量行
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     * @param <T>      行类型
     * @return 当前页切片，越界返回空列表
     */
    private static <T> List<T> pageOf(List<T> all, int page, int pageSize) {
        int from = Math.max(page - 1, 0) * pageSize;
        return from >= all.size() ? Collections.emptyList()
                : new ArrayList<>(all.subList(from, Math.min(from + pageSize, all.size())));
    }

    /**
     * 由设备名猜测文件系统类型（{@code df -Pk} 不输出类型列）。
     *
     * @param device 设备名，如 tmpfs / /dev/sda1
     * @return 类型标识
     */
    private static String fstypeOf(String device) {
        if (device == null || device.isEmpty()) {
            return "";
        }
        if (device.startsWith("/dev/")) {
            return "ext4";
        }
        return device;
    }

    /**
     * 宽松整数转换。
     *
     * @param text 数字字符串，可为 null
     * @return 解析结果；非法时返回 0
     */
    private static int toInt(String text) {
        return (int) toLong(text);
    }

    /**
     * 宽松长整转换。
     *
     * @param text 数字字符串，可为 null
     * @return 解析结果；非法时返回 0
     */
    private static long toLong(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 宽松浮点转换。
     *
     * @param text 数字字符串，可为 null
     * @return 解析结果；非法时返回 0
     */
    private static double toDouble(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0d;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return 0d;
        }
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
}
