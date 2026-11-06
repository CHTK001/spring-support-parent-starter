// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.monitor.support.spi.impl;

import com.chua.starter.monitor.support.enums.ServiceAction;
import com.chua.starter.monitor.support.model.DeletedFileRowVO;
import com.chua.starter.monitor.support.model.DeletedFilesVO;
import com.chua.starter.monitor.support.model.DiskPartitionRowVO;
import com.chua.starter.monitor.support.model.DriverRowVO;
import com.chua.starter.monitor.support.model.FirewallRuleInputVO;
import com.chua.starter.monitor.support.model.FirewallRuleRowVO;
import com.chua.starter.monitor.support.model.HostActionVO;
import com.chua.starter.monitor.support.model.InodeUsageVO;
import com.chua.starter.monitor.support.model.MountRowVO;
import com.chua.starter.monitor.support.model.NetworkConfigVO;
import com.chua.starter.monitor.support.model.ProcessThreadRowVO;
import com.chua.starter.monitor.support.model.SystemConfigItemVO;
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

    /**
     * 根分区 inode 使用情况：Linux 走 {@code df -i}，macOS 同样支持该参数。
     *
     * <p>优先取挂载点为 {@code /} 的一行（页面看的就是根盘）；根盘缺失时退化为使用率最高的一行。
     * {@code tmpfs} 之类的行 inode 数为 {@code -}，解析后 total 为 0，直接跳过。</p>
     */
    @Override
    public InodeUsageVO inodeUsage() {
        return parseInodes(output("df -i 2>/dev/null"));
    }

    /* ---------- 挂载盘（df -T，回退 df -k） ---------- */

    /**
     * 当前挂载列表。
     *
     * <p>优先用 {@code df -kT}：/proc/mounts 只有设备与挂载点，拿不到容量；
     * {@code df} 一行同时给出设备、类型、容量与挂载点，正好是挂载页需要的字段。
     * 老系统 / BSD 没有 {@code -T}，回退 {@code df -k}（文件系统类型留空）。</p>
     */
    @Override
    public List<MountRowVO> mounts() {
        return parseMounts(output("df -kT 2>/dev/null || df -k"));
    }

    @Override
    public HostActionVO mount(String source, String target, String fsType) {
        String src = cleanArg(source);
        String dst = cleanArg(target);
        if (src.isEmpty() || dst.isEmpty()) {
            return HostActionVO.fail("挂载需要设备与挂载点");
        }
        String type = cleanArg(fsType).replaceAll("[^A-Za-z0-9_.+-]", "");
        StringBuilder cmd = new StringBuilder("mkdir -p ").append(shQuote(dst)).append(" && mount");
        if (!type.isEmpty()) {
            cmd.append(" -t ").append(type);
        }
        cmd.append(' ').append(shQuote(src)).append(' ').append(shQuote(dst));
        ShellSupport.ProbeResult result = run(cmd.toString());
        return result.failed()
                ? HostActionVO.fail("挂载失败" + detail(result))
                : HostActionVO.ok("已挂载 " + src + " 到 " + dst + detail(result));
    }

    @Override
    public HostActionVO unmount(String target) {
        String dst = cleanArg(target);
        if (dst.isEmpty()) {
            return HostActionVO.fail("缺少挂载点");
        }
        ShellSupport.ProbeResult result = run("umount " + shQuote(dst));
        return result.failed()
                ? HostActionVO.fail("卸载失败" + detail(result))
                : HostActionVO.ok("已卸载 " + dst + detail(result));
    }

    /* ---------- 已删除但仍被占用的文件（lsof +L1） ---------- */

    /**
     * 用 {@code lsof +L1} 找出 link count 为 0 的打开文件。
     *
     * <p>用 {@code -F} 字段模式而不是默认表格：文件名可能含空格与制表符，
     * 按列切分会把路径切碎；字段模式每行一个 {@code 标识+值}，解析稳定。</p>
     */
    @Override
    public DeletedFilesVO deletedFiles() {
        ShellSupport.ProbeResult result = run("lsof +L1 -n -P -F pRclfns 2>/dev/null");
        String out = result.output();
        if (out == null || out.isBlank()) {
            String why = reason(result);
            if (why.contains("not found") || why.contains("No such file")) {
                return DeletedFilesVO.unsupported("目标机未安装 lsof，无法枚举已删除但仍被占用的文件；"
                        + "安装后重试（apt install lsof / yum install lsof）");
            }
            // lsof 没有任何匹配时退出码非 0，这是「确实没有」而不是失败
            return DeletedFilesVO.of(List.of());
        }
        return DeletedFilesVO.of(parseDeletedFiles(out));
    }

    @Override
    public HostActionVO releaseDeletedFile(long pid, int fd) {
        if (pid <= 0 || fd < 0) {
            return HostActionVO.fail("缺少进程号或文件描述符");
        }
        String path = "/proc/" + pid + "/fd/" + fd;
        ShellSupport.ProbeResult result = run("truncate -s 0 " + path + " 2>/dev/null || : > " + path);
        if (result.failed()) {
            return HostActionVO.fail("释放 " + path + " 失败（进程可能已退出或权限不足）" + detail(result));
        }
        String size = output("stat -L -c %s " + path + " 2>/dev/null");
        if (size != null && !size.isBlank() && !"0".equals(size.trim())) {
            return HostActionVO.fail("已截断但文件仍占用 " + size.trim() + " 字节，进程可能正在持续写入");
        }
        return HostActionVO.ok("已释放 pid " + pid + " 的 fd " + fd + " 所占空间" + detail(result));
    }

    /* ---------- 系统配置（/proc/sys） ---------- */

    @Override
    public List<SystemConfigItemVO> systemConfigs() {
        StringBuilder cmd = new StringBuilder();
        for (SysctlSpec spec : SYSCTL_SPECS) {
            cmd.append("printf '").append(spec.key()).append("='; cat /proc/sys/")
                    .append(spec.key().replace('.', '/')).append(" 2>/dev/null; printf '\\n'; ");
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
        for (SysctlSpec spec : SYSCTL_SPECS) {
            String value = values.get(spec.key());
            if (value == null || value.isBlank()) {
                continue;
            }
            items.add(new SystemConfigItemVO(
                    spec.key(),
                    spec.label(),
                    value,
                    spec.unit(),
                    spec.scope(),
                    "sysctl " + spec.key() + "（/proc/sys/" + spec.key().replace('.', '/') + "）",
                    true,
                    true,
                    spec.confirm(),
                    spec.tip()));
        }
        return items;
    }

    @Override
    public HostActionVO applySystemConfig(String key, String value) {
        SysctlSpec spec = sysctlSpec(key);
        if (spec == null) {
            return HostActionVO.fail("不支持的系统配置项: " + key);
        }
        String v = value == null ? "" : value.trim();
        if (!v.matches("^-?\\d{1,12}$")) {
            return HostActionVO.fail("配置值必须是整数: " + v);
        }
        ShellSupport.ProbeResult result = run("sysctl -w " + spec.key() + "=" + v);
        if (result.failed()) {
            return HostActionVO.fail("写入 " + spec.key() + " 失败" + detail(result));
        }
        // 持久化：sysctl -w 只改运行时，重启后丢失
        String file = "/etc/sysctl.d/99-ms-monitor.conf";
        String persist = "mkdir -p /etc/sysctl.d && { grep -v '^" + spec.key().replace(".", "\\.")
                + "[ =]' " + file + " 2>/dev/null; echo '" + spec.key() + "=" + v + "'; } "
                + "> /tmp/ms-sysctl.conf && mv /tmp/ms-sysctl.conf " + file;
        ShellSupport.ProbeResult persisted = run(persist);
        String suffix = persisted.failed() ? "（本次已生效，但写入 " + file + " 失败，重启后会回退）" : "";
        String got = output("sysctl -n " + spec.key() + " 2>/dev/null");
        if (got == null || !v.equals(got.trim())) {
            return HostActionVO.fail(spec.key() + " 写入后回读不一致（期望 " + v + "，实际 "
                    + (got == null ? "空" : got.trim()) + "）" + detail(result));
        }
        return HostActionVO.ok(spec.key() + " 已改为 " + v + suffix);
    }

    /**
     * 按 key 找系统配置定义。
     *
     * @param key 配置键
     * @return 定义，未收录返回 null
     */
    private static SysctlSpec sysctlSpec(String key) {
        if (key == null) {
            return null;
        }
        String k = key.trim();
        for (SysctlSpec spec : SYSCTL_SPECS) {
            if (spec.key().equals(k)) {
                return spec;
            }
        }
        return null;
    }

    /**
     * {@code df -T} / {@code df -k} 文本 → 挂载行。
     *
     * @param text 命令输出
     * @return 挂载行列表
     */
    private static List<MountRowVO> parseMounts(String text) {
        List<MountRowVO> rows = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return rows;
        }
        String[] lines = text.split("\\r?\\n");
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\s+", 7);
            if (parts.length >= 7) {
                long total = kbToBytes(parts[2]);
                long used = kbToBytes(parts[3]);
                long avail = kbToBytes(parts[4]);
                rows.add(new MountRowVO(parts[0], parts[6], parts[1], "", total, used, avail,
                        usagePercent(total, used), ""));
                continue;
            }
            // 老系统 / macOS：Filesystem 1024-blocks Used Available Capacity Mounted on
            parts = line.split("\\s+", 6);
            if (parts.length < 6) {
                continue;
            }
            long total = kbToBytes(parts[1]);
            long used = kbToBytes(parts[2]);
            long avail = kbToBytes(parts[3]);
            rows.add(new MountRowVO(parts[0], parts[5], "", "", total, used, avail,
                    usagePercent(total, used), ""));
        }
        return rows;
    }

    /**
     * 1K 块数转字节；非数字返回 -1（表示取不到，而不是 0）。
     *
     * @param text 文本
     * @return 字节数
     */
    private static long kbToBytes(String text) {
        try {
            return Long.parseLong(text.trim()) * 1024L;
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /**
     * 计算使用率（保留一位小数）。
     *
     * @param total 总量
     * @param used  已用
     * @return 百分比，取不到返回 -1
     */
    private static double usagePercent(long total, long used) {
        if (total <= 0 || used < 0) {
            return -1D;
        }
        return Math.round(used * 1000.0 / total) / 10.0;
    }

    /**
     * {@code lsof -F} 字段输出 → 已删除文件行。
     *
     * <p>字段含义：{@code p} 进程号、{@code R} 父进程号、{@code c} 命令名、
     * {@code l} 登录名、{@code f} 描述符、{@code s} 大小、{@code n} 路径。
     * 一个进程下有多个描述符，因此在遇到下一个 {@code f} / {@code p} 时才落上一行。</p>
     *
     * @param text 命令输出
     * @return 行列表
     */
    private static List<DeletedFileRowVO> parseDeletedFiles(String text) {
        List<DeletedFileRowVO> rows = new ArrayList<>();
        long pid = -1L;
        long ppid = -1L;
        String process = "";
        String user = "";
        int fd = -1;
        long size = 0L;
        String path = "";
        for (String raw : text.split("\\r?\\n")) {
            if (raw.isEmpty()) {
                continue;
            }
            char tag = raw.charAt(0);
            String value = raw.substring(1);
            switch (tag) {
                case 'p' -> {
                    // 上一个进程的最后一个描述符在这里落行：p 之后字段全部重置，
                    // 不在重置前收尾就会丢掉每个进程的最后一条记录
                    rows.add(deletedRow(pid, ppid, process, user, fd, path, size));
                    pid = parseLong(value);
                    ppid = -1L;
                    process = "";
                    user = "";
                    fd = -1;
                    size = 0L;
                    path = "";
                }
                case 'R' -> ppid = parseLong(value);
                case 'c' -> process = value.trim();
                case 'l' -> user = value.trim();
                case 'f' -> {
                    rows.add(deletedRow(pid, ppid, process, user, fd, path, size));
                    fd = numericFd(value);
                    size = 0L;
                    path = "";
                }
                case 's' -> size = parseLong(value);
                case 'n' -> path = stripDeletedSuffix(value);
                default -> {
                    // 其余字段本次未请求，忽略
                }
            }
        }
        rows.add(deletedRow(pid, ppid, process, user, fd, path, size));
        rows.removeIf(java.util.Objects::isNull);
        return rows;
    }

    /**
     * 组装一条已删除文件行；字段不全时返回 null。
     *
     * @param pid     进程号
     * @param ppid    父进程号
     * @param process 命令名
     * @param user    登录名
     * @param fd      描述符
     * @param path    路径
     * @param size    大小
     * @return 行，无法成行返回 null
     */
    private static DeletedFileRowVO deletedRow(long pid, long ppid, String process, String user,
                                               int fd, String path, long size) {
        if (pid <= 0 || fd < 0 || path == null || path.isBlank()) {
            return null;
        }
        long safeSize = Math.max(0L, size);
        return new DeletedFileRowVO(pid, ppid, process, user, fd, path, safeSize, bytesText(safeSize));
    }

    /**
     * 取描述符编号：{@code 3} / {@code 3u} / {@code 3w} 取数字，{@code cwd} 等非数字返回 -1。
     *
     * @param raw 字段值
     * @return 描述符编号
     */
    private static int numericFd(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("[^0-9].*$", "");
        if (digits.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * 去掉 lsof 在已删除路径后追加的 {@code " (deleted)"}。
     *
     * @param path 原始路径
     * @return 原始文件路径
     */
    private static String stripDeletedSuffix(String path) {
        String suffix = " (deleted)";
        return path != null && path.endsWith(suffix)
                ? path.substring(0, path.length() - suffix.length())
                : path;
    }

    /**
     * 字节数转可读容量。
     *
     * @param bytes 字节数
     * @return 形如 {@code 12.3 MiB}
     */
    private static String bytesText(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
        int unit = 0;
        while (value >= 1024D && unit < units.length - 1) {
            value /= 1024D;
            unit++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }

    /**
     * 清洗作为命令参数的原始文本：去掉控制字符，其余由 {@code shQuote} 保底。
     *
     * @param raw 原始文本
     * @return 清洗后的文本
     */
    private static String cleanArg(String raw) {
        return raw == null ? "" : raw.replaceAll("[\\r\\n\\u0000-\\u001F]", "").trim();
    }

    /**
     * 可热更新的 sysctl 配置定义。
     *
     * @param key     配置键
     * @param label   展示名
     * @param unit    单位
     * @param scope   作用域
     * @param confirm 修改前是否需要二次确认
     * @param tip     操作提示
     */
    private record SysctlSpec(String key, String label, String unit, String scope, boolean confirm, String tip) {
    }

    /**
     * 系统配置白名单：只收录真实存在、可写、且改动风险可控的项。
     */
    private static final List<SysctlSpec> SYSCTL_SPECS = List.of(
            new SysctlSpec("fs.file-max", "全局文件句柄上限", "个", "内核", true,
                    "系统级最大打开文件数。改大立即生效；过小会让新进程拿不到句柄。"
                            + "回退：改回原值，或编辑 /etc/sysctl.d/99-ms-monitor.conf 删除对应行。"),
            new SysctlSpec("fs.inotify.max_user_watches", "inotify 监视上限", "个", "内核", false,
                    "单个用户可注册的文件监视数。前端构建 / 日志采集报 ENOSPC 时调大，立即生效。"),
            new SysctlSpec("kernel.pid_max", "进程号上限", "个", "内核", true,
                    "系统可分配的最大 PID。调小不会回收已存在的进程，重启后才完全生效。"),
            new SysctlSpec("net.core.somaxconn", "监听队列上限", "个", "内核", false,
                    "已完成握手的连接排队上限。高并发服务调大可减少连接被丢弃。"),
            new SysctlSpec("vm.swappiness", "交换倾向", "0-100", "内核", false,
                    "内核把匿名页换出到 swap 的倾向：0 更倾向保留内存，100 更积极换出。"));


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
            rules.add(new FirewallRuleRowVO(
                    null,
                    chain,
                    null,
                    null,
                    null,
                    null,
                    (int) parseLong(parts[0]),
                    part(parts, 2),
                    null,
                    null,
                    part(parts, 1),
                    part(parts, 4),
                    part(parts, 5),
                    part(parts, 6)));
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
            drivers.add(new DriverRowVO(
                    parts[0],
                    null,
                    parseLong(parts[1]),
                    (int) parseLong(parts[2]),
                    part(parts, 3),
                    null,
                    null,
                    null,
                    null,
                    null));
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
            threads.add(new ProcessThreadRowVO(
                    parseLong(parts[0]),
                    parseDouble(parts[1]),
                    parseDouble(parts[2]),
                    parts[3],
                    parts.length > 4 ? parts[4] : "",
                    "",
                    "",
                    parts.length > 5 ? parts[5] : ""));
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
            partitions.add(new DiskPartitionRowVO(
                    parts[0],
                    parts[1],
                    parts[2],
                    parts[3],
                    parts[4],
                    parts[5],
                    parts[6]));
        }
        return partitions;
    }

    /**
     * {@code df -i} 文本 → 根分区 inode 使用情况。
     *
     * @param output 命令输出
     * @return inode 使用情况，解析失败时为 unknown
     */
    private static InodeUsageVO parseInodes(String output) {
        if (output == null || output.isBlank()) {
            return InodeUsageVO.unknown();
        }
        InodeUsageVO root = null;
        InodeUsageVO highest = null;
        String[] lines = output.split("\\r?\\n");
        for (int i = 1; i < lines.length; i++) {
            String[] parts = lines[i].trim().split("\\s+", 6);
            if (parts.length < 6) {
                continue;
            }
            long total = parseLong(parts[1]);
            long used = parseLong(parts[2]);
            if (total <= 0) {
                continue;
            }
            double percent = Math.round(used * 1000.0 / total) / 10.0;
            InodeUsageVO row = new InodeUsageVO(used, total, percent, parts[5]);
            if ("/".equals(parts[5])) {
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