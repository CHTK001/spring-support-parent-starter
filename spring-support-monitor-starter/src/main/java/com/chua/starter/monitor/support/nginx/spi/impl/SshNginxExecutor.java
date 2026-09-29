package com.chua.starter.monitor.support.nginx.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.monitor.support.nginx.spi.NginxExecutor;

/**
 * SSH 执行通道。
 *
 * <p>命令文本与本通道无关：连 Linux 与连 Windows 走的是同一段代码，差别只在命令提供者
 * 拼出来的串与引号规则。这也正是把「命令文本」和「通道」拆成两个扩展点的意义。</p>
 *
 * <p>{@code order = 200}：只要不是本机目标，SSH 就是默认通道。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi(value = "nginx-ssh", order = 200)
public class SshNginxExecutor implements NginxExecutor {

    /**
     * TCP 连接超时（秒）
     */
    private static final int CONNECT_TIMEOUT_SECONDS = 10;

    @Override
    public boolean supports(Target target) {
        return target != null && !target.local();
    }

    @Override
    public String name() {
        return "nginx-ssh";
    }

    @Override
    public ExecResult exec(Target target, String command, long seconds) {
        int port = target.port() > 0 ? target.port() : 22;
        String host = target.host() == null ? "" : target.host().trim();
        String username = target.username() == null ? "" : target.username().trim();
        if (host.isEmpty()) {
            throw new IllegalStateException("Nginx 数据源未配置主机地址");
        }
        if (username.isEmpty()) {
            throw new IllegalStateException("Nginx 数据源未配置 SSH 用户名");
        }
        String password = target.password() == null ? "" : target.password();
        com.chua.ssh.support.client.SshClient ssh = com.chua.ssh.support.client.SshClient.builder()
                .host(host)
                .port(port)
                .username(username)
                .password(password)
                .connectTimeout(CONNECT_TIMEOUT_SECONDS)
                .sessionTimeout((int) seconds)
                .execTimeout(seconds)
                .build();
        try {
            ssh.connect();
            com.chua.ssh.support.client.SshClient.ExecResult r = ssh.exec().command(command).execute();
            return new ExecResult(r.exitCode(),
                    r.stdout() == null ? "" : r.stdout(),
                    r.stderr() == null ? "" : r.stderr());
        } catch (Exception e) {
            throw new IllegalStateException("SSH 执行失败 " + host + ":" + port + " 命令[" + command + "]: "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), e);
        } finally {
            try {
                ssh.disconnect();
            } catch (Exception ignored) {
                // 关闭失败不影响结果
            }
        }
    }
}
