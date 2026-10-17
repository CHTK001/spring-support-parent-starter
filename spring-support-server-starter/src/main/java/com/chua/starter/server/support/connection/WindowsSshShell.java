package com.chua.starter.server.support.connection;

import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.util.SshClients;
import com.chua.ssh.support.client.SshClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Windows 主机经 OpenSSH 的远程执行通道（替代 WinRM）。
 * <p>
 * 与 {@link WinrmShell} 表面一致（exec / execLines / follow），但传输层走 SSH：
 * PowerShell 统一以 {@code -EncodedCommand}（UTF-16LE Base64）下发，由 Windows 自带
 * OpenSSH 的默认外壳（cmd / powershell）拉起 powershell 执行，规避默认外壳的语法差异；
 * stdout / 退出码经 SSH exec 通道回传。文件分发走同端口的 SFTP。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class WindowsSshShell {

    /**
     * 超长脚本落盘时的远端临时目录。
     *
     * <p>固定用 {@code %SystemRoot%\Temp}（此处即 {@code C:\Windows\Temp}）而不是用户临时目录：
     * sshd 的服务账号与管理员账号不同，用户临时目录可能不可写。</p>
     */
    private static final String REMOTE_TMP_DIR = "C:\\Windows\\Temp";

    /**
     * 落盘时单个分块的 base64 字符数。
     *
     * <p>每块自身也要内联下发，仍受 {@link PowerShellCommand#INLINE_BASE64_LIMIT} 约束，
     * 故取其一半再留出固定外壳开销的余量。</p>
     */
    private static final int CHUNK_BASE64 = 2400;

    /**
     * 远端脚本落盘的最大尝试次数。
     *
     * <p>分块写入的每一块都是一次独立 SSH exec，连接可能被中间设备掐断（实测 sshd 日志出现
     * {@code Connection aborted}）。任何一块失败都会让文件短于预期，因此整份重写而不是续写，
     * 避免半块重复。</p>
     */
    private static final int WRITE_ATTEMPTS = 3;

    /**
     * 单次 SSH exec 的实测时长上限（秒）。
     *
     * <p><b>实测硬上限，不是配置项。</b>远端跑一个 90 秒的命令，文件方式与内联方式
     * <b>都恰好在 61 秒</b>被砍成 {@code exit=-1} / {@code 退出码 0: -1}，
     * 而请求里传的是 {@code timeoutSeconds=300}。{@code ExecServiceImpl} 传的是
     * {@code DEFAULT_STEP_TIMEOUT_SECONDS=600}，目标机 sshd_config 也没有任何超时项，
     * 因此上限在 {@code com.chua.ssh.support.client.SshClient} 库内部，改不了。</p>
     *
     * <p>后果：发布链路的「重启并健康检测」需要 60~80 秒（就绪等待 + 稳定窗口 +
     * 回滚后二次启动校验），在这条通道上永远跑不完，健康检查步骤必然 {@code exit=-1}，
     * 自动回滚分支根本走不到。</p>
     */
    private static final int SSH_EXEC_CEILING_SECONDS = 55;

    /**
     * 后台模式下单次轮询命令的最长等待（秒）。
     *
     * <p>必须显著小于 {@link #SSH_EXEC_CEILING_SECONDS}，否则轮询自己会被砍。
     * 取 45 秒意味着一条 90 秒的命令只需 2 次轮询，而不是每几秒开一次连接 ——
     * 后者会在目标机上把 sshd 的并发连接数拉高。</p>
     */
    private static final int POLL_WINDOW_SECONDS = 45;

    /**
     * 从远端取回输出时的字符上限，防止超长输出把一次短 exec 撑爆。
     */
    private static final int FETCH_LIMIT = 200_000;

    /**
     * Windows OpenSSH 默认端口
     */
    private static final int DEFAULT_SSH_PORT = 22;

    /**
     * 远程执行结果（与 WinrmShell.Result / SshClient.ExecResult 对齐）
     *
     * @param exitCode 退出码
     * @param stdout   标准输出
     * @param stderr   错误输出
     */
    public record Result(int exitCode, String stdout, String stderr) {
    }

    /**
     * 持续跟随句柄（实时日志），关闭即断开远端会话
     */
    public interface FollowHandle extends AutoCloseable {
        @Override
        void close();
    }

    /**
     * 是否 Windows 主机
     *
     * @param host 主机
     * @return 是否 Windows
     */
    public static boolean isWindows(MsServerHost host) {
        return host != null && "WINDOWS".equalsIgnoreCase(host.getServerHostOsType());
    }

    /**
     * SSH 端口：优先主机登录端口，否则回退 22
     *
     * @param host 主机
     * @return 端口
     */
    private int portOf(MsServerHost host) {
        return host.getServerHostPort() != null && host.getServerHostPort() > 0
                ? host.getServerHostPort() : DEFAULT_SSH_PORT;
    }

    /**
     * 把 PowerShell 脚本/命令包装为默认外壳也能发起的 EncodedCommand 行。
     *
     * @param ps PowerShell 内容
     * @return {@code powershell -EncodedCommand <b64>}
     */
    private String powershell(String ps) {
        return PowerShellCommand.encoded(ps);
    }

    /**
     * 一次性执行命令，返回完整结果
     *
     * @param host           主机
     * @param cred           凭据
     * @param psCommand      PowerShell 命令
     * @param timeoutSeconds 超时（秒）
     * @return 执行结果
     */
    public Result exec(MsServerHost host, SshCredential cred, String psCommand, int timeoutSeconds) {
        // 优先 PowerShell 7：原生 UTF-8，且不受「按 5.1 canonical 路径拦截」的影响
        // （实测 360 只拦 C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe，
        //   同机 pwsh 正常）。不可用时（不存在 / 被拦）立即回退 5.1 —— 两条路都是
        //   一次 exec，失败路径才多一次往返。
        Result r = execOnce(host, cred, PowerShellCommand.MODERN_SHELL, psCommand, timeoutSeconds);
        if (r.exitCode() == 0 || !PowerShellCommand.looksLikeShellUnavailable(r.stderr())) {
            return verify(host, r);
        }
        Result legacy = execOnce(host, cred, PowerShellCommand.LEGACY_SHELL, psCommand, timeoutSeconds);
        return verify(host, legacy);
    }

    /**
     * 用指定外壳执行一次 PowerShell 命令。
     *
     * @param host           主机
     * @param cred           凭据
     * @param shell          外壳可执行名（{@code pwsh} 或 {@code powershell}）
     * @param psCommand      PowerShell 命令
     * @param timeoutSeconds 超时（秒）
     * @return 执行结果
     */
    private Result execOnce(MsServerHost host, SshCredential cred, String shell,
                            String psCommand, int timeoutSeconds) {
        if (timeoutSeconds > SSH_EXEC_CEILING_SECONDS) {
            return execDetached(host, cred, shell, psCommand, timeoutSeconds, null);
        }
        if (PowerShellCommand.exceedsInlineLimit(psCommand)) {
            return execViaRemoteFile(host, cred, shell, psCommand);
        }
        String command = PowerShellCommand.encoded(shell, psCommand);
        try (SshClients.Handle handle = SshClients.open(host.getServerHostAddress(), portOf(host), cred)) {
            handle.client().connect();
            SshClient.ExecResult r = handle.client().exec().command(command).execute();
            return new Result(r.exitCode(), r.stdout(), r.stderr());
        } catch (WindowsSshExecException e) {
            throw e;
        } catch (Exception e) {
            throw new WindowsSshExecException("OpenSSH 执行失败: " + rootMessage(e), e);
        }
    }

    /**
     * 把超长脚本分块写到远端临时文件，再用 {@code -File} 执行。
     *
     * <p>存在的理由：内联 {@code -EncodedCommand} 会把 base64 塞进命令行，而 {@code cmd.exe}
     * 命令行上限 8191 字符。实测 base64 超过约 8K 就整条被拒，症状是 0 秒退出 + exit 1
     * + 十来个别名错误（错误信息本身也被同一层截断损坏）。重启段（9116）与带健康检查的
     * 升级/回滚命令（29076）都超限，会稳定失败。</p>
     *
     * <p>分块本身仍走 {@code -EncodedCommand}，所以每块的引号与转义都不成问题；
     * 远端文件写成带 BOM 的 UTF-8，保证回退到 Windows PowerShell 5.1 时中文也不会乱码。</p>
     *
     * @param host      主机
     * @param cred      凭据
     * @param shell     外壳可执行名
     * @param psCommand PowerShell 命令
     * @return 执行结果
     */
    private Result execViaRemoteFile(MsServerHost host, SshCredential cred, String shell, String psCommand) {
        String path = REMOTE_TMP_DIR + "\\ms-exec-" + Long.toHexString(System.nanoTime()) + ".ps1";
        try {
            writeRemoteScript(host, cred, shell, psCommand, path);
            Result r = runRaw(host, cred, PowerShellCommand.fileBased(shell, path));
            cleanupQuietly(host, cred, shell, path);
            return r;
        } catch (WindowsSshExecException e) {
            throw e;
        } catch (Exception e) {
            throw new WindowsSshExecException("脚本落盘执行失败: " + rootMessage(e), e);
        }
    }

    /**
     * 把脚本分块写到远端（UTF-8 带 BOM），必要时自动建目录。
     *
     * @param host      主机
     * @param cred      凭据
     * @param shell     外壳可执行名
     * @param psCommand PowerShell 命令
     * @param path      远端目标路径
     */
    private void writeRemoteScript(MsServerHost host, SshCredential cred, String shell,
                                   String psCommand, String path) {
        byte[] raw = PowerShellCommand.body(psCommand).getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[raw.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(raw, 0, withBom, 3, raw.length);
        String b64 = Base64.getEncoder().encodeToString(withBom);
        String lastError = null;
        for (int attempt = 1; attempt <= WRITE_ATTEMPTS; attempt++) {
            String fail = writeRemoteScriptOnce(host, cred, shell, path, b64, withBom.length);
            if (fail == null) {
                return;
            }
            lastError = fail;
        }
        // 绝不把截断的脚本交给 PowerShell：截断后最典型的报错是尾部语句块
        // 「Missing closing '}'」，位置随机（实测落在 else 分支），排查成本极高，
        // 还会让本该自动回滚的升级停在半途（新版本已投放、旧进程已停）。
        throw new WindowsSshExecException("远端脚本落盘不完整（" + path + "，期望 "
                + withBom.length + " 字节）：" + lastError + "；已中止执行，避免不完整脚本被解析运行。", null);
    }

    /**
     * 一次性把整份脚本写完整（先清空再按字节追加），并校验远端长度。
     *
     * <p>按<b>字节</b>追加而不是先解码成字符串：分块边界可能切在多字节 UTF-8 字符中间，
     * 解码再编码会把该字符变成替换符，既破坏脚本文本也改变字节数。</p>
     *
     * @return {@code null} 表示写入完整；否则返回失败原因，交由调用方整份重写
     */
    private String writeRemoteScriptOnce(MsServerHost host, SshCredential cred, String shell,
                                         String path, String b64, int expectedBytes) {
        try {
            Result init = runRaw(host, cred, PowerShellCommand.encoded(shell,
                    "$d = '" + REMOTE_TMP_DIR + "'\n"
                            + "if (-not (Test-Path -LiteralPath $d)) { New-Item -ItemType Directory -Force -Path $d | Out-Null }\n"
                            + "[IO.File]::WriteAllBytes('" + path + "', [byte[]]@())"));
            if (init.exitCode() != 0) {
                return "初始化失败 exit=" + init.exitCode() + " " + brief(init.stderr());
            }
            for (int off = 0; off < b64.length(); off += CHUNK_BASE64) {
                String chunk = b64.substring(off, Math.min(b64.length(), off + CHUNK_BASE64));
                Result r = runRaw(host, cred, PowerShellCommand.encoded(shell,
                        "$b = [Convert]::FromBase64String('" + chunk + "')\n"
                                + "$fs = [IO.File]::Open('" + path + "', 'Append', 'Write')\n"
                                + "try { $fs.Write($b, 0, $b.Length) } finally { $fs.Close() }"));
                if (r.exitCode() != 0) {
                    return "分块写入失败 offset=" + off + " exit=" + r.exitCode() + " " + brief(r.stderr());
                }
            }
            Result size = runRaw(host, cred, PowerShellCommand.encoded(shell,
                    "(Get-Item -LiteralPath '" + path + "').Length"));
            if (size.exitCode() != 0) {
                return "长度校验失败 exit=" + size.exitCode() + " " + brief(size.stderr());
            }
            String got = size.stdout() == null ? "" : size.stdout().trim();
            String want = String.valueOf(expectedBytes);
            if (!want.equals(got)) {
                return "长度不符 期望=" + want + " 实际=" + got;
            }
            return null;
        } catch (RuntimeException e) {
            return "写入异常: " + rootMessage(e);
        }
    }

    /**
     * 取诊断信息末尾一小段，避免把整段 stderr 塞进上层异常。
     *
     * @param text 原始输出
     * @return 压缩成单行的尾部片段
     */
    private static String brief(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String one = text.replace('\r', ' ').replace('\n', ' ').trim();
        return one.length() <= 200 ? one : "…" + one.substring(one.length() - 200);
    }

    /**
     * 尽力删除远端临时文件，失败不影响本次结果。
     *
     * @param host  主机
     * @param cred  凭据
     * @param shell 外壳可执行名
     * @param path  远端路径
     */
    private void cleanupQuietly(MsServerHost host, SshCredential cred, String shell, String path) {
        try {
            runRaw(host, cred, PowerShellCommand.encoded(shell,
                    "Remove-Item -LiteralPath '" + path + "' -Force -ErrorAction SilentlyContinue"));
        } catch (Exception ignored) {
            // 清理失败不影响本次执行结果，远端会残留一个临时脚本
        }
    }

    /**
     * 后台执行长命令：WMI 拉起（计划任务兜底）+ 短命令轮询结果文件。
     *
     * <p>用于绕开 {@link #SSH_EXEC_CEILING_SECONDS} 那道实测硬上限 —— 上限在 ssh 客户端
     * 库内部，改不了，于是改执行模型：让命令在目标机上脱离会话跑完，把退出码与输出落到
     * 文件，再用<b>各自都短于上限</b>的 exec 去取。</p>
     *
     * <p>要点：</p>
     * <ul>
     *   <li>落地启动优先 WMI {@code Win32_Process.Create}：其父进程是 WmiPrvSE，不属于 SSH
     *       会话的作业对象，会话关闭后仍存活；{@code Start-Process} 的子进程属调用方会话作业，
     *       实测会话一断即被腰斩。计划任务同样能脱离会话，但 Task Scheduler 在部分加固主机上
     *       被安全策略禁用，故降级为兜底。</li>
     *   <li>轮询命令自带最多 {@link #POLL_WINDOW_SECONDS} 秒的等待，所以一条 90 秒的命令
     *       只需要 2 次轮询，而不是每几秒开一次连接。</li>
     *   <li>退出码由启动器里的 {@code > done (echo %ERRORLEVEL%)} 落盘，批处理按行解析，
     *       因此 {@code %ERRORLEVEL%} 取到的是上一条命令的真实退出码。</li>
     *   <li><b>必须用 {@code > file (echo %ERRORLEVEL%)} 而不是 {@code echo %ERRORLEVEL%> file}</b>：
     *       后者会被 cmd 解析成「把文件重定向到 stdin 句柄 0」，写出的是空文件，
     *       表现为所有命令的退出码一律变成 1。</li>
     * </ul>
     *
     * @param host           主机
     * @param cred           凭据
     * @param shell          外壳可执行名
     * @param psCommand      PowerShell 命令
     * @param timeoutSeconds 调用方期望的超时（后台模式真正的上限）
     * @param onLine         每行输出回调，可为 {@code null}
     * @return 执行结果（退出码与输出来自远端文件）
     */
    private Result execDetached(MsServerHost host, SshCredential cred, String shell,
                                String psCommand, int timeoutSeconds, Consumer<String> onLine) {
        String tag = Long.toHexString(System.nanoTime());
        String base = REMOTE_TMP_DIR + "\\ms-bg-" + tag;
        String script = base + ".ps1";
        String outFile = base + ".out";
        String errFile = base + ".err";
        String doneFile = base + ".done";
        String launcher = base + ".cmd";
        String task = "ms-bg-" + tag;
        String launchPs = ""
                + "$d = '" + REMOTE_TMP_DIR + "'\n"
                + "if (-not (Test-Path -LiteralPath $d)) { New-Item -ItemType Directory -Force -Path $d | Out-Null }\n"
                + "$lc = @('@echo off',\n"
                + "  '" + shell + " -NoProfile -NonInteractive -ExecutionPolicy Bypass -File \"" + script
                + "\" > \"" + outFile + "\" 2> \"" + errFile + "\"',\n"
                + "  '> \"" + doneFile + "\" (echo %ERRORLEVEL%)')\n"
                + "[IO.File]::WriteAllLines('" + launcher + "', $lc, (New-Object Text.UTF8Encoding($false)))\n"
                + "Remove-Item -LiteralPath '" + doneFile + "' -Force -ErrorAction SilentlyContinue\n"
                // 落地启动原语优先 WMI：Win32_Process.Create 拉起的进程父进程是 WmiPrvSE，
                // 不挂在 SSH 会话的作业对象上，会话关闭后仍存活；而 Start-Process 的子进程
                // 属调用方会话作业，SSH 一断即被回收（实测拉起的 pwsh/cmd 都没跑完就被腰斩）。
                // 计划任务同样能脱离会话，但 Task Scheduler 服务在部分加固主机上被安全策略禁用
                // （实测 Schedule=Stopped，schtasks 报 "The network address is invalid"），
                // 因此降级为兜底而非唯一手段。
                + "$wmiCmd = 'cmd /c ' + '" + launcher + "'\n"
                + "$wmi = Invoke-CimMethod -ClassName Win32_Process -MethodName Create "
                + "-Arguments @{ CommandLine = $wmiCmd; CurrentDirectory = '" + REMOTE_TMP_DIR + "' }\n"
                + "if ($wmi.ReturnValue -eq 0) { 'LAUNCHED-WMI' } else {\n"
                + "  & schtasks.exe /delete /tn '" + task + "' /f 2>&1 | Out-Null\n"
                + "  $c = & schtasks.exe /create /tn '" + task + "' /tr '" + launcher + "' /sc once /st '23:59' /f 2>&1\n"
                + "  if ($LASTEXITCODE -ne 0) { throw ('后台任务创建失败: ' + ($c -join ' ')) }\n"
                + "  & schtasks.exe /run /tn '" + task + "' 2>&1 | Out-Null\n"
                + "  if ($LASTEXITCODE -ne 0) { throw ('后台任务触发失败: ' + '" + task + "') }\n"
                + "  'LAUNCHED-SCHTAS'\n"
                + "}";
        try {
            writeRemoteScript(host, cred, shell, psCommand, script);
            Result launched = runRaw(host, cred, PowerShellCommand.encoded(shell, launchPs));
            if (launched.exitCode() != 0) {
                cleanupDetached(host, cred, shell, true, task, script, launcher, outFile, errFile, doneFile);
                return new Result(launched.exitCode() == 0 ? 1 : launched.exitCode(),
                        launched.stdout(), "后台任务启动失败: " + launched.stderr());
            }
            String pollPs = ""
                    + "$deadline = (Get-Date).AddSeconds(" + POLL_WINDOW_SECONDS + ")\n"
                    + "while (-not (Test-Path -LiteralPath '" + doneFile + "') -and (Get-Date) -lt $deadline) {\n"
                    + "  Start-Sleep -Milliseconds 700\n"
                    + "}\n"
                    + "if (Test-Path -LiteralPath '" + doneFile + "') { 'STATE=DONE' } else { 'STATE=WAIT' }";
            boolean done = false;
            long deadline = System.currentTimeMillis() + Math.max(timeoutSeconds, 60L) * 1000L;
            while (System.currentTimeMillis() < deadline) {
                Result poll = runRaw(host, cred, PowerShellCommand.encoded(shell, pollPs));
                if (poll.exitCode() == 0 && poll.stdout() != null && poll.stdout().contains("STATE=DONE")) {
                    done = true;
                    break;
                }
                if (poll.exitCode() != 0) {
                    // 轮询本身被砍：不能据此判定命令失败，缩短窗口后重试
                    Thread.sleep(1000L);
                    continue;
                }
            }
            Result fetched = runRaw(host, cred, PowerShellCommand.encoded(shell, fetchPs(doneFile, outFile, errFile)));
            cleanupDetached(host, cred, shell, done, task, script, launcher, outFile, errFile, doneFile);
            String exitTxt = extractExit(fetched.stdout());
            if (!done) {
                return new Result(-1, fetched.stdout(),
                        "后台命令未在期望时限内完成（timeout=" + timeoutSeconds + "s）");
            }
            int exitCode;
            try {
                exitCode = Integer.parseInt(exitTxt);
            } catch (NumberFormatException e) {
                exitCode = 1;
            }
            String out = extractBetween(fetched.stdout(), "<<<OUT>>>", "<<<ERR>>>");
            String err = extractAfter(fetched.stdout(), "<<<ERR>>>");
            if (onLine != null && out != null) {
                for (String line : out.split("\\R")) {
                    if (!line.isBlank()) {
                        onLine.accept(line);
                    }
                }
            }
            return new Result(exitCode, out == null ? "" : out, err == null ? "" : err);
        } catch (WindowsSshExecException e) {
            throw e;
        } catch (Exception e) {
            throw new WindowsSshExecException("后台执行失败: " + rootMessage(e), e);
        }
    }

    /**
     * 取回退出码与两侧输出的远端命令。
     *
     * <p>用 base64 承载正文：输出里可能有中文与任意字节，直接内联回传要处理多层转义，
     * 且 exec 通道对超长单行敏感。</p>
     *
     * @param doneFile 完成标记
     * @param outFile  输出文件
     * @param errFile  错误输出文件
     * @return PowerShell 命令
     */
    private static String fetchPs(String doneFile, String outFile, String errFile) {
        return "function _R($p) {\n"
                + "  if (-not (Test-Path -LiteralPath $p)) { return '' }\n"
                + "  $t = [IO.File]::ReadAllText($p)\n"
                + "  if ($t.Length -gt " + FETCH_LIMIT + ") { $t = $t.Substring(0, " + FETCH_LIMIT + ") }\n"
                + "  return [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($t))\n"
                + "}\n"
                + "$code = if (Test-Path -LiteralPath '" + doneFile + "') { (Get-Content '" + doneFile
                + "' -Raw).Trim() } else { 'NA' }\n"
                + "'<<<EXIT>>>' + $code\n"
                + "'<<<OUT>>>' + (_R '" + outFile + "')\n"
                + "'<<<ERR>>>' + (_R '" + errFile + "')";
    }

    /**
     * 清理后台模式留下的临时文件与计划任务。
     *
     * @param host     主机
     * @param cred     凭据
     * @param shell    外壳
     * @param finished 命令是否已结束（结束才敢删脚本与输出文件）
     * @param task     计划任务名
     * @param paths    临时文件路径
     */
    private void cleanupDetached(MsServerHost host, SshCredential cred, String shell,
                                 boolean finished, String task, String... paths) {
        StringBuilder sb = new StringBuilder();
        if (finished) {
            // 重试删除：启动器 cmd 要等子进程结束才关掉 .out/.err 句柄，
            // 紧跟在「done 文件出现」之后删会被占用而失败，单次尝试等于不清理。
            sb.append("$__p = @(");
            for (int i = 0; i < paths.length; i++) {
                sb.append(i == 0 ? "" : ", ").append("'").append(paths[i]).append("'");
            }
            sb.append(")\n");
            sb.append("for ($__i = 0; $__i -lt 20; $__i++) {\n");
            sb.append("  $__left = 0\n");
            sb.append("  foreach ($__x in $__p) {\n");
            sb.append("    if (Test-Path -LiteralPath $__x) {\n");
            sb.append("      Remove-Item -LiteralPath $__x -Force -ErrorAction SilentlyContinue\n");
            sb.append("      if (Test-Path -LiteralPath $__x) { $__left++ }\n");
            sb.append("    }\n");
            sb.append("  }\n");
            sb.append("  if ($__left -eq 0) { break }\n");
            sb.append("  Start-Sleep -Milliseconds 300\n");
            sb.append("}\n");
        }
        // 未完成的命令可能仍在跑：此时删脚本与输出文件会破坏它，只回收计划任务
        sb.append("& schtasks.exe /delete /tn '").append(task).append("' /f 2>&1 | Out-Null\n");
        runQuietly(host, cred, shell, sb.toString());
    }

    /**
     * 尽力执行一段远端清理命令，失败不影响本次结果。
     *
     * <p>与 {@link #cleanupQuietly} 的区别：这里接收的是<b>完整命令</b>而不是单个路径。
     * 早先 cleanupDetached 把多行脚本传给只接受单路径的 cleanupQuietly，后者又用
     * {@code Remove-Item -LiteralPath '...'} 整段包住，引号嵌套把命令写坏，
     * PowerShell 直接报 {@code PositionalParameterNotFound} 且退出码 1，
     * 而异常/非零退出都被静默吞掉 —— 表现为每个后台任务都在 C:\Windows\Temp
     * 留下 .ps1/.cmd/.out/.err/.done 五个文件（实测 55 个任务 100% 复现）。</p>
     *
     * @param host    主机
     * @param cred    凭据
     * @param shell   外壳可执行名
     * @param command 完整命令
     */
    private void runQuietly(MsServerHost host, SshCredential cred, String shell, String command) {
        try {
            runRaw(host, cred, PowerShellCommand.encoded(shell, command));
        } catch (Exception ignored) {
            // 清理失败不影响本次执行结果
        }
    }

    /**
     * 从标记文本中取出 {@code <<<NAME>>>} 之后、下一个标记之前的内容。
     *
     * @param text  远端回传文本
     * @param start 开始标记
     * @param end   结束标记
     * @return base64 内容；不存在时为 {@code null}
     */
    private static String extractBetween(String text, String start, String end) {
        if (text == null) {
            return null;
        }
        int a = text.indexOf(start);
        if (a < 0) {
            return null;
        }
        a += start.length();
        int b = text.indexOf(end, a);
        if (b < 0) {
            return null;
        }
        return decode(text.substring(a, b));
    }

    /**
     * 取最后一个标记之后的全部内容。
     *
     * @param text 远端回传文本
     * @param mark 标记
     * @return base64 内容；不存在时为 {@code null}
     */
    private static String extractAfter(String text, String mark) {
        if (text == null) {
            return null;
        }
        int a = text.indexOf(mark);
        if (a < 0) {
            return null;
        }
        a += mark.length();
        return decode(text.substring(a));
    }

    /**
     * 取退出码标记后的内容。
     *
     * @param text 远端回传文本
     * @return 退出码文本
     */
    private static String extractExit(String text) {
        String v = extractBetween(text, "<<<EXIT>>>", "<<<OUT>>>");
        return v == null ? "" : decode(v).trim();
    }

    /**
     * base64 解码；失败时返回原文（便于排查格式问题）。
     *
     * @param b64 base64 文本
     * @return 解码结果
     */
    private static String decode(String b64) {
        try {
            return new String(Base64.getDecoder().decode(b64.trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return b64;
        }
    }

    /**
     * 用给定命令行执行一次，不做超限改写、不做退化校验。
     *
     * @param cred    凭据
     * @param command 完整命令行
     * @return 执行结果
     */
    private Result runRaw(MsServerHost host, SshCredential cred, String command) {
        try (SshClients.Handle handle = SshClients.open(host.getServerHostAddress(), portOf(host), cred)) {
            handle.client().connect();
            SshClient.ExecResult r = handle.client().exec().command(command).execute();
            return new Result(r.exitCode(), r.stdout(), r.stderr());
        } catch (WindowsSshExecException e) {
            throw e;
        } catch (Exception e) {
            throw new WindowsSshExecException("OpenSSH 执行失败: " + rootMessage(e), e);
        }
    }

    /**
     * 校验执行结果，识别「sshd 默认 shell 被替换」导致的 exec 通道退化。
     *
     * <p>当注册表 {@code HKLM\SOFTWARE\OpenSSH\DefaultShell} 被指向非 cmd 外壳
     * （如 Git Bash）时，Win32-OpenSSH 处理 exec 请求会启动<b>交互式</b> shell 而不执行命令，
     * 表现为 stdout 只有系统启动横幅与提示符、且没有退出码。此处提前拦截并给出可读诊断，
     * 避免上层拿到 {@code exit=-1} + 空 stderr 而误判为「执行成功」。</p>
     *
     * @param host   主机（用于诊断信息）
     * @param result 原始执行结果
     * @return 原样返回（未命中退化特征时）
     */
    private Result verify(MsServerHost host, Result result) {
        if (result.exitCode() != -1) {
            return result;
        }
        String out = result.stdout() == null ? "" : result.stdout();
        if (out.isBlank()) {
            return result;
        }
        String head = out.length() > 400 ? out.substring(0, 400) : out;
        // 交互式 shell 特征：出现 Windows 版本横幅，或以「提示符 + 路径」结尾（cmd 提示符形如 C:\Users\xx>）
        boolean banner = head.contains("Microsoft Windows") || head.contains("Microsoft Windows [");
        boolean prompt = head.matches("(?s).*[A-Za-z]:\\\\[^\\r\\n]*>\\s*$");
        if (banner || prompt) {
            throw new WindowsSshExecException(
                    "OpenSSH exec 通道异常：远端返回的是交互式 shell 提示符而非命令输出"
                            + "（主机 " + (host == null ? "?" : host.getServerHostAddress()) + "）。"
                            + "通常因 sshd 默认登录 shell 被替换：请在目标机删除注册表值 "
                            + "HKLM\\SOFTWARE\\OpenSSH\\DefaultShell 与 DefaultShellCommandLine，"
                            + "重启 sshd 服务后恢复默认（cmd.exe）。", null);
        }
        return result;
    }

    /**
     * 流式执行命令：输出按行实时回调，结束后返回完整结果
     *
     * @param host           主机
     * @param cred           凭据
     * @param psCommand      PowerShell 命令
     * @param timeoutSeconds 超时（秒）
     * @param onLine         每行输出回调（stdout / stderr 合并）
     * @return 完整执行结果
     */
    public Result execLines(MsServerHost host, SshCredential cred, String psCommand,
                            int timeoutSeconds, Consumer<String> onLine) {
        // 优先 PowerShell 7、失败回退 5.1 —— 与 exec 同源（发布任务的步骤执行走这里，
        // 此前只修了 exec 而漏了本方法，导致「/exec/command 已通、发布链路仍 Access is denied」）。
        Result r = execLinesOnce(host, cred, PowerShellCommand.MODERN_SHELL, psCommand, timeoutSeconds, onLine);
        if (r.exitCode() == 0 || !PowerShellCommand.looksLikeShellUnavailable(r.stderr())) {
            return verify(host, r);
        }
        return verify(host, execLinesOnce(host, cred, PowerShellCommand.LEGACY_SHELL, psCommand, timeoutSeconds, onLine));
    }

    /**
     * 用指定外壳流式执行一次。
     *
     * @param host           主机
     * @param cred           凭据
     * @param shell          外壳可执行名
     * @param psCommand      PowerShell 命令
     * @param timeoutSeconds 超时（秒）
     * @param onLine         每行输出回调（stdout / stderr 合并）
     * @return 执行结果
     */
    private Result execLinesOnce(MsServerHost host, SshCredential cred, String shell, String psCommand,
                                 int timeoutSeconds, Consumer<String> onLine) {
        if (timeoutSeconds > SSH_EXEC_CEILING_SECONDS) {
            // 后台模式没有实时流：执行结束后把收集到的行一次性回放给回调，
            // 调用方（发布任务步骤执行器）只关心最终结果与行文本，不依赖实时性。
            return execDetached(host, cred, shell, psCommand, timeoutSeconds, onLine);
        }
        if (PowerShellCommand.exceedsInlineLimit(psCommand)) {
            // 落盘路径没有流式回调：先分块写文件，再用 -File 执行一次取回完整输出。
            return execViaRemoteFile(host, cred, shell, psCommand);
        }
        String command = PowerShellCommand.encoded(shell, psCommand);
        try (SshClients.Handle handle = SshClients.open(host.getServerHostAddress(), portOf(host), cred)) {
            handle.client().connect();
            SshClient.ExecResult r = handle.client().exec().command(command).stream(onLine, onLine);
            return new Result(r.exitCode(), r.stdout(), r.stderr());
        } catch (Exception e) {
            throw new WindowsSshExecException("OpenSSH 流式执行失败: " + rootMessage(e), e);
        }
    }

    /**
     * 持续跟随（实时日志，命令长期不退出）：在后台线程打开通道，输出按行回调，
     * 通道由 MINA SSHD 的 I/O 线程维持，直到返回句柄被关闭。
     *
     * @param host      主机
     * @param cred      凭据
     * @param psCommand PowerShell 跟随命令（如 Get-Content -Wait）
     * @param onLine    每行输出回调
     * @return 跟随句柄，关闭即断开
     */
    public FollowHandle follow(MsServerHost host, SshCredential cred, String psCommand,
                               Consumer<String> onLine) {
        String command = powershell(psCommand);
        AtomicReference<SshClients.Handle> handleRef = new AtomicReference<>();
        AtomicReference<SshClient.StreamHandle> streamRef = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                SshClients.Handle handle = SshClients.open(host.getServerHostAddress(), portOf(host), cred);
                handleRef.set(handle);
                handle.client().connect();
                SshClient.StreamHandle streamHandle = handle.client().exec().command(command).follow(onLine);
                streamRef.set(streamHandle);
            } catch (Exception e) {
                log.debug("[win-ssh] 跟随结束 hostId={}: {}", host.getId(), rootMessage(e));
            }
        }, "win-ssh-follow");
        worker.setDaemon(true);
        worker.start();
        return () -> {
            SshClient.StreamHandle streamHandle = streamRef.get();
            if (streamHandle != null) {
                try {
                    streamHandle.close();
                } catch (Exception ignore) {
                    // 关闭异常不处理
                }
            }
            SshClients.Handle handle = handleRef.get();
            if (handle != null) {
                try {
                    handle.close();
                } catch (Exception ignore) {
                    // 关闭异常不处理
                }
            }
            worker.interrupt();
        };
    }

    /**
     * 取异常链最底层消息
     *
     * @param e 异常
     * @return 根因消息
     */
    private String rootMessage(Throwable e) {
        Throwable c = e;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c.getMessage() == null ? c.getClass().getSimpleName() : c.getMessage();
    }

    /**
     * Windows OpenSSH 执行异常
     */
    public static class WindowsSshExecException extends RuntimeException {
        WindowsSshExecException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}