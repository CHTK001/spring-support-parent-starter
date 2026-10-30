package com.chua.starter.server.support.util;

import com.chua.starter.server.support.model.SshCredential;
import com.chua.ssh.support.client.HostKeyPolicy;
import com.chua.ssh.support.client.SshClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * SSH 客户端工厂
 * <p>
 * 统一主机连接入口：按凭据类型（密码/私钥）装配 {@link SshClient}。
 * 私钥内容经临时文件交给底层库（其 API 仅接受路径），由
 * {@link Handle#close()} 负责客户端断连与临时文件清理，
 * 确保密钥明文不在磁盘残留。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class SshClients {

    /**
     * 默认 SSH 端口
    */
    private static final int DEFAULT_PORT = 22;

    /**
     * 进程级默认：TCP 连接/握手超时（秒）
     */
    private static volatile int defaultConnectSeconds = 15;

    /**
     * 进程级默认：认证/会话建立超时（秒）
     */
    private static volatile int defaultSessionSeconds = 30;

    /**
     * 进程级默认：命令执行/输出读取超时（秒）
     */
    private static volatile long defaultReadSeconds = 60L;

    /**
     * 工具类禁止实例化
     */
    private SshClients() {
    }

    /**
     * 设置进程级默认超时
     * <p>
     * 由 {@code SshTimeouts} 在启动与系统配置变更时调用。所有使用
     * {@link #open(String, Integer, SshCredential)} 的调用点（Windows 远程执行、
     * 主机探测、终端隧道等）都会立即使用新值。
     * </p>
     *
     * @param connectSeconds 连接/握手超时（秒）
     * @param sessionSeconds 认证/会话建立超时（秒）
     * @param readSeconds    命令执行/输出读取超时（秒）
     */
    public static void setDefaults(int connectSeconds, int sessionSeconds, long readSeconds) {
        defaultConnectSeconds = connectSeconds;
        defaultSessionSeconds = sessionSeconds;
        defaultReadSeconds = readSeconds;
    }

    /**
     * 打开已配置（未连接）的 SSH 客户端句柄
     * <p>
     * 使用方必须以 尝试-with-resources 持有返回值，保证临时私钥文件被清理：
     * <pre>{@code
     * try (var handle = SshClients.open(address, port, credential)) {
     *     handle.client().connect();
     *     // ... 执行命令
     * }
     * }</pre>... 执行命令
     * }
     * }</pre>
     * </p>
     *
     * @param address    主机地址
     * @param port       SSH 端口，空 时取 22
     * @param credential 解密后的凭据
     * @return 客户端句柄
     * @throws IOException 私钥临时文件写入失败时抛出
     */
    public static Handle open(String address, Integer port, SshCredential credential) throws IOException {
        // 使用进程级默认超时，可由系统配置 ssh.connect.timeout / ssh.session.timeout / ssh.read.timeout 热更新
        return open(address, port, credential,
                defaultConnectSeconds, defaultSessionSeconds, defaultReadSeconds);
    }

    /**
     * 打开已配置（未连接）的 SSH 客户端句柄，并显式指定各级超时。
     *
     * @param connectTimeoutSec TCP 连接/握手超时（秒）
     * @param sessionTimeoutSec 认证/会话建立超时（秒）
     * @param execTimeoutSec    单条命令执行/读取超时（秒）
     */
    public static Handle open(String address, Integer port, SshCredential credential,
                              int connectTimeoutSec, int sessionTimeoutSec, long execTimeoutSec) throws IOException {
        // 剥离 user@ 前缀：历史数据/粘贴导入可能把 ssh user@host 整行写进地址字段，
        // MINA SSHD 的 hostname 校验器把 @ 当非法字符拒绝连接
        String cleanAddress = stripUserPrefix(address);
        var builder = SshClient.builder()
                .host(cleanAddress)
                .port(port == null || port <= 0 ? DEFAULT_PORT : port)
                .username(credential.username())
                .hostKeyPolicy(HostKeyPolicy.INSECURE)
                .connectTimeout(connectTimeoutSec)
                .sessionTimeout(sessionTimeoutSec)
                .execTimeout(execTimeoutSec);
        Path tempKeyPath = null;
        if (credential.hasKey()) {
            // 底层库仅支持路径方式加载私钥：落盘受限临时文件，关闭时删除
            tempKeyPath = Files.createTempFile("ms-server-key-", ".pem");
            Files.writeString(tempKeyPath, credential.privateKey());
            builder.privateKey(tempKeyPath.toString());
            if (notBlank(credential.password())) {
 // 私钥场景下 密码 字段承载口令（passphrase）
                builder.password(credential.password());
            }
        } else {
            // NONE 认证（免密）：密码为 null，不设 password identity，
            // 让 MINA SSHD 走 "none" 认证方法（不发送凭据），目标主机放行即通过。
            // 之前用空串触发 password 认证，但多数服务器 PermitEmptyPasswords=no 会拒绝。
            if (credential.password() != null) {
                builder.password(credential.password());
            }
        }
        /**
         * 把手。
         * @param builder.build( 构建器.构建(
         * @return 把手的结果
         */
        return new Handle(builder.build(), tempKeyPath);
    }

    /**
     * 非空判断
     *
     * @param value 待判断字符串
     * @return 是否非空
     */
    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 剥离主机地址中的 user@ 前缀。
     *
     * <p>历史数据或粘贴导入可能把整行 ssh 命令（{@code user@host}）写进地址字段，
     * MINA SSHD 的 hostname 校验器把 @ 当非法字符拒绝连接。只剥前缀不改原值——
     * 展示 {@code user@host} 反而更直观。</p>
     *
     * @param raw 原始地址（可能含 user@ 前缀）
     * @return 纯主机地址（无 user@ 前缀）
     */
    private static String stripUserPrefix(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        int at = raw.lastIndexOf('@');
        if (at > 0 && at < raw.length() - 1) {
            String afterAt = raw.substring(at + 1);
            if (afterAt.contains(".") || afterAt.contains(":") || afterAt.contains("/")) {
                return afterAt;
            }
        }
        return raw;
    }

    /**
     * SSH 客户端句柄：聚合客户端与临时私钥文件的生命周期
     *
     * @param client      SSH 客户端
     * @param tempKeyPath 私钥临时文件路径，密码认证时为 空
     * @return 把手的结果
     */
    public record Handle(SshClient client, Path tempKeyPath) implements AutoCloseable {

        /**
         * 关闭客户端并清理临时私钥文件
         */
        @Override
        public void close() {
            try {
                if (client != null) {
                    client.close();
                }
            } finally {
                if (tempKeyPath != null) {
                    try {
                        Files.deleteIfExists(tempKeyPath);
                    } catch (IOException ignored) {
                        // 临时文件删除失败不影响主流程，交由操作系统临时目录清理机制兜底
                    }
                }
            }
        }
    }
}
