package com.chua.starter.monitor.support.spi.impl;

import com.chua.starter.monitor.support.model.NetProcessRowVO;
import com.chua.starter.monitor.support.model.ServiceRowVO;
import com.chua.starter.monitor.support.support.ConsoleOutput;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Windows 本机采集回退：服务清单与端口占用。
 *
 * <p><b>为什么需要它</b>：OSHI 在 Windows 上能给出服务名与状态，但拿不到启动方式
 * （{@code StartMode}），也常常缺端口归属进程。这两个缺口只能靠系统命令补：
 * 服务走 {@code Win32_Service}，端口走 {@code netstat -ano} + {@code tasklist}。</p>
 *
 * <p>本类只服务「管控机本机」通道（{@link WindowsHostOperationsProvider#localServices()} /
 * {@link WindowsHostOperationsProvider#localNetConnections()}）；远端机器走 SSH / WinRM，
 * 命令文本在 {@code WindowsHostOperations} 里。</p>
 *
 * <p>命令按平台事实硬编码为 Windows 原生可执行文件，不经过 shell 拼接，
 * 避免路径与引号在不同代码页下被二次解释。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
final class WindowsLocalProbe {

    /**
     * 命令最长等待时间：本机采集由 HTTP 请求同步触发，超过这个时间不如如实失败。
     */
    private static final long TIMEOUT_MS = 15_000L;

    /**
     * {@code tasklist /FO CSV /NH} 的一行：{@code "进程名","PID",...}。
     */
    private static final Pattern TASKLIST_ROW = Pattern.compile("\"([^\"]+)\",\"(\\d+)\"");

    /**
     * 制表符分隔字段数（服务 5 列）。
     */
    private static final int SERVICE_FIELD_COUNT = 5;

    private WindowsLocalProbe() {
    }

    /**
     * 枚举本机 Windows 服务。
     *
     * <p>{@code Win32_Service} 一次给出名称、显示名、状态、启动方式与进程号，
     * 字段之间用制表符分隔，避免显示名里的空格与逗号影响解析。</p>
     *
     * @return 服务行；命令失败时返回空列表（由调用方回退 OSHI）
     */
    static List<ServiceRowVO> services() {
        String script = "Get-CimInstance Win32_Service | ForEach-Object { "
                + "$_.Name+[char]9+$_.DisplayName+[char]9+$_.State+[char]9+$_.StartMode+[char]9+"
                + "[string]$_.ProcessId }";
        String out = powershell(script);
        List<ServiceRowVO> rows = new ArrayList<>();
        if (out == null || out.isBlank()) {
            return rows;
        }
        for (String line : out.split("\\r?\\n")) {
            String[] fields = line.split("\\t", SERVICE_FIELD_COUNT);
            if (fields.length < SERVICE_FIELD_COUNT || fields[0].isBlank()) {
                continue;
            }
            String name = fields[0].trim();
            String displayName = fields[1].isBlank() ? name : fields[1].trim();
            long pid = parseLong(fields[4]);
            rows.add(new ServiceRowVO(name, displayName, normalizeState(fields[2]), normalizeStartType(fields[3]), pid));
        }
        return rows;
    }

    /**
     * 枚举本机端口占用。
     *
     * <p>{@code netstat -ano} 覆盖 TCP/UDP 与 v4/v6，且每行都带 PID；
     * 进程名用一次 {@code tasklist} 建表后回填，避免逐行查进程。</p>
     *
     * @return 端口占用行；命令失败时返回空列表
     */
    static List<NetProcessRowVO> netConnections() {
        String netstat = run("netstat", "-ano");
        if (netstat == null || netstat.isBlank()) {
            return new ArrayList<>();
        }
        return WindowsNetstatRows.parse(netstat, processNames());
    }

    /**
     * 建立 PID 到进程名的映射。
     *
     * <p>{@code tasklist} 的 CSV 输出里进程名与 PID 恒为前两列，
     * 用正则只取这两列即可，不依赖列数（不同系统的列集合不完全一致）。</p>
     *
     * @return PID（字符串）到进程名的映射
     */
    private static Map<String, String> processNames() {
        Map<String, String> names = new HashMap<>();
        String out = run("tasklist", "/FO", "CSV", "/NH");
        if (out == null || out.isBlank()) {
            return names;
        }
        Matcher matcher = TASKLIST_ROW.matcher(out);
        while (matcher.find()) {
            names.put(matcher.group(2), matcher.group(1));
        }
        return names;
    }

    /**
     * 用 PowerShell 执行脚本。
     *
     * @param script 脚本（只含单引号，不传结构化参数）
     * @return 标准输出；执行失败返回空串
     */
    private static String powershell(String script) {
        return run("powershell", "-NoProfile", "-NonInteractive", "-Command", script);
    }

    /**
     * 执行命令并读取输出。
     *
     * @param command argv
     * @return 标准输出（含非 0 退出）；启动失败或超时返回空串
     */
    private static String run(String... command) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String out = ConsoleOutput.read(process, TIMEOUT_MS);
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            return out == null ? "" : out;
        } catch (Exception e) {
            // 本机采集是「有则更好」的回退路径：失败时交给 OSHI / 空结果，不把异常抛到接口层
            return "";
        }
    }

    /**
     * 归一份服务状态。
     *
     * @param raw {@code Win32_Service.State} 原值
     * @return RUNNING / STOPPED / STARTING / STOPPING / PAUSED / UNKNOWN
     */
    private static String normalizeState(String raw) {
        String value = raw == null ? "" : raw.trim();
        switch (value.toLowerCase(Locale.ROOT)) {
            case "running":
                return "RUNNING";
            case "stopped":
                return "STOPPED";
            case "start pending":
                return "STARTING";
            case "stop pending":
                return "STOPPING";
            case "paused":
                return "PAUSED";
            default:
                return "UNKNOWN";
        }
    }

    /**
     * 归一份启动方式。
     *
     * @param raw {@code Win32_Service.StartMode} 原值
     * @return AUTOMATIC / MANUAL / DISABLED / BOOT / SYSTEM / UNKNOWN
     */
    private static String normalizeStartType(String raw) {
        String value = raw == null ? "" : raw.trim();
        switch (value.toLowerCase(Locale.ROOT)) {
            case "auto":
                return "AUTOMATIC";
            case "manual":
                return "MANUAL";
            case "disabled":
                return "DISABLED";
            case "boot":
                return "BOOT";
            case "system":
                return "SYSTEM";
            default:
                return "UNKNOWN";
        }
    }

    /**
     * 解析数值字段。
     *
     * @param raw 原值
     * @return 解析成功返回数值，失败返回 0
     */
    private static long parseLong(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (Exception e) {
            return 0L;
        }
    }
}
