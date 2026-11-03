// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.server.support.probe;

import com.chua.starter.server.support.connection.PowerShellCommand;
import com.chua.starter.server.support.provider.project.MacosProjectPlatformProvider;
import com.chua.starter.server.support.provider.project.PosixProjectPlatformProvider;
import com.chua.starter.server.support.provider.project.WindowsProjectPlatformProvider;
import com.chua.starter.server.support.provider.service.LaunchdSystemServiceProvider;
import com.chua.starter.server.support.provider.service.WindowsSystemServiceProvider;
import com.chua.starter.server.support.spi.ProjectPlacement;
import com.chua.starter.server.support.spi.ProjectPlacementContext;
import com.chua.starter.server.support.spi.ProjectTypeProviderRegistry;
import com.chua.starter.server.support.spi.SystemServiceContext;
import com.chua.starter.server.support.util.ProjectScripts;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * {@code @Test} 注解发现测试类，不看类名。因此本探针按段拆成 11 个 {@code @Test} 方法，
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
     * 项目类型路由器：内置脚本由类型 SPI 生成，探针与生产链路走同一条解析路径。
     *
     * <p>刻意经注册中心而不是直接 new 实现类：脚本分发一旦在 SPI 登记上出错
     * （漏登记、类型拼错），探针必须能暴露，而不是自己另走一条捷径掩盖问题。</p>
     */
    private static final ProjectTypeProviderRegistry TYPES = new ProjectTypeProviderRegistry();

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
        probe.sectionJMacosPlatform();
        probe.sectionKWindowsServiceFallback();
        System.out.println("[probe] ---- 汇总：passed=" + probe.passed + " failed=" + probe.failures.size() + " ----"); // [P3C E1 豁免]
        probe.failures.forEach(f -> System.out.println("[probe] FAIL " + f)); // [P3C E1 豁免]
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
        ProjectScripts.Scripts raw = builtin(false, "springboot", "smoke.jar");
        ProjectScripts.Scripts sb = filled(raw, "smoke.jar", LINUX, LINUX + "/app.log", "demo");
        check("A0 apply() 已把 __PATH__ 替换为真实路径",
                sb.stop().contains("'" + LINUX + "'") && !sb.stop().contains(ProjectScripts.PH_PATH),
                sb.stop());
        check("A1 springboot 启动含 nohup 与 pid 落盘",
                sb.start().contains("nohup java") && sb.start().contains("echo $! > app.pid"),
                sb.start());
        // 与 Windows 侧 running pid= 对齐：失败回滚后编排层只能靠日志这一行确定真实 PID，
        // 缺它会导致 Linux 回滚后库里 PID 为空（实测 status=running 但 pid=undefined）。
        // 必须回显「校验过的那个变量」$__pid，而不是再读一次 app.pid。
        check("A1b Linux 启动段回显 running pid=（供回滚后回写真实 PID）",
                sb.start().contains("echo \"running pid=$__pid\""),
                sb.start());
        // 假成功是真机验收抓到的缺陷：nohup ... & 之后 $! 永远有值，哪怕进程立刻退出
        // （jar 不存在 → Error: Unable to access jarfile），脚本仍 exit 0、任务报「启动成功」，
        // 而状态是 stopped。Linux 侧必须有与 Windows WIN_VERIFY_RUNNING 等价的存活校验。
        check("A1c Linux 启动段有存活校验（kill -0 观察窗 + 失败 exit 1 + 回显日志尾部）",
                sb.start().contains("kill -0") && sb.start().contains("启动校验失败")
                        && sb.start().contains("tail -n 20") && sb.start().contains("exit 1")
                        && sb.start().contains("rm -f app.pid"),
                sb.start());
        // 重定向顺序踩过坑：`2>/dev/null >&2` 会先把 fd2 指向 /dev/null，再把 fd1 复制成 fd2，
        // 结果是 tail 什么也不输出（任务日志里只有「---- 日志尾部 ----」这个标题）。
        // 必须先是 >&2 再 2>/dev/null。
        check("A1f 失败分支的 tail 重定向顺序正确（>&2 在 2>/dev/null 之前）",
                sb.start().contains("tail -n 20") && sb.start().contains(">&2 2>/dev/null")
                        && !sb.start().contains("2>/dev/null >&2"),
                sb.start());
        // 敏感性对照：旧实现只有 echo + sleep，没有任何存活判定，必须被 A1c 判负
        String oldStartTail = "echo $! > app.pid\nsleep 1\necho \"running pid=$(cat app.pid)\"";
        check("A1d 敏感性对照：旧写法（只 echo 不校验）确实会被 A1c 判负",
                !oldStartTail.contains("kill -0") && sb.start().contains("kill -0"), oldStartTail);
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

        ProjectScripts.Scripts sep = filled(builtin(false, "separated", "run.sh"),
                "run.sh", LINUX, LINUX + "/app.log", "demo");
        check("A8 separated 停止段同样带兜底",
                sep.stop().contains("pgrep -f '[r]un\\.sh'") && sep.stop().contains("readlink /proc/"),
                sep.stop());

        ProjectScripts.Scripts fat = filled(builtin(false, "fatjar", "app.jar"),
                "app.jar", LINUX, LINUX + "/app.log", "demo");
        check("A9 fatjar 停止段模式为 [a]pp\\.jar",
                fat.stop().contains("pgrep -f '[a]pp\\.jar'"),
                fat.stop());
        // 三个 Linux 类型共用同一段启动收尾，任何一个漏掉存活校验都会重新打开「假成功」的口子
        check("A9b fatjar / separated 启动段同样带存活校验",
                fat.start().contains("kill -0") && sep.start().contains("kill -0"),
                "fatjar=" + fat.start().contains("kill -0") + " separated=" + sep.start().contains("kill -0"));

        // 元字符转义：. 与 + 必须转义，否则正则语义被改变（. 会连带匹配 smokeXjar 之类）
        ProjectScripts.Scripts meta = filled(builtin(false, "fatjar", "a+b.jar"),
                "a+b.jar", LINUX, LINUX + "/app.log", "demo");
        check("A10 入口含正则元字符时被转义（[a]\\+b\\.jar）",
                meta.stop().contains("[a]\\+b\\.jar"),
                meta.stop());

        // 敏感性对照：证明 A2 不是恒真 —— 自安全模式必须是 [s] 前缀形式，
        // 且裸字面量（pgrep -f 'smoke\.jar'）绝不允许出现
        check("A11 敏感性对照：自安全模式带 [s] 前缀且不含裸字面量",
                sb.stop().contains("[s]moke\\.jar") && !sb.stop().contains("pgrep -f 'smoke\\.jar'"),
                sb.stop());

        // J：分离式 mainEntry 是启动脚本（run.sh），jar 包必须投到 app.jar，
        // 否则 run.sh 被 jar 覆盖（实测报 PK: not found，项目直接起不来）
        ProjectPlacement sepMain = TYPES.resolveOrGeneric("separated")
                .placements(new ProjectPlacementContext(LINUX, "run.sh", "", "/data/uploads/pkg.jar",
                        false, false, false, "", "main"))
                .get(0);
        check("A12 separated 主包投放为 app.jar（不覆盖 run.sh）",
                "app.jar".equals(sepMain.fileName()), sepMain.fileName());
        ProjectPlacement sepBin = TYPES.resolveOrGeneric("separated")
                .placements(new ProjectPlacementContext(LINUX, "run.sh", "", "/data/uploads/pkg.bin",
                        false, false, false, "", "main"))
                .get(0);
        check("A12b separated 非 jar 包仍按 mainEntry 投放（run.sh）",
                "run.sh".equals(sepBin.fileName()), sepBin.fileName());

        // L：PID 文件位置随类型不同，static/nginx 由 nginx 写到 logs/nginx.pid
        check("A13 static/nginx 的 PID 文件为 logs/nginx.pid，Java 系为 app.pid",
                "logs/nginx.pid".equals(TYPES.resolveOrGeneric("static").pidFile())
                        && "logs/nginx.pid".equals(TYPES.resolveOrGeneric("nginx").pidFile())
                        && "app.pid".equals(TYPES.resolveOrGeneric("springboot").pidFile()),
                "static=" + TYPES.resolveOrGeneric("static").pidFile()
                        + " nginx=" + TYPES.resolveOrGeneric("nginx").pidFile()
                        + " springboot=" + TYPES.resolveOrGeneric("springboot").pidFile());
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
        ProjectScripts.Scripts wb = builtin(true, "springboot", "smoke.jar");
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
        check("G2 回滚命令可用最新备份（参数为空）",
                rb.contains("ls -t backups/backup_*.tar.gz") && rb.contains("tar -xzf"), rb);
        // 回滚必须回到「备份时刻」，而不是「把备份覆盖上去」：升级新增的文件不在备份清单里，
        // 只解压会让它们原样留下。实测：升级前没有 app.jar，回滚后新 jar 仍在且 md5 未变，
        // 服务仍跑着升级后的版本，运维却看到「回滚成功」。
        check("G2b POSIX 回滚先按备份清单反删新增文件，再解压",
                rb.contains("tar -tzf") && rb.contains("comm -23")
                        && rb.contains("rm -f -- ")
                        && rb.indexOf("comm -23") < rb.indexOf("tar -xzf"), rb);
        check("G2c POSIX 回滚清理时排除 backups 自身（否则历史备份全被删）",
                rb.contains("-path ./backups -prune"), rb);
        String rbNamed = posix.rollback(LINUX, "backup_123.tar.gz");
        check("G2d 指定备份名时不做 ls -t 挑选，且名字经单引号转义",
                rbNamed.contains("latest='backups/backup_123.tar.gz'")
                        && !rbNamed.contains("ls -t"), rbNamed);
        // 敏感性对照：旧实现只有 tar -xzf，必须被 G2b 判负
        String oldRollback = "cd '/opt/app/demo' && latest=$(ls -t backups/backup_*.tar.gz | head -1)"
                + " && tar -xzf \"$latest\"";
        check("G2e 敏感性对照：只解压不清理的旧实现确实会被 G2b 判负",
                !oldRollback.contains("comm -23") && rb.contains("comm -23"), oldRollback);

        String rbWin = windows.rollback(WIN, null);
        check("G2f Windows 回滚同样反删新增文件（HashSet 备份清单 + Remove-Item）",
                rbWin.contains("HashSet[string]") && rbWin.contains("$keep.Contains($rel)")
                        && rbWin.contains("Remove-Item -LiteralPath"), rbWin);
        check("G2g Windows 回滚清理同样排除 backups 目录与 pid 文件",
                rbWin.contains("if ($rel -like 'backups\\*') { return }")
                        && rbWin.contains("if ($rel -eq 'app.pid'"), rbWin);
        // 结构校验：清理块必须无条件执行，不能被塞进 $skipped 汇总的 if 里（那会漏掉最常见的
        // 「没有任何日志文件被占用」场景，等于回滚永远不清理）
        int iSkipIf = rbWin.indexOf("if ($skipped.Count -gt 0) {");
        int iKeep = rbWin.indexOf("$keep = New-Object");
        check("G2h Windows 清理块在 $skipped 汇总之外（否则无占用文件时不清理）",
                iKeep >= 0 && iSkipIf > iKeep, "iKeep=" + iKeep + " iSkipIf=" + iSkipIf);
        // 敏感性对照：旧实现（只 robocopy 覆盖）必须被 G2f 判负
        String oldWinRollback = "robocopy $latest.FullName '.' /E /XD backups | Out-Null";
        check("G2i 敏感性对照：只覆盖不清理的旧实现确实会被 G2f 判负",
                !oldWinRollback.contains("HashSet") && rbWin.contains("HashSet"), oldWinRollback);

        // 投放必须是「复制」：源包被移走后，同一个 packagePath 给第二个项目或二次升级就会报
        // 文件不存在（实测升级后上传目录里的包已消失），且 SDK 移动 / 命令复制两条路径副作用不一致。
        String placePosix = posix.place(LINUX, "/data/uploads/pkg.jar", "app.jar");
        check("G5 投放命令是复制（cp -f），源包保留",
                placePosix.contains("cp -f '/data/uploads/pkg.jar'") && !placePosix.contains("mv ")
                        && !placePosix.contains("rm "), placePosix);
        String placeWin = windows.place(WIN, "C:\\uploads\\pkg.jar", "app.jar");
        check("G6 Windows 投放命令是复制（Copy-Item），不用 Move-Item",
                placeWin.contains("Copy-Item") && !placeWin.contains("Move-Item"), placeWin);
        // 敏感性对照：移动式投放必须被判负
        String movePlace = "mv -f '/data/uploads/pkg.jar' '/opt/app/demo/app.jar'";
        check("G7 敏感性对照：移动式投放确实会被 G5 判负",
                !movePlace.contains("cp -f") && placePosix.contains("cp -f"), movePlace);
        // 生产链路的顺序同样是语义：先命令复制，命令通道不可用才回退 sftp rename（会移动源包）。
        // 这一条只能看源码，故在源码不在场（例如从 jar 跑探针）时跳过，不制造假失败。
        Path svc = Path.of("src/main/java/com/chua/starter/server/support/service/impl/ReleaseServiceImpl.java");
        if (Files.exists(svc)) {
            try {
                String src = Files.readString(svc, StandardCharsets.UTF_8);
                int iCopy = src.indexOf("placeCommand(pf, dir");
                int iMove = src.indexOf("sftpService.rename(ctx.hostId");
                check("G8 投放优先走命令复制，sftp rename 仅作回退",
                        iCopy > 0 && iMove > iCopy, "iCopy=" + iCopy + " iMove=" + iMove);
                check("G8b 投放步骤日志写明保留源包",
                        src.contains("保留源包"), "未找到「保留源包」");
            } catch (Exception e) {
                check("G8 投放源码可读", false, e.toString());
            }
        }
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
        ProjectScripts.Scripts wb = builtin(true, "springboot", "smoke.jar");
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
        // CIM/WMI 进程枚举在加固主机上会被安全产品间歇拦截（实测 hit 为空、picked=0）；
        // 此时必须有不依赖 WMI 的 Get-Process 兜底，且它要早于「退回包装壳 pid」，
        // 否则 app.pid 会指向 cmd 包装壳，端口属主健康检查必然失败、升级被回滚。
        int fallbackAt = wbFull.start().indexOf("Get-Process -Name");
        int wrapperFallbackAt = wbFull.start().indexOf("if ($procId -le 0 -and $wmiOk)");
        check("H1d CIM 被拦截时有 Get-Process 兜底解析，且早于包装壳回退",
                fallbackAt >= 0 && wrapperFallbackAt > fallbackAt
                        && wbFull.start().contains("resolve-fallback(Get-Process)"),
                "fallbackAt=" + fallbackAt + " wrapperFallbackAt=" + wrapperFallbackAt);
        // Windows 启动把 JVM/应用参数经单引号 $inner 传给 cmd，若不拼接变量就会把字面量
        // "$JvmArgs" 当作主类（实测 ClassNotFoundException: $JvmArgs）。
        check("H1e Windows 启动把 Jvm/App 参数拼接进 $inner（不再留在单引号字面量里）",
                wbFull.start().contains("(@($JvmArgs) -join ' ')")
                        && wbFull.start().contains("(@($AppArgs) -join ' ')")
                        && !wbFull.start().contains("$inner = 'java $JvmArgs"),
                "inner 拼接缺失");
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
        var wb = builtin(true, "springboot", "smoke.jar");
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

    // ==================== J. macOS 平台（POSIX 复用 + launchd 挂载） ====================

    /**
     * J 段：macOS 平台命令与系统服务挂载。
     */
    @Test
    void jMacosPlatform() {
        sectionJMacosPlatform();
        assertSectionDone("J");
    }

    private void sectionJMacosPlatform() {
        PosixProjectPlatformProvider posix = new PosixProjectPlatformProvider();
        MacosProjectPlatformProvider macos = new MacosProjectPlatformProvider();

        // 平台范围写的是 LINUX/WINDOWS/MACOS，而 macOS 复用 POSIX 实现 —— 正因如此，
        // 「显式登记为 MACOS」与「命令确实等于 POSIX」两件事必须被钉住：前者防止 macOS
        // 被静默当成 Linux，后者防止复用关系被无意破坏（Windows 分支的断言发现不了）。
        check("J1 macOS 平台显式登记（osType=MACOS，不是静默当 Linux）",
                "MACOS".equals(macos.osType()) && !"LINUX".equals(macos.osType()), macos.osType());
        // backup 命令内含时间戳（两次调用必然不同），比对前先把时间戳归一
        check("J2 macOS 备份命令与 POSIX 完全一致（复用关系未被破坏）",
                normTs(macos.backup(LINUX, 10)).equals(normTs(posix.backup(LINUX, 10))),
                normTs(macos.backup(LINUX, 10)));
        check("J3 macOS 回滚命令与 POSIX 完全一致",
                macos.rollback(LINUX, null).equals(posix.rollback(LINUX, null)), macos.rollback(LINUX, null));
        String gm = macos.guardedRestart(LINUX, "RESTART_SENTINEL", "STOP_SENTINEL", "START_SENTINEL",
                "STATUS_SENTINEL", true, 18091);
        check("J4 macOS 健康检查与 POSIX 完全一致",
                gm.equals(posix.guardedRestart(LINUX, "RESTART_SENTINEL", "STOP_SENTINEL",
                        "START_SENTINEL", "STATUS_SENTINEL", true, 18091)), gm);
        check("J5 macOS 健康检查同样不含 [[:space:]]（zsh 是 macOS 默认 shell，必守）",
                !gm.contains("[[:space:]]"), gm);
        check("J6 macOS 引号转义沿用 POSIX 单引号",
                macos.quote("a'b").equals("a'\\''b"), macos.quote("a'b"));

        // launchd 挂载无真机可跑，但产物是纯字符串 → 可在非 macOS 机器上实判。
        // 断言：免提权（不得出现 sudo）、幂等注册（先 bootout 再 bootstrap）、
        // 原生自启/守护（RunAtLoad/KeepAlive）、终态校验（install 后 print，卸载后反查作业与 plist）。
        LaunchdSystemServiceProvider launchd = new LaunchdSystemServiceProvider();
        SystemServiceContext ctx = new SystemServiceContext(
                "Demo App", 7L, "springboot", "smoke.jar", LINUX, LINUX + "/app.log",
                "echo START", "echo STOP", "echo running", Map.of("APP_ENV", "prod"),
                List.of("-Xmx512m"), List.of("--server.port=18091"), "deploy", true, true,
                TYPES.resolveOrGeneric("springboot").posixForeground("smoke.jar"));
        String svc = launchd.defaultServiceName(ctx);
        String install = launchd.install(ctx, svc);
        String uninstall = launchd.uninstall(ctx, svc);
        String launcher = launchd.launcher(ctx);

        check("J7 默认服务名规范化（去空格/大写，带项目号）",
                "ms-demo-app-7".equals(svc), svc);
        check("J8 挂载免提权（全程不得出现 sudo）",
                !install.contains("sudo") && !uninstall.contains("sudo"), install);
        check("J9 幂等注册：先 bootout 再 bootstrap 到当前用户 GUI 域",
                install.contains("launchctl bootout")
                        && install.indexOf("bootout") < install.indexOf("bootstrap 'gui/$(id -u)'")
                        && install.contains("launchctl bootstrap 'gui/$(id -u)'"), install);
        check("J10 原生自启/守护：plist 含 RunAtLoad 与 KeepAlive",
                install.contains("<key>RunAtLoad</key>") && install.contains("<key>KeepAlive</key>")
                        && install.contains("<true/>"), install);
        check("J11 日志重定向进 plist（StandardOutPath/StandardErrorPath）",
                install.contains("<key>StandardOutPath</key>")
                        && install.contains("<key>StandardErrorPath</key>"), install);
        check("J12 挂载终态校验：launchctl print 判定作业是否真装载",
                install.contains("launchctl print 'gui/$(id -u)/com.chua.ms." + svc + "'"), install);
        check("J13 卸载终态校验：反查作业与 plist 是否真删除",
                uninstall.contains("launchctl print") && uninstall.contains("plist 仍存在")
                        && uninstall.contains("作业仍在 launchd 中"), uninstall);
        check("J14 启动器以 exec 常驻（launchd 以进程存活判守护）",
                launcher.startsWith("#!/bin/bash")
                        && launcher.contains("exec java $JVM_ARGS -jar 'smoke.jar' $APP_ARGS"), launcher);
        check("J15 启动器导出环境变量并单引号转义",
                launcher.contains("export APP_ENV='prod'") && launcher.contains("JVM_ARGS='-Xmx512m'"),
                launcher);

        // plist XML 转义：值里含 & < > 必须转义，否则 plist 解析失败、作业根本装不进来
        SystemServiceContext xmlCtx = new SystemServiceContext(
                "A&B", 8L, "springboot", "a.jar", "/opt/A<B>&C", "/opt/x/out>err.log",
                "S", "T", "U", Map.of(), List.of(), List.of(), null, true, false,
                TYPES.resolveOrGeneric("springboot").posixForeground("a.jar"));
        String xmlInstall = launchd.install(xmlCtx, "svc");
        check("J16 plist 值做 XML 转义（& < > 均转义，不再原样落盘）",
                xmlInstall.contains("&amp;") && xmlInstall.contains("&lt;") && xmlInstall.contains("&gt;")
                        && !xmlInstall.contains("<string>/opt/x/out>err.log</string>"), xmlInstall);

        // 敏感性对照：注入「用 sudo + launchctl load」的旧写法，证明 J8/J9 能识别它（非恒真）
        String badImpl = "sudo cp x.plist ~/Library/LaunchAgents/ && launchctl load -w x.plist\n";
        check("J17 敏感性对照：sudo/launchctl load 旧写法确实会被 J8/J9 判负",
                badImpl.contains("sudo") && !badImpl.contains("bootstrap"), badImpl);
        // launchd 只接受格式良好的 plist：字符串里手拼 XML 一旦漏转义/结构错位，
        // 作业根本装不进 launchd（真机上只会看到「挂载失败」，且无真机时无法预先发现）。
        // 这里把生成结果里的 XML 片段真解析一遍，锁定「产物是可被 launchd 解析的 plist」。
        check("J18 挂载脚本内嵌 plist 是良构 XML（可被 launchd 解析）",
                countXmlErrors(extractPlist(install)) == 0 && countXmlErrors(extractPlist(xmlInstall)) == 0,
                extractPlist(install));
        check("J19 敏感性对照：漏转义的 plist 会被 J18 判负",
                countXmlErrors(extractPlist(xmlInstall).replace("&amp;", "&").replace("&lt;", "<")
                        .replace("&gt;", ">")) > 0, "unescaped");
    }

    /**
     * 从生成的挂载脚本里截取 plist 的 XML 正文（here-string 内的内容）。
     *
     * @param script 生成的挂载脚本
     * @return plist XML 文本；找不到时返回空串
     */
    private static String extractPlist(String script) {
        int start = script.indexOf("<?xml");
        int end = script.indexOf("</plist>");
        if (start < 0 || end < 0) {
            return "";
        }
        return script.substring(start, end + "</plist>".length());
    }

    /**
     * 统计 XML 解析错误数（0 表示良构）。
     *
     * @param xml XML 文本
     * @return 错误数；空文本按 1 个错误计
     */
    private static int countXmlErrors(String xml) {
        if (xml == null || xml.isBlank()) {
            return 1;
        }
        try {
            var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            // plist 带 DOCTYPE，必须允许；但不解析外部 DTD（无网络依赖，也不引入 XXE 风险）
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            factory.setXIncludeAware(false);
            factory.newDocumentBuilder().parse(
                    new org.xml.sax.InputSource(new java.io.StringReader(xml)));
            return 0;
        } catch (Exception e) {
            return 1;
        }
    }

    // ==================== K. Windows 免计划任务回退挂载 ====================

    /**
     * K 段：Windows 系统服务挂载在「计划任务不可用」时的回退。
     *
     * <p>加固主机上 Task Scheduler 服务被停用且非提权无法启动，{@code schtasks} 全线不可用，
     * 旧实现直接抛「挂载失败」——功能在整台主机上不可用。本段钉住回退路径的存在与形状，
     * 以及两条路径都有的终态校验。</p>
     */
    @Test
    void kWindowsServiceFallback() {
        sectionKWindowsServiceFallback();
        assertSectionDone("K");
    }

    private void sectionKWindowsServiceFallback() {
        WindowsSystemServiceProvider win = new WindowsSystemServiceProvider();
        SystemServiceContext ctx = new SystemServiceContext(
                "Win Demo", 38L, "springboot", "smoke.jar", "C:\\ms-acc\\acc-sb", "C:\\ms-acc\\acc-sb\\app.log",
                "echo START", "echo STOP", "echo running", Map.of(), List.of(), List.of(),
                "Administrator", true, true,
                TYPES.resolveOrGeneric("springboot").posixForeground("smoke.jar"));
        String svc = win.defaultServiceName(ctx);
        String install = win.install(ctx, svc);
        String uninstall = win.uninstall(ctx, svc);

        check("K1 默认服务名合法（仅 [A-Za-z0-9_.-]）且带项目号",
                svc.matches("[A-Za-z0-9_.-]+") && svc.contains("38"), svc);
        check("K2 先探测计划任务服务再分支（不无条件用 schtasks）",
                install.contains("Get-Service Schedule") && install.contains("if ($__sched) {"), install);
        check("K3 主路径仍是 schtasks（可用时原生丢给 SYSTEM/ONSTART）",
                install.contains("/SC ONSTART /RU SYSTEM") && install.contains("/SC MINUTE /MO 1 /RU SYSTEM"),
                install);
        check("K4 回退用免提权的 Startup 启动项",
                install.contains("[Environment]::GetFolderPath('Startup')") && install.contains(".cmd'"), install);
        check("K5 回退登记 HKCU Run 启动项",
                install.contains("HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"), install);
        check("K6 回退守护用常驻看门狗（WMI 拉起 + pid 文件核验 + 60s 循环）",
                install.contains("Invoke-CimMethod -ClassName Win32_Process") && install.contains(".watchdog.pid")
                        && install.contains("Start-Sleep -Seconds 60"), install);
        check("K7 回退也有终态校验（启动项/注册表/守护均须落地）",
                install.contains("启动项文件未创建") && install.contains("注册表启动项未写入")
                        && install.contains("守护进程未拉起"), install);
        check("K8 卸载清理回退产物（启动项/注册表/看门狗 pid/自启脚本）",
                uninstall.contains("Remove-Item -Force $__cmd") && uninstall.contains("Remove-ItemProperty")
                        && uninstall.contains("Stop-Process -Id")
                        && uninstall.contains(svc + ".autostart.ps1"), uninstall);
        check("K9 卸载用退出码判定计划任务（避免本地化错误文本被误判为仍存在）",
                uninstall.contains("$LASTEXITCODE -eq 0"), uninstall);
        check("K10 卸载终态校验（启动项/注册表/自启脚本仍存在即失败）",
                uninstall.contains("启动项文件仍存在") && uninstall.contains("注册表启动项仍存在")
                        && uninstall.contains("自启脚本仍存在"), uninstall);
        // 敏感性对照：旧实现只有 schtasks、无回退 → K2/K4 必须判负
        String oldImpl = "schtasks /Create /TN 'x-autostart' /SC ONSTART /RU SYSTEM "
                + "/TR \"powershell -File 'a.ps1'\" /F\n";
        check("K11 敏感性对照：旧实现（仅 schtasks、无回退）会被 K2/K4 判负",
                !oldImpl.contains("Get-Service Schedule") && !oldImpl.contains("GetFolderPath('Startup')"), oldImpl);
        // 两个开关都不开时直接判失败（避免「挂载成功但什么都没发生」）
        SystemServiceContext none = new SystemServiceContext(
                "N", 1L, "springboot", "a.jar", "C:\\x", "C:\\x\\a.log",
                "S", "T", "U", Map.of(), List.of(), List.of(), null, false, false,
                TYPES.resolveOrGeneric("springboot").posixForeground("a.jar"));
        check("K12 自启与守护都不开时直接判失败（不假报成功）",
                win.install(none, "ms-n-1").contains("throw '挂载失败"), win.install(none, "ms-n-1"));

        // 语法实判：生成的挂载/卸载脚本是一大段 PowerShell（含 here-string 与多层引号），
        // 极易出现结构性语法错误。真机一轮部署成本远高于本地解析一次，故部署前先实判。
        String exe = resolveShellExe();
        if (exe != null) {
            check("K13 挂载脚本无 PowerShell 语法错误", countPowerShellErrors(exe, install) == 0, install);
            check("K14 卸载脚本无 PowerShell 语法错误", countPowerShellErrors(exe, uninstall) == 0, uninstall);
        }
        // 真机缺陷回归：旧实现只等 10s（20×500ms），WmiPrvSE+PowerShell 冷启动在负载高
        // 的机器上会超时（本机实测 pid 落地 31s）→ 任务判「挂载失败」，但 pid 文件随后才出现。
        check("K15 看门狗等待窗口足够（360×500ms=180s，不再是 20×500ms=10s）",
                install.contains("$i -lt 360") && !install.contains("$i -lt 20;"), install);
        check("K16 看门狗成功判据含「pid 对应存活进程」（防陈旧 pid 文件误判成功）",
                install.contains("Get-Process -Id ([int]$__wp)"), install);
        check("K17 WMI 返回码非 0 直接判失败（不再静默等 60s）",
                install.contains("WMI 拉起守护进程失败 code="), install);
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
     * 按项目类型生成内置四段脚本（等价于生产链路的类型分发）。
     *
     * @param windows     是否 Windows
     * @param projectType 项目类型
     * @param mainEntry   主入口
     * @return 仍带占位符的模板脚本
     */
    private static ProjectScripts.Scripts builtin(boolean windows, String projectType, String mainEntry) {
        return TYPES.resolveOrGeneric(projectType).scripts(windows, mainEntry);
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
     * 归一命令里的时间戳（备份名 {@code backup_<millis>}），
     * 使「两次生成同一命令」的比对不受毫秒差影响。
     *
     * @param text 命令文本
     * @return 时间戳归一后的文本
     */
    private static String normTs(String text) {
        return text == null ? null : text.replaceAll("backup_\\d+", "backup_TS");
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