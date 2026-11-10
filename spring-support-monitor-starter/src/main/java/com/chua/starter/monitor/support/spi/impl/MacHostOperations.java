// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.monitor.support.spi.impl;

import com.chua.starter.monitor.support.enums.ServiceAction;
import com.chua.starter.monitor.support.model.DiskPartitionRowVO;
import com.chua.starter.monitor.support.model.DriverRowVO;
import com.chua.starter.monitor.support.model.FirewallRuleInputVO;
import com.chua.starter.monitor.support.model.FirewallRuleRowVO;
import com.chua.starter.monitor.support.model.HostActionVO;
import com.chua.starter.monitor.support.model.InodeUsageVO;
import com.chua.starter.monitor.support.model.NetworkConfigVO;
import com.chua.starter.monitor.support.model.ProcessThreadRowVO;
import com.chua.starter.monitor.support.model.SystemConfigItemVO;
import com.chua.starter.monitor.support.spi.SystemInfoProvider;
import com.chua.starter.monitor.support.support.ShellSupport;
import com.chua.starter.server.support.entity.MsServerHost;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * macOS（Darwin）平台的动作与采集命令。
 *
 * <p>为什么单独一个类而不是继续共用 {@link PosixHostOperations}：两者的命令族并不是同一族。
 * macOS 没有 systemd / NetworkManager / iptables / lsmod / {@code /proc}，
 * 继续按 Linux 口径下发只会得到「命令看起来执行了但没生效」——尤其是
 * limits.d 与环境变量两处，Linux 的写法在 macOS 上写完文件也不会被读取，
 * 而回读又恰好读的是同一个文件，于是把「没生效」报成了「已生效」。</p>
 *
 * <p>本类只覆盖与 Linux 有真实差异的方法，其余（进程存在性探测、杀进程、挂载、lsof 采集
 * 已删除文件、句柄数、挂载列表）继续用父类的 POSIX 口径，那些命令在 macOS 上确实可用。</p>
 *
 * <p>平台差异落点：服务走 {@code launchctl}，网络走 {@code networksetup}，防火墙走 {@code pf} 锚点，
 * 驱动走 {@code kmutil showloaded} / {@code kextstat}，系统配置走 {@code sysctl} 的 Darwin 键名，
 * 用户限制走 {@code launchctl limit} 并由 LaunchDaemon 在开机时重放。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public class MacHostOperations extends PosixHostOperations {

    /**
     * 本系统在目标机上存放辅助脚本的目录
     */
    private static final String MS_DIR = "/usr/local/ms-monitor";

    /**
     * 开机重放用户限制（launchctl limit）的脚本
     */
    private static final String LIMIT_SCRIPT = MS_DIR + "/limits.sh";

    /**
     * 承载 {@link #LIMIT_SCRIPT} 的 LaunchDaemon
     */
    private static final String LIMIT_PLIST = "/Library/LaunchDaemons/com.chua.ms-monitor.limits.plist";

    /**
     * 开机重放环境变量（launchctl setenv）的脚本
     */
    private static final String ENV_SCRIPT = MS_DIR + "/env.sh";

    /**
     * 承载 {@link #ENV_SCRIPT} 的 LaunchDaemon
     */
    private static final String ENV_PLIST = "/Library/LaunchDaemons/com.chua.ms-monitor.env.plist";

    /**
     * 承载本系统防火墙规则的 pf 锚点名
     */
    private static final String PF_ANCHOR = "ms-monitor";

    /**
     * pf 锚点规则文件
     */
    private static final String PF_ANCHOR_FILE = "/etc/pf.anchors/ms-monitor";

    /**
     * pf 主配置文件
     */
    private static final String PF_CONF = "/etc/pf.conf";

    /**
     * LaunchDaemon 标签：用户限制
     */
    private static final String LIMIT_LABEL = "com.chua.ms-monitor.limits";

    /**
     * LaunchDaemon 标签：环境变量
     */
    private static final String ENV_LABEL = "com.chua.ms-monitor.env";

    /**
     * kextstat / kmutil 的输出行：序号 引用数 地址 大小 常驻 名称 (版本) 依赖
     */
    private static final Pattern KEXT_LINE = Pattern.compile(
            "^\\s*(\\d+)\\s+(\\d+)\\s+(0x[0-9a-fA-F]+)\\s+(0x[0-9a-fA-F]+)\\s+(0x[0-9a-fA-F]+)"
                    + "\\s+(\\S+)\\s+\\(([^)]*)\\)(.*)$");

    /**
     * IPv4 点分地址
     */
    private static final String IPV4 = "^\\d{1,3}(\\.\\d{1,3}){3}$";

    /**
     * @param channel 目标主机所在通道
     * @param host    目标主机
     */
    public MacHostOperations(SystemInfoProvider channel, MsServerHost host) {
        super(channel, host);
    }

    /* ---------- 服务（launchd） ---------- */

    /**
     * 服务动作：launchd 的服务用 {@code domain/label} 定位。
     *
     * <p>先认系统域（系统级 LaunchDaemon），再认当前用户的 GUI 域（LaunchAgent）。
     * 停止用 {@code kill SIGTERM} 而不是 {@code bootout}：后者会把作业从 launchd 卸载，
     * 再启动就找不到 plist 了；这里只负责「让进程停下」。</p>
     *
     * @param name   已净化的服务名（launchd 标签，如 {@code com.apple.smbd}）
     * @param action 动作
     * @return 命令行
     */
    @Override
    protected String serviceCommand(String name, ServiceAction action) {
        String label = safeName(name);
        String sub;
        switch (action) {
            case START:
                sub = "launchctl kickstart \"$T\"";
                break;
            case STOP:
                sub = "launchctl kill SIGTERM \"$T\"";
                break;
            default:
                sub = "launchctl kickstart -k \"$T\"";
                break;
        }
        return "L=" + q(label)
                + "; if launchctl print system/$L >/dev/null 2>&1; then T=system/$L;"
                + " elif launchctl print gui/$(id -u)/$L >/dev/null 2>&1; then T=gui/$(id -u)/$L;"
                + " else T=system/$L; fi; " + sub;
    }

    /**
     * 服务状态：{@code launchctl print} 的 {@code state =} 行。
     *
     * <p>不拼成一条 {@code a || b}：前一个域失败时的报错文本会混进后一个域的输出里，
     * 把「系统域没有、用户域有」误判成服务不存在。因此两个域分开探测。</p>
     *
     * @param name 已净化的服务名
     * @return {@code RUNNING} / {@code STOPPED} / {@code MISSING} / {@code UNKNOWN}
     */
    @Override
    protected String serviceState(String name) {
        String label = safeName(name);
        ShellSupport.ProbeResult system = run("launchctl print system/" + label + " 2>&1");
        String systemText = system.output() == null ? "" : system.output();
        String state = stateOf(systemText);
        if (state != null) {
            return state;
        }
        ShellSupport.ProbeResult gui = run("launchctl print gui/$(id -u)/" + label + " 2>&1");
        String guiText = gui.output() == null ? "" : gui.output();
        state = stateOf(guiText);
        if (state != null) {
            return state;
        }
        String all = (systemText + " " + guiText).toLowerCase(Locale.ROOT);
        return all.contains("could not find service") || all.contains("no such process") ? "MISSING" : "UNKNOWN";
    }

    /**
     * 从 {@code launchctl print} 输出里取状态。
     *
     * @param text 输出文本
     * @return 状态；输出里没有 {@code state =} 时返回 null
     */
    private static String stateOf(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (!lower.contains("state =")) {
            return null;
        }
        if (lower.contains("state = running")) {
            return "RUNNING";
        }
        if (lower.contains("state = not running") || lower.contains("state = waiting")
                || lower.contains("state = exited") || lower.contains("state = spawn scheduled")) {
            return "STOPPED";
        }
        return "UNKNOWN";
    }

    /* ---------- 进程 ---------- */

    /**
     * 重启进程：macOS 没有 {@code /proc}，命令行取 {@code ps -o command=}，
     * 工作目录取 {@code lsof -d cwd}。
     *
     * @param pid 进程 ID
     * @return 动作结果；只回「指令已下发」，与父类口径一致
     */
    @Override
    public HostActionVO restartProcess(int pid) {
        String command = "C=$(ps -o command= -p " + pid + "); "
                + "D=$(lsof -a -p " + pid + " -d cwd -Fn 2>/dev/null | sed -n 's/^n//p' | head -n 1); "
                + "[ -z \"$D\" ] && D=/; "
                + "kill -9 " + pid + " 2>/dev/null; sleep 1; "
                + "( cd \"$D\" && nohup $C >/dev/null 2>&1 & )";
        ShellSupport.ProbeResult result = run(command);
        return result.failed()
                ? HostActionVO.fail("进程 " + pid + " 重启失败" + detail(result))
                : HostActionVO.ok("进程 " + pid + " 重启指令已下发" + detail(result));
    }

    /**
     * 线程列表：{@code ps -M} 每个线程一行。
     *
     * <p>macOS 的 {@code ps} 不暴露线程 ID（Linux 的 {@code tid} 关键字在 Darwin 上不存在），
     * 输出的 {@code tid} 取线程在本次输出中的行序号，仅用于前端逐行展示，
     * 不要拿它去对系统调用或 {ps -M} 以外的工具关联。</p>
     *
     * @param pid 进程 ID
     * @return 线程行列表
     */
    @Override
    public List<ProcessThreadRowVO> processThreads(int pid) {
        String out = output("ps -M -p " + pid + " -o pid,pcpu,pmem,state,comm,time");
        List<ProcessThreadRowVO> rows = new ArrayList<>();
        if (out == null || out.isBlank()) {
            return rows;
        }
        String[] lines = out.split("\\r?\\n");
        long index = 0;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\s+", 6);
            if (parts.length < 5) {
                continue;
            }
            index++;
            rows.add(new ProcessThreadRowVO(
                    index,
                    parseDouble(parts[1]),
                    parseDouble(parts[2]),
                    parts[3],
                    parts[4],
                    "",
                    "",
                    parts.length > 5 ? parts[5] : ""));
        }
        return rows;
    }

    /**
     * 释放已删除但仍被占用的文件：macOS 不支持。
     *
     * <p>Linux 的实现是 {@code truncate /proc/<pid>/fd/<fd>}；macOS 没有 {@code /proc}，
     * 也没有能按他人描述符截断的可写路径。这里明确报不支持，而不是返回成功——
     * 报成功会让操作者以为空间已经释放。</p>
     *
     * @param pid 持有进程 ID
     * @param fd  文件描述符编号
     * @return 动作结果
     */
    @Override
    public HostActionVO releaseDeletedFile(long pid, int fd) {
        return HostActionVO.fail("macOS 无法截断其他进程持有的已删除文件描述符（无 /proc 与对应可写路径）；"
                + "请在应用侧关闭该文件或重启进程以释放空间");
    }

    /* ---------- 网络与防火墙 ---------- */

    /**
     * 网络配置：macOS 用 {@code networksetup} 按「网络服务名」下发，而不是网卡名。
     *
     * <p>网卡名（en0）与服务名（Wi-Fi / Ethernet）不是一回事，
     * 先用 {@code -listallhardwareports} 把两者对上，再下发。</p>
     *
     * @param config 配置项
     * @return 动作结果
     */
    @Override
    public HostActionVO applyNetworkConfig(NetworkConfigVO config) {
        String ifname = trim(config.ifname());
        String dev = ifname.replaceAll("[^A-Za-z0-9_.:-]", "");
        if (dev.isEmpty()) {
            return HostActionVO.fail("缺少网卡名 ifname");
        }
        String ip = trim(config.ip());
        String gateway = trim(config.gateway());
        if (config.staticMode()) {
            if (ip.isEmpty()) {
                return HostActionVO.fail("静态地址缺少 ip");
            }
            if (!ip.matches(IPV4)) {
                return HostActionVO.fail("IP 格式不合法: " + ip);
            }
            if (!gateway.isEmpty() && !gateway.matches(IPV4)) {
                return HostActionVO.fail("网关格式不合法: " + gateway);
            }
        }
        String mask = dottedMask(trim(config.mask()));
        StringBuilder cmd = new StringBuilder();
        cmd.append("S=$(networksetup -listallhardwareports | awk -v d=").append(q(dev))
                .append(" '/^Hardware Port:/{p=substr($0,index($0,\":\")+2)}")
                .append(" /^Device:/{if($2==d){print p; exit}}'); ")
                .append("[ -z \"$S\" ] && { echo ").append(q("未找到网卡 " + dev + " 对应的网络服务"))
                .append("; exit 2; }; ");
        if (config.staticMode()) {
            cmd.append("networksetup -setmanual \"$S\" ").append(q(ip)).append(' ').append(q(mask));
            if (!gateway.isEmpty()) {
                cmd.append(' ').append(q(gateway));
            }
        } else {
            cmd.append("networksetup -setdhcp \"$S\"");
        }
        cmd.append("; ");
        String dns1 = trim(config.dns1());
        String dns2 = trim(config.dns2());
        if (!dns1.isEmpty() || !dns2.isEmpty()) {
            cmd.append("networksetup -setdnsservers \"$S\"");
            if (!dns1.isEmpty()) {
                cmd.append(' ').append(q(dns1));
            }
            if (!dns2.isEmpty()) {
                cmd.append(' ').append(q(dns2));
            }
        } else if ("dhcp".equalsIgnoreCase(trim(config.dnsMode()))) {
            cmd.append("networksetup -setdnsservers \"$S\" Empty");
        }
        ShellSupport.ProbeResult result = run(cmd.toString());
        return result.failed()
                ? HostActionVO.fail("网络配置未生效" + detail(result))
                : HostActionVO.ok("网络配置已应用到网络服务" + detail(result));
    }

    /**
     * 防火墙规则：读取 pf 锚点 {@code ms-monitor} 的规则集。
     *
     * @return 规则列表
     */
    @Override
    public List<FirewallRuleRowVO> firewallRules() {
        ShellSupport.ProbeResult result = run("pfctl -a " + PF_ANCHOR + " -sr 2>&1");
        String out = result.output() == null ? "" : result.output();
        if (result.failed() && !out.isBlank()) {
            throw new IllegalStateException("读取 pf 规则失败（pfctl 通常需要 root 权限）: " + out.trim());
        }
        return parsePfRules(out);
    }

    /**
     * 新增防火墙规则：写入 pf 锚点文件并重载，确保规则真的被评估。
     *
     * <p>只往锚点里追加是不够的：主配置 {@code /etc/pf.conf} 必须先有对应的
     * {@code anchor} 与 {@code load anchor} 两行，锚点里的规则才会生效。
     * 这里缺哪行补哪行，然后 {@code pfctl -f} 重载并 {@code -E} 启用 pf。</p>
     *
     * @param rule 规则入参
     * @return 动作结果
     */
    @Override
    public HostActionVO addFirewallRule(FirewallRuleInputVO rule) {
        String direction = "out".equalsIgnoreCase(trim(rule.direction())) ? "out" : "in";
        String action = "block".equalsIgnoreCase(trim(rule.action())) ? "block" : "pass";
        String proto = trim(rule.protocol()).toLowerCase(Locale.ROOT);
        boolean useProto = "tcp".equals(proto) || "udp".equals(proto);
        String port = pfPort(trim(rule.localPort()));
        StringBuilder text = new StringBuilder(action).append(' ').append(direction);
        if (useProto) {
            text.append(" proto ").append(proto);
        }
        text.append(" from any to any");
        if (!port.isEmpty()) {
            text.append(" port ").append(port);
        }
        String script = "F=" + PF_ANCHOR_FILE + "; C=" + PF_CONF + "; mkdir -p /etc/pf.anchors; touch $F; "
                + "grep -q " + q("anchor \"" + PF_ANCHOR + "\"") + " $C 2>/dev/null || echo "
                + q("anchor \"" + PF_ANCHOR + "\"") + " >> $C; "
                + "grep -q " + q("load anchor \"" + PF_ANCHOR + "\"") + " $C 2>/dev/null || echo "
                + q("load anchor \"" + PF_ANCHOR + "\" from \"" + PF_ANCHOR_FILE + "\"") + " >> $C; "
                + "printf '%s\\n' " + q(text.toString()) + " >> $F; "
                + "pfctl -f $C && pfctl -E";
        ShellSupport.ProbeResult result = run(script);
        return result.failed()
                ? HostActionVO.fail("pf 规则添加失败" + detail(result))
                : HostActionVO.ok("pf 规则已写入锚点 " + PF_ANCHOR + "（" + text + "）并已重载" + detail(result));
    }

    /**
     * 删除防火墙规则：按锚点内的行号重写锚点文件并重载。
     *
     * @param name  规则显示名（macOS 不参与定位）
     * @param chain 锚点名（macOS 固定 {@code ms-monitor}）
     * @param num   锚点内规则行号，从 1 开始
     * @return 动作结果
     */
    @Override
    public HostActionVO deleteFirewallRule(String name, String chain, Integer num) {
        if (num == null || num < 1) {
            return HostActionVO.fail("缺少规则序号");
        }
        String script = "F=" + PF_ANCHOR_FILE + "; [ -f $F ] || { echo " + q("锚点文件不存在: " + PF_ANCHOR_FILE)
                + "; exit 2; }; "
                + "awk -v n=" + num + " 'NR!=n' $F > $F.tmp && mv $F.tmp $F; "
                + "pfctl -f " + PF_CONF + " && pfctl -E";
        ShellSupport.ProbeResult result = run(script);
        return result.failed()
                ? HostActionVO.fail("pf 规则删除失败" + detail(result))
                : HostActionVO.ok("pf 锚点第 " + num + " 条规则已删除并重载" + detail(result));
    }

    /**
     * 驱动：优先 {@code kmutil showloaded}（macOS 11+），回退 {@code kextstat -l}。
     *
     * @return 已加载的内核扩展列表
     */
    @Override
    public List<DriverRowVO> drivers() {
        ShellSupport.ProbeResult kmutil = run("kmutil showloaded --no-kernel-components 2>/dev/null");
        String out = kmutil.failed() ? null : kmutil.output();
        if (out == null || out.isBlank()) {
            ShellSupport.ProbeResult kextstat = run("kextstat -l 2>/dev/null");
            out = kextstat.failed() ? null : kextstat.output();
        }
        if (out == null || out.isBlank()) {
            throw new IllegalStateException("读取内核扩展失败：kmutil 与 kextstat 均不可用");
        }
        List<DriverRowVO> rows = new ArrayList<>();
        for (String line : out.split("\\r?\\n")) {
            Matcher matcher = KEXT_LINE.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String name = matcher.group(6);
            rows.add(new DriverRowVO(
                    name,
                    name,
                    hexToLong(matcher.group(4)),
                    parseInt(matcher.group(2)),
                    dependsOn(matcher.group(8)),
                    "",
                    matcher.group(7),
                    "",
                    name,
                    null));
        }
        return rows;
    }

    /* ---------- 磁盘 ---------- */

    /**
     * 分区列表：macOS 的 {@code df -h} 自带 inode 列，取前 5 列与末列（挂载点）。
     *
     * <p>不用 {@code df -hT}：macOS 的 {@code df} 不认 {@code -T}，命令会整体失败。</p>
     *
     * @return 分区列表
     */
    @Override
    public List<DiskPartitionRowVO> diskPartitions() {
        String out = output("df -h");
        List<DiskPartitionRowVO> rows = new ArrayList<>();
        if (out == null || out.isBlank()) {
            return rows;
        }
        String[] lines = out.split("\\r?\\n");
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\s+");
            if (parts.length < 6) {
                continue;
            }
            rows.add(new DiskPartitionRowVO(
                    parts[0],
                    "",
                    parts[1],
                    parts[2],
                    parts[3],
                    parts[4],
                    parts[parts.length - 1]));
        }
        return rows;
    }

    /**
     * 根分区 inode 使用情况：按表头定位 {@code iused} 列，而不是按固定列号。
     *
     * <p>macOS 的 {@code df -i} 在块容量之外附加 inode 列，列序与 Linux 的
     * {@code df -i}（用 inode 列替换容量列）不同，固定下标会读到错位的值。</p>
     *
     * @return inode 使用情况，解析失败时为 unknown
     */
    @Override
    public InodeUsageVO inodeUsage() {
        String out = output("df -i 2>/dev/null");
        if (out == null || out.isBlank()) {
            return InodeUsageVO.unknown();
        }
        String[] lines = out.split("\\r?\\n");
        if (lines.length < 2) {
            return InodeUsageVO.unknown();
        }
        String[] head = lines[0].trim().split("\\s+");
        int usedIndex = -1;
        for (int i = 0; i < head.length; i++) {
            if ("iused".equalsIgnoreCase(head[i])) {
                usedIndex = i;
                break;
            }
        }
        if (usedIndex < 0) {
            return InodeUsageVO.unknown();
        }
        InodeUsageVO root = null;
        InodeUsageVO highest = null;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\s+");
            if (parts.length <= usedIndex + 1) {
                continue;
            }
            long used = parseLong(parts[usedIndex]);
            long free = parseLong(parts[usedIndex + 1]);
            long total = used + free;
            if (used < 0 || free < 0 || total <= 0) {
                continue;
            }
            double percent = Math.round(used * 1000.0 / total) / 10.0;
            InodeUsageVO row = new InodeUsageVO(used, total, percent, parts[parts.length - 1]);
            if ("/".equals(parts[parts.length - 1])) {
                root = row;
            }
            if (highest == null || row.percent() > highest.percent()) {
                highest = row;
            }
        }
        if (root != null) {
            return root;
        }
        return highest == null ? InodeUsageVO.unknown() : highest;
    }

    /* ---------- 系统配置（Darwin sysctl 与 launchctl limit） ---------- */

    /**
     * 系统配置项：Darwin 的 sysctl 键名 + launchd 的资源限制。
     *
     * <p>Linux 的 {@code fs.file-max} / {@code net.ipv4.*} 在 Darwin 上不存在
     * （{@code sysctl -w} 会报 unknown oid），因此这里用 Darwin 自己的键名；
     * 读不到的键直接跳过，不编造默认值。</p>
     *
     * @return 配置项列表
     */
    @Override
    public List<SystemConfigItemVO> systemConfigs() {
        StringBuilder cmd = new StringBuilder();
        for (MacSysctl spec : MAC_SYSCTLS) {
            cmd.append("printf '").append(spec.key()).append("='; sysctl -n ")
                    .append(spec.key()).append(" 2>/dev/null; printf '\\n'; ");
        }
        String out = output(cmd.toString());
        java.util.Map<String, String> values = new java.util.HashMap<>();
        for (String line : out.split("\\r?\\n")) {
            int eq = line.indexOf('=');
            if (eq > 0) {
                values.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
        }
        List<SystemConfigItemVO> items = new ArrayList<>();
        for (MacSysctl spec : MAC_SYSCTLS) {
            String value = values.get(spec.key());
            if (value == null || value.isBlank()) {
                continue;
            }
            items.add(new SystemConfigItemVO(
                    spec.key(),
                    spec.label(),
                    value.trim(),
                    spec.unit(),
                    spec.scope(),
                    "sysctl -n " + spec.key() + "（持久化 /etc/sysctl.conf）",
                    true,
                    true,
                    spec.confirm(),
                    spec.tip(),
                    spec.group(),
                    spec.type(),
                    spec.options()));
        }
        for (MacLimit spec : MAC_LIMITS) {
            String[] current = launchLimit(spec.launchdKey());
            if (current == null) {
                continue;
            }
            String value = "soft".equals(spec.kind()) ? current[0] : current[1];
            items.add(new SystemConfigItemVO(
                    spec.key(),
                    spec.label(),
                    value,
                    spec.unit(),
                    spec.scope(),
                    "launchctl limit " + spec.launchdKey() + "（开机由 " + LIMIT_SCRIPT + " 重放）",
                    true,
                    false,
                    false,
                    spec.tip(),
                    "用户限制（launchd）",
                    "text",
                    ""));
        }
        return items;
    }

    /**
     * 修改一项系统配置：sysctl 直接写并持久化到 {@code /etc/sysctl.conf}；
     * launchd 限制写 {@code launchctl limit} 并由 LaunchDaemon 开机重放。
     *
     * @param key   配置键
     * @param value 新值
     * @return 动作结果
     */
    @Override
    public HostActionVO applySystemConfig(String key, String value) {
        String k = trim(key);
        String v = trim(value);
        MacLimit limit = findLimit(k);
        if (limit != null) {
            return applyLaunchLimit(limit, v);
        }
        MacSysctl spec = findSysctl(k);
        if (spec == null) {
            return HostActionVO.fail("macOS 不支持的系统配置项: " + key);
        }
        String invalid = validateValue(spec, v);
        if (invalid != null) {
            return HostActionVO.fail(invalid);
        }
        ShellSupport.ProbeResult result = run("sysctl -w " + spec.key() + "=" + q(v));
        if (result.failed()) {
            return HostActionVO.fail("写入 " + spec.key() + " 失败" + detail(result));
        }
        // 持久化：sysctl -w 只改运行时，重启后由 /etc/sysctl.conf 恢复
        String persist = "F=/etc/sysctl.conf; touch $F; { grep -v " + q("^" + spec.key() + "=") + " $F; echo "
                + q(spec.key() + "=" + v) + "; } > $F.tmp && mv $F.tmp $F";
        ShellSupport.ProbeResult persisted = run(persist);
        String suffix = persisted.failed()
                ? "（本次已生效，但写入 /etc/sysctl.conf 失败，重启后会回退）"
                : "";
        String got = output("sysctl -n " + spec.key() + " 2>/dev/null");
        if (got == null || !v.equals(got.trim())) {
            return HostActionVO.fail(spec.key() + " 写入后回读不一致（期望 " + v + "，实际 "
                    + (got == null ? "空" : got.trim()) + "）" + detail(result));
        }
        return HostActionVO.ok(spec.key() + " 已改为 " + v + suffix);
    }

    /**
     * 写入 launchd 资源限制并回读确认。
     *
     * <p>soft/hard 要一起下发：{@code launchctl limit} 的语义是「soft hard」两个值同时设置，
     * 只改一个会把另一个重置成默认。这里按当前值补齐另一端，soft 超过 hard 时同时抬高 hard。</p>
     *
     * @param spec 限制定义
     * @param v    新值（整数或 unlimited）
     * @return 动作结果
     */
    private HostActionVO applyLaunchLimit(MacLimit spec, String v) {
        if (!"unlimited".equalsIgnoreCase(v) && !v.matches("^\\d{1,12}$")) {
            return HostActionVO.fail(spec.label() + " 必须是整数或 unlimited: " + v);
        }
        String[] current = launchLimit(spec.launchdKey());
        if (current == null) {
            return HostActionVO.fail("读取当前 launchctl limit " + spec.launchdKey() + " 失败");
        }
        String soft = current[0];
        String hard = current[1];
        if ("soft".equals(spec.kind())) {
            soft = v;
            if (!"unlimited".equalsIgnoreCase(soft) && !"unlimited".equalsIgnoreCase(hard)
                    && parseLong(soft) > parseLong(hard)) {
                hard = soft;
            }
        } else {
            hard = v;
            if (!"unlimited".equalsIgnoreCase(hard) && !"unlimited".equalsIgnoreCase(soft)
                    && parseLong(soft) > parseLong(hard)) {
                soft = hard;
            }
        }
        String line = "launchctl limit " + spec.launchdKey() + " " + soft + " " + hard;
        String script = "D=" + MS_DIR + "; mkdir -p $D; S=" + LIMIT_SCRIPT + "; touch $S; "
                + "{ grep -v " + q("^launchctl limit " + spec.launchdKey() + " ") + " $S 2>/dev/null; echo "
                + q(line) + "; } > $S.tmp && mv $S.tmp $S; chmod +x $S; "
                + "P=" + LIMIT_PLIST + "; [ -f $P ] || printf '%s\\n' "
                + q(plist(LIMIT_LABEL, LIMIT_SCRIPT)) + " > $P; "
                + "launchctl print system/" + LIMIT_LABEL + " >/dev/null 2>&1"
                + " || launchctl bootstrap system $P >/dev/null 2>&1; "
                + line;
        ShellSupport.ProbeResult result = run(script);
        if (result.failed()) {
            return HostActionVO.fail(spec.label() + " 设置失败" + detail(result));
        }
        String[] now = launchLimit(spec.launchdKey());
        String got = now == null ? null : ("soft".equals(spec.kind()) ? now[0] : now[1]);
        if (got == null || !v.equalsIgnoreCase(got)) {
            return HostActionVO.fail(spec.label() + " 设置后回读不一致（期望 " + v + "，实际 "
                    + (got == null ? "空" : got) + "）");
        }
        return HostActionVO.ok(spec.label() + " 已设为 " + v
                + "（当前 launchd 上下文生效；开机由 " + LIMIT_SCRIPT + " 重放）");
    }

    /**
     * 读取一组 launchd 资源限制。
     *
     * @param launchdKey {@code maxfiles} / {@code maxproc}
     * @return {@code [soft, hard]}；读取失败返回 null
     */
    private String[] launchLimit(String launchdKey) {
        ShellSupport.ProbeResult result = run("launchctl limit " + launchdKey + " 2>/dev/null");
        String out = result.output();
        if (out == null || out.isBlank()) {
            return null;
        }
        String[] parts = out.trim().split("\\s+");
        if (parts.length < 3) {
            return null;
        }
        return new String[]{parts[1], parts[2]};
    }

    /* ---------- 环境变量（launchctl setenv） ---------- */

    /**
     * 设置环境变量：{@code launchctl setenv} 立即生效，脚本 + LaunchDaemon 保证开机重放。
     *
     * <p>不用 {@code /etc/environment}：那是 pam_env 的约定，macOS 默认不读，
     * 写完既不生效、回读还因为读的是同一个文件而「一致」。</p>
     *
     * @param name  已校验的变量名
     * @param value 变量值
     * @return 命令行
     */
    @Override
    protected String envSetCommand(String name, String value) {
        String line = "launchctl setenv " + name + " " + q(value);
        return "D=" + MS_DIR + "; mkdir -p $D; S=" + ENV_SCRIPT + "; touch $S; "
                + "{ grep -v " + q("^launchctl setenv " + name + " ") + " $S 2>/dev/null; echo "
                + q(line) + "; } > $S.tmp && mv $S.tmp $S; chmod +x $S; "
                + "P=" + ENV_PLIST + "; [ -f $P ] || printf '%s\\n' "
                + q(plist(ENV_LABEL, ENV_SCRIPT)) + " > $P; "
                + "launchctl print system/" + ENV_LABEL + " >/dev/null 2>&1"
                + " || launchctl bootstrap system $P >/dev/null 2>&1; "
                + line;
    }

    /**
     * 删除环境变量：从重放脚本里去掉该行并 {@code unsetenv}。
     *
     * @param name 已校验的变量名
     * @return 命令行
     */
    @Override
    protected String envDeleteCommand(String name) {
        return "S=" + ENV_SCRIPT + "; [ -f $S ] && { grep -v " + q("^launchctl setenv " + name + " ")
                + " $S > $S.tmp && mv $S.tmp $S; }; launchctl unsetenv " + name;
    }

    /**
     * 读取环境变量：{@code launchctl getenv} 读的是 launchd 真实持有的值，
     * 与 {@code setenv} 写在同一个上下文，不会出现「读了文件就当生效」。
     *
     * @param name 已校验的变量名
     * @return 变量值，不存在返回 null
     */
    @Override
    protected String readEnvValue(String name) {
        ShellSupport.ProbeResult result = run("launchctl getenv " + name + " 2>/dev/null");
        String out = result.output();
        if (out == null || out.isBlank()) {
            return null;
        }
        return out.trim();
    }

    /* ---------- 内部工具 ---------- */

    /**
     * macOS 系统配置定义。
     *
     * @param key     配置键（Darwin sysctl 名）
     * @param label   展示名
     * @param unit    单位
     * @param scope   作用域
     * @param confirm 修改前是否需要二次确认
     * @param tip     操作提示
     * @param group   分组
     * @param type    控件类型
     * @param options 枚举候选
     */
    private record MacSysctl(String key, String label, String unit, String scope, boolean confirm,
                             String tip, String group, String type, String options) {
    }

    /**
     * macOS 资源限制定义。
     *
     * @param key        配置键
     * @param label      展示名
     * @param unit       单位
     * @param scope      作用域
     * @param tip        操作提示
     * @param launchdKey {@code maxfiles} / {@code maxproc}
     * @param kind       {@code soft} / {@code hard}
     */
    private record MacLimit(String key, String label, String unit, String scope, String tip,
                            String launchdKey, String kind) {
    }

    /**
     * Darwin 系统配置白名单：只收录真实存在且可写的键。
     */
    private static final List<MacSysctl> MAC_SYSCTLS = List.of(
            new MacSysctl("kern.maxfiles", "全局文件句柄上限", "个", "内核", true,
                    "全系统可打开的文件描述符总数。改大立即生效；过小会让新进程拿不到句柄。"
                            + "回退：改回原值，或编辑 /etc/sysctl.conf 删除对应行。",
                    "文件与句柄", "int", ""),
            new MacSysctl("kern.maxfilesperproc", "单进程文件句柄上限", "个", "内核", true,
                    "单个进程可打开的文件描述符上限，必须不大于 kern.maxfiles。",
                    "文件与句柄", "int", ""),
            new MacSysctl("kern.maxproc", "进程总数上限", "个", "内核", true,
                    "全系统进程上限；调小会导致 fork 失败，调大后新进程才用得上。",
                    "进程与线程", "int", ""),
            new MacSysctl("kern.maxprocperuid", "单用户进程上限", "个", "内核", true,
                    "单个用户可创建的进程数，必须不大于 kern.maxproc。",
                    "进程与线程", "int", ""),
            new MacSysctl("kern.ipc.somaxconn", "监听队列上限", "个", "内核", false,
                    "已完成握手等待 accept 的队列长度；高并发接入时调大。",
                    "网络与端口", "int", ""),
            new MacSysctl("kern.ipc.maxsockbuf", "套接字缓冲上限", "字节", "内核", false,
                    "单个套接字缓冲的最大字节数，大带宽长肥管道场景调大。",
                    "网络与端口", "int", ""),
            new MacSysctl("kern.ipc.nmbclusters", "网络缓冲簇数量", "个", "内核", false,
                    "网卡收包缓冲簇总数；丢包且伴随内存不足时调大。",
                    "网络与端口", "int", ""),
            new MacSysctl("net.inet.tcp.sendspace", "TCP 发送缓冲", "字节", "内核", false,
                    "新建 TCP 连接的默认发送缓冲大小。",
                    "网络与端口", "int", ""),
            new MacSysctl("net.inet.tcp.recvspace", "TCP 接收缓冲", "字节", "内核", false,
                    "新建 TCP 连接的默认接收缓冲大小。",
                    "网络与端口", "int", ""),
            new MacSysctl("net.inet.tcp.msl", "TIME_WAIT 时长", "毫秒", "内核", false,
                    "最大报文存活时间，决定 TIME_WAIT 停留时长（2×msl）。",
                    "网络与端口", "int", ""),
            new MacSysctl("net.inet.tcp.keepidle", "保活起始时间", "毫秒", "内核", false,
                    "空闲多久后开始发送保活探测。",
                    "网络与端口", "int", ""),
            new MacSysctl("net.inet.tcp.keepintvl", "保活探测间隔", "毫秒", "内核", false,
                    "两次保活探测之间的间隔。",
                    "网络与端口", "int", ""),
            new MacSysctl("net.inet.tcp.keepcnt", "保活探测次数", "次", "内核", false,
                    "判定连接失效前发送的保活探测次数。",
                    "网络与端口", "int", ""),
            new MacSysctl("net.inet.tcp.blackhole", "静默丢弃无效连接", "级别", "内核", false,
                    "对不存在的端口/连接的响应方式：0 正常回 RST，1 丢弃入站 SYN，2 全部丢弃。",
                    "安全与权限", "enum", "0=正常回应;1=丢弃入站;2=全部丢弃"),
            new MacSysctl("net.inet.tcp.log_in_vain", "记录无效连接", "开关", "内核", false,
                    "是否记录发往未监听端口的连接尝试。",
                    "诊断", "enum", "0=关闭;1=开启"),
            new MacSysctl("net.inet.udp.maxdgram", "UDP 最大报文", "字节", "内核", false,
                    "单个 UDP 数据报的最大长度。",
                    "网络与端口", "int", ""),
            new MacSysctl("net.inet.ip.forwarding", "IP 转发", "开关", "内核", true,
                    "开启后本机作为路由器转发 IPv4 报文；非路由用途不要开启。",
                    "安全与权限", "enum", "0=关闭;1=开启"),
            new MacSysctl("net.inet.ip.portrange.first", "临时端口起始", "端口", "内核", false,
                    "主动连接使用的临时端口范围下界。",
                    "网络与端口", "int", ""),
            new MacSysctl("net.inet.ip.portrange.last", "临时端口结束", "端口", "内核", false,
                    "主动连接使用的临时端口范围上界。",
                    "网络与端口", "int", ""),
            new MacSysctl("net.inet.icmp.icmplim", "ICMP 限速", "包/秒", "内核", false,
                    "每秒允许发送的 ICMP 不可达包数量。",
                    "网络与端口", "int", ""),
            new MacSysctl("kern.sysv.shmmax", "共享内存段上限", "字节", "内核", false,
                    "单个 System V 共享内存段的最大字节数。",
                    "内存与文件系统", "int", ""),
            new MacSysctl("kern.sysv.shmall", "共享内存总页数", "页", "内核", false,
                    "System V 共享内存可用的总页数。",
                    "内存与文件系统", "int", ""),
            new MacSysctl("kern.sysv.shmmni", "共享内存段数量", "个", "内核", false,
                    "System V 共享内存段的最大数量。",
                    "内存与文件系统", "int", ""),
            new MacSysctl("kern.sysv.semmni", "信号量集数量", "个", "内核", false,
                    "System V 信号量集的最大数量。",
                    "进程与线程", "int", ""),
            new MacSysctl("kern.sysv.semmns", "信号量总数", "个", "内核", false,
                    "全系统信号量总数上限。",
                    "进程与线程", "int", ""),
            new MacSysctl("kern.sysv.semmsl", "单集信号量数", "个", "内核", false,
                    "单个信号量集内的信号量数量上限。",
                    "进程与线程", "int", ""),
            new MacSysctl("kern.sysv.semopm", "单次可操作信号量", "个", "内核", false,
                    "单次 semop 调用可操作的信号量数量上限。",
                    "进程与线程", "int", ""));

    /**
     * launchd 资源限制白名单：与 Linux 侧 limits.nofile / limits.nproc 同名，便于台账统一。
     */
    private static final List<MacLimit> MAC_LIMITS = List.of(
            new MacLimit("limits.nofile.soft", "文件句柄软限制", "个", "用户会话",
                    "当前 launchd 上下文对后续进程生效；已运行进程不受影响。"
                            + "开机由 " + LIMIT_SCRIPT + " 重放，回退：改回原值。",
                    "maxfiles", "soft"),
            new MacLimit("limits.nofile.hard", "文件句柄硬限制", "个", "用户会话",
                    "硬限制是软限制的上界，只有 root 能抬高。",
                    "maxfiles", "hard"),
            new MacLimit("limits.nproc.soft", "进程数软限制", "个", "用户会话",
                    "单个用户可创建的进程数软限制。",
                    "maxproc", "soft"),
            new MacLimit("limits.nproc.hard", "进程数硬限制", "个", "用户会话",
                    "单个用户可创建的进程数硬限制，只有 root 能抬高。",
                    "maxproc", "hard")
    );

    /**
     * 按配置键查 sysctl 定义。
     *
     * @param key 配置键
     * @return 定义，未收录返回 null
     */
    private static MacSysctl findSysctl(String key) {
        for (MacSysctl spec : MAC_SYSCTLS) {
            if (spec.key().equals(key)) {
                return spec;
            }
        }
        return null;
    }

    /**
     * 按配置键查资源限制定义。
     *
     * @param key 配置键
     * @return 定义，未收录返回 null
     */
    private static MacLimit findLimit(String key) {
        for (MacLimit spec : MAC_LIMITS) {
            if (spec.key().equals(key)) {
                return spec;
            }
        }
        return null;
    }

    /**
     * 校验配置值是否符合该项的类型约束。
     *
     * @param spec  配置定义
     * @param value 待写入值
     * @return 不合法时的原因，合法返回 null
     */
    private static String validateValue(MacSysctl spec, String value) {
        String v = value == null ? "" : value.trim();
        String type = spec.type();
        if ("int".equals(type)) {
            if (!v.matches("^\\d{1,12}$")) {
                return spec.label() + " 必须是非负整数: " + v;
            }
            return null;
        }
        if ("enum".equals(type)) {
            for (String option : spec.options().split(";")) {
                if (option.startsWith(v + "=")) {
                    return null;
                }
            }
            return spec.label() + " 取值必须是 " + spec.options();
        }
        if (v.isEmpty()) {
            return spec.label() + " 不能为空";
        }
        return null;
    }

    /**
     * 生成只运行一行脚本的 LaunchDaemon plist（单行 XML，避免多行写入）。
     *
     * @param label  作业标签
     * @param script 脚本路径
     * @return plist 文本
     */
    private static String plist(String label, String script) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\""
                + " \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">"
                + "<plist version=\"1.0\"><dict>"
                + "<key>Label</key><string>" + label + "</string>"
                + "<key>ProgramArguments</key><array><string>/bin/sh</string>"
                + "<string>-c</string><string>" + script + "</string></array>"
                + "<key>RunAtLoad</key><true/>"
                + "<key>KeepAlive</key><false/>"
                + "</dict></plist>";
    }

    /**
     * {@code iptables} 风格端口文本 → pf 端口表达式。
     *
     * <p>逗号列表在 pf 里要写成集合 {@code { 80, 443 }}；区间沿用 {@code 8000:9000}。</p>
     *
     * @param raw 原始端口文本
     * @return pf 端口表达式，空串表示不限端口
     */
    private static String pfPort(String raw) {
        String digits = raw.replaceAll("[^0-9,:\\-]", "");
        if (digits.isEmpty()) {
            return "";
        }
        String normalized = digits.replace('-', ':');
        if (normalized.contains(",")) {
            return "{ " + normalized.replace(",", ", ") + " }";
        }
        return normalized.contains(":") ? normalized : "= " + normalized;
    }

    /**
     * 解析 pf 规则文本。
     *
     * @param output {@code pfctl -a ... -sr} 输出
     * @return 规则列表
     */
    private static List<FirewallRuleRowVO> parsePfRules(String output) {
        List<FirewallRuleRowVO> rules = new ArrayList<>();
        if (output == null || output.isBlank()) {
            return rules;
        }
        int num = 0;
        for (String line : output.split("\\r?\\n")) {
            String rule = line.trim();
            if (rule.isEmpty() || rule.startsWith("#")) {
                continue;
            }
            num++;
            String[] tokens = rule.split("\\s+");
            String action = tokens[0];
            String direction = "";
            String proto = "";
            String source = "";
            String destination = "";
            String port = "";
            for (int i = 1; i < tokens.length; i++) {
                String token = tokens[i];
                if ("in".equals(token) || "out".equals(token)) {
                    direction = token;
                } else if ("proto".equals(token) && i + 1 < tokens.length) {
                    proto = tokens[++i];
                } else if ("from".equals(token) && i + 1 < tokens.length) {
                    source = tokens[++i];
                } else if ("to".equals(token) && i + 1 < tokens.length) {
                    destination = tokens[++i];
                } else if ("port".equals(token) && i + 1 < tokens.length) {
                    String next = tokens[i + 1];
                    if ("=".equals(next) && i + 2 < tokens.length) {
                        port = tokens[i + 2];
                        i += 2;
                    } else {
                        port = next;
                        i++;
                    }
                }
            }
            String directionText = "in".equals(direction) ? "入站" : ("out".equals(direction) ? "出站" : "");
            String actionText = "block".equalsIgnoreCase(action) ? "阻止" : "允许";
            rules.add(new FirewallRuleRowVO(
                    "#" + num,
                    PF_ANCHOR,
                    directionText,
                    actionText,
                    "是",
                    "pf",
                    num,
                    proto,
                    port,
                    "",
                    actionText,
                    source.isEmpty() ? "any" : source,
                    destination.isEmpty() ? "any" : destination,
                    rule));
        }
        return rules;
    }

    /**
     * 从 kextstat 的依赖段里取内核扩展名。
     *
     * @param tail 依赖段原文，形如 {@code <... 1 2>}
     * @return 依赖名，用逗号分隔；无依赖返回空串
     */
    private static String dependsOn(String tail) {
        if (tail == null || tail.isBlank()) {
            return "";
        }
        Matcher matcher = Pattern.compile("<([^>]*)>").matcher(tail);
        if (!matcher.find()) {
            return "";
        }
        String inside = matcher.group(1).trim();
        if (inside.isEmpty()) {
            return "";
        }
        StringBuilder names = new StringBuilder();
        String[] parts = inside.split("\\s+");
        for (String part : parts) {
            if (part.matches("^\\d+$")) {
                continue;
            }
            if (names.length() > 0) {
                names.append(',');
            }
            names.append(part);
        }
        return names.toString();
    }

    /**
     * 十六进制字节数转十进制；{@code 0x0} 之类表示「未占用」时返回 0。
     *
     * @param text 十六进制文本
     * @return 字节数，解析失败返回 -1
     */
    private static long hexToLong(String text) {
        try {
            return Long.parseLong(text.trim().replaceFirst("^0[xX]", ""), 16);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /**
     * 点分掩码转换：允许传前缀长度（1~32）。
     *
     * @param mask 掩码或前缀
     * @return 点分掩码，为空或非法时按 255.255.255.0
     */
    private static String dottedMask(String mask) {
        if (mask == null || mask.isBlank()) {
            return "255.255.255.0";
        }
        String text = mask.trim();
        if (text.matches("^\\d{1,2}$")) {
            int prefix = Integer.parseInt(text);
            if (prefix >= 0 && prefix <= 32) {
                int value = prefix == 0 ? 0 : (0xffffffff << (32 - prefix));
                return ((value >>> 24) & 0xff) + "." + ((value >>> 16) & 0xff) + "."
                        + ((value >>> 8) & 0xff) + "." + (value & 0xff);
            }
        }
        return text.matches(IPV4) ? text : "255.255.255.0";
    }

    /**
     * 单引号包裹，内部单引号按 shell 规则转义。
     *
     * @param text 原文
     * @return 可直接拼进命令的文本
     */
    private static String q(String text) {
        return "'" + (text == null ? "" : text.replace("'", "'\\''")) + "'";
    }

    /**
     * 取整数，非数字返回 -1。
     *
     * @param text 文本
     * @return 整数
     */
    private static long parseLong(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /**
     * 取整数，非数字返回 -1。
     *
     * @param text 文本
     * @return 整数
     */
    private static int parseInt(String text) {
        long value = parseLong(text);
        return value > Integer.MAX_VALUE ? -1 : (int) value;
    }

    /**
     * 取浮点数，非数字返回 0。
     *
     * @param text 文本
     * @return 浮点数
     */
    private static double parseDouble(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
