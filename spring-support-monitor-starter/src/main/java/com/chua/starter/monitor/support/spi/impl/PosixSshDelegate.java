package com.chua.starter.monitor.support.spi.impl;

import com.chua.common.support.spi.annotations.SpiIgnore;
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
import com.chua.starter.monitor.support.support.ShellSupport;
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
@SpiIgnore
public class PosixSshDelegate implements SystemInfoProvider {

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
                // Linux 侧必须同时匹配三种 PCI 显示类枚举名。原来只 grep -i vga，
                // 而 vga 字面量只出现在 Intel 核显的「VGA compatible controller」上：
                // NVIDIA 独显枚举为「3D controller」，AMD 独显是「Display controller」，
                // 两者都匹配不上 → 结果为空 →回落成 unknown。也就是说带独显的机器
                // 反而拿不到型号名，只剩 Intel 核显能显示。
                : "{ lspci 2>/dev/null | grep -Ei 'VGA compatible controller|3D controller|Display controller' | cut -d: -f3 | xargs; } || true; "
                + "[ -z \"$(lspci 2>/dev/null | grep -Ei 'VGA compatible controller|3D controller|Display controller')\" ] && echo 'unknown'",
                10000));
        info.setMemoryMb(parseMemoryMb(sshCache().exec(host, memCmd, 10000), mac));
        // 公网 IP 仅作展示，目标无外网时 curl 失败不应中断整个画像，best-effort 容错
        try {
            info.setPublicIp(sshCache().exec(host, "curl -s --connect-timeout 3 ifconfig.me 2>/dev/null || true", 8000));
        } catch (Exception ex) {
            info.setPublicIp("");
        }
        // 主机编码：Linux machine-id，macOS IOPlatformUUID
        String codeCmd = mac
                ? "ioreg -rd1 -c IOPlatformExpertDevice | awk '/IOPlatformUUID/ {print $3}' | tr -d '\"'"
                : "cat /etc/machine-id 2>/dev/null || cat /var/lib/dbus/machine-id 2>/dev/null";
        info.setHostCode(sshCache().exec(host, codeCmd, 8000));
        info.setCpuPercent(parseCpuPercent(sshCache().exec(host,
                "top -bn1 | grep 'Cpu(s)' | awk '{print 100 - $8}'", 10000)));
        info.setMemoryUsed(parseMemoryUsedMb(host, mac));
        // Linux 磁盘IO / 网络吞吐（KB/s）：两次采样瞬时速率，best-effort
        applyThroughput(host, info, mac);
        return info;
    }

    /**
     * 采样 Linux 磁盘IO与网络吞吐（KB/s）：{@code /proc/diskstats}、{@code /proc/net/dev}
     * 各两次采样（间隔 1s），取整盘 / 非 lo 计数器增量。macOS 暂不支持，直接跳过。
     *
     * @param host 目标主机
     * @param info 待填充的主机信息
     * @param mac  是否 macOS
     */
    private void applyThroughput(MsServerHost host, HostInfoVO info, boolean mac) {
        if (mac) {
            return;
        }
        try {
            // 磁盘：整盘扇区增量（每扇区 512B）；whole() 过滤分区
            String disk = sshCache().exec(host,
                    "f(){ awk 'function whole(n){if(n~/^(nvme|mmcblk)/)return index(n,\"p\")==0;"
                            + "return n!~/[0-9]$/}{if($3~/^(sd|vd|xvd|hd|nvme|mmcblk)/&&whole($3))"
                            + "{r+=$6;w+=$10}}END{printf \"%d %d\",r,w}' /proc/diskstats;};"
                            + "a=$(f);sleep 1;b=$(f);echo \"$a $b\"", 15000);
            parseDelta4(disk, 512.0 / KB, info, true);
            // 网络：非 lo 接口字节增量
            String net = sshCache().exec(host,
                    "g(){ awk 'NR>2{gsub(\":\",\"\",$1);if($1!=\"lo\"){rx+=$2;tx+=$10}}"
                            + "END{printf \"%d %d\",rx,tx}' /proc/net/dev;};"
                            + "a=$(g);sleep 1;b=$(g);echo \"$a $b\"", 15000);
            parseDelta4(net, 1.0 / KB, info, false);
        } catch (Exception ex) {
            // 采样失败保持 null，前端按"该通道暂不提供"处理
        }
    }

    /**
     * 解析两次采样输出 {@code "x1 y1 x2 y2"}，按因子换算增量速率并写入。
     *
     * @param out    命令输出
     * @param factor 计数器增量到 KB/s 的换算因子
     * @param info   待填充的主机信息
     * @param disk   true 写磁盘IO，false 写网络吞吐
     */
    private static void parseDelta4(String out, double factor, HostInfoVO info, boolean disk) {
        if (out == null) {
            return;
        }
        String[] t = out.trim().split("\\s+");
        if (t.length < 4) {
            return;
        }
        try {
            long x1 = Long.parseLong(t[0]), y1 = Long.parseLong(t[1]);
            long x2 = Long.parseLong(t[2]), y2 = Long.parseLong(t[3]);
            double v1 = Math.round(Math.max(0, (x2 - x1) * factor) * 100.0) / 100.0;
            double v2 = Math.round(Math.max(0, (y2 - y1) * factor) * 100.0) / 100.0;
            if (disk) {
                info.setDiskReadKbs(v1);
                info.setDiskWriteKbs(v2);
            } else {
                info.setNetworkRxKb(v1);
                info.setNetworkTxKb(v2);
            }
        } catch (NumberFormatException ignore) {
            // 非数字输出不填充
        }
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
            vo.setMemory(toLong(p[3]) / KB);
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
            NetworkInterfaceRowVO vo = byName.computeIfAbsent(name, PosixSshDelegate::newInterface);
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
     * 追加一个 IPv4 地址（分号连接）。
     *
     * @param vo   目标网卡
     * @param addr 地址，空值忽略
     */
    private static void appendIpv4(NetworkInterfaceRowVO vo, String addr) {
        if (addr != null && !addr.isEmpty()) {
            vo.setIpv4(vo.getIpv4() == null || vo.getIpv4().isEmpty() ? addr : vo.getIpv4() + ";" + addr);
        }
    }

    /**
     * 追加一个 IPv6 地址（分号连接）。
     *
     * @param vo   目标网卡
     * @param addr 地址，空值忽略
     */
    private static void appendIpv6(NetworkInterfaceRowVO vo, String addr) {
        if (addr != null && !addr.isEmpty()) {
            vo.setIpv6(vo.getIpv6() == null || vo.getIpv6().isEmpty() ? addr : vo.getIpv6() + ";" + addr);
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
        String addrs = paren < 0 ? name : name.substring(0, paren).trim();
        String state = paren < 0 ? (type.equalsIgnoreCase("TCP") ? "ESTABLISHED" : "UNCONN")
                : name.substring(paren + 1, name.length() - 1).replace("LISTENING", "LISTEN");
        int arrow = addrs.indexOf("->");
        String[] local = splitAddrPort(arrow < 0 ? addrs : addrs.substring(0, arrow).trim());
        String[] peer = arrow < 0 ? new String[]{"", "0"}
                : splitAddrPort(addrs.substring(arrow + 2).trim());
        return NetProcessRowVO.of(type, local[0], toInt(local[1]), peer[0], toInt(peer[1]),
                state, toInt(p[1]), p[0]);
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
        String safe = path.replace("'", "");
        String out = sshCache().exec(host, "if [ -d '" + safe + "' ]; then ls -la --time-style=long-iso '"
                + safe + "'; else echo " + ShellSupport.NO_DIR_MARK + "; fi", 15000);
        if (out != null && out.trim().equals(ShellSupport.NO_DIR_MARK)) {
            throw new IllegalStateException("目录不存在或不是目录: " + path);
        }
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
            log.error("SFTP upload failed host={} path={}", host.getId(), remotePath, e);
            // 只回 false 会让用户看到「上传失败: 路径」，真实原因留在日志无人读
            throw new IllegalStateException(e.getMessage(), e);
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

    @Override
    public boolean mkdir(MsServerHost host, String path) {
        // mkdir -p 自动补齐父目录且目录已存在不报错
        String out = sshCache().exec(host, "mkdir -p " + shQuote(path) + " && echo OK", 15000);
        return out != null && out.contains("OK");
    }

    @Override
    public boolean delete(MsServerHost host, String path) {
        // rm -rf 同时处理文件与非空目录，删完回检
        String out = sshCache().exec(host, "rm -rf " + shQuote(path) + " && [ ! -e " + shQuote(path) + " ] && echo OK", 20000);
        return out != null && out.contains("OK");
    }

    @Override
    public boolean rename(MsServerHost host, String from, String to) {
        // mv -f 覆盖同名目标；目标父目录需已存在
        String out = sshCache().exec(host, "mv -f " + shQuote(from) + " " + shQuote(to) + " && echo OK", 15000);
        return out != null && out.contains("OK");
    }

    /**
     * 单引号包裹 shell 参数，转义路径中的单引号。
     *
     * @param value 原始路径
     * @return 单引号安全串
     */
    private static String shQuote(String value) {
        String v = value == null ? "" : value;
        return "'" + v.replace("'", "'\\''") + "'";
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
