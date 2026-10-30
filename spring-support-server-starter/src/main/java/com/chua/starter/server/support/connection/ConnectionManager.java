package com.chua.starter.server.support.connection;

import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.util.SshClients;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SSH 客户端统一连接缓存管理器
 * <p>
 * 以 {@code hostId} 为键缓存已建立的 {@link SshClients.Handle}，
 * 供批量执行 / 硬件探测 / SFTP 等场景复用，避免每次操作重复握手。
 * 由 {@link com.chua.starter.server.support.scheduler.ServerTaskScheduler}
 * 按系统级周期执行健康检查：空闲超时或探测失败的连接被关闭并移出缓存。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectionManager {

    /**
     * 默认空闲超时(秒)
    */
    private static final long DEFAULT_IDLE_TIMEOUT_SECONDS = 300L;

    /**
     * 健康检查：ECHO 命令
    */
    private static final String HEALTH_PROBE_CMD = "echo ok";

    /**
     * SSH 三级超时（连接/认证/读取），数据库系统配置优先，保存后热生效
     */
    private final SshTimeouts timeouts;

    /**
     * 缓存项：已连接句柄 + 最后活动时间 + 引用计数
    */
    static class Entry {
        /**
         * SSH 句柄
        */
        final SshClients.Handle handle;
        /**
         * 凭据指纹（凭据变化时强制重建）
        */
        final String credentialFingerprint;
        /**
         * 最后活动纳秒时间
        */
        volatile long lastActiveNanos;
        /**
         * 引用计数
        */
        final AtomicInteger refCount = new AtomicInteger(0);

        /**
         * Entry
         * @param credentialFingerprint 字符串
         * @param handle 处理
         * @param nowNanos 长整数
         * @return 返回值
        */
        Entry(SshClients.Handle handle, String credentialFingerprint, long nowNanos) {
            this.handle = handle;
            this.credentialFingerprint = credentialFingerprint;
            this.lastActiveNanos = nowNanos;
        }
    }

    /**
     * 主机标识 -> 缓存项
    */
    private final ConcurrentHashMap<Long, Entry> cache = new ConcurrentHashMap<>();

    /**
     * 借出已连接客户端（无缓存或凭据变化时新建并连接）
     *
     * @param host       主机
     * @param credential 解密后的凭据
     * @return 借出的句柄（使用方负责 {@link #release} 归还，禁止自行 关闭）
     * @throws IOException 连接失败时抛出
     */
    public SshClients.Handle borrow(MsServerHost host, SshCredential credential) throws IOException {
        if (host == null || host.getId() == null) {
            return SshClients.open(host == null ? "unknown" : host.getServerHostAddress(),
                    host == null ? null : host.getServerHostPort(), credential);
        }
        long now = System.nanoTime();
        String fp = fingerprint(credential);
        Entry entry = cache.get(host.getId());
        // 无缓存 / 凭据变化 / 已断开：重建
        if (entry == null || !fp.equals(entry.credentialFingerprint) || isClosed(entry)) {
            entry = rebuild(host, credential, fp, now);
        }
        entry.lastActiveNanos = now;
        entry.refCount.incrementAndGet();
        return entry.handle;
    }

    /**
     * 归还借出的连接（减少引用计数，不关闭）
     *
     * @param hostId 主机 标识
     */
    public void release(Long hostId) {
        if (hostId == null) {
            return;
        }
        Entry entry = cache.get(hostId);
        if (entry != null) {
            entry.refCount.decrementAndGet();
        }
    }

    /**
     * 健康检查：探测全部缓存连接，失败或空闲超时即关闭移除
     *
     * @param idleTimeoutSeconds 空闲超时(秒)，<=0 时跳过空闲清理
     * @return 清理的连接数
     */
    public int sweep(long idleTimeoutSeconds) {
        int closed = 0;
        long now = System.nanoTime();
        for (var it = cache.entrySet().iterator(); it.hasNext(); ) {
            var me = it.next();
            Long hostId = me.getKey();
            Entry entry = me.getValue();
            boolean idleTimeout = idleTimeoutSeconds > 0 && entry.refCount.get() == 0
                    && now - entry.lastActiveNanos > TimeUnit.SECONDS.toNanos(idleTimeoutSeconds);
            if (idleTimeout || (entry.refCount.get() == 0 && !isHealthy(entry))) {
                if (closeEntry(hostId, entry)) {
                    closed++;
                }
            }
        }
        if (closed > 0) {
            log.info("[connection] 健康检查关闭 {} 个空闲/失活连接", closed);
        }
        return closed;
    }

    /**
     * 主动关闭并移除指定主机连接
     *
     * @param hostId 主机 标识
     */
    public void invalidate(Long hostId) {
        if (hostId == null) {
            return;
        }
        Entry entry = cache.remove(hostId);
        if (entry != null) {
            /**
             * 关闭entry。
             * @param hostId 主机标识
             * @param entry entry
             */
            closeEntry(hostId, entry);
        }
    }

    /**
     * 当前缓存连接数
     *
     * @return 连接数
     */
    public int size() {
        return cache.size();
    }

    /**
     * 重建并连接
     *
     * @throws IOException 连接失败时抛出
     * @param host 主机
     * @param credential credential
     * @param fp fp
     * @param now now
     * @return rebuild的结果
     */
    private Entry rebuild(MsServerHost host, SshCredential credential, String fp, long now) throws IOException {
        Long hostId = host.getId();
        Entry old = cache.remove(hostId);
        if (old != null) {
            /**
             * 关闭entry。
             * @param hostId 主机标识
             * @param old 旧
             */
            closeEntry(hostId, old);
        }
        try {
            var handle = SshClients.open(host.getServerHostAddress(),
                    host.getServerHostPort(), credential,
                    timeouts.connectSeconds(), timeouts.sessionSeconds(), timeouts.readSeconds());
            handle.client().connect();
            Entry entry = new Entry(handle, fp, now);
            cache.put(hostId, entry);
            return entry;
        } catch (IOException e) {
            int port = host.getServerHostPort() == null || host.getServerHostPort() <= 0
                    ? 22 : host.getServerHostPort();
            throw new ConnectionException(
                    "SSH 连接失败: " + host.getServerHostAddress() + ":" + port + "，" + rootReason(e), e);
        }
    }

    /**
     * 取异常链上最里层的可读原因。
     *
     * @param e 原始异常
     * @return 非空原因文本，无 message 时退回类名
     */
    private static String rootReason(Throwable e) {
        Throwable t = e;
        String last = "";
        while (t != null) {
            String msg = t.getMessage();
            if (msg != null && !msg.isBlank()) {
                last = msg;
            }
            t = t.getCause();
        }
        return last.isEmpty() ? e.getClass().getSimpleName() : last;
    }

    /**
     * 判断缓存连接是否已关闭
     * @param entry entry
     * @return 是否关闭的结果
     */
    private boolean isClosed(Entry entry) {
        return !entry.handle.client().isConnected();
    }

    /**
     * 健康探测（ECHO 保活）
     * @param entry entry
     * @return 是否healthy的结果
     */
    private boolean isHealthy(Entry entry) {
        try {
            var result = entry.handle.client().exec().command(HEALTH_PROBE_CMD).execute();
            return result.exitCode() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 关闭并移除缓存项
     *
     * @param hostId 主机 标识
     * @param entry  缓存项
     * @return 是否关闭成功
     */
    private boolean closeEntry(Long hostId, Entry entry) {
        boolean removed = cache.remove(hostId, entry);
        try {
            entry.handle.close();
            return true;
        } catch (Exception e) {
            log.warn("[connection] 关闭连接失败 hostId={}: {}", hostId, e.getMessage());
            return removed;
        }
    }

    /**
     * 凭据指纹（主机@用户:认证类型，用于检测凭据变化）
     * @param credential credential
     * @return fingerprint的结果
     */
    private String fingerprint(SshCredential credential) {
        if (credential == null) {
            return "";
        }
        String username = credential.username() == null ? "" : credential.username();
        return username + ":" + (credential.hasKey() ? "KEY" : "PWD");
    }
}
