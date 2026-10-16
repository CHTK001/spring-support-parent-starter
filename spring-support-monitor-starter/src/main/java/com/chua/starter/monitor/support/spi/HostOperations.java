package com.chua.starter.monitor.support.spi;

import com.chua.starter.datasourcehub.support.spi.Platforms;
import com.chua.starter.monitor.support.enums.ServiceAction;
import com.chua.starter.monitor.support.enums.ShellDialect;
import com.chua.starter.monitor.support.model.DiskPartitionRowVO;
import com.chua.starter.monitor.support.model.DriverRowVO;
import com.chua.starter.monitor.support.model.FirewallRuleInputVO;
import com.chua.starter.monitor.support.model.FirewallRuleRowVO;
import com.chua.starter.monitor.support.model.HostActionVO;
import com.chua.starter.monitor.support.model.NetworkConfigVO;
import com.chua.starter.monitor.support.model.ProcessThreadRowVO;
import com.chua.starter.monitor.support.spi.impl.PosixHostOperations;
import com.chua.starter.monitor.support.spi.impl.WindowsHostOperations;
import com.chua.starter.monitor.support.support.ShellSupport;
import com.chua.starter.server.support.entity.MsServerHost;

import java.util.List;

/**
 * 主机平台能力集：命令文本的唯一存放地。
 *
 * <p>控制层与 {@link SystemInfoProvider} 各通道实现都不拼命令：动作（杀进程、重启进程、服务启停、
 * 网络配置）与平台专属采集（防火墙、驱动、线程、分区）按目标主机真实平台解析到本类的子类，
 * 由子类给出命令文本、回查确认与输出解析。新增平台只加一个子类，通道实现与调用方均不动。</p>
 *
 * <p>命令一律经 {@link ShellSupport} 植入退出码探针，因此「目标机报了错」不会被当成执行成功；
 * 动作是否生效还要回查（进程是否消失、服务状态是否翻转），不能凭命令有回显推断。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public abstract class HostOperations {

    /**
     * 目标主机所在通道
     */
    protected final SystemInfoProvider channel;

    /**
     * 目标主机
     */
    protected final MsServerHost host;

    /**
     * @param channel 目标主机所在通道
     * @param host    目标主机
     */
    protected HostOperations(SystemInfoProvider channel, MsServerHost host) {
        this.channel = channel;
        this.host = host;
    }

    /**
     * 按目标主机真实平台解析能力集。
     *
     * @param channel 目标主机所在通道
     * @param host    目标主机
     * @return 平台能力集，永不为 null
     */
    public static HostOperations of(SystemInfoProvider channel, MsServerHost host) {
        String platform = channel.platform(host);
        if (platform == null || platform.isEmpty()) {
            // 台账未填平台时沿用控制端平台，与该调用点改造前的兜底口径一致
            platform = Platforms.normalize(System.getProperty("os.name"));
        }
        return Platforms.WINDOWS.equals(platform)
                ? new WindowsHostOperations(channel, host)
                : new PosixHostOperations(channel, host);
    }

    /**
     * 关闭进程：下发杀进程命令后回查进程是否真的消失。
     *
     * @param pid  进程 ID
     * @param port 端口，仅用于回显，可为空
     * @return 动作结果
     */
    public final HostActionVO killProcess(int pid, Integer port) {
        if (!processAlive(pid)) {
            return HostActionVO.fail("进程 " + pid + " 不存在");
        }
        ShellSupport.ProbeResult result = run(killCommand(pid));
        String suffix = port == null ? "" : " (端口 " + port + ")";
        for (int i = 0; i < 3; i++) {
            if (!processAlive(pid)) {
                return HostActionVO.ok("进程 " + pid + suffix + " 已关闭" + detail(result));
            }
            sleepQuietly(800L);
        }
        String detail = detail(result);
        return HostActionVO.fail("进程 " + pid + suffix + " 未能关闭"
                + (detail.isEmpty() ? "（进程可能不存在或权限不足）" : detail));
    }

    /**
     * 服务启停：下发动作命令后复查真实状态，避免把「指令已下发」当成成功。
     *
     * @param name   服务名
     * @param action 动作
     * @return 动作结果
     */
    public final HostActionVO controlService(String name, ServiceAction action) {
        String safe = safeName(name);
        ShellSupport.ProbeResult result = run(serviceCommand(safe, action));
        String expect = action.expectState();
        String last = "";
        for (int i = 0; i < 4; i++) {
            last = serviceState(safe);
            if (expect.equals(last)) {
                return HostActionVO.ok("服务 " + name + " 已" + action.getDesc() + "（当前 " + last + "）" + detail(result));
            }
            if ("MISSING".equals(last) || "UNKNOWN".equals(last)) {
                break;
            }
            sleepQuietly(1000L);
        }
        return HostActionVO.fail("服务 " + name + " " + action.getDesc() + "未生效，当前状态 " + last + detail(result));
    }

    /**
     * 按原命令行重启进程。
     *
     * @param pid 进程 ID
     * @return 动作结果，Windows 成功时带新进程号
     */
    public abstract HostActionVO restartProcess(int pid);

    /**
     * 设置系统级（持久化）环境变量，下发后回读值确认。
     *
     * @param rawName 变量名
     * @param value   变量值，null 按空串处理
     * @return 动作结果
     */
    public final HostActionVO setEnvVar(String rawName, String value) {
        String name = envName(rawName);
        if (name == null) {
            return HostActionVO.fail("非法环境变量名: " + rawName);
        }
        String v = value == null ? "" : value;
        ShellSupport.ProbeResult result = run(envSetCommand(name, v));
        if (result.failed()) {
            return HostActionVO.fail("环境变量 " + name + " 设置失败" + detail(result));
        }
        String got = readEnvValue(name);
        if (v.equals(got)) {
            return HostActionVO.ok("环境变量 " + name + " 已设置" + detail(result));
        }
        return HostActionVO.fail("环境变量 " + name + " 设置后回读不一致（期望 " + v + "，实际 " + got + "）"
                + detail(result));
    }

    /**
     * 删除系统级环境变量，下发后回读确认已不存在。
     *
     * @param rawName 变量名
     * @return 动作结果
     */
    public final HostActionVO deleteEnvVar(String rawName) {
        String name = envName(rawName);
        if (name == null) {
            return HostActionVO.fail("非法环境变量名: " + rawName);
        }
        ShellSupport.ProbeResult result = run(envDeleteCommand(name));
        if (result.failed()) {
            return HostActionVO.fail("环境变量 " + name + " 删除失败" + detail(result));
        }
        String got = readEnvValue(name);
        if (got == null) {
            return HostActionVO.ok("环境变量 " + name + " 已删除" + detail(result));
        }
        return HostActionVO.fail("环境变量 " + name + " 仍存在" + detail(result));
    }

    /**
     * 新增防火墙规则。
     *
     * @param rule 规则入参
     * @return 动作结果
     */
    public abstract HostActionVO addFirewallRule(FirewallRuleInputVO rule);

    /**
     * 删除防火墙规则。
     *
     * @param name  规则显示名（Windows）
     * @param chain 链名（Linux iptables）
     * @param num   规则序号（Linux iptables）
     * @return 动作结果
     */
    public abstract HostActionVO deleteFirewallRule(String name, String chain, Integer num);

    /**
     * 设置持久环境变量的命令。
     *
     * @param name  已校验的变量名
     * @param value 变量值
     * @return 命令行
     */
    protected abstract String envSetCommand(String name, String value);

    /**
     * 删除持久环境变量的命令。
     *
     * @param name 已校验的变量名
     * @return 命令行
     */
    protected abstract String envDeleteCommand(String name);

    /**
     * 读取持久化的变量值。
     *
     * @param name 已校验的变量名
     * @return 变量值，不存在返回 null
     */
    protected abstract String readEnvValue(String name);

    /**
     * 校验环境变量名，合法返回原名，否则 null。
     *
     * @param name 原始名称
     * @return 变量名，非法返回 null
     */
    protected static String envName(String name) {
        if (name == null) {
            return null;
        }
        String n = name.trim();
        return n.matches("^[A-Za-z_][A-Za-z0-9_]*$") ? n : null;
    }

    /**
     * 应用网络配置。
     *
     * @param config 配置项
     * @return 动作结果；平台不支持该能力时返回失败并说明原因
     */
    public abstract HostActionVO applyNetworkConfig(NetworkConfigVO config);

    /**
     * 防火墙规则。
     *
     * @return 规则列表，采集失败时抛出带目标机原因的异常
     */
    public abstract List<FirewallRuleRowVO> firewallRules();

    /**
     * 驱动列表。
     *
     * @return 驱动列表，采集失败时抛出带目标机原因的异常
     */
    public abstract List<DriverRowVO> drivers();

    /**
     * 进程的线程列表。
     *
     * @param pid 进程 ID
     * @return 线程列表
     */
    public abstract List<ProcessThreadRowVO> processThreads(int pid);

    /**
     * 主机句柄数：Windows 为系统句柄总数（各进程 HandleCount 汇总），
     * POSIX 为已分配的打开文件句柄数。
     *
     * <p>命令文本按平台差异放在本层：Windows 走 PowerShell，POSIX 读 {@code /proc/sys/fs/file-nr}
     * （macOS 回退 {@code sysctl kern.num_files}）。取不到时返回 -1，不抛异常——
     * 句柄数只是画像的一项指标，拿不到不应让整页采集失败。</p>
     *
     * @return 句柄数，取不到返回 -1
     */
    public abstract long handleCount();

    /**
     * 从命令输出里取第一个整数。
     *
     * @param text 命令输出
     * @return 首个整数，取不到返回 -1
     */
    protected static long firstInt(String text) {
        if (text == null) {
            return -1L;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("-?\\d+").matcher(text);
        if (!matcher.find()) {
            return -1L;
        }
        try {
            return Long.parseLong(matcher.group());
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /**
     * 磁盘分区列表。
     *
     * @return 分区列表
     */
    public abstract List<DiskPartitionRowVO> diskPartitions();

    /**
     * 进程是否存在。
     *
     * <p>用一条存在性探测命令而不是拉全量进程表：动作前后要多次回查，全表在 Agent 通道上一次就要几秒。</p>
     *
     * @param pid 进程 ID
     * @return 存在返回 true
     */
    protected abstract boolean processAlive(int pid);

    /**
     * 杀进程命令。
     *
     * @param pid 进程 ID
     * @return 命令行
     */
    protected abstract String killCommand(int pid);

    /**
     * 服务动作命令。
     *
     * @param name   已净化的服务名
     * @param action 动作
     * @return 命令行
     */
    protected abstract String serviceCommand(String name, ServiceAction action);

    /**
     * 查询单个服务的当前状态。
     *
     * @param name 已净化的服务名
     * @return {@code RUNNING} / {@code STOPPED} / {@code MISSING} / {@code UNKNOWN}
     */
    protected abstract String serviceState(String name);

    /**
     * 以目标通道的真实方言下发命令并还原探针结果。
     *
     * @param cmd 目标方言原生命令
     * @return 探针还原结果，永不为 null
     */
    protected ShellSupport.ProbeResult run(String cmd) {
        ShellDialect dialect = channel.shellDialect(host);
        ShellSupport.Probe probe = ShellSupport.compose(null, cmd, dialect);
        return ShellSupport.interpret(channel.exec(host, probe.line()), probe);
    }

    /**
     * 下发只读采集命令，失败时抛出带目标机原因的错误。
     *
     * @param cmd 目标方言原生命令
     * @return 命令输出
     */
    protected String output(String cmd) {
        ShellSupport.ProbeResult result = run(cmd);
        if (result.failed()) {
            throw new IllegalStateException(reason(result));
        }
        return result.output();
    }

    /**
     * 取失败原因文本。
     *
     * @param result 探针还原结果
     * @return 原因文本，可为空串
     */
    protected static String reason(ShellSupport.ProbeResult result) {
        String output = result.output() == null ? "" : result.output();
        return result.reason() == null ? output : (output.isEmpty() ? result.reason() : result.reason() + " | " + output);
    }

    /**
     * 结果说明后缀。
     *
     * @param result 探针还原结果
     * @return 形如 {@code : 原因}，无内容时返回空串
     */
    protected static String detail(ShellSupport.ProbeResult result) {
        String text = result.failed() ? reason(result) : result.output();
        return text == null || text.isBlank() ? "" : ": " + text.trim();
    }

    /**
     * 剔除名称里的 shell 元字符，防止拼接命令时被注入。
     *
     * @param name 原始名称
     * @return 可安全拼进命令的名称
     */
    protected static String safeName(String name) {
        return name == null ? "" : name.replaceAll("[\";&|`$\\\\]", "");
    }

    /**
     * 归一字符串，null 转空串并去首尾空白。
     *
     * @param value 原始值
     * @return 字符串
     */
    protected static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 睡眠指定毫秒，被打断时恢复中断标记。
     *
     * @param millis 毫秒
     */
    protected static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
