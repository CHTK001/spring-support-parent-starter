package com.chua.starter.monitor.support.spi.impl;

import com.chua.starter.monitor.support.enums.ServiceAction;
import com.chua.starter.monitor.support.model.DiskPartitionRowVO;
import com.chua.starter.monitor.support.model.DriverRowVO;
import com.chua.starter.monitor.support.model.FirewallRuleInputVO;
import com.chua.starter.monitor.support.model.FirewallRuleRowVO;
import com.chua.starter.monitor.support.model.HostActionVO;
import com.chua.starter.monitor.support.model.NetworkConfigVO;
import com.chua.starter.monitor.support.model.ProcessThreadRowVO;
import com.chua.starter.monitor.support.spi.HostOperations;
import com.chua.starter.monitor.support.spi.SystemInfoProvider;
import com.chua.starter.monitor.support.support.ShellSupport;
import com.chua.starter.server.support.entity.MsServerHost;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Linux / macOS 平台的动作与采集命令。
 *
 * <p>命令按 {@code /bin/sh} 语义拼装：进程存在性用 {@code kill -0} 探测，服务状态用
 * {@code systemctl show} 单查，网络配置走 NetworkManager 的 {@code nmcli}。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class PosixHostOperations extends HostOperations {

    /**
     * @param channel 目标主机所在通道
     * @param host    目标主机
     */
    public PosixHostOperations(SystemInfoProvider channel, MsServerHost host) {
        super(channel, host);
    }

    @Override
    protected boolean processAlive(int pid) {
        String out = run("kill -0 " + pid + " 2>/dev/null && echo ALIVE || echo DEAD").output();
        if (out == null || out.isBlank()) {
            return false;
        }
        String upper = out.toUpperCase(Locale.ROOT);
        return upper.contains("ALIVE") && !upper.contains("DEAD");
    }

    @Override
    protected String killCommand(int pid) {
        return "kill -9 " + pid;
    }

    @Override
    protected String serviceCommand(String name, ServiceAction action) {
        return "systemctl " + action.getCode() + " " + name;
    }

    @Override
    protected String serviceState(String name) {
        String out = run("systemctl show -p LoadState,ActiveState --value " + name + " 2>/dev/null").output();
        if (out == null || out.isBlank()) {
            return "UNKNOWN";
        }
        String text = out.trim();
        if (text.contains("not-found") || text.contains("MISSING")) {
            return "MISSING";
        }
        String[] lines = text.split("\\r?\\n");
        String state = lines[lines.length - 1].trim();
        if ("running".equalsIgnoreCase(state) || "active".equalsIgnoreCase(state)) {
            return "RUNNING";
        }
        if ("stopped".equalsIgnoreCase(state) || "inactive".equalsIgnoreCase(state) || "failed".equalsIgnoreCase(state)) {
            return "STOPPED";
        }
        return "UNKNOWN";
    }

    /**
     * 重启进程：从 {@code /proc} 读回命令行与工作目录，杀掉后以原样后台拉起。
     *
     * <p>只回「指令已下发」而不回「已重启」：拉起是分离出去的子进程，通道拿不到它的退出状态，
     * 谎报成功会让操作者以为服务已经恢复。</p>
     */
    @Override
    public HostActionVO restartProcess(int pid) {
        String command = "A=$(tr '\\0' ' ' < /proc/" + pid + "/cmdline); "
                + "D=$(readlink /proc/" + pid + "/cwd); "
                + "kill -9 " + pid + " 2>/dev/null; sleep 1; "
                + "( cd \"$D\" && nohup $A >/dev/null 2>&1 & )";
        ShellSupport.ProbeResult result = run(command);
        return result.failed()
                ? HostActionVO.fail("进程 " + pid + " 重启失败" + detail(result))
                : HostActionVO.ok("进程 " + pid + " 重启指令已下发" + detail(result));
    }

    @Override
    public HostActionVO applyNetworkConfig(NetworkConfigVO config) {
        String ifname = trim(config.ifname());
        if (ifname.isEmpty()) {
            return HostActionVO.fail("缺少网卡名 ifname");
        }
        String command = nmcliCommand(config, ifname);
        if (command == null) {
            return HostActionVO.fail("静态地址缺少 ip");
        }
        ShellSupport.ProbeResult result = run(command);
        return result.failed()
                ? HostActionVO.fail("网络配置未生效" + detail(result))
                : HostActionVO.ok("网络配置已应用" + detail(result));
    }

    @Override
    public List<FirewallRuleRowVO> firewallRules() {
        return parseIptables(output("iptables -L -n --line-numbers"));
    }

    @Override
    public List<DriverRowVO> drivers() {
        return parseLsmod(output("lsmod"));
    }

    @Override
    public List<ProcessThreadRowVO> processThreads(int pid) {
        return parseThreadRows(output("ps -L -p " + pid + " -o tid,pcpu,pmem,stat,comm,time"));
    }

    /**
     * 打开文件句柄数：Linux 读 {@code /proc/sys/fs/file-nr} 首列（已分配文件句柄数），
     * macOS 回退 {@code sysctl kern.num_files}。
     */
    @Override
    public long handleCount() {
        return firstInt(output("cat /proc/sys/fs/file-nr 2>/dev/null || sysctl -n kern.num_files 2>/dev/null"));
    }

    @Override
    public List<DiskPartitionRowVO> diskPartitions() {
        return parsePartitions(output("df -hT"));
    }

    /* ---------- 环境变量（/etc/environment，Linux/macOS 通用） ---------- */

    @Override
    protected String envSetCommand(String name, String value) {
        String line = name + "='" + value + "'";
        return "touch /etc/environment; (grep -v '^" + name + "=' /etc/environment || true) "
                + "> /etc/environment.tmp; mv /etc/environment.tmp /etc/environment; "
                + "echo " + shQuote(line) + " >> /etc/environment";
    }

    @Override
    protected String envDeleteCommand(String name) {
        return "touch /etc/environment; (grep -v '^" + name + "=' /etc/environment || true) "
                + "> /etc/environment.tmp; mv /etc/environment.tmp /etc/environment";
    }

    @Override
    protected String readEnvValue(String name) {
        String line = output("grep '^" + name + "=' /etc/environment 2>/dev/null | tail -n 1");
        if (line == null || line.isBlank()) {
            return null;
        }
        String text = line.trim();
        String prefix = name + "=";
        if (!text.startsWith(prefix)) {
            return null;
        }
        String val = text.substring(prefix.length());
        if (val.length() >= 2
                && ((val.startsWith("'") && val.endsWith("'"))
                || (val.startsWith("\"") && val.endsWith("\"")))) {
            val = val.substring(1, val.length() - 1);
        }
        return val;
    }

    /* ---------- 防火墙（iptables；macOS 为 pf，此处按 iptables 口径） ---------- */

    @Override
    public HostActionVO addFirewallRule(FirewallRuleInputVO rule) {
        String chain = "out".equalsIgnoreCase(trim(rule.direction())) ? "OUTPUT" : "INPUT";
        String target = "block".equalsIgnoreCase(trim(rule.action())) ? "DROP" : "ACCEPT";
        StringBuilder cmd = new StringBuilder("iptables -I ").append(chain).append(" 1");
        String proto = trim(rule.protocol()).toLowerCase(Locale.ROOT);
        boolean useProto = "tcp".equals(proto) || "udp".equals(proto);
        String port = trim(rule.localPort());
        if (useProto) {
            cmd.append(" -p ").append(proto);
        }
        if (useProto && !port.isEmpty()) {
            cmd.append(" --dport ").append(port.replaceAll("[^0-9,\\-:]", ""));
        }
        cmd.append(" -j ").append(target);
        ShellSupport.ProbeResult result = run(cmd.toString());
        return result.failed()
                ? HostActionVO.fail("iptables 规则添加失败" + detail(result))
                : HostActionVO.ok("iptables 规则已下发到 " + chain + "（运行时生效，重启后不保留；"
                        + "如需持久化请执行 iptables-save / netfilter-persistent）" + detail(result));
    }

    @Override
    public HostActionVO deleteFirewallRule(String name, String chain, Integer num) {
        String ch = trim(chain);
        if (ch.isEmpty() || num == null || num <= 0) {
            return HostActionVO.fail("Linux 防火墙需按「链 + 序号」删除，缺少 chain/num");
        }
        ShellSupport.ProbeResult result = run("iptables -D " + ch + " " + num);
        return result.failed()
                ? HostActionVO.fail("iptables 规则删除失败" + detail(result))
                : HostActionVO.ok("iptables 规则 " + ch + " #" + num + " 已删除（运行时生效）" + detail(result));
    }

    /**
     * 把字符串包成单引号 shell 字面量，内部单引号按 {@code '\''} 转义。
     *
     * @param s 原始字符串
     * @return 如 {@code 'a b'\''c'}
     */
    private static String shQuote(String s) {
        return "'" + (s == null ? "" : s.replace("'", "'\\''")) + "'";
    }

    /**
     * 组装 NetworkManager 配置命令。
     *
     * @param config 配置项
     * @param ifname 已归一的网卡名
     * @return 命令行；静态模式缺 ip 时返回 null
     */
    private static String nmcliCommand(NetworkConfigVO config, String ifname) {
        String con = "'" + ifname.replace("'", "") + "'";
        StringBuilder cmd = new StringBuilder();
        if (config.staticMode()) {
            String ip = trim(config.ip());
            if (ip.isEmpty()) {
                return null;
            }
            cmd.append("nmcli con mod ").append(con).append(" ipv4.addresses '").append(ip)
                    .append('/').append(toPrefix(config.mask())).append("' ipv4.method manual");
            String gateway = trim(config.gateway());
            if (!gateway.isEmpty()) {
                cmd.append(" ipv4.gateway '").append(gateway).append("'");
            }
            String dns = joinNonEmpty(" ", trim(config.dns1()), trim(config.dns2()));
            if (!dns.isEmpty()) {
                cmd.append(" ipv4.dns '").append(dns).append("' ipv4.ignore-auto-dns yes");
            }
        } else {
            cmd.append("nmcli con mod ").append(con).append(" ipv4.method auto");
        }
        return cmd.append(" && nmcli con up ").append(con).toString();
    }

    /**
     * 子网掩码转前缀长度。
     *
     * @param mask 点分掩码，空表示 24
     * @return 前缀长度
     */
    private static int toPrefix(String mask) {
        if (mask == null || mask.isBlank()) {
            return 24;
        }
        if (mask.matches("^\\d{1,2}$")) {
            return Integer.parseInt(mask);
        }
        int prefix = 0;
        for (String part : mask.split("\\.")) {
            prefix += Integer.bitCount(Integer.parseInt(part) & 0xFF);
        }
        return prefix == 0 ? 24 : prefix;
    }

    /**
     * 用分隔符拼接非空片段。
     *
     * @param sep   分隔符
     * @param parts 片段
     * @return 拼接结果
     */
    private static String joinNonEmpty(String sep, String... parts) {
        List<String> kept = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                kept.add(part);
            }
        }
        return String.join(sep, kept);
    }

    /**
     * {@code iptables -L -n --line-numbers} 文本 → 规则视图。
     *
     * @param output 命令输出
     * @return 规则列表
     */
    private static List<FirewallRuleRowVO> parseIptables(String output) {
        List<FirewallRuleRowVO> rules = new ArrayList<>();
        String chain = "";
        for (String line : output.split("\\r?\\n")) {
            if (line.startsWith("Chain")) {
                chain = line.split("\\s+")[1];
                continue;
            }
            if (!line.matches("^\\d+.*")) {
                continue;
            }
            String[] parts = line.trim().split("\\s+", 7);
            FirewallRuleRowVO vo = new FirewallRuleRowVO();
            vo.setChain(chain);
            vo.setNum((int) parseLong(parts[0]));
            vo.setTarget(part(parts, 1));
            vo.setProtocol(part(parts, 2));
            vo.setSource(part(parts, 4));
            vo.setDestination(part(parts, 5));
            vo.setInfo(part(parts, 6));
            rules.add(vo);
        }
        return rules;
    }

    /**
     * {@code lsmod} 文本 → 驱动视图（首行是表头，跳过）。
     *
     * @param output 命令输出
     * @return 驱动列表
     */
    private static List<DriverRowVO> parseLsmod(String output) {
        List<DriverRowVO> drivers = new ArrayList<>();
        String[] lines = output.split("\\r?\\n");
        for (int i = 1; i < lines.length; i++) {
            String[] parts = lines[i].trim().split("\\s+", 4);
            if (parts.length < 3) {
                continue;
            }
            DriverRowVO vo = new DriverRowVO();
            vo.setName(parts[0]);
            vo.setSize(parseLong(parts[1]));
            vo.setUsedBy((int) parseLong(parts[2]));
            vo.setDependsOn(part(parts, 3));
            drivers.add(vo);
        }
        return drivers;
    }

    /**
     * {@code ps -L} 文本 → 线程视图（首行是表头，跳过）。
     *
     * @param output 命令输出
     * @return 线程列表
     */
    private static List<ProcessThreadRowVO> parseThreadRows(String output) {
        List<ProcessThreadRowVO> threads = new ArrayList<>();
        String[] lines = output.split("\\r?\\n");
        for (int i = 1; i < lines.length; i++) {
            String[] parts = lines[i].trim().split("\\s+", 6);
            if (parts.length < 5) {
                continue;
            }
            ProcessThreadRowVO vo = new ProcessThreadRowVO();
            vo.setTid(parseLong(parts[0]));
            vo.setCpu(parseDouble(parts[1]));
            vo.setMemory(parseDouble(parts[2]));
            vo.setState(parts[3]);
            vo.setName(parts.length > 4 ? parts[4] : "");
            vo.setCpuTime(parts.length > 5 ? parts[5] : "");
            threads.add(vo);
        }
        return threads;
    }

    /**
     * {@code df -hT} 文本 → 分区视图（首行是表头，跳过）。
     *
     * @param output 命令输出
     * @return 分区列表
     */
    private static List<DiskPartitionRowVO> parsePartitions(String output) {
        List<DiskPartitionRowVO> partitions = new ArrayList<>();
        String[] lines = output.split("\\r?\\n");
        for (int i = 1; i < lines.length; i++) {
            String[] parts = lines[i].trim().split("\\s+", 7);
            if (parts.length < 7) {
                continue;
            }
            DiskPartitionRowVO vo = new DiskPartitionRowVO();
            vo.setFilesystem(parts[0]);
            vo.setType(parts[1]);
            vo.setSize(parts[2]);
            vo.setUsed(parts[3]);
            vo.setAvailable(parts[4]);
            vo.setUsePercent(parts[5]);
            vo.setMountPoint(parts[6]);
            partitions.add(vo);
        }
        return partitions;
    }

    /**
     * 安全取数组片段。
     *
     * @param parts 片段数组
     * @param index 下标
     * @return 值，越界返回空串
     */
    private static String part(String[] parts, int index) {
        return parts.length > index ? parts[index] : "";
    }

    /**
     * 宽松 long 解析。
     *
     * @param value 数字字符串
     * @return 解析结果，非法时返回 0
     */
    private static long parseLong(String value) {
        try {
            return value == null ? 0L : Long.parseLong(value.trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * 宽松 double 解析。
     *
     * @param value 数字字符串
     * @return 解析结果，非法时返回 0
     */
    private static double parseDouble(String value) {
        try {
            return value == null ? 0.0 : Double.parseDouble(value.trim());
        } catch (Exception e) {
            return 0.0;
        }
    }
}
