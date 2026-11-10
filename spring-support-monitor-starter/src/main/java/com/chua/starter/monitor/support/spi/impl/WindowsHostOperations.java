// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.monitor.support.spi.impl;

import com.chua.starter.monitor.support.enums.ServiceAction;
import com.chua.starter.monitor.support.enums.ShellDialect;
import com.chua.starter.monitor.support.model.DiskPartitionRowVO;
import com.chua.starter.monitor.support.model.DriverRowVO;
import com.chua.starter.monitor.support.model.DeletedFilesVO;
import com.chua.starter.monitor.support.model.FirewallRuleInputVO;
import com.chua.starter.monitor.support.model.FirewallRuleRowVO;
import com.chua.starter.monitor.support.model.HostActionVO;
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
 * Windows 平台的动作与采集命令。
 *
 * <p>脚本按通道方言承载：本机/Agent 通道跑在 cmd.exe 上，脚本要塞进
 * {@code powershell -Command "..."} 的引号里，因此脚本内不得出现双引号（需要引号时用
 * {@code [char]34} 现拼）；SSH/WinRM 通道本身以 PowerShell 承载，脚本直接下发。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class WindowsHostOperations extends HostOperations {

    /**
     * @param channel 目标主机所在通道
     * @param host    目标主机
     */
    public WindowsHostOperations(SystemInfoProvider channel, MsServerHost host) {
        super(channel, host);
    }

    @Override
    protected boolean processAlive(int pid) {
        String out = run("tasklist /FI \"PID eq " + pid + "\" /NH").output();
        return out != null && out.contains(String.valueOf(pid));
    }

    @Override
    protected String killCommand(int pid) {
        return "taskkill /F /PID " + pid;
    }

    @Override
    protected String serviceCommand(String name, ServiceAction action) {
        String verb = action == ServiceAction.START ? "Start-Service"
                : action == ServiceAction.STOP ? "Stop-Service" : "Restart-Service";
        return ps(verb + " -Name " + name + (action == ServiceAction.START ? " -PassThru" : " -Force"));
    }

    @Override
    protected String serviceState(String name) {
        String out = run(ps("$s = Get-Service -Name '" + name + "' -ErrorAction SilentlyContinue; "
                + "if ($s) { $s.Status } else { 'MISSING' }")).output();
        if (out == null || out.isBlank()) {
            return "UNKNOWN";
        }
        String text = out.trim();
        if (text.contains("MISSING")) {
            return "MISSING";
        }
        String state = lastLine(text);
        if ("Running".equalsIgnoreCase(state)) {
            return "RUNNING";
        }
        return "Stopped".equalsIgnoreCase(state) ? "STOPPED" : "UNKNOWN";
    }

    /**
     * 重启进程：读回原命令行后杀掉，再用 {@code Win32_Process.Create} 以原命令拉起。
     *
     * <p>命令行读不到时回落到映像路径；两者都读不到（受保护进程）直接失败，不凭映像名猜参数。</p>
     *
     * <p>必须先确认原进程已消失再拉起：{@code Stop-Process} 因权限不足失败时若照样 Create，
     * 目标机上就会留下两份实例。</p>
     */
    @Override
    public HostActionVO restartProcess(int pid) {
        ShellSupport.ProbeResult result = run(ps(restartScript(pid)));
        if (result.failed()) {
            return HostActionVO.fail("进程 " + pid + " 重启失败" + detail(result));
        }
        return HostActionVO.ok("进程 " + pid + " 已重启", newPid(result.output()));
    }

    @Override
    public HostActionVO applyNetworkConfig(NetworkConfigVO config) {
        return HostActionVO.fail("网络配置仅支持 NetworkManager 主机（Linux），目标主机不适用");
    }

    @Override
    public List<FirewallRuleRowVO> firewallRules() {
        // 先确认防火墙服务在跑：MpsSvc 停止时 Get-NetFirewallRule 只报模糊的 RPC(endpoint mapper) 错误，
        // netsh 也同样依赖该服务。不自动启动 —— 防火墙起来后可能按默认策略拦截端口、锁死远程访问。
        String svc = run(ps("(Get-Service MpsSvc -ErrorAction SilentlyContinue).Status")).output();
        if (svc == null || !svc.trim().equalsIgnoreCase("Running")) {
            throw new IllegalStateException("Windows 防火墙服务(MpsSvc)未运行，无法读取规则；"
                    + "请在该主机执行 net start MpsSvc 后重试（启动防火墙可能按默认策略拦截端口，请先确认放行规则）");
        }
        try {
            return WindowsProfileRows.firewallRules(powershell(WindowsProfileScripts.FIREWALL));
        } catch (IllegalStateException e) {
            throw new IllegalStateException("读取防火墙规则失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<DriverRowVO> drivers() {
        return WindowsProfileRows.drivers(powershell(WindowsProfileScripts.DRIVER));
    }

    @Override
    public List<ProcessThreadRowVO> processThreads(int pid) {
        return WindowsProfileRows.threads(powershell(WindowsProfileScripts.threads(pid)));
    }

    /**
     * 系统句柄总数：与任务管理器「句柄数」同口径，取各进程 HandleCount 之和。
     *
     * <p>显式转 {@code [long]}：{@code Measure-Object -Sum} 回的是 double，大数会被 PowerShell
     * 用科学计数法打印（如 {@code 1.23456E+05}），直接取首个整数会只读到 {@code 1}。</p>
     */
    @Override
    public long handleCount() {
        return firstInt(output(ps("[long]((Get-Process | Measure-Object -Property HandleCount -Sum).Sum)")));
    }

    /* ---------- 系统配置（注册表 + 实时统计） ---------- */

    /**
     * 读取本平台全部系统配置项。
     *
     * <p>一次 PowerShell 脚本拿全量：注册表项按「路径 + 值名」读 DWORD / 字符串，
     * 读不到（键不存在）的项直接跳过，不编造默认值；句柄数、进程数等实时统计项
     * 走只读表达式，与任务管理器同口径。</p>
     */
    @Override
    public List<SystemConfigItemVO> systemConfigs() {
        StringBuilder script = new StringBuilder("$ErrorActionPreference='SilentlyContinue';");
        for (WinSpec spec : SYSTEM_CONFIG_SPECS) {
            script.append("'").append(spec.key()).append("=' + [string](")
                    .append(readExpression(spec)).append(");");
        }
        String out = powershell(script.toString());
        java.util.Map<String, String> values = new java.util.HashMap<>();
        if (out != null) {
            for (String line : out.split("\\r?\\n")) {
                int eq = line.indexOf('=');
                if (eq > 0) {
                    values.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                }
            }
        }
        List<SystemConfigItemVO> items = new ArrayList<>();
        for (WinSpec spec : SYSTEM_CONFIG_SPECS) {
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
                    spec.regPath() == null
                            ? "实时统计（PowerShell " + readExpression(spec) + "）"
                            : "注册表 " + spec.regPath() + "\\" + spec.regName(),
                    spec.regPath() != null,
                    spec.hot(),
                    spec.confirm(),
                    spec.tip(),
                    spec.group(),
                    spec.type(),
                    spec.options()));
        }
        return items;
    }

    @Override
    public HostActionVO applySystemConfig(String key, String value) {
        WinSpec spec = winSpec(key);
        if (spec == null) {
            return HostActionVO.fail("不支持的系统配置项: " + key);
        }
        if (spec.regPath() == null) {
            return HostActionVO.fail(spec.label() + " 是实时统计项，只读");
        }
        String v = value == null ? "" : value.trim();
        String invalid = validateWindowsValue(spec, v);
        if (invalid != null) {
            return HostActionVO.fail(invalid);
        }
        String psValue = "bool".equals(spec.type()) ? ("1".equals(v) ? "$true" : "$false") : v;
        String type = "text".equals(spec.type()) ? "String" : "DWord";
        String script = "$p='" + spec.regPath() + "';Set-ItemProperty -Path $p -Name '"
                + spec.regName() + "' -Value " + psValue + " -Type " + type
                + ";[string]((Get-ItemProperty -Path $p -Name '" + spec.regName()
                + "' -ErrorAction SilentlyContinue)." + spec.regName() + ")";
        ShellSupport.ProbeResult result = run(ps(script));
        if (result.failed()) {
            return HostActionVO.fail("写入 " + spec.label() + " 失败（需要管理员权限）" + detail(result));
        }
        String got = lastLine(result.output() == null ? "" : result.output());
        String expected = "bool".equals(spec.type()) ? ("1".equals(v) ? "True" : "False") : v;
        if (!expected.equalsIgnoreCase(got) && !expected.equals(got)) {
            return HostActionVO.fail(spec.label() + " 写入后回读不一致（期望 " + expected
                    + "，实际 " + (got.isEmpty() ? "空" : got) + "）");
        }
        return HostActionVO.ok(spec.label() + " 已改为 " + v
                + (spec.hot() ? "（立即生效）" : "（已写入注册表，重启后生效）"));
    }

    /**
     * 按 key 找 Windows 系统配置定义。
     *
     * @param key 配置键
     * @return 定义，未收录返回 null
     */
    private static WinSpec winSpec(String key) {
        if (key == null) {
            return null;
        }
        String k = key.trim();
        for (WinSpec spec : SYSTEM_CONFIG_SPECS) {
            if (spec.key().equals(k)) {
                return spec;
            }
        }
        return null;
    }

    /**
     * 生成某项的读取表达式：注册表项取属性值，实时统计项直接用配置的表达式。
     *
     * @param spec 配置定义
     * @return PowerShell 表达式（不含双引号）
     */
    private static String readExpression(WinSpec spec) {
        if (spec.regPath() == null) {
            return spec.readExpr();
        }
        return "(Get-ItemProperty -Path '" + spec.regPath() + "' -Name '" + spec.regName()
                + "' -ErrorAction SilentlyContinue)." + spec.regName();
    }

    /**
     * 校验 Windows 配置值。
     *
     * @param spec  配置定义
     * @param value 待写入值
     * @return 不合法时的原因，合法返回 null
     */
    private static String validateWindowsValue(WinSpec spec, String value) {
        String v = value == null ? "" : value.trim();
        if ("int".equals(spec.type())) {
            if (!v.matches("^-?\\d{1,12}$")) {
                return spec.label() + " 必须是整数: " + v;
            }
            long n;
            try {
                n = Long.parseLong(v);
            } catch (NumberFormatException e) {
                return spec.label() + " 数值越界: " + v;
            }
            if (n < spec.min() || n > spec.max()) {
                return spec.label() + " 取值范围 " + spec.min() + " ~ " + spec.max() + ": " + v;
            }
            return null;
        }
        if ("bool".equals(spec.type()) || "enum".equals(spec.type())) {
            return spec.options().contains(v + "=") ? null
                    : spec.label() + " 取值必须是 " + spec.options() + ": " + v;
        }
        return v.matches("^[0-9A-Za-z_.:\\-\\\\ ]{1,256}$") ? null
                : spec.label() + " 含非法字符（只允许字母、数字、路径分隔符、点、冒号、下划线与空格）";
    }

    /**
     * 系统配置定义。
     *
     * @param key      配置键
     * @param group    分组
     * @param label    展示名
     * @param unit     单位
     * @param scope    作用域
     * @param type     控件类型：int / bool / enum / text
     * @param options  枚举候选
     * @param hot      是否立即生效
     * @param confirm  修改前是否需要二次确认
     * @param tip      操作提示
     * @param regPath  注册表路径（实时统计项为 null）
     * @param regName  注册表值名（实时统计项为 null）
     * @param readExpr 实时统计项的 PowerShell 表达式
     * @param min      整数最小值
     * @param max      整数最大值
     */
    private record WinSpec(String key, String group, String label, String unit, String scope,
                           String type, String options, boolean hot, boolean confirm, String tip,
                           String regPath, String regName, String readExpr, long min, long max) {
    }

    /**
     * 注册表型配置定义。
     *
     * @param key     配置键
     * @param group   分组
     * @param label   展示名
     * @param unit    单位
     * @param type    控件类型
     * @param options 枚举候选
     * @param hot     是否立即生效
     * @param confirm 是否需要二次确认
     * @param tip     操作提示
     * @param path    注册表路径
     * @param name    注册表值名
     * @param min     整数最小值
     * @param max     整数最大值
     * @return 配置定义
     */
    private static WinSpec reg(String key, String group, String label, String unit, String type,
                               String options, boolean hot, boolean confirm, String tip,
                               String path, String name, long min, long max) {
        return new WinSpec(key, group, label, unit, "系统", type, options, hot, confirm, tip,
                path, name, null, min, max);
    }

    /**
     * 只读实时统计项定义。
     *
     * @param key   配置键
     * @param group 分组
     * @param label 展示名
     * @param tip   操作提示
     * @param expr  PowerShell 表达式
     * @return 配置定义
     */
    private static WinSpec stat(String key, String group, String label, String tip, String expr) {
        return new WinSpec(key, group, label, "个", "系统", "int", "", true, false, tip,
                null, null, expr, 0, Long.MAX_VALUE);
    }

    private static final String TCPIP = "HKLM:\\SYSTEM\\CurrentControlSet\\Services\\Tcpip\\Parameters";
    private static final String AFD = "HKLM:\\SYSTEM\\CurrentControlSet\\Services\\AFD\\Parameters";
    private static final String LSA = "HKLM:\\SYSTEM\\CurrentControlSet\\Control\\Lsa";
    private static final String TERMINAL_SERVER = "HKLM:\\SYSTEM\\CurrentControlSet\\Control\\Terminal Server";
    private static final String MEMORY_MANAGEMENT =
            "HKLM:\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Memory Management";

    /**
     * Windows 系统配置白名单。
     *
     * <p>覆盖句柄配额、AFD 动态 backlog、TCP 端口与超时、内存管理、安全权限、
     * 崩溃转储与实时统计；注册表写入需要管理员权限。</p>
     */
    private static final List<WinSpec> SYSTEM_CONFIG_SPECS = List.of(
            stat("win.handle.total", "句柄与进程", "系统句柄总数",
                    "与任务管理器「句柄数」同口径，取全部进程 HandleCount 之和。", 
                    "[long]((Get-Process | Measure-Object -Property HandleCount -Sum).Sum)"),
            stat("win.process.count", "句柄与进程", "进程总数",
                    "当前进程数，用于判断句柄是否随进程堆积。", "(Get-Process | Measure-Object).Count"),
            stat("win.thread.count", "句柄与进程", "线程总数",
                    "全部进程的线程数之和，线程暴涨时优先查这里。",
                    "[long]((Get-Process | ForEach-Object { $_.Threads.Count } | Measure-Object -Sum).Sum)"),
            stat("win.tcp.count", "网络与端口", "TCP 连接数",
                    "netstat 统计到的 TCP 连接总数（含监听端口，v4/v6 全部）。",
                    "[long](@(netstat -ano -p tcp | Where-Object { $_.TrimStart().StartsWith('TCP') }).Count)"),
            reg("win.user.handle.quota", "句柄与进程", "每进程 USER 句柄配额", "个", "int", "",
                    false, true, "USER 对象句柄配额，默认 10000，范围 200~18000；修改后需重启。"
                            + "回退：改回原值。",
                    "HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Windows",
                    "USERProcessHandleQuota", 200, 18000),
            reg("win.gdi.handle.quota", "句柄与进程", "每进程 GDI 句柄配额", "个", "int", "",
                    false, true, "GDI 对象句柄配额，默认 10000，范围 256~65536；修改后需重启。",
                    "HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Windows",
                    "GDIProcessHandleQuota", 256, 65536),
            reg("win.critical.workers", "句柄与进程", "关键工作线程数", "个", "int", "",
                    false, false, "Executive 额外关键工作线程，高并发服务器可调大；修改后需重启。",
                    "HKLM:\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Executive",
                    "AdditionalCriticalWorkerThreads", 0, 256),
            reg("win.delayed.workers", "句柄与进程", "延迟工作线程数", "个", "int", "",
                    false, false, "Executive 额外延迟工作线程，修改后需重启。",
                    "HKLM:\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Executive",
                    "AdditionalDelayedWorkerThreads", 0, 256),
            reg("win.afd.dynamicBacklog", "网络与端口", "AFD 动态 backlog", "开关", "enum", "0=关闭;1=开启",
                    false, true, "AFD 驱动动态 backlog 总开关，开启后按连接数在最小/最大值之间伸缩。"
                            + "AFD 参数在系统启动时读取，修改后需重启；不可用时可改用注册表回退。",
                    AFD, "EnableDynamicBacklog", 0, 1),
            reg("win.afd.minBacklog", "网络与端口", "AFD 最小 backlog", "个", "int", "",
                    false, false, "动态 backlog 的最小值，连接数回落时不会低于该值；需重启。",
                    AFD, "MinimumDynamicBacklog", 0, 1000000),
            reg("win.afd.maxBacklog", "网络与端口", "AFD 最大 backlog", "个", "int", "",
                    false, false, "动态 backlog 的最大值，高并发建连场景可调大；需重启。",
                    AFD, "MaximumDynamicBacklog", 0, 1000000),
            reg("win.afd.maxActive", "网络与端口", "AFD 最大活跃连接", "个", "int", "",
                    false, false, "单 AFD 端点允许的最大活跃连接数；需重启。",
                    AFD, "MaximumActiveConnections", 0, 1000000),
            reg("win.tcp.maxUserPort", "网络与端口", "最大用户端口", "端口号", "int", "",
                    false, true, "客户端动态端口上限，默认 5000；调大需同时确认 ip_local_port_range 等效范围。"
                            + "修改后需重启。",
                    TCPIP, "MaxUserPort", 5000, 65534),
            reg("win.tcp.timedWaitDelay", "网络与端口", "TIME_WAIT 时长", "秒", "int", "",
                    false, false, "主动关闭后的 TIME_WAIT 等待秒数，默认 120，范围 30~300；需重启。",
                    TCPIP, "TcpTimedWaitDelay", 30, 300),
            reg("win.tcp.numConnections", "网络与端口", "TCP 连接数上限", "个", "int", "",
                    false, false, "允许的最大 TCP 连接数，0 表示由系统自动决定；需重启。",
                    TCPIP, "TcpNumConnections", 0, 16777214),
            reg("win.tcp.defaultTtl", "网络与端口", "默认 TTL", "跳", "int", "",
                    false, false, "出站 IP 包的默认 TTL，修改后需重启。",
                    TCPIP, "DefaultTTL", 1, 255),
            reg("win.tcp.pmtuDiscovery", "网络与端口", "PMTU 发现", "开关", "enum", "0=关闭;1=开启",
                    false, false, "路径 MTU 自动发现，关闭可能导致大包被丢弃；需重启。",
                    TCPIP, "EnablePMTUDiscovery", 0, 1),
            reg("win.tcp.pmtuBlackHole", "网络与端口", "PMTU 黑洞检测", "开关", "enum", "0=关闭;1=开启",
                    false, false, "检测 PMTU 黑洞并自动降包长，跨 VPN 场景常见；需重启。",
                    TCPIP, "EnablePMTUBHDetect", 0, 1),
            reg("win.tcp.sackOpts", "网络与端口", "SACK 选项", "开关", "enum", "0=关闭;1=开启",
                    false, false, "选择性确认，弱网或高丢包链路建议开启；需重启。",
                    TCPIP, "SackOpts", 0, 1),
            reg("win.tcp.windowScaling", "网络与端口", "窗口缩放", "位掩码", "enum",
                    "0=关闭;1=仅时间戳;2=仅窗口缩放;3=两者都开",
                    false, false, "窗口缩放与时间戳选项，长肥管道需要开启；需重启。",
                    TCPIP, "Tcp1323Opts", 0, 3),
            reg("win.tcp6.disabledComponents", "网络与端口", "IPv6 禁用位", "位掩码", "int", "",
                    false, true, "按位禁用 IPv6 组件（0xFFFFFFFF 为全禁用）；修改后需重启，误设可能影响回环外的网络。",
                    "HKLM:\\SYSTEM\\CurrentControlSet\\Services\\Tcpip6\\Parameters",
                    "DisabledComponents", 0, 4294967295L),
            reg("win.lanman.size", "网络与端口", "文件共享内存档位", "档", "enum", "1=最小;2=均衡;3=最大",
                    false, false, "LanmanServer 共享缓存档位，文件服务器可调大；需重启 Server 服务。",
                    "HKLM:\\SYSTEM\\CurrentControlSet\\Services\\LanmanServer\\Parameters",
                    "Size", 1, 3),
            reg("win.memory.clearPageFile", "内存与文件系统", "关机清空页面文件", "开关", "enum",
                    "0=关闭;1=开启", false, false, "关机时清空 pagefile，提升安全但显著延长关机时间。",
                    MEMORY_MANAGEMENT, "ClearPageFileAtShutdown", 0, 1),
            reg("win.memory.largeSystemCache", "内存与文件系统", "大系统缓存", "开关", "enum",
                    "0=关闭;1=开启", false, false, "文件服务器可开启大系统缓存；数据库服务器通常关闭。",
                    MEMORY_MANAGEMENT, "LargeSystemCache", 0, 1),
            reg("win.ntfs.disableLastAccess", "内存与文件系统", "禁用最后访问时间", "开关", "enum",
                    "0=记录;1=禁用", true, false, "禁用 NTFS 最后访问时间更新，可减少磁盘写入；立即生效。",
                    "HKLM:\\SYSTEM\\CurrentControlSet\\Control\\FileSystem",
                    "NtfsDisableLastAccessUpdate", 0, 1),
            reg("win.ntfs.memoryUsage", "内存与文件系统", "NTFS 内存档位", "档", "enum",
                    "1=最小;2=均衡", false, false, "NTFS 内存使用档位，修改后需重启。",
                    "HKLM:\\SYSTEM\\CurrentControlSet\\Control\\FileSystem", "NtfsMemoryUsage", 1, 2),
            reg("win.priority.separation", "内存与文件系统", "前台/后台优先级", "值", "int", "",
                    false, false, "Win32PrioritySeparation，控制前台与后台线程的时间片分配；默认 2。",
                    "HKLM:\\SYSTEM\\CurrentControlSet\\Control\\PriorityControl",
                    "Win32PrioritySeparation", 0, 38),
            reg("win.lsa.limitBlankPassword", "安全与权限", "限制空白密码", "开关", "enum", "0=允许;1=限制",
                    false, false, "限制空白密码账户只能本地登录，默认开启。",
                    LSA, "LimitBlankPasswordUse", 0, 1),
            reg("win.lsa.restrictAnonymous", "安全与权限", "匿名枚举限制", "级别", "enum",
                    "0=不限制;1=限制;2=严格", false, false, "限制匿名用户枚举 SAM 账户与共享；修改后需重启。",
                    LSA, "RestrictAnonymous", 0, 2),
            reg("win.lsa.lmCompatibility", "安全与权限", "LAN Manager 认证级别", "级别", "enum",
                    "0=LM/NTLM;1=LM/NTLM(128);2=仅NTLM;3=NTLMv2;4=仅NTLMv2;5=仅NTLMv2拒绝LM",
                    false, true, "认证协议级别，越严格越安全；调低会削弱认证强度。修改后需重启。",
                    LSA, "LmCompatibilityLevel", 0, 5),
            reg("win.uac.enableLua", "安全与权限", "UAC 总开关", "开关", "enum", "0=关闭;1=开启",
                    false, true, "关闭 UAC 会让所有管理员进程直接提权，显著降低安全性；修改后需重启。",
                    "HKLM:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\System",
                    "EnableLUA", 0, 1),
            reg("win.uac.consentPrompt", "安全与权限", "UAC 提权提示行为", "级别", "enum",
                    "0=不提示直接提升;1=提示凭据;2=提示同意;5=提示同意(非安全桌面)",
                    false, false, "管理员提权时的提示方式，0 最宽松；修改后需重新登录。",
                    "HKLM:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\System",
                    "ConsentPromptBehaviorAdmin", 0, 5),
            reg("win.uac.secureDesktop", "安全与权限", "UAC 安全桌面", "开关", "enum", "0=关闭;1=开启",
                    false, false, "提权提示是否使用安全桌面，关闭会降低抗欺骗能力。",
                    "HKLM:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\System",
                    "PromptOnSecureDesktop", 0, 1),
            reg("win.rdp.denyConnections", "安全与权限", "禁止远程桌面", "开关", "enum", "0=允许;1=禁止",
                    false, true, "1 表示关闭 RDP 监听；修改后需重启 TermService 或重启系统。",
                    TERMINAL_SERVER, "fDenyTSConnections", 0, 1),
            reg("win.rdp.nla", "安全与权限", "RDP 网络级认证", "开关", "enum", "0=关闭;1=开启",
                    false, false, "要求连接前完成 NLA 认证；修改后需重启 TermService。",
                    TERMINAL_SERVER + "\\WinStations\\RDP-Tcp", "UserAuthentication", 0, 1),
            reg("win.rdp.securityLayer", "安全与权限", "RDP 安全层", "级别", "enum",
                    "0=协商;1=SSL;2=协商(RDP)", false, false, "RDP 传输安全层，默认协商；修改后需重启 TermService。",
                    TERMINAL_SERVER + "\\WinStations\\RDP-Tcp", "SecurityLayer", 0, 2),
            reg("win.crash.dumpEnabled", "崩溃与诊断", "崩溃转储级别", "级别", "enum",
                    "0=无;1=小内存dump;2=内核dump;3=小内存dump(覆盖);7=自动内存dump",
                    false, false, "系统崩溃时写入的转储级别；7 适合排障，占用磁盘。",
                    "HKLM:\\SYSTEM\\CurrentControlSet\\Control\\CrashControl",
                    "CrashDumpEnabled", 0, 7),
            reg("win.crash.autoReboot", "崩溃与诊断", "崩溃后自动重启", "开关", "enum", "0=关闭;1=开启",
                    false, false, "蓝屏后自动重启；排障时建议关闭以便查看蓝屏码。",
                    "HKLM:\\SYSTEM\\CurrentControlSet\\Control\\CrashControl",
                    "AutoReboot", 0, 1),
            reg("win.crash.overwrite", "崩溃与诊断", "转储覆盖", "开关", "enum", "0=保留;1=覆盖",
                    false, false, "是否覆盖已有转储文件，关闭后多次蓝屏可能因同名文件而丢失。",
                    "HKLM:\\SYSTEM\\CurrentControlSet\\Control\\CrashControl",
                    "Overwrite", 0, 1),
            reg("win.errorMode", "崩溃与诊断", "系统错误提示模式", "模式", "enum",
                    "0=对话框;1=无提示;2=仅日志", false, false, "0 弹窗 / 1 忽略 / 2 写入事件日志；服务器常用 1。",
                    "HKLM:\\SYSTEM\\CurrentControlSet\\Control\\Windows", "ErrorMode", 0, 2)
    );

    @Override
    public List<DiskPartitionRowVO> diskPartitions() {
        return WindowsProfileRows.partitions(powershell(WindowsProfileScripts.VOLUMES));
    }

    /* ---------- 挂载盘（卷 → 盘符） ---------- */

    /**
     * Windows 没有「挂载表」概念：盘符就是挂载点，{@code Win32_LogicalDisk} 的每一行即一条挂载。
     *
     * <p>没有盘符的卷（如恢复分区、未分配盘）无法作为挂载点使用，仍然列出并让
     * target 留空，页面据此显示为「未挂载」。</p>
     */
    @Override
    public List<MountRowVO> mounts() {
        return WindowsProfileRows.logicalDisks(powershell(WindowsProfileScripts.LOGICAL_DISKS));
    }

    /**
     * 已删除但仍被占用的文件：需要句柄枚举工具（handle.exe / NtQuerySystemInformation），
     * 当前通道没有可靠实现，明确返回「不支持」，不要用空列表让用户误以为没有。
     */
    @Override
    public DeletedFilesVO deletedFiles() {
        return DeletedFilesVO.unsupported("Windows 暂不支持枚举已删除但仍被占用的文件："
                + "需要管理员权限的句柄枚举工具（handle.exe / NtQuerySystemInformation），当前采集通道未接入");
    }


    /* ---------- 环境变量（系统级 / Machine） ---------- */

    @Override
    protected String envSetCommand(String name, String value) {
        return ps("[Environment]::SetEnvironmentVariable(" + psStr(name) + "," + psStr(value) + ",'Machine')");
    }

    @Override
    protected String envDeleteCommand(String name) {
        return ps("[Environment]::SetEnvironmentVariable(" + psStr(name) + ",$null,'Machine')");
    }

    @Override
    protected String readEnvValue(String name) {
        String script = "$v=[Environment]::GetEnvironmentVariable(" + psStr(name) + ",'Machine');"
                + " if($null -eq $v){ '<ENVNULL>' } else { $v }";
        String out = output(ps(script));
        if (out == null) {
            return null;
        }
        String text = out.trim();
        return "<ENVNULL>".equals(text) ? null : text;
    }

    /* ---------- 防火墙（netsh） ---------- */

    @Override
    public HostActionVO addFirewallRule(FirewallRuleInputVO rule) {
        String nm = safeName(rule == null ? null : rule.name());
        if (nm.isBlank()) {
            return HostActionVO.fail("缺少规则名");
        }
        ShellSupport.ProbeResult result = run(firewallAddCommand(rule, nm));
        if (result.failed()) {
            return HostActionVO.fail("防火墙规则 " + nm + " 添加失败" + detail(result));
        }
        return firewallNameExists(nm)
                ? HostActionVO.ok("防火墙规则 " + nm + " 已添加（运行时生效）" + detail(result))
                : HostActionVO.fail("防火墙规则 " + nm + " 添加后未查询到" + detail(result));
    }

    @Override
    public HostActionVO deleteFirewallRule(String name, String chain, Integer num) {
        String nm = safeName(name);
        if (nm.isBlank()) {
            return HostActionVO.fail("缺少规则名");
        }
        ShellSupport.ProbeResult result = run("netsh advfirewall firewall delete rule name=\"" + nm + "\"");
        if (result.failed()) {
            return HostActionVO.fail("防火墙规则 " + nm + " 删除失败" + detail(result));
        }
        return firewallNameExists(nm)
                ? HostActionVO.fail("防火墙规则 " + nm + " 仍存在" + detail(result))
                : HostActionVO.ok("防火墙规则 " + nm + " 已删除" + detail(result));
    }

    /**
     * 组装 netsh 新增规则命令（可选项仅在有值时追加）。
     *
     * @param rule 规则入参
     * @param nm   已净化的规则名
     * @return netsh 命令
     */
    private static String firewallAddCommand(FirewallRuleInputVO rule, String nm) {
        StringBuilder cmd = new StringBuilder("netsh advfirewall firewall add rule name=\"").append(nm).append("\"");
        String dir = "out".equalsIgnoreCase(trim(rule.direction())) ? "OUT" : "IN";
        String act = "block".equalsIgnoreCase(trim(rule.action())) ? "BLOCK" : "ALLOW";
        cmd.append(" dir=").append(dir).append(" action=").append(act);
        String proto = trim(rule.protocol()).toUpperCase(Locale.ROOT);
        if ("TCP".equals(proto) || "UDP".equals(proto)) {
            cmd.append(" protocol=").append(proto);
        }
        String port = trim(rule.localPort());
        if (!port.isEmpty()) {
            cmd.append(" localport=").append(port.replaceAll("[^0-9,\\-]", ""));
        }
        String program = trim(rule.program());
        if (!program.isEmpty()) {
            cmd.append(" program=\"").append(program).append("\"");
        }
        return cmd.toString();
    }

    /**
     * 用 netsh 查询规则是否存在（输出会回显规则名）。
     *
     * @param nm 规则名
     * @return 存在返回 true
     */
    private boolean firewallNameExists(String nm) {
        String out = run("netsh advfirewall firewall show rule name=\"" + nm + "\"").output();
        return out != null && out.contains(nm);
    }

    /**
     * 把任意字符串构造为 PowerShell 字符串表达式：普通字符走单引号字面量，
     * 单/双引号用 {@code [char]39/34} 拼接，保证脚本本身不含双引号。
     *
     * @param s 原始字符串
     * @return 如 {@code ('a b'+[char]39+'c')}
     */
    private static String psStr(String s) {
        String text = s == null ? "" : s;
        StringBuilder expr = new StringBuilder();
        StringBuilder buf = new StringBuilder();
        boolean any = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'' || c == '"') {
                if (buf.length() > 0) {
                    if (any) {
                        expr.append('+');
                    }
                    expr.append('\'').append(buf).append('\'');
                    any = true;
                    buf.setLength(0);
                }
                if (any) {
                    expr.append('+');
                }
                expr.append("[char]").append(c == '"' ? 34 : 39);
                any = true;
            } else {
                buf.append(c);
            }
        }
        if (buf.length() > 0) {
            if (any) {
                expr.append('+');
            }
            expr.append('\'').append(buf).append('\'');
            any = true;
        }
        return any ? "(" + expr + ")" : "''";
    }

    /**
     * 按通道方言承载 PowerShell 脚本并取输出。
     *
     * @param script PowerShell 脚本（不含双引号）
     * @return 命令输出
     */
    private String powershell(String script) {
        return output(ps(script));
    }

    /**
     * 把 PowerShell 脚本包成当前方言可执行的命令。
     *
     * @param script PowerShell 脚本（不含双引号）
     * @return 可下发命令
     */
    private String ps(String script) {
        ShellDialect dialect = channel.shellDialect(host);
        return dialect == ShellDialect.POWERSHELL
                ? script
                : "powershell -NoProfile -ExecutionPolicy Bypass -Command \"" + script + "\"";
    }

    /**
     * 组装「读命令行 → 结束原进程 → 确认消失 → 原样拉起」脚本。
     *
     * @param pid 目标进程 ID
     * @return PowerShell 脚本
     */
    private static String restartScript(int pid) {
        return "$q=[char]34;$p=Get-CimInstance Win32_Process -Filter 'ProcessId=" + pid + "';"
                + "if(-not $p){ '" + ShellSupport.FAIL_MARK + ":进程 " + pid + " 不存在' }"
                + "else{ $cmd=$p.CommandLine;"
                + "if(-not $cmd -and $p.ExecutablePath){ $cmd=$q+$p.ExecutablePath+$q }"
                + "if(-not $cmd){ '" + ShellSupport.FAIL_MARK + ":读不到进程 " + pid + " 的命令行（受保护进程）' }"
                + "else{ $a=@{CommandLine=$cmd};"
                + "if($p.ExecutablePath){ $a.CurrentDirectory=Split-Path -Parent $p.ExecutablePath }"
                + "Stop-Process -Id " + pid + " -Force -ErrorAction SilentlyContinue;"
                + "Start-Sleep -Milliseconds 500;"
                + "if(Get-Process -Id " + pid + " -ErrorAction SilentlyContinue){"
                + "'" + ShellSupport.FAIL_MARK + ":进程 " + pid + " 未能结束（权限不足），已放弃拉起新实例' }"
                + "else{ $r=Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments $a;"
                + "if($r.ReturnValue -eq 0){ 'NEW=' + $r.ProcessId }"
                + "else{ '" + ShellSupport.FAIL_MARK + ":拉起失败 ReturnValue=' + $r.ReturnValue } } } }";
    }

    /**
     * 从输出里取新进程号。
     *
     * @param output 命令输出
     * @return 新进程号，取不到返回 null
     */
    private static Integer newPid(String output) {
        String text = output == null ? "" : output.trim();
        int at = text.indexOf("NEW=");
        if (at < 0) {
            return null;
        }
        try {
            return Integer.valueOf(text.substring(at + 4).trim().split("\\s+")[0]);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 取最后一行非空文本。
     *
     * @param text 多行文本
     * @return 末行
     */
    private static String lastLine(String text) {
        String[] lines = text.split("\\r?\\n");
        return lines[lines.length - 1].trim();
    }
}