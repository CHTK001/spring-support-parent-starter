package com.chua.starter.job.support.sync;

import com.chua.common.support.network.ProtocolType;
import com.chua.common.support.objects.ObjectContext;
import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.SyncServer;
import com.chua.common.support.network.server.SyncServerListener;
import com.chua.common.support.network.server.filter.ServerFilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 进程内 {@link SyncServer} 默认实现。
 *
 * <p>当 SPI 未发现 SyncServer 扩展时 (常见于精简部署或单元测试),
 * {@link com.chua.starter.job.support.JobAutoConfiguration} 装配本 bean,
 * 让 SCHEDULER 模式不需要真实网络也能工作。</p>
 *
 * <p>所有 publish / send 都进入内存列表,可通过 {@link #getPublished} 断言。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public class InMemorySyncServer implements SyncServer {

    /**
     * 已发布的 topic 列表
     */
    private final Map<String, List<Object>> published = new HashMap<>();

    /**
     * listener 列表
     */
    private final List<SyncServerListener> listeners = new ArrayList<>();

    /**
     * 客户端列表
     */
    private final List<String> clients = new ArrayList<>();

    @Override
    public void publish(String topic, Object message) {
        published.computeIfAbsent(topic, k -> new ArrayList<>()).add(message);
        for (SyncServerListener listener : listeners) {
            try {
                listener.onMessage("local", topic, message);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void send(String clientId, String topic, Object message) {
        published.computeIfAbsent(topic, k -> new ArrayList<>()).add(message);
    }

    @Override
    public List<String> getConnectedClients() {
        return Collections.unmodifiableList(clients);
    }

    @Override
    public Map<String, Object> getClientMetadata(String clientId) {
        return Map.of();
    }

    @Override
    public void addListener(SyncServerListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(SyncServerListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    public ProtocolType getProtocolType() {
        return ProtocolType.WS;
    }

    @Override
    public int getPort() {
        return 18081;
    }

    @Override
    public ServerSetting getSetting() {
        return null;
    }

    @Override
    public ObjectContext getObjectContext() {
        return null;
    }

    @Override
    public void setObjectContext(ObjectContext objectContext) {
    }

    @Override
    public List<ServerFilter> getFilters() {
        return List.of();
    }

    @Override
    public Server addFilter(ServerFilter filter) {
        return this;
    }

    @Override
    public Server removeFilter(ServerFilter filter) {
        return this;
    }

    @Override
    public Server refreshFilters() {
        return this;
    }

    @Override
    public Server registerBean(Object bean) {
        return this;
    }

    @Override
    public Server unregisterBean(Object bean) {
        return this;
    }

    @Override
    public void close() {
    }

    /**
     * 读取已发布的消息。
     *
     * @param topic 主题
     * @return 消息列表
     */
    public List<Object> getPublished(String topic) {
        return Collections.unmodifiableList(published.getOrDefault(topic, List.of()));
    }

    /**
     * 模拟一个客户端连入。
     *
     * @param clientId 客户端标识
     */
    public void addClient(String clientId) {
        clients.add(clientId);
    }
}
