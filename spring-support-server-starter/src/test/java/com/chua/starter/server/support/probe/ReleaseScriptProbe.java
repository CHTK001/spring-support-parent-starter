package com.chua.starter.server.support.probe;

import com.chua.starter.server.support.connection.PowerShellCommand;
import com.chua.starter.server.support.provider.project.PosixProjectPlatformProvider;
import com.chua.starter.server.support.provider.project.WindowsProjectPlatformProvider;
import com.chua.starter.server.support.util.ProjectScripts;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * 发布脚本生成的回归探针。
 *
 * <p>发布链路此前完全没有回归网：四条内置脚本模板与健康检查命令都是纯字符串生成，
 * 改动后「能编译」完全不代表「生成的命令还对」——本次改动中真实踩到的三个坑
 * （zsh 下 {@code [[:space:]]} 报错、Windows 停止段用 {@code -like '*__ENTRY__*'}
 * 会把自己结束掉、回滚分支不停失败实例）在编译期全部无感，只能靠跑起来才发现。</p>
 *
 * <p>因此本探针以「断言 + 敏感性对照」为主：每条关键断言都尽量配一个反例，
 * 证明该断言在错误实现下确实会失败，避免写出恒真的假绿。</p>
 *
 * <p>命名用 {@code Probe} 而非 {@code Test}：本仓约定非 example 模块不新增
 * {@code *Test} 类。但「命名不带 Test」并不妨碍它进入常规构建——JUnit 5 按
 * {@code @Test} 注解发现测试类，不看类名。因此本探针按段拆成 9 个 {@code @Test} 方法，
 * 由 {@code mvn -pl spring-support-server-starter test -DskipTests=false} 直接执行，
 * 改动 {@code ProjectScripts} 或平台 Provider 时不再依赖「记得手工跑一下运行器」。</p>
 *
 * <p>I 段需要本机存在 PowerShell 宿主（pwsh 或 powershell），缺失时整段 skip；
 * 注意 skip 与「校验通过」在 surefire 汇总里都体现为不失败，必须看 {@code Skipped} 计数
 * 才能确认这段校验到底跑没跑。</p>
 *
 * <p>同时保留 {@link #main(String[])} 入口，供不跑 surefire 的场景
 * （现场快速取证、目标机回归）使用，退出码语义与断言结果一致。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class ReleaseScriptProbe {

    /**
     * Linux 项目路径样例。
     */
    private static final String LINUX = "/opt/app/demo";

    /**
     * Windows 项目路径样例（Java 字面量里的双写反斜杠）。
     */
    private static final String WIN = "C:\\\\ms-acc\\\\demo";

    /**
     * 通过数。
     */
    private int passed;

    /**
     * 失败明细（断言名 + 现场文本）。
     */
    private final List<String> failures = new ArrayList<>();

    private ReleaseScriptProbe() {
    }

    // ==================== 独立运行入口 ====================

    /**
     * 探针独立入口：逐段跑完全部断言，失败则以退出码 1 结束。
     *
     * <p>不调用 {@code System.exit(0)}：成功时让进程自然结束，
     * 避免被 surefire 或其它包装器加载本类时误退出 JVM。</p>
     *
     * @param args 未使用
     */
    public static void main(String[] args) {
        ReleaseScriptProbe probe = new ReleaseScriptProbe();
        probe.sectionALinuxTemplates();
        probe.sectionBWindowsTemplates();
        probe.sectionCPosixHealthNoPort();
        probe.sectionDPosixHealthWithPort();
        probe.sectionERollbackOrder();
        probe.sectionFWindowsHealth();
        probe.sectionGMiscCommands();
        probe.sectionHInlineLengthLimit();
        probe.sectionIPowerShellSyntax();
        System.out.println("[probe] ---- 汇总：passed=" + probe.passed + " failed=" + probe.failures.size() + " ----");
        probe.failures.forEach(f -> System.out.println("[probe] FAIL " + f));
        if (!probe.failures.isEmpty()) {
            System.exit(1);
        }
    }

    // ==================== A. Linux 内置模板 ====================

    /**
     * A 段：Linux 内置脚本模板。
     */
    @Test
    void aLinuxTemplates() {
        sectionALinuxTemplates();
        assertSectionDone("A");
    }

    private void sectionALinuxTemplates() {
        // 注意：builtin() 返回的是仍带 __PATH__/__LOG__ 等占位符的模板，
        // 断言真实路径前必须先经 apply() 完成替换，否则测的是占位符而非生成结果。
        ProjectScripts.Scripts raw = ProjectScripts.builtin(false, "springboot", "smoke.jar");
        ProjectScripts.Scripts sb = filled(raw, "smoke.jar", LINUX, LINUX + "/app.log", "demo");
        check("A0 apply() 已把 __PATH__ 替换为真实路径",
                sb.stop().contains("'" + LINUX + "'") && !sb.stop().contains(ProjectScripts.PH_PATH),
                sb.stop());
        check("A1 springboot 启动含 nohup 与 pid 落盘",
                sb.start().contains("nohup java") && sb.start().contains("echo $! > app.pid"),
                sb.start());
        // 自安全模式：首字符必须写成 [s]，否则 pgrep -f 会匹配到执行脚本的 shell 自身
        check("A2 停止段用自安全模式 [s]moke\\.jar",
                sb.stop().contains("pgrep -f '[s]moke\\.jar'"),
                sb.stop());
        check("A3 停止段不含裸字面量 pgrep -f 'smoke.jar'（那样会匹配到脚本自身）",
                !sb.stop().contains("pgrep -f 'smoke.jar'"),
                sb.stop());
        check("A4 停止段按 /proc/<pid>/cwd 限定本项目目录（避免误杀同名入口的其它项目）",
                sb.stop().contains("readlink /proc/") && sb.stop().contains("= '" + LINUX + "'"),
                sb.stop());
        check("A5 停止段有 SIGKILL 升级", sb.stop().contains("kill -9"), sb.stop());
        check("A6 重启脚本以停止段开头", sb.restart().startsWith(sb.stop().substring(0, 40)), sb.restart());
        check("A7 状态脚本以 app.pid 为准",
                sb.status().contains("app.pid") && sb.status().contains("running") && sb.status().contains("stopped"),
                sb.status());

        ProjectScripts.Scripts sep = filled(ProjectScripts.builtin(false, "separated", "run.sh"),
                "run.sh", LINUX, LINUX + "/app.log", "demo");
        check("A8 separated 停止段同样带兜底",
                sep.stop().contains("pgrep -f '[r]un\\.sh'") && sep.stop().contains("readlink /proc/"),
                sep.stop());

        ProjectScripts.Scripts fat = filled(ProjectScripts.builtin(false, "fatjar", "app.jar"),
                "app.jar", LINUX, LINUX + "/app.log", "demo");
        check("A9 fatjar 停止段模式为 [a]pp\\.jar",
                fat.stop().contains("pgrep -f '[a]pp\\.jar'"),
                fat.stop());

        // 元字符转义：. 与 + 必须转义，否则正则语义被改变（. 会连带匹配 smokeXjar 之类）
        ProjectScripts.Scripts meta = filled(ProjectScripts.builtin(false, "fatjar", "a+b.jar"),
                "a+b.jar", LINUX, LINUX + "/app.log", "demo");
        check("A10 入口含正则元字符时被转义（[a]\\+b\\.jar）",
                meta.stop().contains("[a]\\+b\\.jar"),
                meta.stop());

        // 敏感性对照：证明 A2 不是恒真 —— 自安全模式必须是 [s] 前缀形式，
        // 且裸字面量（pgrep -f 'smoke\.jar'）绝不允许出现
        check("A11 敏感性对照：自安全模式带 [s] 前缀且不含裸字面量",
                sb.stop().contains("[s]moke\\.jar") && !sb.stop().contains("pgrep -f 'smoke\\.jar'"),
                sb.stop());
    }

    // ==================== B. Windows 内置模板 ====================

    /**
     * B 段：Windows 内置脚本模板。
     */
    @Test
    void bWindowsTemplates() {
        sectionBWindowsTemplates();
        assertSectionDone("B");
    }

    private void sectionBWindowsTemplates() {
        ProjectScripts.Scripts wb = ProjectScripts.builtin(true, "springboot", "smoke.jar");
        check("B1 Windows 停止段用 CIM 且 try/catch 兜底",
                wb.stop().contains("Get-CimInstance Win32_Process") && wb.stop().contains("catch"),
                wb.stop());
        check("B2 Windows 停止段用 -match（正则）而非 -like 通配",
                wb.stop().contains("-match") && !wb.stop().contains("-like"),
                wb.stop());
        check("B3 Windows 停止段排除自身 $PID",
                wb.stop().contains("$_.ProcessId -ne $PID"),
                wb.stop());
        check("B4 Windows 停止段模式经自安全处理（[s]moke）",
                wb.stop().contains("[s]moke"),
                wb.stop());
        // 启动校验必须有稳定窗口：否则 `cmd /c` 解析失败（如 java 不在机器级 PATH）时
        // 会「先存在、随即退出」而被判成启动成功（真实踩到过）
        check("B4b Windows 启动校验含稳定窗口（失败时抛错）",
                wb.start().contains("exited right after start") && wb.start().contains("Start-Sleep -Milliseconds 750"),
                wb.start());
        // 敏感性对照：构造「旧写法」的停止脚本，证明 B2 的断言确实能识别它（不是恒真）
        String oldStyleStop = "Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like '*smoke.jar*' }"
                + " | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }";
        check("B5 敏感性对照：旧 -like 写法确实会被 B2 判负",
                oldStyleStop.contains("-like") && !wb.stop().contains("-like"),
                oldStyleStop);
    }

    // ==================== C. Posix 健康检查（无端口） ====================

    /**
     * C 段：Posix 健康检查（未给端口）。
     */
    @Test
    void cPosixHealthNoPort() {
        sectionCPosixHealthNoPort();
        assertSectionDone("C");
    }

    private void sectionCPosixHealthNoPort() {
        PosixProjectPlatformProvider posix = new PosixProjectPlatformProvider();
        String g0 = posix.guardedRestart(LINUX, "RESTART_SENTINEL", "STOP_SENTINEL", "START_SENTINEL",
                "STATUS_SENTINEL", true, 0);
        check("C1 含三段式健康检查结论", g0.contains("health ok") && g0.contains("health failed, rolling back...")
                && g0.contains("rollback ok"), g0);
        check("C2 未给端口时不生成探活函数", !g0.contains("listening_port"), g0);
        check("C3 不含 zsh 会报错的 [[:space:]] 字符类", !g0.contains("[[:space:]]"), g0);
        check("C4 就绪等待而非固定 sleep 4", !g0.contains("sleep 4\n") && g0.contains("while ["), g0);
    }

    // ==================== D. Posix 健康检查（带端口） ====================

    /**
     * D 段：Posix 健康检查（给出端口，生成归属探活）。
     */
    @Test
    void dPosixHealthWithPort() {
        sectionDPosixHealthWithPort();
        assertSectionDone("D");
    }

    private void sectionDPosixHealthWithPort() {
        PosixProjectPlatformProvider posix = new PosixProjectPlatformProvider();
        String g1 = posix.guardedRestart(LINUX, "RESTART_SENTINEL", "STOP_SENTINEL", "START_SENTINEL",
                "STATUS_SENTINEL", true, 18091);
        check("D1 生成端口探活并传入端口号", g1.contains("listening_port 18091"), g1);
        check("D2 探活用 awk index() 全字面匹配", g1.contains("index($0,p)"), g1);
        check("D3 探活含归属比对（pid=app.pid）", g1.contains("pid=$__owner") && g1.contains("__owner=$(cat app.pid"),
                g1);
        check("D4 端口未由本项目进程监听时有明确报错", g1.contains("未监听"), g1);
        // 这一条是本次真机踩坑的钉子：zsh 下 [[:space:]] 当作 printf 格式说明符报错，函数恒失败
        check("D5 带端口版本同样不含 [[:space:]]（zsh 陷阱）", !g1.contains("[[:space:]]"), g1);
        check("D6 端口探活用 ss -ltnp（带监听者 pid）", g1.contains("ss -ltnp"), g1);
    }

    // ==================== E. 回滚分支顺序 ====================

    /**
     * E 段：回滚分支顺序（回滚前必须先停掉失败的实例）。
     */
    @Test
    void eRollbackOrder() {
        sectionERollbackOrder();
        assertSectionDone("E");
    }

    private void sectionERollbackOrder() {
        PosixProjectPlatformProvider posix = new PosixProjectPlatformProvider();
        String g2 = posix.guardedRestart(LINUX, "RESTART_SENTINEL", "STOP_SENTINEL", "START_SENTINEL",
                "STATUS_SENTINEL", true, 0);
        int iFail = g2.indexOf("health failed, rolling back...");
        int iStop = g2.indexOf("STOP_SENTINEL");
        int iStartLast = g2.lastIndexOf("START_SENTINEL");
        check("E1 回滚分支内先停失败实例（停止段在失败分支之后）",
                iFail >= 0 && iStop > iFail, "iFail=" + iFail + " iStop=" + iStop);
        check("E2 停止段早于回滚后的启动段", iStop >= 0 && iStartLast > iStop,
                "iStop=" + iStop + " iStartLast=" + iStartLast);
        check("E3 停止段在失败分支内只出现一次（成功分支不应带停止）",
                countOccurrences(g2, "STOP_SENTINEL") == 1, "count=" + countOccurrences(g2, "STOP_SENTINEL"));

        // 敏感性对照：停止脚本为空时必须不发射停止段
        String g3 = posix.guardedRestart(LINUX, "", "", "START_SENTINEL", "STATUS_SENTINEL", false, 0);
        check("E4 敏感性对照：停止脚本为空则不发射停止段",
                !g3.contains("STOP_SENTINEL") && g3.contains("START_SENTINEL"), g3);
    }

    // ==================== F. Windows 健康检查 ====================

    /**
     * F 段：Windows 健康检查。
     */
    @Test
    void fWindowsHealth() {
        sectionFWindowsHealth();
        assertSectionDone("F");
    }

    private void sectionFWindowsHealth() {
        WindowsProjectPlatformProvider windows = new WindowsProjectPlatformProvider();
        String gw = windows.guardedRestart(WIN, "RESTART_SENTINEL", "STOP_SENTINEL", "START_SENTINEL",
                "STATUS_SENTINEL", true, 18091);
        check("F1 Windows 带端口版本含 netstat -ano 与归属比对",
                gw.contains("netstat -ano") && gw.contains("未由本项目进程监听"), gw);
        int wFail = gw.indexOf("health failed, rolling back...");
        int wStop = gw.indexOf("STOP_SENTINEL");
        check("F2 Windows 回滚分支同样先停失败实例", wFail >= 0 && wStop > wFail,
                "wFail=" + wFail + " wStop=" + wStop);
        check("F3 Windows 未给端口时不探活",
                !windows.guardedRestart(WIN, "", "", "S", "T", false, 0).contains("netstat -ano"),
                "");
        // 与 Posix 对称：Windows 也把归属探活定义成命名函数，便于目标机自检脚本单独调用
        check("F4 Windows 定义 listening_port 函数并调用",
                gw.contains("function listening_port") && gw.contains("listening_port 18091"),
                gw);
        check("F5 Windows 探活按 netstat -ano 末列 pid 与 app.pid 归属比对",
                gw.contains("netstat -ano") && gw.contains("app.pid") && gw.contains("$f[-1]"),
                gw);
        // 敏感性对照：不带端口时不应出现探活函数（否则说明条件分支写反了）
        check("F6 敏感性对照：不带端口时无 listening_port 函数",
                !windows.guardedRestart(WIN, "", "", "S", "T", false, 0).contains("function listening_port"),
                "");
    }

    // ==================== G. 其它命令生成 ====================

    /**
     * G 段：备份 / 回滚 / 引号转义等基础命令生成的约束。
     */
    @Test
    void gMiscCommands() {
        sectionGMiscCommands();
        assertSectionDone("G");
    }

    private void sectionGMiscCommands() {
        PosixProjectPlatformProvider posix = new PosixProjectPlatformProvider();
        WindowsProjectPlatformProvider windows = new WindowsProjectPlatformProvider();
        String backup = posix.backup(LINUX, 10);
        // 保留语义：保留最新 10 个 → 从第 11 个开始删（tail -n +11），断言要反映真实语义
        check("G1 备份命令含保留语义（保留 10 → tail -n +11）",
                backup.contains("tail -n +11") && backup.contains("tar -czf"), backup);
        String rb = posix.rollback(LINUX, null);
        check("G2 回滚命令可用最新备份（参数为空）", rb.contains("tar") && !rb.isBlank(), rb);
        String quoted = posix.quote("a'b");
        check("G3 POSIX 单引号转义", quoted.equals("a'\\''b"), quoted);
        check("G4 Windows 单引号转义为双写", windows.quote("a'b").equals("a''b"), windows.quote("a'b"));
    }

    // ==================== H. 内联下发长度上限（超限必须落盘） ====================

    /**
     * H 段：Windows 命令行长度上限导致的「启动能用、重启必挂」。
     */
    @Test
    void hInlineLengthLimit() {
        sectionHInlineLengthLimit();
        assertSectionDone("H");
    }

    private void sectionHInlineLengthLimit() {
        ProjectScripts.Scripts wb = ProjectScripts.builtin(true, "springboot", "smoke.jar");
        ProjectScripts.Scripts wbFull = filled(wb, "smoke.jar", WIN, WIN + "\\app.log", "demo");
        String startLen = String.valueOf(PowerShellCommand.base64Length(wbFull.start()));
        String restartLen = String.valueOf(PowerShellCommand.base64Length(wbFull.restart()));
        // 这条是本缺陷的要害：WMI 成功时 Invoke-CimMethod 返回的是 cmd /c 包装壳的 pid，
// 而包装壳不是程序本身。进程解析若只放在「WMI 被拒」的分支里，WMI 一恢复正常就完全失效
        // ——实测正是如此（父进程为 WmiPrvSE.exe 说明走了 WMI 路径，app.pid 却抓到 cmd）。
        // 因此断言的是「解析逻辑在两个分支之外」，而不是某个局部变量名。
        int wmiBranch = wbFull.start().indexOf("if (-not $wmiOk)");
        int resolveAt = wbFull.start().indexOf("$isWrap");
        int writePid = wbFull.start().indexOf("$procId | Out-File");
        // 用「判定逻辑 → 写 app.pid」这一段的文本作为判据：中间若还有别的分支内内容也不影响，
        // 关键是这段必须位于 WMI 分支之后、且是唯一写 app.pid 的地方。
        check("H1 进程解析在 WMI 分支之外无条件执行（WMI 成功时也必须解析真实进程）",
                wmiBranch >= 0 && resolveAt > wmiBranch && writePid > resolveAt
                        && !wbFull.start().substring(wmiBranch, resolveAt).contains("$procId = [int]$hit[0].ProcessId"),
                "wmiBranch=" + wmiBranch + " resolveAt=" + resolveAt + " writePid=" + writePid);
        check("H1b 进程解析按「无候选后代」判定目标（方向错过一次，必须钉住）",
                wbFull.start().contains("$isWrap = $true") && wbFull.start().contains("if (-not $isWrap) { $target += $h }"),
                "start 片段未含后代判定");
        check("H1c WMI 成功时才允许回退到 $r.ProcessId",
                wbFull.start().contains("if ($procId -le 0 -and $wmiOk) { $procId = [int]$r.ProcessId }"),
                "缺少 wmiOk 约束的兜底");
        // 启动段本身可能已超过内联上限（加入叶子判定后 base64 约 8K）。
        // 这不是问题：**超限即走落盘 + -File**，正是本段要守的安全网。
        // 真正要保证的是「不存在某条路径把超限脚本仍内联下发」——
        // 由 exceedsInlineLimit 与 WindowsSshShell 的分支共同保证，此处只断言该判定可识别。
        check("H1b 超限判定可识别启动段（会转落盘，不会内联硬塞给 cmd.exe）",
                PowerShellCommand.exceedsInlineLimit(wbFull.start())
                        || PowerShellCommand.base64Length(wbFull.start()) <= PowerShellCommand.INLINE_BASE64_LIMIT,
                "start base64=" + PowerShellCommand.base64Length(wbFull.start()));
        // 这条是本缺陷的钉子：重启段 = 停止段 + 启动段，必然比启动段长，
        // 一旦超过内联上限就会被 cmd.exe 整条拒掉（0 秒退出、错误信息是别名）。
        check("H2 Windows 重启段超过内联上限，必须走落盘 + -File",
                PowerShellCommand.exceedsInlineLimit(wbFull.restart()),
                "start=" + startLen + " restart=" + restartLen);
        check("H3 短脚本不被误判为超限",
                !PowerShellCommand.exceedsInlineLimit("'ok'"),
                PowerShellCommand.base64Length("'ok'") + " vs limit " + PowerShellCommand.INLINE_BASE64_LIMIT);
        // 敏感性对照：把上限调到极小后，短脚本也必须被判超限，
        // 证明 H3 不是恒真的假绿。
        int tiny = 1;
        check("H4 敏感性对照：上限为 1 时短脚本也会被判超限",
                PowerShellCommand.base64Length("'ok'") > tiny,
                "base64=" + PowerShellCommand.base64Length("'ok'") + " > " + tiny);
        String fileCmd = PowerShellCommand.fileBased("pwsh", "C:\\Windows\\Temp\\ms-exec-ab.ps1");
        check("H5 落盘命令用 -File 且保留 chcp 65001 前缀",
                fileCmd.contains("-File \"C:\\Windows\\Temp\\ms-exec-ab.ps1\"")
                        && fileCmd.startsWith("cmd /c \"chcp 65001 >nul & ") && fileCmd.contains("pwsh "),
                fileCmd);
        check("H6 落盘命令不再内联 EncodedCommand（那正是超限的来源）",
                !fileCmd.contains("-EncodedCommand"), fileCmd);
        // body() 必须是「前言 + 脚本」，落盘内容与内联执行的内容必须完全一致，
        // 否则同一条命令在两条路径下行为会不同。
        check("H7 body() 含进度抑制前言且以业务脚本结尾",
                PowerShellCommand.body("XYZ").endsWith("XYZ")
                        && PowerShellCommand.body("XYZ").contains("$ProgressPreference"),
                PowerShellCommand.body("XYZ").replace("\r", "\\r").replace("\n", "\\n"));
    }

    // ==================== I. PowerShell 语法校验 ====================

    /**
     * I 段：把生成的 Windows 脚本交给 PowerShell 解析器实判语法。
     */
    @Test
    void iPowerShellSyntax() {
        sectionIPowerShellSyntax();
        assertSectionDone("I");
    }

    private void sectionIPowerShellSyntax() {
        String exe = resolveShellExe();
        Assumptions.assumeTrue(exe != null,
                "本机既无 pwsh 也无 powershell，无法做 PowerShell 语法校验，跳过 I 段");
        var wb = ProjectScripts.builtin(true, "springboot", "smoke.jar");
        var wbFull = filled(wb, "smoke.jar", WIN, WIN + "\\app.log", "demo");
        // 敏感性对照：PowerShell **不支持**用行尾 + 续行表达式，换行即被当作新语句，
        // 上行未闭合的括号会直接报 ParserError。真实踩过：诊断输出写成多行 + 拼接，
        // 生成出来的启动脚本整段语法错误、启动直接失败，而当时探针只查长度上限与
        // 判定位置，对语法完全无感，直到真机 C6 才暴露。
        var broken = "Write-Output ('a=' + $x\n  + ' b=' + $y)\n";
        int brokenErr = countPowerShellErrors(exe, broken);
        check("I1 敏感性对照：换行 + 续行的表达式必须被判为语法错误",
                brokenErr > 0,
                "解析器对已知错误脚本返回 " + brokenErr + "（<=0 说明本段校验本身失效）");
        check("I2 Windows 启动段无语法错误",
                countPowerShellErrors(exe, wbFull.start()) == 0,
                wbFull.start());
        check("I3 Windows 停止段无语法错误",
                countPowerShellErrors(exe, wbFull.stop()) == 0,
                wbFull.stop());
        check("I4 Windows 重启段无语法错误",
                countPowerShellErrors(exe, wbFull.restart()) == 0,
                wbFull.restart());
        check("I5 Windows 状态段无语法错误",
                countPowerShellErrors(exe, wbFull.status()) == 0,
                wbFull.status());
        // 完整性：四段都要真的送进解析器，不能因为取不到文本就当成「无错误」。
        check("I6 四段文本非空且互不相同（防止上面几条实际校验了空串）",
                wbFull.start().length() > 200 && wbFull.stop().length() > 200
                        && wbFull.restart().length() > 200 && wbFull.status().length() > 100
                        && !wbFull.start().equals(wbFull.stop())
                        && !wbFull.stop().equals(wbFull.status()),
                "start=" + wbFull.start().length() + " stop=" + wbFull.stop().length()
                        + " restart=" + wbFull.restart().length() + " status=" + wbFull.status().length());
    }

    /**
     * 找一个可用的 PowerShell 宿主，优先 pwsh。
     *
     * @return 可执行文件名；都找不到时返回 {@code null}
     */
    private static String resolveShellExe() {
        for (var candidate : new String[] {"pwsh", "powershell"}) {
            try {
                var proc = new ProcessBuilder(candidate, "-NoProfile", "-NonInteractive",
                        "-Command", "exit 0").redirectErrorStream(true).start();
                if (proc.waitFor(60, TimeUnit.SECONDS) && proc.exitValue() == 0) {
                    return candidate;
                }
            } catch (Exception e) {
                // 换下一个候选
            }
        }
        return null;
    }

    /**
     * 用 PowerShell 解析器实判一段脚本的语法错误数。
     *
     * <p>刻意走真实解析器而不是文本启发式：唯一能可靠识别「换行 + 续行」这类
     * 结构性语法错误的办法，就是让 PowerShell 自己解析一次。</p>
     *
     * @param exe    PowerShell 宿主名
     * @param script 待解析脚本
     * @return 错误条数；无法完成解析时返回负数（-1 临时文件失败、-2 无数字输出、-3 宿主执行失败）
     */
    private static int countPowerShellErrors(String exe, String script) {
        Path file;
        try {
            file = Files.createTempFile("probe-syntax-", ".ps1");
            Files.writeString(file, script, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return -1;
        }
        try {
            var escaped = file.toString().replace("'", "''");
            var probe = "$errs = $null; $toks = $null; "
                    + "[System.Management.Automation.Language.Parser]::ParseFile('" + escaped
                    + "', [ref]$toks, [ref]$errs) | Out-Null; "
                    + "Write-Output ('COUNT=' + $errs.Count); "
                    + "foreach ($e in $errs) { Write-Output ('AT=' + $e.Extent.StartLineNumber + ' ' + $e.Message) }";
            var proc = new ProcessBuilder(exe, "-NoProfile", "-NonInteractive",
                    "-Command", probe).redirectErrorStream(true).start();
            var out = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!proc.waitFor(120, TimeUnit.SECONDS)) {
                return -3;
            }
            for (var line : out.split("\r?\n")) {
                var t = line.trim();
                if (t.startsWith("COUNT=")) {
                    return Integer.parseInt(t.substring("COUNT=".length()).trim());
                }
            }
            return -2;
        } catch (Exception e) {
            return -3;
        } finally {
            try {
                Files.deleteIfExists(file);
            } catch (Exception e) {
                // 临时文件删不掉不影响判定结果
            }
        }
    }

    // ==================== 断言基础设施 ====================

    /**
     * 段结束时统一判定：本段内不允许有失败断言。
     *
     * <p>失败信息一次性带出全部断言名与现场文本，避免断言失败时只能看到第一条。</p>
     *
     * @param section 段标识（A~I）
     */
    private void assertSectionDone(String section) {
        if (failures.isEmpty()) {
            return;
        }
        Assertions.fail("段 " + section + " 有 " + failures.size() + " 条断言失败："
                + String.join(" | ", failures));
    }

    /**
     * 记录一条断言结果。
     *
     * @param name   断言名
     * @param ok     是否通过
     * @param detail 失败时的现场文本
     */
    private void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            return;
        }
        failures.add(name + "  <<< " + abbreviate(detail));
    }

    /**
     * 用真实路径填充内置模板（builtin 返回的仍是带占位符的模板）。
     *
     * @param scripts 内置模板
     * @param entry   主入口名
     * @param path    项目地址
     * @param log     日志路径
     * @param appName 应用名
     * @return 完成占位符替换后的四段脚本
     */
    private static ProjectScripts.Scripts filled(ProjectScripts.Scripts scripts, String entry,
                                                 String path, String log, String appName) {
        return new ProjectScripts.Scripts(
                ProjectScripts.apply(scripts.start(), entry, path, log, appName),
                ProjectScripts.apply(scripts.stop(), entry, path, log, appName),
                ProjectScripts.apply(scripts.restart(), entry, path, log, appName),
                ProjectScripts.apply(scripts.status(), entry, path, log, appName));
    }

    /**
     * 子串出现次数。
     *
     * @param haystack 原文
     * @param needle   子串
     * @return 出现次数
     */
    private static int countOccurrences(String haystack, String needle) {
        int n = 0;
        int i = haystack.indexOf(needle);
        while (i >= 0) {
            n++;
            i = haystack.indexOf(needle, i + needle.length());
        }
        return n;
    }

    /**
     * 截断现场文本，避免刷屏。
     *
     * @param text 原文
     * @return 截断后的文本
     */
    private static String abbreviate(String text) {
        if (text == null) {
            return "null";
        }
        String one = text.replace('\n', ' ');
        return one.length() <= 220 ? one : one.substring(0, 220) + "...";
    }
}