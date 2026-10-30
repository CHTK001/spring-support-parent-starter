package com.chua.starter.server.support.connection;

import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.winrm.support.client.WinRmExecClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.Writer;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * WinRM 远程执行通道（Windows 主机）。
 * <p>
 * 与基于 SSH 的 {@link ConnectionManager} 对等：当目标主机平台为 Windows 时，
 * 命令经 WinRM（wsman）执行，PowerShell 统一以 {@code -EncodedCommand}（UTF-16LE Base64）
 * 下发，彻底规避 winrm4j 默认经 cmd.exe 执行导致 PowerShell 语法不识别的问题。
 * </p>
 * <p>
 * winrm4j 的 Receive 为长轮询循环，每次收到增量输出即 {@code Writer.write}；
 * {@link LineWriter} 在写入过程中按行拆分并实时回调，因此安装等长任务可像 SSH 一样
 * 逐行经 socket 推送给前端 ReProcessTask。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class WinrmShell {

    /**
     * WinRM HTTP 默认端口
     */
    private static final int DEFAULT_WINRM_PORT = 5985;

    /**
     * 远程执行结果（与 SSH ExecResult 对齐）
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
     * WinRM 端口：优先主机登录端口，否则回退 5985
     *
     * @param host 主机
     * @return 端口
     */
    private int portOf(MsServerHost host) {
        return host.getServerHostPort() != null && host.getServerHostPort() > 0
                ? host.getServerHostPort() : DEFAULT_WINRM_PORT;
    }

    /**
     * 建立并返回已连接的 WinRM 客户端（调用方负责关闭）
     *
     * @param host 主机
     * @param cred 凭据
     * @return 已连接客户端
     */
    private WinRmExecClient connect(MsServerHost host, SshCredential cred) {
        WinRmExecClient client = WinRmExecClient.builder()
                .host(host.getServerHostAddress())
                .port(portOf(host))
                .username(cred.username())
                .password(cred.password())
                .connectTimeout(15_000)
                .sessionTimeout(120_000)
                .build();
        client.connect();
        return client;
    }

    /**
     * 把 PowerShell 脚本/命令包装为 cmd 也能发起的 EncodedCommand 行。
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
        String command = powershell(psCommand);
        try (WinRmExecClient client = connect(host, cred)) {
            var r = client.exec().command(command).execute();
            return new Result(r.exitCode(), r.stdout(), r.stderr());
        } catch (Exception e) {
            throw new WinrmExecException("WinRM 执行失败: " + rootMessage(e), e);
        }
    }

    /**
     * 流式执行命令：输出按行实时回调，结束后返回完整结果
     *
     * @param host           主机
     * @param cred           凭据
     * @param psCommand      PowerShell 命令
     * @param timeoutSeconds 超时（秒）
     * @param onLine         每行输出回调
     * @return 完整执行结果
     */
    public Result execLines(MsServerHost host, SshCredential cred, String psCommand,
                            int timeoutSeconds, Consumer<String> onLine) {
        String command = powershell(psCommand);
        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        try (WinRmExecClient client = connect(host, cred)) {
            Writer outWriter = new LineWriter(line -> {
                out.append(line).append('\n');
                onLine.accept(line);
            });
            Writer errWriter = new LineWriter(line -> {
                err.append(line).append('\n');
                onLine.accept(line);
            });
            int exitCode = client.getWinRmClient().command(command, outWriter, errWriter);
            return new Result(exitCode, out.toString(), err.toString());
        } catch (Exception e) {
            throw new WinrmExecException("WinRM 流式执行失败: " + rootMessage(e), e);
        }
    }

    /**
     * 持续跟随（实时日志，命令长期不退出）：在后台线程执行，输出按行回调
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
        AtomicReference<WinRmExecClient> clientRef = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            WinRmExecClient client = null;
            try {
                client = connect(host, cred);
                clientRef.set(client);
                Writer outWriter = new LineWriter(onLine);
                Writer errWriter = new LineWriter(onLine);
                client.getWinRmClient().command(command, outWriter, errWriter);
            } catch (Exception e) {
                log.debug("[winrm] 跟随结束 hostId={}: {}", host.getId(), rootMessage(e));
            } finally {
                if (client != null) {
                    try {
                        client.close();
                    } catch (Exception ignore) {
                        // 关闭异常不处理
                    }
                }
            }
        }, "winrm-follow");
        worker.setDaemon(true);
        worker.start();
        return () -> {
            WinRmExecClient client = clientRef.get();
            if (client != null) {
                try {
                    client.close();
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
     * 按行广播 Writer：winrm4j 每次 receive 到增量数据即写入，
     * 缓冲到换行后整行回调，半行保留待下次。
     */
    static final class LineWriter extends Writer {
        /**
         * 行回调
         */
        private final Consumer<String> onLine;
        /**
         * 半行缓冲
         */
        private final StringBuilder buffer = new StringBuilder();

        /**
         * Line Writer
         * @param onLine 参数
         * @return 返回值
        */
        LineWriter(Consumer<String> onLine) {
            this.onLine = onLine;
        }

        @Override
        public void write(char[] cbuf, int off, int len) {
            for (int i = off; i < off + len; i++) {
                char ch = cbuf[i];
                if (ch == '\n') {
                    flushLine();
                } else if (ch != '\r') {
                    buffer.append(ch);
                }
            }
        }

        /**
         * 回调当前缓冲行并清空
         */
        private void flushLine() {
            onLine.accept(buffer.toString());
            buffer.setLength(0);
        }

        @Override
        public void flush() {
            // 不主动 flush 半行，避免输出碎片行
        }

        @Override
        public void close() {
            if (buffer.length() > 0) {
                flushLine();
            }
        }
    }

    /**
     * WinRM 执行异常
     */
    public static class WinrmExecException extends RuntimeException {
        /**
         * Winrm Exec Exception
         * @param cause 可抛出异常
         * @param message 字符串
         * @return 返回值
        */
        WinrmExecException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
