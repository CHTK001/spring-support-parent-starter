package com.chua.starter.monitor.support.spi.impl;

import com.chua.starter.monitor.support.enums.ServiceAction;
import com.chua.starter.monitor.support.enums.ShellDialect;
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

    @Override
    public List<DiskPartitionRowVO> diskPartitions() {
        return WindowsProfileRows.partitions(powershell(WindowsProfileScripts.VOLUMES));
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
