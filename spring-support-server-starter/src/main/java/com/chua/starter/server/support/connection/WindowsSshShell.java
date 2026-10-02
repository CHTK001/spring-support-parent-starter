package com.chua.starter.server.support.connection;

import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.util.SshClients;
import com.chua.ssh.support.client.SshClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
        String command = powershell(psCommand);
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