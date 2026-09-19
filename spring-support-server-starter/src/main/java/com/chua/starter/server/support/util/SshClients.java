package com.chua.starter.server.support.util;

import com.chua.starter.server.support.model.SshCredential;
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

    /** 默认 SSH 端口 */
    private static final int DEFAULT_PORT = 22;

    /**
     * 工具类禁止实例化
     */
    private SshClients() {
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
        var builder = SshClient.builder()
                .host(address)
                .port(port == null || port <= 0 ? DEFAULT_PORT : port)
                .username(credential.username())
                .connectTimeout(8);
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
            builder.password(credential.password() == null ? "" : credential.password());
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
