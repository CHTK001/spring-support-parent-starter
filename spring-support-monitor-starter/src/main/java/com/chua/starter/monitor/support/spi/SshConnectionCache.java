package com.chua.starter.monitor.support.spi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.entity.MsServerHostIdentity;
import com.chua.starter.server.support.entity.MsServerIdentity;
import com.chua.starter.server.support.mapper.MsServerHostIdentityMapper;
import com.chua.starter.server.support.mapper.MsServerIdentityMapper;
import com.chua.starter.monitor.support.config.MonitorSshProperties;
import com.chua.starter.server.support.util.CredentialCipher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * SSH / SFTP 连接缓存。
 *
 * <p>按主机 ID 复用 {@code SshClient} / {@code SftpClient} 会话，TTL 5 分钟，
 * 由守护线程每分钟清理一次过期会话，避免频繁建连带来的开销。</p>
 *
 * <p>命令执行按主机限流：同一会话上并发通道超过服务端上限会连整条连接被拆掉，
 * 因此超出 {@link #MAX_INFLIGHT_PER_HOST} 的调用排队等槽。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class SshConnectionCache {

    /**
     * 单台主机的会话缓存项
    */
    private static class CachedSession {
        /**
         * SSH 会话
        */
        com.chua.ssh.support.client.SshClient ssh;
        /**
         * SFTP 会话
        */
        com.chua.ssh.support.client.SftpClient sftp;
        /**
         * 过期时间戳（毫秒）
        */
        long expireAt;
    }

    /**
     * 缓存：主机 ID -> 会话
    */
    private static final Map<Object, CachedSession> cache = new ConcurrentHashMap<>();
    /**
     * 会话空闲有效期：5 分钟
    */
    private static final long TTL_MS = 5 * 60 * 1000L;
    /**
     * 单主机的执行槽位闸门：主机 ID -> 信号量
    */
    private static final Map<Object, Semaphore> GATES = new ConcurrentHashMap<>();
    /**
     * 单主机在途通道上限：服务端 MaxSessions（Windows OpenSSH 默认 10）被打满会直接拆掉整条连接，
     * 故留出余量
    */
    private static final int MAX_INFLIGHT_PER_HOST = 4;
    /**
     * 拆会话前独占槽位的等待上限：同主机其它命令多为秒级，等得起；超时也只退化为「不独占地丢弃会话」
    */
    private static final long DRAIN_WAIT_MS = 10_000L;
    /**
     * 等槽上限：宁可排队等前序命令让位，也不把并发压力直接变成用户可见失败；超时才快速失败
    */
    private static final long GATE_WAIT_MS = 120_000L;
    /**
     * 未取到退出码：通道在 EXEC 结束前被关掉，输出同样不可信
    */
    private static final int NO_EXIT_STATUS = -1;

    @Autowired(required = false)
    /**
     * 主机IdentityMapper
    */
    private MsServerHostIdentityMapper hostIdentityMapper;
    @Autowired(required = false)
    /**
     * identityMapper
    */
    private MsServerIdentityMapper identityMapper;
    @Autowired(required = false)
    /**
     * credentialCipher
    */
    private CredentialCipher credentialCipher;
    @Autowired(required = false)
    /**
     * ssh 超时配置
    */
    private MonitorSshProperties sshProperties;

    /**
     * 连接超时（秒），缺省给 15
     * @return 返回值
    */
    private int connectTimeoutSeconds() {
        return sshProperties == null ? 15 : Math.max(1, sshProperties.getConnectTimeoutSeconds());
    }
    /**
     * 会话超时（秒），缺省给 15
     * @return 返回值
    */
    private int sessionTimeoutSeconds() {
        return sshProperties == null ? 15 : Math.max(1, sshProperties.getSessionTimeoutSeconds());
    }
    /**
     * 命令执行超时（秒），缺省给 60
     * @return 返回值
    */
    private long execTimeoutSeconds() {
        return sshProperties == null ? 60L : Math.max(1L, sshProperties.getExecTimeoutSeconds());
    }
    @Autowired(required = false)
    /**
     * 键Mapper
    */
    private com.chua.starter.server.support.mapper.MsServerKeyMapper keyMapper;

    /**
     * 主机绑定的登录凭据（SSH 口令/私钥与 WinRM 口令同源，均来自主机绑定的身份）。
     *
     * @param username   登录名
     * @param password   口令或私钥口令，可为空
     * @param privateKey 私钥内容，可为空
     */
    public record LoginCredential(String username, String password, String privateKey) {
    }

    /**
     * 解析主机绑定的全部可用登录凭据，按优先级排序（非 root 优先）。
     *
     * @param host 主机，不允许为 null
     * @return 结果列表，无可用凭据时为空列表
     */
    public List<LoginCredential> credentials(MsServerHost host) {
        List<LoginCredential> result = new java.util.ArrayList<>();
        if (hostIdentityMapper == null || identityMapper == null) {
            return result;
        }
        try {
            List<MsServerHostIdentity> rels = hostIdentityMapper.selectList(
                    new LambdaQueryWrapper<MsServerHostIdentity>()
                            .eq(MsServerHostIdentity::getMsServerHostIdentityHostId, host.getId())
                            .orderByAsc(MsServerHostIdentity::getMsServerHostIdentitySort));
            List<LoginCredential> roots = new java.util.ArrayList<>();
            for (MsServerHostIdentity rel : rels) {
                MsServerIdentity id = identityMapper.selectById(rel.getMsServerHostIdentityIdentityId());
                if (id == null) {
                    continue;
                }
                String uname = id.getMsServerIdentityUsername();
                if (uname == null) {
                    continue;
                }
                LoginCredential cred = null;
                if ("PUBLIC_KEY".equalsIgnoreCase(id.getMsServerIdentityAuthType())) {
                    if (keyMapper != null && id.getMsServerIdentityKeyId() != null) {
                        var key = keyMapper.selectById(id.getMsServerIdentityKeyId());
                        if (key != null && key.getMsServerKeyPrivateKey() != null) {
                            String privKey = credentialCipher != null ? credentialCipher.decrypt(key.getMsServerKeyPrivateKey()) : key.getMsServerKeyPrivateKey();
                            String passPhrase = key.getMsServerKeyPassword() != null ? (credentialCipher != null ? credentialCipher.decrypt(key.getMsServerKeyPassword()) : key.getMsServerKeyPassword()) : null;
                            cred = new LoginCredential(uname, passPhrase, privKey);
                        }
                    }
                } else if ("NONE".equalsIgnoreCase(id.getMsServerIdentityAuthType())) {
                    // 免密认证：目标主机已放行（authorized_keys 或允许空口令），
                    // 凭据只承载用户名，密码/私钥均为 null；连接层不设空口令，
                    // SshClient 会回落到默认身份文件走公钥认证
                    cred = new LoginCredential(uname, null, null);
                } else {
                    String encPass = id.getMsServerIdentityPassword();
                    if (encPass != null && !encPass.isBlank()) {
                        String pass = credentialCipher != null ? credentialCipher.decrypt(encPass) : encPass;
                        cred = new LoginCredential(uname, pass, null);
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
            // 地址里的 user@ 前缀是网关分配的真实登录名，优先于台账身份里的历史账号
            result = preferAddressAccount(host, result);
            // 本机自采集主机没有绑定身份时，用平台运行账号走回环免密
            if (result.isEmpty() && isLocal(host)) {
                String osUser = System.getProperty("user.name");
                if (osUser != null && !osUser.isBlank()) {
                    result.add(new LoginCredential(osUser, null, null));
                }
            }
        } catch (Exception e) {
            log.warn("credentials failed host={}: {}", host.getId(), e.getMessage());
        }
        return result;
    }

    /**
     * 地址前缀账号优先。
     *
     * <p>{@code ibssh_xxx@ssh.instabox.sh} 里的 {@code ibssh_xxx} 是网关分配的登录名，
     * 台账里绑定的身份账号可能是历史默认值（user/root），直接连必然认证失败。
     * 这里用同一份口令/私钥先按地址账号试一次，再保留原身份作为回落。</p>
     *
     * @param host  主机
     * @param creds 已解析的绑定身份凭据
     * @return 地址账号排在前的凭据列表
     */
    private static List<LoginCredential> preferAddressAccount(MsServerHost host, List<LoginCredential> creds) {
        String user = addressUser(host.getServerHostAddress());
        if (user == null) {
            return creds;
        }
        java.util.List<LoginCredential> merged = new java.util.ArrayList<>(creds.size() + 1);
        boolean same = false;
        for (LoginCredential c : creds) {
            if (user.equalsIgnoreCase(c.username())) {
                same = true;
                break;
            }
        }
        if (!same) {
            for (LoginCredential c : creds) {
                merged.add(new LoginCredential(user, c.password(), c.privateKey()));
            }
        }
        merged.addAll(creds);
        return merged;
    }

    /**
     * 取地址里的登录名（{@code user@host} 的 user 部分）。
     *
     * @param raw 原始地址
     * @return 登录名；地址不含 user@ 前缀时返回 null
     */
    private static String addressUser(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int at = raw.lastIndexOf('@');
        if (at <= 0 || at >= raw.length() - 1) {
            return null;
        }
        String afterAt = raw.substring(at + 1);
        if (!afterAt.contains(".") && !afterAt.contains(":") && !afterAt.contains("/")) {
            return null;
        }
        String user = raw.substring(0, at).trim();
        if (user.isEmpty() || user.contains("/") || user.contains(":") || user.contains("\\")) {
            return null;
        }
        return user;
    }

    /**
     * 是否本机自采集主机。
     *
     * @param host 主机
     * @return true=LOCAL
     */
    private static boolean isLocal(MsServerHost host) {
        return host.getServerHostCollectMode() != null
                && "LOCAL".equalsIgnoreCase(host.getServerHostCollectMode().trim());
    }

    static {
        // 守护线程周期清理过期会话，避免连接泄漏
// [P3C C1 豁免] 守护/后台线程：有独立生命周期，不适合交给通用执行器
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
        List<LoginCredential> creds = credentials(host);
        if (creds.isEmpty()) {
            throw new IllegalStateException("主机 " + host.getId() + " 未绑定 SSH 凭据，请先在主机资产配置身份");
        }
        Exception lastErr = null;
        for (LoginCredential cred : creds) {
            try {
                // host.getServerHostAddress() 可能带 user@ 前缀（历史数据/粘贴导入），
                // MINA SSHD 的 hostname 校验器拒绝 @ 字符，必须剥离；
                // 本机自采集主机走回环，避免绕公网入口/网桥地址
                String address = effectiveAddress(host);
                var builder = com.chua.ssh.support.client.SshClient.builder()
                        .host(address)
                        .port(host.getServerHostPort() != null ? host.getServerHostPort() : 22)
                        .username(cred.username())
                        .hostKeyPolicy(com.chua.ssh.support.client.HostKeyPolicy.INSECURE)
                        .connectTimeout(connectTimeoutSeconds())
                        .sessionTimeout(sessionTimeoutSeconds())
                        .execTimeout(execTimeoutSeconds());
                if (cred.privateKey() != null) {
                    java.nio.file.Path tmp = java.nio.file.Files.createTempFile("ms-ssh-", ".pem");
                    java.nio.file.Files.writeString(tmp, cred.privateKey());
                    builder.privateKey(tmp.toString());
                    if (cred.password() != null && !cred.password().isBlank()) {
                        builder.password(cred.password());
                    }
                } else if (cred.password() != null && !cred.password().isBlank()) {
                    builder.password(cred.password());
                }
                com.chua.ssh.support.client.SshClient ssh = builder.build();
                ssh.connect();
                if (c != null && c.ssh != null) {
                    try {
                        c.ssh.disconnect();
                    } catch (Exception ignored) {
                        // 旧会话已失效，断开失败不影响替换
                    }
                }
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
        throw new IllegalStateException("SSH 连接失败 host=" + host.getId() + " "
                + host.getServerHostAddress() + ":" + (host.getServerHostPort() != null ? host.getServerHostPort() : 22)
                + " 账号=" + creds.stream().map(LoginCredential::username).distinct().reduce((a, b) -> a + "/" + b).orElse("-")
                + " 已尝试 " + creds.size() + " 组凭据"
                + (lastErr == null ? "" : "，最后原因: " + describe(lastErr)), lastErr);
    }

    /**
     * 剥离主机地址中的 user@ 前缀。
     *
     * <p>历史数据或粘贴导入可能把整行 ssh 命令（{@code user@host}）写进地址字段，
     * MINA SSHD 的 hostname 校验器把 @ 当非法字符拒绝连接。这里只剥前缀，
     * 不修改原字段值——地址可能用于展示，展示 {@code user@host} 反而更直观。</p>
     *
     * @param raw 原始地址（可能含 user@ 前缀）
     * @return 纯主机地址（无 user@ 前缀）
     */
    private static String cleanAddress(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        int at = raw.lastIndexOf('@');
        // 仅当 @ 后面看起来像主机地址（含 . 或 : 或 /）才剥离
        if (at > 0 && at < raw.length() - 1) {
            String afterAt = raw.substring(at + 1);
            if (afterAt.contains(".") || afterAt.contains(":") || afterAt.contains("/")) {
                return afterAt;
            }
        }
        return raw;
    }

    /**
     * 实际拨号地址：本机自采集主机固定回环，其余剥离 user@ 前缀。
     *
     * <p>本机主机台账里的地址可能是网桥地址（172.18.0.1）或公网入口地址，
     * 从平台内部再拨这两个地址都属于绕路：前者依赖 docker 网桥存活，后者还会
     * 撞上 hairpin NAT。SSH/SFTP 对本机统一走 127.0.0.1。</p>
     *
     * @param host 主机
     * @return 可拨号的地址
     */
    private static String effectiveAddress(MsServerHost host) {
        if (isLocal(host)) {
            return "127.0.0.1";
        }
        return cleanAddress(host.getServerHostAddress());
    }

    /**
     * 取异常链上最里层的可读描述。
     *
     * <p>必须走到最里层：SSH 客户端把整条命令行塞进了最外层的 message（EncodedCommand 是上千字符
     * 的 base64），真正的原因只在更内层的 cause 上。</p>
     *
     * @param e 原始异常
     * @return 非空的原因文本，异常未带 message 时退回类名
     */
    private static String describe(Throwable e) {
        String deepest = null;
        Throwable last = e;
        for (Throwable t = e; t != null; t = t.getCause()) {
            last = t;
            String msg = t.getMessage();
            if (msg != null && !msg.isBlank()) {
                deepest = msg;
            }
        }
        if (deepest == null) {
            return last.getClass().getSimpleName();
        }
        String oneLine = deepest.replaceAll("-EncodedCommand\\s+\\S+", "-EncodedCommand <base64>").replaceAll("\\r?\\n", " ");
        return oneLine.length() <= 300 ? oneLine : oneLine.substring(0, 300) + "...";
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
        if (c.sftp != null && c.sftp.isOpen()) {
            c.expireAt = System.currentTimeMillis() + TTL_MS;
            return c.sftp;
        }
        List<LoginCredential> creds = credentials(host);
        if (creds.isEmpty()) {
            throw new IllegalStateException("主机 " + host.getId() + " 未绑定 SSH 凭据，请先在主机资产配置身份");
        }
        Exception lastErr = null;
        for (LoginCredential cred : creds) {
            try {
                // host.getServerHostAddress() 可能带 user@ 前缀（历史数据/粘贴导入），
                // MINA SSHD 的 hostname 校验器拒绝 @ 字符，必须剥离；
                // 本机自采集主机走回环，避免绕公网入口/网桥地址
                String address = effectiveAddress(host);
                var sftpBuilder = com.chua.ssh.support.client.SftpClient.builder()
                        .host(address)
                        .port(host.getServerHostPort() != null ? host.getServerHostPort() : 22)
                        .username(cred.username())
                        .hostKeyPolicy(com.chua.ssh.support.client.HostKeyPolicy.INSECURE)
                        .connectTimeout(connectTimeoutSeconds());
                if (cred.privateKey() != null) {
                    java.nio.file.Path tmp = java.nio.file.Files.createTempFile("ms-sftp-", ".pem");
                    java.nio.file.Files.writeString(tmp, cred.privateKey());
                    sftpBuilder.privateKey(tmp.toString());
                    if (cred.password() != null && !cred.password().isBlank()) {
                        sftpBuilder.password(cred.password());
                    }
                } else if (cred.password() != null && !cred.password().isBlank()) {
                    sftpBuilder.password(cred.password());
                }
                com.chua.ssh.support.client.SftpClient sftp = sftpBuilder.build();
                sftp.connect();
                c.sftp = sftp;
                c.expireAt = System.currentTimeMillis() + TTL_MS;
                return sftp;
            } catch (Exception e) {
                lastErr = e;
                log.warn("SFTP connect failed host={} user={}: {}", host.getId(), cred.username(), e.getMessage());
            }
        }
        throw new IllegalStateException("SFTP 连接失败 host=" + host.getId() + " "
                + host.getServerHostAddress() + ":" + (host.getServerHostPort() != null ? host.getServerHostPort() : 22)
                + " 已尝试 " + creds.size() + " 组凭据"
                + (lastErr == null ? "" : "，最后原因: " + describe(lastErr)), lastErr);
    }

    /**
     * 在目标主机执行命令并返回结构化结果（退出码 + 标准输出 + 标准错误），会话失效时重连重试一次。
     *
     * <p>缓存的长连接可能变成僵尸态：MINA 的 {@code isConnected()} 仍为真，但打不开新通道，
     * 每条命令都在十几毫秒内失败。这类失败必须换连接，否则会一直坏到进程重启。</p>
     *
     * <p>并发受 {@link #MAX_INFLIGHT_PER_HOST} 约束：同一条会话上并发通道一多，服务端会直接拆掉整条
     * 连接（实测 192.168.200.18 八路并发即触发），此时正在写入的其它通道会一起收到
     * {@code Write attempt on a closed session}；因此重连只允许拆自己用过的那条会话，见
     * {@link #retireUsed}。</p>
     *
     * @param host 目标主机
     * @param cmd  命令
     * @return 执行结果
     * @throws IllegalStateException 无法建连、排队超时或执行中断，避免以空输出冒充执行成功
     */
    public com.chua.ssh.support.client.SshClient.ExecResult execResult(MsServerHost host, String cmd) {
        Semaphore gate = gate(host.getId());
        boolean acquired;
        try {
            acquired = gate.tryAcquire(GATE_WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("主机 " + host.getId() + " 等待执行槽位被中断", e);
        }
        if (!acquired) {
            throw new IllegalStateException("主机 " + host.getId() + " 并发执行排队超过 " + GATE_WAIT_MS + "ms");
        }
        try {
            return execWithReconnect(host, cmd, gate);
        } finally {
            gate.release();
        }
    }

    /**
     * 执行命令，最多两轮：首轮失败则丢弃本次用到的会话再重连一次。
     *
     * <p>「服务端只是拒开通道、会话还能用」的猜测已被实测推翻：拒开之后原地重试继续拒开，28 路并发
     * 全部失败且永不自愈；换一条新连接则立刻恢复。拿不到退出码同理，说明通道中途被关掉，输出不可信。</p>
     *
     * @param host 目标主机
     * @param cmd  命令
     * @param gate 本主机已占用的执行槽位闸门
     * @return 执行结果
     */
    private com.chua.ssh.support.client.SshClient.ExecResult execWithReconnect(MsServerHost host, String cmd,
                                                                               Semaphore gate) {
        Exception last = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            com.chua.ssh.support.client.SshClient ssh = getSsh(host);
            try {
                com.chua.ssh.support.client.SshClient.ExecResult r = ssh.exec().command(cmd).execute();
                if (r.exitCode() != NO_EXIT_STATUS || attempt == 2) {
                    return r;
                }
                last = new IllegalStateException("未取到退出码，通道在执行中被关闭");
            } catch (Exception e) {
                last = e;
            }
            log.warn("SSH exec 第 {} 次失败 host={}，丢弃会话重连: {}", attempt, host.getId(), describe(last));
            retireUsed(host, gate, ssh);
        }
        throw new IllegalStateException("主机 " + host.getId() + " 命令执行失败: " + describe(last), last);
    }

    /**
     * 丢弃本次失败所用的会话，且尽量不误伤同主机其它在途命令。
     *
     * <p>两级保护：一是身份比对，缓存里已被别的线程换成新会话时直接返回，否则重试风暴会把刚建好的连接
     * 再拆一遍；二是先申请走该主机剩余的全部槽位，拿到即独占，此时关连接没人正在用。等槽超时才退化为
     * 直接丢弃——本条连接大概率已经坏了，留给它继续被使用只会让更多命令失败。</p>
     *
     * @param host 目标主机
     * @param gate 本主机已占用的执行槽位闸门（本线程持 1 个）
     * @param used 本次执行实际使用的会话
     */
    private void retireUsed(MsServerHost host, Semaphore gate, com.chua.ssh.support.client.SshClient used) {
        int spare = MAX_INFLIGHT_PER_HOST - 1;
        boolean drained = false;
        try {
            drained = gate.tryAcquire(spare, DRAIN_WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            closeIfSame(host, used);
        } finally {
            if (drained) {
                gate.release(spare);
            }
        }
    }

    /**
     * 仅当缓存里的会话就是 {@code used} 时才移除并关闭。
     *
     * @param host 目标主机
     * @param used 待丢弃的会话
     */
    private synchronized void closeIfSame(MsServerHost host, com.chua.ssh.support.client.SshClient used) {
        CachedSession c = cache.get(host.getId());
        if (c == null || c.ssh != used) {
            return;
        }
        cache.remove(host.getId(), c);
        closeQuietly(c);
    }

    /**
     * 取（或新建）主机的执行槽位闸门。
     *
     * @param key 主机 ID
     * @return 该主机的信号量
     */
    private static Semaphore gate(Object key) {
        return GATES.computeIfAbsent(key, k -> new Semaphore(MAX_INFLIGHT_PER_HOST));
    }

    /**
     * 在目标主机执行命令并返回标准输出。
     *
     * @param host      目标主机
     * @param cmd       命令
     * @param timeoutMs 预留的超时参数，当前由会话级超时兜底
     * @return 标准输出（已 trim）；命令无输出时为空字符串
     * @throws IllegalStateException 无法建连或执行中断，避免以空输出冒充执行成功
     */
    public String exec(MsServerHost host, String cmd, long timeoutMs) {
        String out = execResult(host, cmd).stdout();
        return out != null ? out.trim() : "";
    }

    /**
     * 丢弃并静默关闭主机的缓存会话（SSH + SFTP），强制下次重建连接。
     *
     * @param host 目标主机
     */
    public synchronized void invalidate(MsServerHost host) {
        CachedSession c = cache.remove(host.getId());
        if (c != null) {
            closeQuietly(c);
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
