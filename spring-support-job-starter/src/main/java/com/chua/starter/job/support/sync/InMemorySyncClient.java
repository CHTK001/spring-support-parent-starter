package com.chua.starter.job.support.sync;

import com.chua.common.support.network.sync.SyncClient;
import com.chua.common.support.network.sync.SyncFlowListener;
import com.chua.common.support.network.sync.SyncMessageHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 进程内 {@link SyncClient} 默认实现。
 *
 * <p>当 SPI 未发现 SyncClient 扩展时,装配本 bean。EXECUTOR 模式不需要真实连接也能工作。
 * 消息暂存到内存,可通过 {@link #getSent} 断言;订阅关系通过 {@link #deliver} 模拟推送。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public class InMemorySyncClient implements SyncClient {

    /**
     * 客户端标识
     */
    private final String clientId = "stub-client-" + UUID.randomUUID();

    /**
     * 是否已连接
     */
    private volatile boolean connected = false;

    /**
     * 各 topic 的订阅 handler
     */
    private final Map<String, CopyOnWriteArrayList<SyncMessageHandler>> handlers = new ConcurrentHashMap<>();

    /**
     * 已发送的消息
     */
    private final Map<String, java.util.List<Object>> sent = new ConcurrentHashMap<>();

    @Override
    public void connect() {
        connected = true;
    }

    @Override
    public void disconnect() {
        connected = false;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public String getClientId() {
        return clientId;
    }

    @Override
    public void send(String topic, Object message) {
        sent.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(message);
    }

    @Override
    public void subscribe(String topic, SyncMessageHandler handler) {
        handlers.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    @Override
    public void unsubscribe(String topic) {
        handlers.remove(topic);
    }

    @Override
    public void addListener(SyncFlowListener listener) {
    }

    @Override
    public void removeListener(SyncFlowListener listener) {
    }

    @Override
    public Map<String, Object> getMetadata() {
        return new HashMap<>();
    }

    @Override
    public void close() {
        connected = false;
    }

    /**
     * 读取已发送的消息。
     *
     * @param topic 主题
     * @return 消息列表
     */
    public java.util.List<Object> getSent(String topic) {
        return sent.getOrDefault(topic, java.util.List.of());
    }

    /**
     * 模拟服务端推送一条消息给所有订阅者。
     *
     * @param topic   主题
     * @param message 消息内容
     */
    public void deliver(String topic, Object message) {
        CopyOnWriteArrayList<SyncMessageHandler> list = handlers.get(topic);
        if (list != null) {
            for (SyncMessageHandler h : list) {
                try {
                    h.handle(topic, message);
                } catch (Exception ignored) {
                }
            }
        }
    }
}
