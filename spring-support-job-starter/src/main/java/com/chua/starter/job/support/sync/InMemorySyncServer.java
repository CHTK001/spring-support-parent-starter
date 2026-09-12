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
   * 让 调度器 模式不需要真实网络也能工作。</p>
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
      * 监听器 列表
     */
    private final List<SyncServerListener> listeners = new ArrayList<>();

    /**
     * 客户端列表
     */
    private final List<String> clients = new ArrayList<>();

    @Override
    /** 发布 */
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
    /** 发送 */
    public void send(String clientId, String topic, Object message) {
        published.computeIfAbsent(topic, k -> new ArrayList<>()).add(message);
    }

    @Override
    /** 获取连接客户端 */
    public List<String> getConnectedClients() {
        return Collections.unmodifiableList(clients);
    }

    @Override
    /** 获取客户端metadata */
    public Map<String, Object> getClientMetadata(String clientId) {
        return Map.of();
    }

    @Override
    /** 添加监听器 */
    public void addListener(SyncServerListener listener) {
        listeners.add(listener);
    }

    @Override
    /** 移除监听器 */
    public void removeListener(SyncServerListener listener) {
        listeners.remove(listener);
    }

    @Override
    /** 开始 */
    public void start() {
    }

    @Override
    /** 停止 */
    public void stop() {
    }

    @Override
    /** 是否Running */
    public boolean isRunning() {
        return true;
    }

    @Override
    /** 获取协议类型 */
    public ProtocolType getProtocolType() {
        return ProtocolType.WS;
    }

    @Override
    /** 获取端口 */
    public int getPort() {
        return 18081;
    }

    @Override
    /** 获取Setting */
    public ServerSetting getSetting() {
        return null;
    }

    @Override
    /** 获取对象上下文 */
    public ObjectContext getObjectContext() {
        return null;
    }

    @Override
    /** 设置对象上下文 */
    public void setObjectContext(ObjectContext objectContext) {
    }

    @Override
    /** 获取过滤器 */
    public List<ServerFilter> getFilters() {
        return List.of();
    }

    @Override
    /** 添加过滤 */
    public Server addFilter(ServerFilter filter) {
        return this;
    }

    @Override
    /** 移除过滤 */
    public Server removeFilter(ServerFilter filter) {
        return this;
    }

    @Override
    /** refresh过滤器 */
    public Server refreshFilters() {
        return this;
    }

    @Override
    /** 注册Bean */
    public Server registerBean(Object bean) {
        return this;
    }

    @Override
    /** 注销Bean */
    public Server unregisterBean(Object bean) {
        return this;
    }

    @Override
    /** 关闭 */
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
