package com.chua.sync.support.client;

import com.chua.common.support.protocol.ProtocolSetting;
import com.chua.common.support.protocol.listener.ConnectionEvent;
import com.chua.common.support.protocol.listener.ConnectionListener;
import com.chua.common.support.protocol.listener.DataEvent;
import com.chua.common.support.protocol.listener.TopicListener;
import com.chua.common.support.protocol.sync.SyncProtocol;
import com.chua.common.support.protocol.sync.SyncProtocolClient;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.sync.support.properties.SyncProperties;
import com.chua.sync.support.server.SyncServer;
import com.chua.sync.support.spi.SyncMessageHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 同步协议客户端
 * <p>
 * 基于 SyncProtocol 实现长连接通信，支持：
 * <ul>
 *   <li>订阅配置的所有主题</li>
 *   <li>通过 SPI 加载处理器处理消息</li>
 *   <li>自动重连机制</li>
 *   <li>心跳保活</li>
 * </ul>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/04
 */
@Slf4j
public class SyncClient implements InitializingBean, DisposableBean {

    private final SyncProperties syncProperties;

    /**
     * 同步协议客户端
     */
    @Getter
    private SyncProtocolClient protocolClient;

    /**
     * 定时同步调度器
     */
    private ScheduledExecutorService scheduler;

    /**
     * 消息处理器映射：topic -> handlers
     */
    private final Map<String, List<SyncMessageHandler>> handlerMap = new ConcurrentHashMap<>();

    /**
     * 连接状态
     */
    private final AtomicBoolean connected = new AtomicBoolean(false);

    /**
     * 重连次数
     */
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

    public SyncClient(SyncProperties syncProperties) {
        this.syncProperties = syncProperties;
        loadHandlers();
    }

    /**
     * 加载所有消息处理器
     */
    private void loadHandlers() {
        ServiceProvider<SyncMessageHandler> serviceProvider = ServiceProvider.of(SyncMessageHandler.class);
        List<SyncMessageHandler> allHandlers = new ArrayList<>(serviceProvider.collect());
        allHandlers.sort(Comparator.comparingInt(SyncMessageHandler::getOrder));

        // 按配置的 topics 映射处理器
        Map<String, String> topics = syncProperties.getTopics();
        for (Map.Entry<String, String> entry : topics.entrySet()) {
            String topic = entry.getKey();
            String handlerName = entry.getValue();

            List<SyncMessageHandler> handlers = allHandlers.stream()
                    .filter(h -> handlerName.equals(h.getName()) || h.supports(topic))
                    .toList();

            if (!handlers.isEmpty()) {
                handlerMap.put(topic, handlers);
                log.info("[Sync客户端] 主题 {} 加载了 {} 个处理器", topic, handlers.size());
            }
        }

        log.info("[Sync客户端] 共加载 {} 个主题的处理器", handlerMap.size());
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!syncProperties.isEnable() || !"client".equalsIgnoreCase(syncProperties.getType())) {
            log.info("[Sync客户端] 未启用");
            return;
        }

        connect();
        startScheduler();
    }

    /**
     * 连接到服务端
     */
    private void connect() {
        try {
            // 解析服务端地址
            URI serverUri = URI.create(syncProperties.getServerAddress());
            String host = serverUri.getHost();
            int port = serverUri.getPort() > 0 ? serverUri.getPort() : syncProperties.getServerPort();

            // 构建协议配置
            ProtocolSetting protocolSetting = ProtocolSetting.builder()
                    .protocol(syncProperties.getProtocol())
                    .host(host)
                    .port(port)
                    .heartbeat(syncProperties.isHeartbeat())
                    .heartbeatInterval(syncProperties.getHeartbeatInterval())
                    .connectTimeoutMillis(syncProperties.getConnectTimeout())
                    .build();

            // 创建同步协议实例
            SyncProtocol protocol = SyncProtocol.create(syncProperties.getProtocol(), protocolSetting);

            // 创建客户端
            protocolClient = protocol.createClient(protocolSetting);

            // 订阅所有配置的主题 + 系统主题
            List<String> allTopics = new ArrayList<>(syncProperties.getTopics().keySet());
            allTopics.add(SyncServer.TOPIC_HEALTH);
            allTopics.add(SyncServer.TOPIC_RESPONSE);
            allTopics.add(SyncServer.TOPIC_CONNECT);      // 监听其他客户端连接事件
            allTopics.add(SyncServer.TOPIC_DISCONNECT);   // 监听其他客户端断开事件
            protocolClient.subscribe(allTopics.toArray(new String[0]));

            // 为每个主题添加监听器
            for (String topic : allTopics) {
                protocolClient.getListenerManager().addTopicListener(new TopicListener() {
                    @Override
                    public String getTopic() {
                        return topic;
                    }

                    @Override
                    public void onEvent(DataEvent event) {
                        SyncClient.this.onMessage(event.getTopic(), event.getData());
                    }
                });
            }

            // 添加连接监听器
            protocolClient.getListenerManager().addConnectionListener(new ConnectionListener() {
                @Override
                public void onEvent(ConnectionEvent event) {
                    boolean isConnected = event.getEventType() == ConnectionEvent.Type.CONNECTED ||
                            event.getEventType() == ConnectionEvent.Type.RECONNECTED;
                    onConnectionStateChanged(isConnected);
                }
            });

            // 连接服务端
            protocolClient.connect();
            connected.set(true);
            reconnectAttempts.set(0);

            log.info("[Sync客户端] 连接成功，协议: {}，服务器: {}:{}",
                    syncProperties.getProtocol(), host, port);
        } catch (Exception e) {
            log.error("[Sync客户端] 连接失败", e);
            connected.set(false);
            scheduleReconnect();
        }
    }

    /**
     * 处理连接状态变化
     */
    private void onConnectionStateChanged(boolean isConnected) {
        if (isConnected) {
            connected.set(true);
            reconnectAttempts.set(0);
            log.info("[Sync客户端] 已连接");
        } else {
            connected.set(false);
            log.warn("[Sync客户端] 已断开");
            scheduleReconnect();
        }
    }

    /**
     * 调度重连
     */
    private void scheduleReconnect() {
        int maxAttempts = syncProperties.getMaxReconnectAttempts();

        if (maxAttempts >= 0 && reconnectAttempts.get() >= maxAttempts) {
            log.error("[Sync客户端] 达到最大重连次数 {}，停止重连", maxAttempts);
            return;
        }

        int attempts = reconnectAttempts.incrementAndGet();
        int delay = syncProperties.getReconnectInterval();

        log.info("[Sync客户端] {} 秒后尝试第 {} 次重连", delay, attempts);

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.schedule(this::connect, delay, TimeUnit.SECONDS);
        }
    }

    /**
     * 处理接收到的消息
     */
    @SuppressWarnings("unchecked")
    private void onMessage(String topic, Object data) {
        log.debug("[Sync客户端] 收到消息: topic={}", topic);

        try {
            // 处理系统主题
            if (SyncServer.TOPIC_HEALTH.equals(topic)) {
                log.debug("[Sync客户端] 服务端健康状态: {}", data);
                return;
            }

            if (SyncServer.TOPIC_RESPONSE.equals(topic)) {
                Map<String, Object> response = (Map<String, Object>) data;
                log.debug("[Sync客户端] 收到响应: topic={}, code={}",
                        response.get("topic"), response.get("code"));
                return;
            }

            // 处理连接/断开事件
            if (SyncServer.TOPIC_CONNECT.equals(topic)) {
                Map<String, Object> event = (Map<String, Object>) data;
                log.info("[Sync客户端] 其他客户端连接: sessionId={}, 当前在线: {}",
                        event.get("sessionId"), event.get("onlineCount"));
                handleConnectionEvent(topic, event);
                return;
            }

            if (SyncServer.TOPIC_DISCONNECT.equals(topic)) {
                Map<String, Object> event = (Map<String, Object>) data;
                log.info("[Sync客户端] 其他客户端断开: sessionId={}, 当前在线: {}",
                        event.get("sessionId"), event.get("onlineCount"));
                handleConnectionEvent(topic, event);
                return;
            }

            // 获取主题对应的处理器
            List<SyncMessageHandler> handlers = handlerMap.get(topic);
            if (handlers == null || handlers.isEmpty()) {
                log.warn("[Sync客户端] 未找到主题 {} 的处理器", topic);
                return;
            }

            // 循环执行所有处理器
            Map<String, Object> dataMap = data instanceof Map ? (Map<String, Object>) data : Map.of("data", data);
            for (SyncMessageHandler handler : handlers) {
                try {
                    handler.handle(topic, null, dataMap);
                    log.debug("[Sync客户端] 处理器 {} 处理主题 {} 完成", handler.getName(), topic);
                } catch (Exception e) {
                    log.error("[Sync客户端] 处理器 {} 处理主题 {} 失败", handler.getName(), topic, e);
                }
            }
        } catch (Exception e) {
            log.error("[Sync客户端] 处理消息失败: topic={}", topic, e);
        }
    }

    /**
     * 发布消息到服务端
     *
     * @param topic 主题
     * @param data  数据
     */
    public void publish(String topic, Object data) {
        if (!connected.get() || protocolClient == null) {
            log.warn("[Sync客户端] 未连接服务端，无法发布消息");
            return;
        }
        protocolClient.publish(topic, data);
        log.debug("[Sync客户端] 已发布消息: topic={}", topic);
    }

    /**
     * 发送健康检查请求
     */
    public void healthCheck() {
        if (!connected.get() || protocolClient == null) {
            log.warn("[Sync客户端] 未连接服务端，无法进行健康检查");
            return;
        }
        protocolClient.publish(SyncServer.TOPIC_HEALTH, Map.of("action", "ping"));
    }

    /**
     * 启动定时同步调度器
     */
    private void startScheduler() {
        SyncProperties.ScheduleSync scheduleSync = syncProperties.getScheduleSync();
        if (!scheduleSync.isEnable()) {
            log.info("[Sync客户端] 定时同步未启用");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "sync-client-scheduler");
            thread.setDaemon(true);
            return thread;
        });

        scheduler.scheduleAtFixedRate(
                this::scheduledSync,
                scheduleSync.getInitialDelay(),
                scheduleSync.getInterval(),
                TimeUnit.SECONDS);

        log.info("[Sync客户端] 定时同步调度器启动成功，间隔: {}秒", scheduleSync.getInterval());
    }

    /**
     * 定时同步任务
     */
    private void scheduledSync() {
        // 子类可重写此方法实现定时同步逻辑
        log.debug("[Sync客户端] 执行定时同步任务");
    }

    /**
     * 处理连接/断开事件
     * <p>
     * 会查找配置中 topics 映射的 "sync/connect" 或 "sync/disconnect" 处理器执行
     * </p>
     *
     * @param topic 主题（sync/connect 或 sync/disconnect）
     * @param event 事件数据
     */
    private void handleConnectionEvent(String topic, Map<String, Object> event) {
        // 查找该主题对应的处理器
        List<SyncMessageHandler> handlers = handlerMap.get(topic);
        if (handlers == null || handlers.isEmpty()) {
            // 没有配置处理器，仅记录日志
            return;
        }

        // 循环执行所有处理器
        for (SyncMessageHandler handler : handlers) {
            try {
                handler.handle(topic, null, event);
                log.debug("[Sync客户端] 处理器 {} 处理 {} 事件完成", handler.getName(), topic);
            } catch (Exception e) {
                log.error("[Sync客户端] 处理器 {} 处理 {} 事件失败", handler.getName(), topic, e);
            }
        }
    }

    /**
     * 检查是否已连接
     */
    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public void destroy() throws Exception {
        if (scheduler != null) {
            scheduler.shutdown();
            log.info("[Sync客户端] 定时同步调度器已关闭");
        }

        if (protocolClient != null) {
            protocolClient.close();
            log.info("[Sync客户端] 已关闭");
        }

        connected.set(false);
    }
}
