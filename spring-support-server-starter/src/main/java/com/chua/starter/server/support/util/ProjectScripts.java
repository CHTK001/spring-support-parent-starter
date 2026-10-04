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
     * <p>末尾做终态校验：进程树未真正结束时报错，不再无条件成功。
     * 变量名避开 PowerShell 只读自动变量 {@code $PID}。</p>
     */
    private static final String WIN_PID_STOP_BODY = "$ErrorActionPreference = 'SilentlyContinue'\n"
            + "$stopIds = @()\n"
            + "if (Test-Path 'app.pid') { $stopIds = @(Get-Content 'app.pid' | Where-Object { $_ -match '^\\d+$' } | ForEach-Object { [int]$_ }) }\n"
            + "foreach ($sid in $stopIds) { & taskkill.exe /PID $sid /T /F | Out-Null }\n"
            + "Remove-Item 'app.pid' -Force\n"
            + "$stillAlive = @()\n"
            + "for ($i = 0; $i -lt 10; $i++) {\n"
            + "  $stillAlive = @($stopIds | Where-Object { Get-Process -Id $_ -ErrorAction SilentlyContinue })\n"
            + "  if ($stillAlive.Count -eq 0) { break }\n"
            + "  Start-Sleep -Milliseconds 500\n"
            + "}\n"
            + "if ($stillAlive.Count -gt 0) { throw ('停止失败: 进程仍在运行 pid=' + ($stillAlive -join ',')) }";

    /**
     * Windows 停止脚本（独立使用，终态输出 stopped）
     */
    private static final String WIN_PID_STOP = WIN_PID_STOP_BODY + "\nWrite-Output 'stopped'";

    /**
     * Windows 启动终态校验：app.pid 已写入且进程存活才算启动成功。
     *
     * <p>Windows 上 {@code cmd /c} 托管的程序（典型是 nginx）会立刻返回 0，
     * 启动失败只体现在日志里，任务却报「启动成功」；显式校验可把
     * 「报了成功但 status=stopped」提前暴露为任务失败。</p>
     */
    private static final String WIN_VERIFY_RUNNING = "$pidFile = 'app.pid'\n"
            + "if (-not (Test-Path $pidFile)) { throw '启动校验失败: 未写入 app.pid' }\n"
            + "$procId = (Get-Content $pidFile -Raw).Trim()\n"
            + "for ($i = 0; $i -lt 10; $i++) {\n"
            + "  if (Get-Process -Id $procId -ErrorAction SilentlyContinue) { break }\n"
            + "  Start-Sleep -Milliseconds 500\n"
            + "}\n"
            + "if (-not (Get-Process -Id $procId -ErrorAction SilentlyContinue)) { throw ('启动校验失败: 进程未运行 pid=' + $procId) }\n"
            // 稳定窗口：Windows 启动经 `cmd /c` 托管，若被托管的命令解析失败
            // （最典型：java 不在机器级 PATH 上，因为 Win32_Process.Create 起的进程
            // 继承的是机器/用户环境，JAVA_HOME 不会被继承），cmd 会「先存在、随即退出」。
            // 上面只校验了「进程曾经存在」，会把这种情况判成启动成功（实测踩到：
            // 启动脚本打印 running pid=…，4 秒后进程数已为 0）。
            // 因此再观察一段时间，进程若在其中退出即判失败。
            + "for ($i = 0; $i -lt 4; $i++) {\n"
            + "  Start-Sleep -Milliseconds 750\n"
            + "  if (-not (Get-Process -Id $procId -ErrorAction SilentlyContinue)) { throw ('start verify failed: process exited right after start pid=' + $procId) }\n"
            + "}\n"
            + "Write-Output ('running pid=' + $procId)";

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
        return builtinLinux(projectType, mainEntry);
    }

    /**
     * 由主入口名生成「不会匹配到脚本自身命令行」的进程匹配模式。
     *
     * <p>直接用 {@code pgrep -f '<主入口>'} 会匹配到执行脚本的 shell 自身
     * （其命令行即整段脚本文本，含主入口字面量），造成「永远匹配到、永远杀不干净」。
     * 把首字符写成字符类 {@code [x]yyy} 后可避开：脚本自身命令行里是 {@code [x]yyy}，
     * {@code x} 之后紧跟 {@code ]}，与模式要求的下一个字符不符；而目标进程命令行里是
     * 裸的 {@code xyyy}，能被匹配。</p>
     *
     * <p>同时把正则元字符转义，避免入口名里的 {@code + * ( )} 等改变匹配语义。</p>
     *
     * @param mainEntry 主入口名（可含路径，只取文件名部分）
     * @return 自安全匹配模式；入口为空时返回 {@code null}
     */
    private static String selfSafePattern(String mainEntry) {
        if (mainEntry == null || mainEntry.isBlank()) {
            return null;
        }
        String e = mainEntry.trim();
        int slash = Math.max(e.lastIndexOf('/'), e.lastIndexOf('\\'));
        if (slash >= 0) {
            e = e.substring(slash + 1);
        }
        if (e.isEmpty()) {
            return null;
        }
        var sb = new StringBuilder(e.length() + 4);
        for (int i = 0; i < e.length(); i++) {
            char c = e.charAt(i);
            // . 也要转义：不转义时 "smoke.jar" 作为正则会连带匹配 "smokeXjar" 这类无关进程
            if ("\\+*?()[]{}|^$.".indexOf(c) >= 0) {
                sb.append('\\');
            }
            sb.append(c);
        }
        String esc = sb.toString();
        if (Character.isLetterOrDigit(esc.charAt(0))) {
            return "[" + esc.charAt(0) + "]" + esc.substring(1);
        }
        return esc;
    }

    /**
     * Linux 停止脚本的孤儿兜底：{@code app.pid} 陈旧或丢失时回收本项目的残留进程。
     *
     * <p>只按 {@code app.pid} 停止是不够的：pid 文件一旦指向已退出的进程（回滚还原备份会
     * 带回当时的旧 pid、异常中断会留下未更新的 pid），真实进程就再也停不掉，残留进程会
     * 一直占着端口，使后续启动 bind 失败。实测出现过三个同类进程并存。</p>
     *
     * <p>为避免误杀「主入口同名但属于别的项目」的进程，Linux 下再按
     * {@code /proc/<pid>/cwd} 限定为本项目目录；无 {@code /proc} 的平台退化为仅按模式匹配。</p>
     *
     * @param mainEntry 主入口名
     * @return 兜底片段；无法生成模式时返回空串
     */
    private static String linuxOrphanReaper(String mainEntry) {
        String pat = selfSafePattern(mainEntry);
        if (pat == null) {
            return "";
        }
        return "\n# 兜底：app.pid 陈旧/丢失时回收本项目残留进程。模式首字符用字符类以免匹配到脚本自身；\n"
                + "# 并限定「工作目录=本项目目录」，避免误杀主入口同名的其它项目。\n"
                + "if [ -d /proc ] && [ -n '" + PH_PATH + "' ]; then\n"
                + "  for __p in $(pgrep -f '" + pat + "' 2>/dev/null); do\n"
                + "    [ \"$__p\" = \"$$\" ] && continue\n"
                + "    [ \"$(readlink /proc/$__p/cwd 2>/dev/null)\" = '" + PH_PATH + "' ] && kill -9 \"$__p\" 2>/dev/null\n"
                + "  done\n"
                + "else\n"
                + "  pkill -9 -f '" + pat + "' 2>/dev/null\n"
                + "fi\n"
                + "true";
    }

    /**
     * Windows 停止脚本的孤儿兜底：{@code app.pid} 陈旧或丢失时按命令行回收本项目残留进程。
     *
     * <p>与 Linux 同理，只按 {@code app.pid} 结束进程在 pid 陈旧时完全失效。
     * Windows 上获取「某 pid 的工作目录」没有等价于 {@code /proc/<pid>/cwd} 的廉价手段，
     * 因此按命令行匹配；为不误伤主入口同名的其它项目，模式仍走
     * {@link #selfSafePattern(String)}（首字符字符类），并排除当前 PowerShell 自身。</p>
     *
     * <p>普通用户执行 {@code Get-CimInstance Win32_Process} 可能被拒（本项目既有约定），
     * 故整体 try/catch：CIM 不可用时静默跳过，不影响按 app.pid 停止这条主路径。</p>
     *
     * @param mainEntry 主入口名
     * @return 兜底片段；无法生成模式时返回空串
     */
    private static String winOrphanReaper(String mainEntry) {
        String pat = selfSafePattern(mainEntry);
        if (pat == null) {
            return "";
        }
        return "\n# 兜底：app.pid 陈旧/丢失时按命令行回收本项目残留进程（CIM 不可用时静默跳过）\n"
                + "try {\n"
                + "  $re = '" + pat.replace("'", "''") + "'\n"
                + "  foreach ($op in (Get-CimInstance Win32_Process -ErrorAction Stop |\n"
                + "      Where-Object { $_.CommandLine -and $_.ProcessId -ne $PID -and $_.CommandLine -match $re })) {\n"
                + "    & taskkill.exe /PID $op.ProcessId /T /F | Out-Null\n"
                + "  }\n"
                + "} catch { }";
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

    private static Scripts builtinLinux(String projectType, String mainEntry) {
        String type = projectType == null ? "" : projectType.trim().toLowerCase();
        // 停止 = 按 app.pid 停止 + 回收本项目残留进程（app.pid 陈旧时唯一兜底）
        String stop = LINUX_PID_STOP + linuxOrphanReaper(mainEntry);
        String start;
        String restart;
        switch (type) {
            case "springboot" -> {
                start = linuxEnter()
                        + "nohup java __JVMARGS__ -jar " + PH_ENTRY + " __ARGS__ --spring.profiles.active=prod > " + PH_LOG + " 2>&1 &\n"
                        + "echo $! > app.pid";
                restart = stop + "\nsleep 2\n" + start;
            }
            case "fatjar" -> {
                start = linuxEnter()
                        + "nohup java __JVMARGS__ -jar " + PH_ENTRY + " __ARGS__ > " + PH_LOG + " 2>&1 &\n"
                        + "echo $! > app.pid";
                restart = stop + "\nsleep 2\n" + start;
            }
            case "static", "nginx" -> {
                // nginx 自建 pid 文件（相对前缀目录），状态/停止只认本项目，避免误杀/误报其它项目的 nginx。
                // 必须显式给出 -p/-c：nginx 默认使用编译期前缀（Debian 为 /usr/share/nginx），
                // 裸 nginx 会去读系统配置并抢占系统端口（实测撞 0.0.0.0:8080），
                // 静态项目因此无法在自己的目录里独立运行。前缀所需目录由脚本自行补齐。
                String pid = "logs/nginx.pid";
                String dirs = "mkdir -p logs temp/client_body_temp temp/proxy_temp temp/fastcgi_temp temp/uwsgi_temp temp/scgi_temp\n";
                // 启动必须把 nginx 的 stdio 重定向掉：nginx 会 daemon 化，若继承 SSH channel 的 stdout/stderr，
                // 执行器等不到 channel EOF 会一直挂到超时（退出码 -1），并留下不受管的 nginx 与 pid 文件竞态；
                // 桌面实测「主机上手工跑没问题、经执行器下发就失败」即此因。
                String nginxCtl = "nginx -p '" + PH_PATH + "' -c conf/nginx.conf";
                String nginx = "nginx -p '" + PH_PATH + "' -c conf/nginx.conf > /dev/null 2>&1 < /dev/null";
                // 先读取 pid 再停止：若写成 `[ -f pid ]` 之后再 `cat`，nginx 退出时会删除 pid 文件，
                // 两次操作之间产生竞态并输出「cat: logs/nginx.pid: 没有那个文件」噪声；
                // 更关键的是必须先取到 pid —— `nginx -s stop` 成功后 nginx 会自行删除 pid 文件，
                // 之后再读就取不到 pid，「等待退出」整段会被跳过，脚本立刻返回而 nginx 仍在退出，
                // 紧随其后的 start 就会撞端口（bind: Address already in use）。
                String readPid = "pid=$(cat " + pid + " 2>/dev/null || true)\n"
                        // 陈旧 pid 必须视为无效：回滚会把备份里的 logs/nginx.pid 还原回来，
                        // 那份 pid 指向的是备份时刻的旧进程（通常早已退出），若直接采用会导致
                        // 「读到死 pid → 判定已停止 → 不执行停止」，真实进程继续占端口，
                        // 紧接着的 start 撞 bind: Address already in use。
                        + "if [ -n \"$pid\" ] && ! kill -0 \"$pid\" 2>/dev/null; then pid=\"\"; fi\n"
                        // 兜底：无 pid（或 pid 已陈旧）时按前缀找回真实 master。
                        // 模式必须用括号转义（nginx: [m]aster ...）：执行器以 `bash -c '<脚本文本>'`
                        // 下发时，脚本自身 shell 的命令行就包含这段模式串，未转义的 pgrep -f 会匹配到
                        // 自己 → kill -0 永远为真 → 等待循环耗满预算 → 执行器超时（退出码 -1），
                        // 且随后的 kill -9 可能误杀执行 shell。主机上手工 `sh stop.sh` 不复现该问题。
                        + "if [ -z \"$pid\" ]; then pid=$(pgrep -f 'nginx: [m]aster process nginx -p "
                        + PH_PATH + "' 2>/dev/null | head -1); fi\n";
                // pid 文件只能在确认进程已退出后删除：先删掉会让运行中的实例失去唯一句柄
                String stopBody = readPid
                        + "if [ -n \"$pid\" ]; then\n"
                        // 必须在下发 stop 之前抓子进程：master 一退出，worker 会被 reparent 到 init，
                        // 之后 pgrep -P 再也找不到它们；而 kill -9 master 会留下继承监听套接字的孤儿 worker，
                        // 表现为「端口仍被占用但 status 显示 stopped」且无法再停止。
                        + "  kids=$(pgrep -P $pid 2>/dev/null)\n"
                        + "  " + nginxCtl + " -s stop 2>/dev/null || true\n"
                        + "  i=0\n"
                        + "  while kill -0 $pid 2>/dev/null && [ $i -lt 5 ]; do\n"
                        + "    sleep 1\n"
                        + "    i=$((i + 1))\n"
                        + "  done\n"
                        + "  kill -9 $pid 2>/dev/null\n"
                        + "  if [ -n \"$kids\" ]; then kill -9 $kids 2>/dev/null; fi\n"
                        + "  i=0\n"
                        + "  while kill -0 $pid 2>/dev/null && [ $i -lt 3 ]; do\n"
                        + "    sleep 1\n"
                        + "    i=$((i + 1))\n"
                        + "  done\n"
                        + "fi\n"
                        + "rm -f " + pid;
                String runningCheck = readPid + "if [ -n \"$pid\" ] && kill -0 $pid 2>/dev/null; then\n";
                start = dirs + nginx;
                restart = runningCheck
                        + "  " + nginxCtl + " -s reload > /dev/null 2>&1\n"
                        + "else\n"
                        + indent(stopBody, "  ") + "\n"
                        + "  sleep 2\n"
                        + indent(start, "  ") + "\n"
                        + "fi";
                return new Scripts(start,
                        stopBody,
                        restart,
                        runningCheck + "  echo running\nelse\n  echo stopped\nfi");
            }
            case "separated" -> {
                start = linuxEnter()
                        + "chmod +x " + PH_ENTRY + ";\n"
                        + "nohup ./" + PH_ENTRY + " __ARGS__ > " + PH_LOG + " 2>&1 &\n"
                        + "echo $! > app.pid";
                restart = stop + "\nsleep 2\n" + start;
            }
            default -> {
                return new Scripts("./start.sh", "./stop.sh", "./restart.sh", LINUX_STATUS);
            }
        }
        return new Scripts(start, stop, restart, LINUX_PID_STATUS);
    }

    /**
     * Windows 启动片段：优先经 WMI {@code Win32_Process.Create} 脱离 SSH/WinRM 会话启动，
     * 并把进程号写入 app.pid。
     *
     * <p>WMI 被主机安全产品拦截时（实测 {@code ReturnValue=2}）回退到<b>计划任务</b>：
     * 进程由 Task Scheduler 服务拉起，完全脱离会话与作业对象。</p>
     *
     * <p>回退方案的选择是实测结论，不能想当然：{@code Start-Process} 看起来能"脱离"，但
     * 它的子进程仍属于调用方会话的作业对象，会话关闭即被回收 —— 实测启动后在全新
     * exec 会话里已是 DEAD，且端口从未进入监听（app.log 有 LISTENING 但进程已消失）。
     * 计划任务方式实测跨会话存活、端口归属正确。</p>
     *
     * <p>命令行放进启动器 {@code .cmd} 再把 {@code .cmd} 交给 {@code schtasks /tr}：
     * {@code /tr} 只接受单个不含空格的路径，把含空格的可执行文件路径直接塞进去会被
     * 拆成多个参数（实测报 {@code Invalid argument/option - 'Files\Amazon'}）。</p>
     *
     * @param exe       可执行程序
     * @param args      参数串
     * @param matchName 计划任务回退时用于定位目标进程的命令行特征（一般是主入口名）
     * @return 启动脚本
     */
    private static String winStart(String exe, String args, String matchName) {
        String tail = args == null || args.isEmpty() ? "" : " " + args;
        String inner = exe + tail;
        String match = matchName == null || matchName.isBlank() ? exe : matchName;
        // 启动器内容由 Java 侧拼好，避免在 PowerShell 里再拼一次命令行导致引号层级失控。
        // 启动器必须先 cd 到项目目录：计划任务的工作目录默认是 C:\Windows\System32，
        // 而入口名是相对路径（smoke.jar），不切目录会「java 起来了又立刻退出」。
        // 注意每条命令各占一个数组元素 —— PowerShell 单引号字符串里写 \r\n 是字面文本，不是换行。
        String cdLine = "cd /d \"" + PH_PATH + "\"";
        String startLine = "start \"\" /b \"" + exe + "\"" + tail + " > \"" + PH_LOG + "\" 2>&1";
        String launcherPs = startLine.replace("'", "''");
        String cdPs = cdLine.replace("'", "''");
        return "$inner = '" + inner + "'\n"
                + "$cmd = 'cmd /c ' + $inner + ' > \"" + PH_LOG + "\" 2>&1'\n"
                + "$r = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{ CommandLine = $cmd; CurrentDirectory = '"
                + PH_PATH + "' }\n"
                + "$procId = 0\n"
                + "$task = ''\n"
                + "$wmiOk = ($r.ReturnValue -eq 0)\n"
                + "if (-not $wmiOk) {\n"
                + "  # WMI 创建进程被拒：主机安全产品拦截（实测 ReturnValue=2）。\n"
                + "  # 回退到计划任务 —— 进程由 Task Scheduler 拉起，真正脱离会话。\n"
                + "  $task = 'ms-launch-' + ('" + PH_PATH + "' -replace '[^0-9A-Za-z]', '_')\n"
                + "  $global:__msTask = $task\n"
                + "  $launcher = Join-Path '" + PH_PATH + "' 'ms-launch.cmd'\n"
                + "  [IO.File]::WriteAllLines($launcher, @('@echo off', '" + cdPs + "', '" + launcherPs + "'))\n"
                + "  & schtasks.exe /delete /tn $task /f 2>&1 | Out-Null\n"
                + "  $c = & schtasks.exe /create /tn $task /tr $launcher /sc once /st '23:59' /f 2>&1\n"
                + "  if ($LASTEXITCODE -ne 0) { throw ('计划任务创建失败: ' + ($c -join ' ')) }\n"
                + "  & schtasks.exe /run /tn $task 2>&1 | Out-Null\n"
                + "  if ($LASTEXITCODE -ne 0) { throw ('计划任务触发失败: ' + $task) }\n"
                + "}\n"
                // 进程解析必须放在两条启动路径**之外**无条件执行。
                // 起因：WMI 成功时 Invoke-CimMethod 返回的是 cmd /c 包装壳的 pid，
                // 而包装壳不是程序本身 —— app.pid 指向它，端口属主却是它的 java 子进程，
                // 于是「端口属主 == app.pid」的健康检查与验收断言全部误判为不通过。
                // 此前只在「WMI 被拒 → 回退计划任务」分支里解析进程，WMI 恢复正常后就完全失效。
                + "# 解析真正的程序进程：命令行含主入口名的候选里，取**没有候选后代**的那个。\n"
                + "# 判据说明（实测进程树，父进程为 WmiPrvSE 说明走的是 WMI 路径）：\n"
                + "#   cmd /c java -jar smoke.jar ...   ppid=WmiPrvSE.exe   有候选后代 → 包装壳\n"
                + "#   java -jar smoke.jar ...         ppid=上述 cmd        无候选后代 → 目标\n"
                + "# 反过来判（取「没有候选祖先」者）会正好选中包装壳 —— 这个方向错过一次。\n"
                + "$want = '" + match.replace("'", "''") + "'\n"
                + "for ($i = 0; $i -lt 40; $i++) {\n"
                + "  $all = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue)\n"
                + "  $byId = @{}\n"
                + "  foreach ($p in $all) { $byId[[int]$p.ProcessId] = $p }\n"
                + "  $hit = @($all | Where-Object {\n"
                + "    $_.CommandLine -and $_.ProcessId -ne $PID -and $_.CommandLine -like ('*' + $want + '*') })\n"
                + "  $target = @()\n"
                + "  foreach ($h in $hit) {\n"
                + "    $isWrap = $false\n"
                + "    foreach ($o in $hit) {\n"
                + "      if ([int]$o.ProcessId -eq [int]$h.ProcessId) { continue }\n"
                + "      $pp = [int]$o.ParentProcessId\n"
                + "      $g = 0\n"
                + "      while ($pp -and $g -lt 32) {\n"
                + "        if ($pp -eq [int]$h.ProcessId) { $isWrap = $true; break }\n"
                + "        if (-not $byId.ContainsKey($pp)) { break }\n"
                + "        $pp = [int]$byId[$pp].ParentProcessId\n"
                + "        $g++\n"
                + "      }\n"
                + "      if ($isWrap) { break }\n"
                + "    }\n"
                + "    if (-not $isWrap) { $target += $h }\n"
                + "  }\n"
                + "  if ($target.Count -gt 0) { $target = @($target | Sort-Object CreationDate -Descending) }\n"
                + "  # 时序门控：Invoke-CimMethod 刚返回时，真正的程序进程可能尚未创建完成，\n"
                + "  # 此时候选里只有 cmd 包装壳一个。它「没有候选后代」会被判成目标，\n"
                + "  # 于是 app.pid 写进包装壳，而端口属主是稍后才出现的 java 子进程 ——\n"
                + "  # 健康检查与验收断言都会误判为「端口不属于应用」。\n"
                + "  # 因此只有「候选里同时存在包装壳与目标」才认定解析完成；\n"
                + "  # 只见单个候选时继续等，不提前落定。\n"
                + "  $wrapped = $hit.Count - $target.Count\n"
                + "  if ($target.Count -gt 0 -and $wrapped -gt 0) { $procId = [int]$target[0].ProcessId; break }\n"
                + "  Start-Sleep -Milliseconds 500\n"
                + "}\n"
                // 解析结果打进任务日志：排查「端口属主 != app.pid」这类问题时，
                // 需要看到当时有哪些候选、最终判成了谁，不必再到目标机上加探针。
                // 注意：PowerShell 不支持用行尾 + 续行表达式（换行即视为新语句，
                // 括号会不闭合而报 ParserError），因此这里用数组字面量跨行拼接。
                + "$__r = @('resolve: want=' + $want,\n"
                + "  'hit=' + (($hit | ForEach-Object { $_.ProcessId }) -join ','),\n"
                + "  'target=' + (($target | ForEach-Object { $_.ProcessId }) -join ','),\n"
                + "  'picked=' + $procId)\n"
                + "Write-Output ($__r -join ' ')\n"
                + "if ($task) { & schtasks.exe /delete /tn $task /f 2>&1 | Out-Null }\n"
                + "if ($procId -le 0 -and $wmiOk) { $procId = [int]$r.ProcessId }\n"
                + "if ($procId -le 0) {\n"
                + "  throw ('启动失败：未能确定程序进程 (WMI code=' + $r.ReturnValue + ', want=' + $want + ')')\n"
                + "}\n"
                + "if (-not $wmiOk) { Write-Output ('WMI 创建进程被拒(code=' + $r.ReturnValue + ')，已回退计划任务启动 pid=' + $procId) }\n"
                + "$procId | Out-File 'app.pid' -Encoding ascii\n"
                + "Write-Output $procId";
    }

    /**
     * Windows 启动片段（进程特征取可执行文件名）。
     *
     * @param exe  可执行程序
     * @param args 参数串
     * @return 启动脚本
     */
    private static String winStart(String exe, String args) {
        return winStart(exe, args, exe);
    }

    /**
     * Windows 静态/nginx 启动片段：先补齐 nginx 前缀所需目录，再以显式前缀与配置启动。
     *
     * <p>与 Linux 侧同理必须给出 {@code -p/-c}：让 nginx 只在项目目录内取配置与写日志，
     * 而不是落到安装目录的 config。{@code temp/*} 缺失时 nginx 启动即报
     * {@code CreateDirectory() ... failed}，故由脚本自行补齐，不依赖宿主预置。</p>
     *
     * @return 启动脚本
     */
    private static String winStaticStart() {
        return "foreach ($d in @('logs', 'temp', 'temp\\client_body_temp', 'temp\\proxy_temp', "
                + "'temp\\fastcgi_temp', 'temp\\uwsgi_temp', 'temp\\scgi_temp')) {\n"
                + "  New-Item -ItemType Directory -Force -Path $d | Out-Null\n"
                + "}\n"
                + winStart("nginx.exe", "-p " + PH_PATH + " -c conf/nginx.conf");
    }

    private static Scripts builtinWindows(String projectType, String mainEntry) {
        String type = projectType == null ? "" : projectType.trim().toLowerCase();
        String entry = mainEntry == null ? "" : mainEntry.trim().toLowerCase();
        // 停止/重启前的停止段都带上孤儿兜底（app.pid 陈旧时唯一能真正停掉进程的手段）
        String stop = WIN_PID_STOP + winOrphanReaper(mainEntry);
        String stopBody = WIN_PID_STOP_BODY + winOrphanReaper(mainEntry);
        switch (type) {
            case "springboot", "fatjar" -> {
                // 与 Linux 模板对齐：必须把 JVM 参数与应用参数传下去。
                // 原先写成 `-jar <入口>` 而不带 __JVMARGS__/__ARGS__，项目里配的
                // cmdArgs（如 --server.port=NNNN）与 jvmArgs 会被静默忽略。
                String start = winStart("java", "__JVMARGS__ -jar " + PH_ENTRY + " __ARGS__", mainEntry)
                        + "\n" + WIN_VERIFY_RUNNING;
                return new Scripts(start, stop, stopBody + "\nStart-Sleep -Seconds 2\n" + start, WIN_STATUS);
            }
            case "static", "nginx" -> {
                // 停/重启用 WIN_PID_STOP_BODY（不带末尾 exit 0）：WIN_PID_STOP 会以 exit 0 结束整个脚本，
                // 重启时停完即退出、启动段永不执行，表现为「restart 报成功但 STATUS 是 stopped」。
                String start = winStaticStart() + "\n" + WIN_VERIFY_RUNNING;
                return new Scripts(start, stop, stopBody + "\nStart-Sleep -Seconds 2\n" + start, WIN_STATUS);
            }
            case "separated" -> {
                // 与 Linux 模板对齐：分离式应用同样要能收到 __ARGS__
                String start = (entry.endsWith(".ps1")
                        ? winStart("powershell", "-NoProfile -ExecutionPolicy Bypass -File " + PH_ENTRY)
                        : winStart(PH_ENTRY, "__ARGS__")) + "\n" + WIN_VERIFY_RUNNING;
                return new Scripts(start, stop, stopBody + "\nStart-Sleep -Seconds 2\n" + start, WIN_STATUS);
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

    /**
     * 给多行文本的每一行加前缀（用于把片段嵌入 if/else 分支时保持缩进可读）。
     *
     * @param text   多行文本
     * @param prefix 前缀
     * @return 加前缀后的文本
     */
    private static String indent(String text, String prefix) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(prefix).append(lines[i]);
        }
        return sb.toString();
    }
}
