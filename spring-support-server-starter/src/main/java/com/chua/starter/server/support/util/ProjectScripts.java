package com.chua.starter.server.support.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 发布项目「启动 / 停止 / 重启 / 状态」四个脚本的内置通用模板。
 *
 * <p>模板以占位符书写，创建/更新项目时按项目字段一键展开，用户无需为每个项目手写脚本：</p>
 * <ul>
 *   <li>{@code __ENTRY__}   主入口名称（jar / 二进制 / 启动脚本 / node 入口等）</li>
 *   <li>{@code __LOG__}     日志文件路径（由日志地址推导，缺省为 项目地址/app.log）</li>
 *   <li>{@code __PATH__}    项目地址</li>
 *   <li>{@code __APPNAME__} 应用名（取项目名，用于 systemd / Windows 服务等）</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class ProjectScripts {

    /** 主入口占位符 */
    public static final String PH_ENTRY = "__ENTRY__";
    /** 日志路径占位符 */
    public static final String PH_LOG = "__LOG__";
    /** 项目地址占位符 */
    public static final String PH_PATH = "__PATH__";
    /** 应用名占位符 */
    public static final String PH_APPNAME = "__APPNAME__";
    /** 默认日志文件名（日志地址被填成目录时补用） */
    private static final String DEFAULT_LOG_NAME = "app.log";

    private ProjectScripts() {
    }

    /**
     * 展开后的四个脚本
     *
     * @param start   启动脚本
     * @param stop    停止脚本
     * @param restart 重启脚本
     * @param status  状态检测脚本
     */
    public record Scripts(String start, String stop, String restart, String status) {
    }

    /**
     * Linux PID 停止片段
     * <p>{@code kill} 只发 SIGTERM，JVM 收到后需优雅退出；若 10 秒内未退出
     * 会被升级为 SIGKILL，否则「重启」会与旧进程抢 jar 与端口。</p>
     */
    private static final String LINUX_PID_STOP = "if [ -f app.pid ]; then\n"
            + "  pid=$(cat app.pid)\n"
            + "  kill $pid 2>/dev/null\n"
            + "  i=0\n"
            + "  while kill -0 $pid 2>/dev/null && [ $i -lt 10 ]; do\n"
            + "    sleep 1\n"
            + "    i=$((i + 1))\n"
            + "  done\n"
            + "  kill -9 $pid 2>/dev/null\n"
            + "  rm -f app.pid\n"
            + "fi";

    /**
     * Linux PID 状态脚本：仅以 app.pid 为准
     * <p>内置启动模板都会写 app.pid，故不做 {@code pgrep -f} 匹配——脚本自身
     * 命令行同样包含主入口路径，{@code pgrep -f} 会匹配到执行脚本的 shell 造成误报。</p>
     */
    private static final String LINUX_PID_STATUS = "if [ -f app.pid ] && kill -0 $(cat app.pid) 2>/dev/null; then\n"
            + "  echo running\n"
            + "else\n"
            + "  echo stopped\n"
            + "fi";

    /**
     * Linux 通用状态脚本：优先 app.pid，其次按主入口匹配进程
     * <p>用于用户自带脚本（无 app.pid）场景，匹配时排除当前 shell 自身。</p>
     */
    private static final String LINUX_STATUS = "if [ -f app.pid ] && kill -0 $(cat app.pid) 2>/dev/null; then\n"
            + "  echo running\n"
            + "elif [ -n '" + PH_ENTRY + "' ] && pgrep -f '" + PH_ENTRY + "' 2>/dev/null | grep -qv \"^$$\\$\"; then\n"
            + "  echo running\n"
            + "else\n"
            + "  echo stopped\n"
            + "fi";

    /**
     * Windows 停止片段：优先按 app.pid 结束（taskkill /T 连子进程），
     * <p>不依赖 WMI（{@code Get-CimInstance Win32_Process} 普通用户会被拒），
     * 启动模板会把进程号写入 app.pid，停/重启/状态均以它为准。</p>
     */
    private static final String WIN_PID_STOP_BODY = "$ErrorActionPreference = 'SilentlyContinue'\n"
            + "if (Test-Path 'app.pid') { Get-Content 'app.pid' | ForEach-Object { & taskkill.exe /PID $_ /T /F | Out-Null }; Remove-Item 'app.pid' -Force }";

    /**
     * Windows 停止脚本（独立使用，末尾 exit 0 保证「停止」任务成功）
     */
    private static final String WIN_PID_STOP = WIN_PID_STOP_BODY + "\nexit 0";

    /**
     * Windows 状态脚本：仅以 app.pid 为准（SSH 会话内 WMI 枚举常被拒，故不走命令行匹配）
     */
    private static final String WIN_STATUS = "if (Test-Path 'app.pid') { if (Get-Process -Id (Get-Content 'app.pid') -ErrorAction SilentlyContinue) { 'running' } else { 'stopped' } } else { 'stopped' }";

    /**
     * 生成内置四脚本（未展开前，占位符仍在，便于持久化后按项目字段解析）
     *
     * @param windows    是否 Windows
     * @param projectType 项目类型
     * @param mainEntry  主入口（用于判断 .ps1 等）
     * @return 模板脚本
     */
    public static Scripts builtin(boolean windows, String projectType, String mainEntry) {
        if (windows) {
            return builtinWindows(projectType, mainEntry);
        }
        return builtinLinux(projectType);
    }

    /**
     * Linux 启动脚本首行：显式切换到项目目录并以 {@code ;} 收尾。
     *
     * <p>必须自带 {@code cd}：调用方（ReleaseServiceImpl#buildCommand）会在脚本前拼接
     * {@code mkdir -p X && cd 'X' && }，而 {@code &&} 优先级高于 {@code &}，若脚本首行以
     * {@code &} 结尾，整条 {@code &&} 链会被整体后台化，导致 cd 只在子 shell 生效
     * （app.pid 写到错误目录），且后台链持有 SSH channel 的 stdout 使 exec 永远等不到
     * EOF 而超时（退出码 -1）。首行以 {@code ;} 收尾可切断该链，只让 nohup 行被后台化。</p>
     * <p>项目目录不存在时不能 {@code exit 1}：{@code __PATH__} 可能为空或尚未创建，
     * 此时仅忽略 cd 失败，让后续命令按调用方已 cd 成功的行为执行。</p>
     */
    private static String linuxEnter() {
        return "cd '" + PH_PATH + "' 2>/dev/null || true;\n";
    }

    private static Scripts builtinLinux(String projectType) {
        String type = projectType == null ? "" : projectType.trim().toLowerCase();
        String start;
        String restart;
        switch (type) {
            case "springboot" -> {
                start = linuxEnter()
                        + "nohup java __JVMARGS__ -jar " + PH_ENTRY + " __ARGS__ --spring.profiles.active=prod > " + PH_LOG + " 2>&1 &\n"
                        + "echo $! > app.pid";
                restart = LINUX_PID_STOP + "\nsleep 2\n" + start;
            }
            case "fatjar" -> {
                start = linuxEnter()
                        + "nohup java __JVMARGS__ -jar " + PH_ENTRY + " __ARGS__ > " + PH_LOG + " 2>&1 &\n"
                        + "echo $! > app.pid";
                restart = LINUX_PID_STOP + "\nsleep 2\n" + start;
            }
            case "static", "nginx" -> {
                // nginx 自建 pid 文件（相对项目目录），状态/停止只认本项目，避免误杀/误报其它项目的 nginx
                String pid = "logs/nginx.pid";
                start = "nginx";
                restart = "if [ -f " + pid + " ] && kill -0 $(cat " + pid + ") 2>/dev/null; then\n"
                        + "  nginx -s reload\n"
                        + "else\n"
                        + "  nginx\n"
                        + "fi";
                return new Scripts(start,
                        "nginx -s stop 2>/dev/null\n"
                                + "if [ -f " + pid + " ]; then kill $(cat " + pid + ") 2>/dev/null; rm -f " + pid + "; fi",
                        restart,
                        "if [ -f " + pid + " ] && kill -0 $(cat " + pid + ") 2>/dev/null; then\n"
                                + "  echo running\n"
                                + "else\n"
                                + "  echo stopped\n"
                                + "fi");
            }
            case "separated" -> {
                start = linuxEnter()
                        + "chmod +x " + PH_ENTRY + ";\n"
                        + "nohup ./" + PH_ENTRY + " __ARGS__ > " + PH_LOG + " 2>&1 &\n"
                        + "echo $! > app.pid";
                restart = LINUX_PID_STOP + "\nsleep 2\n" + start;
            }
            default -> {
                return new Scripts("./start.sh", "./stop.sh", "./restart.sh", LINUX_STATUS);
            }
        }
        return new Scripts(start, LINUX_PID_STOP, restart, LINUX_PID_STATUS);
    }

    /**
     * Windows 启动片段：经 WMI {@code Win32_Process.Create} 脱离 SSH/WinRM 会话启动
     * （直接 Start-Process 的子进程会随会话作业结束被回收），并把进程号写入 app.pid。
     *
     * @param exe  可执行程序
     * @param args 参数串
     * @return 启动脚本
     */
    private static String winStart(String exe, String args) {
        String cmd = exe + (args == null || args.isEmpty() ? "" : " " + args);
        return "$cmd = 'cmd /c " + cmd + " > \"" + PH_LOG + "\" 2>&1'\n"
                + "$r = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{ CommandLine = $cmd; CurrentDirectory = '"
                + PH_PATH + "' }\n"
                + "if ($r.ReturnValue -ne 0) { throw ('启动失败 Win32_Process.Create code=' + $r.ReturnValue) }\n"
                + "$r.ProcessId | Out-File 'app.pid' -Encoding ascii\n"
                + "Write-Output $r.ProcessId";
    }

    private static Scripts builtinWindows(String projectType, String mainEntry) {
        String type = projectType == null ? "" : projectType.trim().toLowerCase();
        String entry = mainEntry == null ? "" : mainEntry.trim().toLowerCase();
        switch (type) {
            case "springboot", "fatjar" -> {
                String start = winStart("java", "-jar " + PH_ENTRY);
                return new Scripts(start, WIN_PID_STOP, WIN_PID_STOP_BODY + "\nStart-Sleep -Seconds 2\n" + start, WIN_STATUS);
            }
            case "static", "nginx" -> {
                // 与其他类型一致：WMI 脱离会话启动（Start-Process 起的 nginx 会随 SSH 会话被回收），
                // cmd 托管 nginx 主子进程并把 cmd pid 写入 app.pid，停止/状态按 app.pid（taskkill /T 连带 nginx）
                String start = winStart("nginx.exe", "");
                return new Scripts(start, WIN_PID_STOP, WIN_PID_STOP + "\nStart-Sleep -Seconds 2\n" + start, WIN_STATUS);
            }
            case "separated" -> {
                String start = entry.endsWith(".ps1")
                        ? winStart("powershell", "-NoProfile -ExecutionPolicy Bypass -File " + PH_ENTRY)
                        : winStart(PH_ENTRY, "");
                return new Scripts(start, WIN_PID_STOP, WIN_PID_STOP_BODY + "\nStart-Sleep -Seconds 2\n" + start, WIN_STATUS);
            }
            default -> {
                return new Scripts(".\\start.ps1", ".\\stop.ps1", ".\\restart.ps1", WIN_STATUS);
            }
        }
    }

    /**
     * 用项目字段展开占位符
     *
     * @param template   模板文本（可为空）
     * @param mainEntry  主入口
     * @param projectPath 项目地址
     * @param logPath    日志地址
     * @param appName    应用名
     * @return 展开后的脚本（模板为空返回空）
     */
    public static String apply(String template, String mainEntry, String projectPath, String logPath, String appName) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        String path = trim(projectPath);
        Map<String, String> ctx = new LinkedHashMap<>();
        ctx.put(PH_ENTRY, trim(mainEntry));
        ctx.put(PH_PATH, path);
        ctx.put(PH_LOG, resolveLogFile(logPath, path));
        ctx.put(PH_APPNAME, trim(appName).isEmpty() ? "app" : trim(appName));
        String out = template;
        for (Map.Entry<String, String> e : ctx.entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    }

    /**
     * 由日志地址推导日志文件路径：为空时取 项目地址/app.log。
     *
     * <p>末尾为分隔符时按<b>目录</b>处理并补默认文件名：直接拿目录做重定向目标会得到
     * {@code > /path/to/logs 2>&1}，shell 报「Is a directory」而 java 根本没启动，但
     * 紧随其后的 {@code echo $! > app.pid} 仍会执行、任务仍报「启动成功」，
     * 最终表现为「启动成功但状态是 stopped 且没有任何日志」，极难定位。</p>
     *
     * @param log  日志地址
     * @param path 项目地址
     * @return 日志文件路径
     */
    public static String resolveLogFile(String log, String path) {
        String value = trim(log);
        if (value.isEmpty()) {
            String base = trim(path);
            return base.isEmpty() ? "app.log" : base.replaceAll("/+$", "") + "/app.log";
        }
        if (value.endsWith("/") || value.endsWith("\\")) {
            return value + DEFAULT_LOG_NAME;
        }
        return value;
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }
}
