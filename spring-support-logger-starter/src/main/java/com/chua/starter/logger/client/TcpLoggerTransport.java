// [P3C A20 豁免] A20/A30/A31: 含日志/地址字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.logger.client;

import com.chua.common.support.network.discovery.ServiceDiscovery;
import com.chua.common.support.network.sync.SyncAddressResolver;
import com.chua.common.support.network.sync.impl.TcpSyncClient;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 日志传输通道：utils 层 {@link TcpSyncClient} 的懒连接包装。
 *
 * <p>寻址规则（每次重连都重新解析，发现中心故障可自动回落）：</p>
 * <ol>
 *   <li>服务发现可用：按 {@code serviceName + scatterId} 查询；</li>
 *   <li>否则/查询失败：回落显式 {@code fallbackUrl}（{@code both} 模式下为
 *       {@code tcp://127.0.0.1:serverPort} 的进程内回环）。</li>
 * </ol>
 *
 * <p>发送失败即认为连接失效：关闭旧连接并置空，下一批触发重连；
 * 连不上的批次直接丢弃并计数，不缓存、不阻塞。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public class TcpLoggerTransport implements AutoCloseable {

    /**
     * 日志事件主题（线上协议为 {@code topic:payload} 单行，topic 不得含冒号）
     */
    public static final String TOPIC = "logger-events";

    private final String clientId;
    private final ServiceDiscovery discovery;
    private final String serviceName;
    private final String scatterId;
    private final String fallbackUrl;

    private volatile TcpSyncClient client;
    private volatile boolean closed;
    private final Object connectLock = new Object();
    private final AtomicLong sentBatches = new AtomicLong();
    private final AtomicLong failedBatches = new AtomicLong();

    /**
     * @param clientId    客户端标识（实例维度唯一）
     * @param discovery   服务发现，可为 null
     * @param serviceName 服务名
     * @param scatterId   业务分组，可为 null
     * @param fallbackUrl 显式地址（含 both 模式回环地址），可为 null
     */
    public TcpLoggerTransport(String clientId, ServiceDiscovery discovery,
                              String serviceName, String scatterId, String fallbackUrl) {
        this.clientId = clientId;
        this.discovery = discovery;
        this.serviceName = serviceName;
        this.scatterId = scatterId;
        this.fallbackUrl = fallbackUrl;
    }

    /**
     * 解析当前应连接的服务端地址。
     *
     * @return 地址；解析不到返回 null
     */
    public String resolveAddress() {
        if (discovery != null && serviceName != null && !serviceName.isBlank()) {
            String url = SyncAddressResolver.resolve(discovery, serviceName, scatterId, null);
            if (url != null && !url.isBlank()) {
                return url;
            }
        }
        return fallbackUrl;
    }

    /**
     * 发送一批事件（单行 JSON 数组）。
     *
     * @param payload 载荷
     */
    public void send(String payload) {
        if (closed || payload == null || payload.isEmpty()) {
            return;
        }
        TcpSyncClient current = ensureConnected();
        if (current == null) {
            failedBatches.incrementAndGet();
            return;
        }
        try {
            current.send(TOPIC, payload);
            sentBatches.incrementAndGet();
        } catch (Exception e) {
            failedBatches.incrementAndGet();
            closeQuietly(current);
            client = null;
        }
    }

    /**
     * 懒连接：已有健康连接直接复用，否则按当前解析地址新建。
     *
     * @return 可用客户端；连接失败返回 null
     */
    private TcpSyncClient ensureConnected() {
        TcpSyncClient current = client;
        if (current != null && current.isConnected()) {
            return current;
        }
        synchronized (connectLock) {
            if (closed) {
                return null;
            }
            current = client;
            if (current != null && current.isConnected()) {
                return current;
            }
            String url = resolveAddress();
            if (url == null || url.isBlank()) {
                return null;
            }
            try {
                TcpSyncClient fresh = new TcpSyncClient(clientId, url);
                fresh.connect();
                client = fresh;
                log.debug("[logger] 传输通道已连接: {}", url);
                return fresh;
            } catch (Exception e) {
                log.debug("[logger] 传输通道连接失败: {}, cause={}", url, e.getMessage());
                return null;
            }
        }
    }

    private void closeQuietly(TcpSyncClient target) {
        try {
            target.disconnect();
        } catch (Exception ignored) {
            // 关闭失败忽略
        }
    }

    /**
     * 已成功发送的批次数
     *
     * @return 计数
     */
    public long sentBatches() {
        return sentBatches.get();
    }

    /**
     * 发送失败的批次数
     *
     * @return 计数
     */
    public long failedBatches() {
        return failedBatches.get();
    }

    @Override
    public void close() {
        closed = true;
        synchronized (connectLock) {
            TcpSyncClient current = client;
            client = null;
            if (current != null) {
                closeQuietly(current);
            }
        }
    }
}
