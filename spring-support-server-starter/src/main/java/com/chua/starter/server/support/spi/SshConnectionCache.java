package com.chua.starter.server.support.spi;

import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.ssh.support.client.SftpClient;
import com.chua.ssh.support.client.SshClient;
import com.chua.starter.server.support.connection.ConnectionManager;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.util.SshClients;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * 主机采集 SSH/SFTP 通道门面。
 *
 * <p>连接、凭据指纹、空闲清理与密钥临时文件统一复用 server 的
 * {@link ConnectionManager}；本类只负责按主机凭据优先级借还连接，
 * 不再维护第二套会话缓存。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SshConnectionCache {

    private final ConnectionManager connectionManager;
    private final ServerHostService serverHostService;

    /**
     * 解析主机绑定的全部可用登录凭据。
     *
     * @param host 主机
     * @return 凭据列表，无可用凭据时为空
     */
    public List<SshCredential> credentials(MsServerHost host) {
        if (serverHostService == null || host == null || host.getId() == null) {
            return List.of();
        }
        try {
            return serverHostService.resolveCredentials(host.getId());
        } catch (Exception e) {
            log.warn("credentials failed host={}: {}", host.getId(), e.getMessage());
            return List.of();
        }
    }

    /**
     * 执行命令；连接失效时丢弃 server 缓存中的旧连接并重试一次。
     *
     * @param host 目标主机
     * @param cmd  命令
     * @return 原生执行结果
     */
    public SshClient.ExecResult execResult(MsServerHost host, String cmd) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            SshClients.Handle handle = borrow(host);
            try {
                return handle.client().exec().command(cmd).execute();
            } catch (RuntimeException e) {
                last = e;
                connectionManager.invalidate(host.getId());
                if (attempt == 1) {
                    throw e;
                }
            } finally {
                connectionManager.release(host.getId());
            }
        }
        throw new IllegalStateException("SSH 命令执行失败: " + host.getId(), last);
    }

    /**
     * 执行命令并返回标准输出。
     *
     * @param host      目标主机
     * @param cmd       命令
     * @param timeoutMs 兼容调用方的超时参数；实际超时由 server 的 SSH 三级超时统一管理
     * @return 标准输出
     */
    public String exec(MsServerHost host, String cmd, long timeoutMs) {
        return execResult(host, cmd).stdout();
    }

    /**
     * 在复用 SSH 会话的 SFTP 通道上执行操作。
     *
     * @param host   目标主机
     * @param action SFTP 操作
     * @param <T>    返回值
     * @return 操作结果
     */
    public <T> T withSftp(MsServerHost host, Function<SftpClient, T> action) {
        SshClients.Handle handle = borrow(host);
        try (SftpClient sftp = handle.client().sftp()) {
            return action.apply(sftp);
        } finally {
            connectionManager.release(host.getId());
        }
    }

    /**
     * 主动失效主机连接。
     *
     * @param host 目标主机
     */
    public void invalidate(MsServerHost host) {
        connectionManager.invalidate(host.getId());
    }

    private SshClients.Handle borrow(MsServerHost host) {
        List<SshCredential> credentials = credentials(host);
        if (credentials.isEmpty()) {
            throw new IllegalStateException("主机 " + host.getId() + " 未绑定 SSH 凭据，请先在主机资产配置身份");
        }
        Exception last = null;
        for (SshCredential credential : credentials) {
            try {
                return connectionManager.borrow(host, credential);
            } catch (IOException | RuntimeException e) {
                last = e;
                log.warn("SSH connect failed host={} user={}: {}", host.getId(), credential.username(), e.getMessage());
                if (isNetworkFailure(e)) {
                    break;
                }
            }
        }
        throw new IllegalStateException("SSH 连接失败 host=" + host.getId()
                + " 已尝试 " + credentials.size() + " 组凭据"
                + (last == null ? "" : "，最后原因: " + rootReason(last)), last);
    }

    private static boolean isNetworkFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String value = message.toLowerCase(Locale.ROOT);
                if (value.contains("timed out") || value.contains("timeout")
                        || value.contains("connection refused") || value.contains("no route to host")
                        || value.contains("unknown host") || value.contains("connect exception")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static String rootReason(Throwable error) {
        Throwable current = error;
        String reason = error == null ? "" : error.getClass().getSimpleName();
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                reason = current.getMessage();
            }
            current = current.getCause();
        }
        return reason;
    }
}
