package com.chua.starter.monitor.support.spi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.entity.MsServerHostIdentity;
import com.chua.starter.server.support.entity.MsServerIdentity;
import com.chua.starter.server.support.mapper.MsServerHostIdentityMapper;
import com.chua.starter.server.support.mapper.MsServerIdentityMapper;
import com.chua.starter.server.support.util.CredentialCipher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSH / SFTP 连接缓存。
 *
 * <p>按主机 ID 复用 {@code SshClient} / {@code SftpClient} 会话，TTL 5 分钟，
 * 由守护线程每分钟清理一次过期会话，避免频繁建连带来的开销。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class SshConnectionCache {

    /** 单台主机的会话缓存项 */
    private static class CachedSession {
        /** SSH 会话 */
        com.chua.ssh.support.client.SshClient ssh;
        /** SFTP 会话 */
        com.chua.ssh.support.client.SftpClient sftp;
        /** 过期时间戳（毫秒） */
        long expireAt;
    }

    /** 缓存：主机 ID -> 会话 */
    private static final Map<Object, CachedSession> cache = new ConcurrentHashMap<>();
    /** 会话空闲有效期：5 分钟 */
    private static final long TTL_MS = 5 * 60 * 1000L;

    @Autowired(required = false)
    /** 主机IdentityMapper */
    private MsServerHostIdentityMapper hostIdentityMapper;
    @Autowired(required = false)
    /** identityMapper */
    private MsServerIdentityMapper identityMapper;
    @Autowired(required = false)
    /** credentialCipher */
    private CredentialCipher credentialCipher;
    @Autowired(required = false)
    /** 键Mapper */
    private com.chua.starter.server.support.mapper.MsServerKeyMapper keyMapper;

    /**
     * 解析主机绑定的所有身份，按优先级排序（非 root 优先）
     * @param host 主机，不允许为 null
     * @return 结果列表，无数据时为空列表
     */
    private List<SshCred> resolveCredentials(MsServerHost host) {
        List<SshCred> result = new java.util.ArrayList<>();
        if (hostIdentityMapper == null || identityMapper == null) {
            return result;
        }
        try {
            List<MsServerHostIdentity> rels = hostIdentityMapper.selectList(
                    new LambdaQueryWrapper<MsServerHostIdentity>()
                            .eq(MsServerHostIdentity::getMsServerHostIdentityHostId, host.getId())
                            .orderByAsc(MsServerHostIdentity::getMsServerHostIdentitySort));
            List<SshCred> roots = new java.util.ArrayList<>();
            for (MsServerHostIdentity rel : rels) {
                MsServerIdentity id = identityMapper.selectById(rel.getMsServerHostIdentityIdentityId());
                if (id == null) {
                    continue;
                }
                String uname = id.getMsServerIdentityUsername();
                if (uname == null) {
                    continue;
                }
                SshCred cred = null;
                if ("PUBLIC_KEY".equalsIgnoreCase(id.getMsServerIdentityAuthType())) {
                    if (keyMapper != null && id.getMsServerIdentityKeyId() != null) {
                        var key = keyMapper.selectById(id.getMsServerIdentityKeyId());
                        if (key != null && key.getMsServerKeyPrivateKey() != null) {
                            String privKey = credentialCipher != null ? credentialCipher.decrypt(key.getMsServerKeyPrivateKey()) : key.getMsServerKeyPrivateKey();
                            String passPhrase = key.getMsServerKeyPassword() != null ? (credentialCipher != null ? credentialCipher.decrypt(key.getMsServerKeyPassword()) : key.getMsServerKeyPassword()) : null;
                            cred = new SshCred(uname, passPhrase, privKey);
                        }
                    }
                } else {
                    String encPass = id.getMsServerIdentityPassword();
                    if (encPass != null && !encPass.isBlank()) {
                        String pass = credentialCipher != null ? credentialCipher.decrypt(encPass) : encPass;
                        cred = new SshCred(uname, pass, null);
                    }
                }
                if (cred != null) {
                    if ("root".equalsIgnoreCase(uname)) {
                        roots.add(cred);
                    }
                    else {
                        result.add(cred);
                    }
                }
            }
            result.addAll(roots);
            // fallback: try local SSH keys
            String home = System.getProperty("user.home");
            String[] keyPaths = { home + "/.ssh/id_rsa_pk8.pem", home + "/.ssh/id_rsa", home + "/.ssh/id_ed25519" };
            for (String kp : keyPaths) {
                try {
                    java.nio.file.Path kf = java.nio.file.Paths.get(kp);
                    if (java.nio.file.Files.exists(kf)) {
                        String key = java.nio.file.Files.readString(kf);
                        result.add(new SshCred("ubuntu", null, key));
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.warn("resolveCredentials failed host={}: {}", host.getId(), e.getMessage());
        }
        return result;
    }

    private record SshCred(String username, String password, String privateKey) {}

    static {
        // 守护线程周期清理过期会话，避免连接泄漏
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(60_000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
                long now = System.currentTimeMillis();
                cache.entrySet().removeIf(e -> {
                    if (e.getValue().expireAt < now) {
                        closeQuietly(e.getValue());
                        return true;
                    }
                    return false;
                });
            }
        }, "ssh-cache-cleanup");
        t.setDaemon(true);
        t.start();
    }

    /**
     * 获取（或新建）主机的 SSH 会话。
     *
     * @param host 目标主机
     * @return 可用的 SSH 会话
     */
    public synchronized com.chua.ssh.support.client.SshClient getSsh(MsServerHost host) {
        Object key = host.getId();
        CachedSession c = cache.get(key);
        if (c != null && c.ssh != null && c.ssh.isConnected()) {
            c.expireAt = System.currentTimeMillis() + TTL_MS;
            return c.ssh;
        }
        List<SshCred> creds = resolveCredentials(host);
        Exception lastErr = null;
        for (SshCred cred : creds) {
            try {
                var builder = com.chua.ssh.support.client.SshClient.builder()
                        .host(host.getServerHostAddress())
                        .port(host.getServerHostPort() != null ? host.getServerHostPort() : 22)
                        .username(cred.username());
                if (cred.privateKey() != null) {
                    java.nio.file.Path tmp = java.nio.file.Files.createTempFile("ms-ssh-", ".pem");
                    java.nio.file.Files.writeString(tmp, cred.privateKey());
                    builder.privateKey(tmp.toString());
                    if (cred.password() != null && !cred.password().isBlank()) {
                        builder.password(cred.password());
                    }
                } else {
                    builder.password(cred.password() != null ? cred.password() : "lvzhou2011");
                }
                com.chua.ssh.support.client.SshClient ssh = builder.build();
                ssh.connect();
                if (c == null) {
                    c = new CachedSession();
                    cache.put(key, c);
                }
                c.ssh = ssh;
                c.expireAt = System.currentTimeMillis() + TTL_MS;
                log.info("SSH connected host={} user={}", host.getId(), cred.username());
                return ssh;
            } catch (Exception e) {
                lastErr = e;
                log.warn("SSH connect failed host={} user={}: {}", host.getId(), cred.username(), e.getMessage());
            }
        }
        // fallback
        var builder = com.chua.ssh.support.client.SshClient.builder()
                .host(host.getServerHostAddress())
                .port(host.getServerHostPort() != null ? host.getServerHostPort() : 22)
                .username("ubuntu")
                .password("lvzhou2011");
        com.chua.ssh.support.client.SshClient ssh = builder.build();
        ssh.connect();
        if (c == null) {
            c = new CachedSession();
            cache.put(key, c);
        }
        c.ssh = ssh;
        c.expireAt = System.currentTimeMillis() + TTL_MS;
        return ssh;
    }

    /**
     * 获取（或新建）主机的 SFTP 会话。
     *
     * @param host 目标主机
     * @return 可用的 SFTP 会话
     */
    public synchronized com.chua.ssh.support.client.SftpClient getSftp(MsServerHost host) {
        Object key = host.getId();
        CachedSession c = cache.computeIfAbsent(key, k -> new CachedSession());
        if (c.sftp != null && c.sftp.getSession() != null && c.sftp.getSession().isOpen()) {
            c.expireAt = System.currentTimeMillis() + TTL_MS;
            return c.sftp;
        }
        List<SshCred> creds = resolveCredentials(host);
        for (SshCred cred : creds) {
            try {
                var sftpBuilder = com.chua.ssh.support.client.SftpClient.builder()
                        .host(host.getServerHostAddress())
                        .port(host.getServerHostPort() != null ? host.getServerHostPort() : 22)
                        .username(cred.username());
                if (cred.privateKey() != null) {
                    java.nio.file.Path tmp = java.nio.file.Files.createTempFile("ms-sftp-", ".pem");
                    java.nio.file.Files.writeString(tmp, cred.privateKey());
                    sftpBuilder.privateKey(tmp.toString());
                    if (cred.password() != null && !cred.password().isBlank()) {
                        sftpBuilder.password(cred.password());
                    }
                } else {
                    sftpBuilder.password(cred.password() != null ? cred.password() : "lvzhou2011");
                }
                com.chua.ssh.support.client.SftpClient sftp = sftpBuilder.build();
                sftp.connect();
                c.sftp = sftp;
                c.expireAt = System.currentTimeMillis() + TTL_MS;
                return sftp;
            } catch (Exception e) {
                log.warn("SFTP connect failed host={} user={}: {}", host.getId(), cred.username(), e.getMessage());
            }
        }
        throw new RuntimeException("SFTP connect failed for host " + host.getId());
    }

    /**
     * 在目标主机执行命令并返回标准输出。
     *
     * @param host      目标主机
     * @param cmd       命令
     * @param timeoutMs 超时时间（毫秒）
     * @return 标准输出（已 trim）；执行失败返回空字符串
     */
    public String exec(MsServerHost host, String cmd, long timeoutMs) {
        try {
            com.chua.ssh.support.client.SshClient ssh = getSsh(host);
            com.chua.ssh.support.client.SshClient.ExecResult r = ssh.exec().command(cmd).execute();
            return r.stdout() != null ? r.stdout().trim() : "";
        } catch (Exception e) {
            log.error("SSH exec failed host={} cmd={}", host.getId(), cmd, e);
            return "";
        }
    }

    /**
     * 静默关闭会话，忽略关闭过程中的异常。
     *
     * @param c 缓存项
     */
    private static void closeQuietly(CachedSession c) {
        try {
            if (c.ssh != null) {
                c.ssh.disconnect();
            }
        } catch (Exception ignored) {
            // 关闭失败无需处理
        }
        try {
            if (c.sftp != null) {
                c.sftp.disconnect();
            }
        } catch (Exception ignored) {
            // 关闭失败无需处理
        }
    }
}
