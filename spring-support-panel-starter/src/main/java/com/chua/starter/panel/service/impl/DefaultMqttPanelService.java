package com.chua.starter.panel.service.impl;

import com.chua.starter.panel.service.MqttPanelService;
import com.chua.socket.support.session.SocketSessionTemplate;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 默认 MQTT 面板服务实现。
 * 使用 Eclipse Paho MqttAsyncClient 直连 MQTT Broker。
 *
 * @author CH
 */
public class DefaultMqttPanelService implements MqttPanelService {

    private static final Logger log = LoggerFactory.getLogger(DefaultMqttPanelService.class);
    private static final int MAX_CACHED_MESSAGES = 500;
    private static final int CONNECTION_TIMEOUT_SEC = 10;
    private static final String MQTT_MODULE = "MQTT";
    private static final String MQTT_EVENT_MESSAGE = "MESSAGE";

    private final ConcurrentHashMap<String, MqttConnection> connections = new ConcurrentHashMap<>();
    private final ObjectProvider<SocketSessionTemplate> socketSessionTemplateProvider;

    public DefaultMqttPanelService() {
        this.socketSessionTemplateProvider = null;
    }

    public DefaultMqttPanelService(ObjectProvider<SocketSessionTemplate> socketSessionTemplateProvider) {
        this.socketSessionTemplateProvider = socketSessionTemplateProvider;
    }

    // ── 连接生命周期 ──────────────────────────────────────────────────────

    @Override
    public String open(String host, int port, String username, String password, String clientId) {
        if (clientId == null || clientId.isBlank()) {
            clientId = "panel-mqtt-" + UUID.randomUUID().toString().substring(0, 8);
        }
        String serverUri = "tcp://" + host + ":" + port;
        String connectionId = UUID.randomUUID().toString();

        try {
            MqttAsyncClient client = new MqttAsyncClient(serverUri, clientId, new MemoryPersistence());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setConnectionTimeout(CONNECTION_TIMEOUT_SEC);
            options.setKeepAliveInterval(30);
            options.setAutomaticReconnect(false);
            if (username != null && !username.isBlank()) {
                options.setUserName(username);
            }
            if (password != null && !password.isBlank()) {
                options.setPassword(password.toCharArray());
            }

            // 消息缓存和订阅集合
            MqttMessageCache messageCache = new MqttMessageCache(MAX_CACHED_MESSAGES);
            Set<String> subscriptions = new CopyOnWriteArraySet<>();

            // 设置回调
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    log.warn("[MQTT:{}] 连接断开: {}", connectionId, cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    messageCache.add(topic, message.getPayload(), message.getQos());
                    log.debug("[MQTT:{}] 收到消息 topic={}, qos={}", connectionId, topic, message.getQos());
                    // 通过 Socket.IO 实时推送消息
                    broadcastMessage(connectionId, topic, message.getPayload(), message.getQos());
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // 发布完成通知，无需处理
                }
            });

            // 连接（同步等待）
            IMqttToken connectToken = client.connect(options);
            connectToken.waitForCompletion(CONNECTION_TIMEOUT_SEC * 1000L);

            if (!connectToken.isComplete() || !client.isConnected()) {
                client.close();
                throw new RuntimeException("MQTT 连接超时或失败");
            }

            MqttConnection conn = new MqttConnection(client, subscriptions, messageCache);
            connections.put(connectionId, conn);
            log.info("[MQTT:{}] 连接成功 server={}, clientId={}", connectionId, serverUri, clientId);
            return connectionId;

        } catch (Exception e) {
            log.error("[MQTT] 连接失败 server={}, clientId={}: {}", serverUri, clientId, e.getMessage());
            throw new RuntimeException("MQTT 连接失败: " + e.getMessage());
        }
    }

    @Override
    public void close(String connectionId) {
        MqttConnection conn = connections.remove(connectionId);
        if (conn == null) return;
        try {
            MqttAsyncClient client = conn.client;
            if (client.isConnected()) {
                client.disconnect(3000);
            }
            client.close();
            log.info("[MQTT:{}] 连接已关闭", connectionId);
        } catch (Exception e) {
            log.warn("[MQTT:{}] 关闭连接异常: {}", connectionId, e.getMessage());
        }
    }

    // ── Broker 信息 ────────────────────────────────────────────────────────

    @Override
    public Map<String, Object> brokerInfo(String connectionId) {
        MqttConnection conn = getConnection(connectionId);
        MqttAsyncClient client = conn.client;
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("connected", client.isConnected());
        info.put("serverURI", client.getServerURI());
        info.put("clientId", client.getClientId());
        info.put("subscriptionCount", conn.subscriptions.size());
        info.put("cachedMessageCount", conn.messageCache.size());
        return info;
    }

    // ── 发布消息 ───────────────────────────────────────────────────────────

    @Override
    public boolean publish(String connectionId, String topic, String payload, int qos) {
        MqttConnection conn = getConnection(connectionId);
        try {
            MqttMessage message = new MqttMessage();
            message.setPayload(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(Math.min(Math.max(qos, 0), 2));
            message.setRetained(false);

            IMqttDeliveryToken token = conn.client.publish(topic, message);
            token.waitForCompletion(5000);
            return true;
        } catch (Exception e) {
            log.error("[MQTT:{}] 发布消息失败 topic={}: {}", connectionId, topic, e.getMessage());
            throw new RuntimeException("发布消息失败: " + e.getMessage());
        }
    }

    // ── 订阅管理 ───────────────────────────────────────────────────────────

    @Override
    public boolean subscribe(String connectionId, String topic, int qos) {
        MqttConnection conn = getConnection(connectionId);
        try {
            int actualQos = Math.min(Math.max(qos, 0), 2);
            conn.client.subscribe(topic, actualQos);
            conn.subscriptions.add(topic);
            log.info("[MQTT:{}] 订阅成功 topic={}, qos={}", connectionId, topic, actualQos);
            return true;
        } catch (Exception e) {
            log.error("[MQTT:{}] 订阅失败 topic={}: {}", connectionId, topic, e.getMessage());
            throw new RuntimeException("订阅失败: " + e.getMessage());
        }
    }

    @Override
    public boolean unsubscribe(String connectionId, String topic) {
        MqttConnection conn = getConnection(connectionId);
        try {
            conn.client.unsubscribe(topic);
            conn.subscriptions.remove(topic);
            log.info("[MQTT:{}] 取消订阅 topic={}", connectionId, topic);
            return true;
        } catch (Exception e) {
            log.error("[MQTT:{}] 取消订阅失败 topic={}: {}", connectionId, topic, e.getMessage());
            throw new RuntimeException("取消订阅失败: " + e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> listSubscriptions(String connectionId) {
        MqttConnection conn = getConnection(connectionId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (String topic : conn.subscriptions) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("topic", topic);
            item.put("qos", 1); // 服务端不存储订阅的 QoS，默认显示 1
            result.add(item);
        }
        return result;
    }

    // ── 消息历史 ───────────────────────────────────────────────────────────

    @Override
    public List<Map<String, Object>> messages(String connectionId, String topic, int limit) {
        MqttConnection conn = getConnection(connectionId);
        return conn.messageCache.query(topic, limit > 0 ? limit : 50);
    }

    // ── 内部工具方法 ───────────────────────────────────────────────────────

    private MqttConnection getConnection(String connectionId) {
        MqttConnection conn = connections.get(connectionId);
        if (conn == null) {
            throw new IllegalArgumentException("连接不存在或已关闭: " + connectionId);
        }
        return conn;
    }

    /**
     * 通过 Socket.IO 广播 MQTT 消息。
     */
    private void broadcastMessage(String connectionId, String topic, byte[] payload, int qos) {
        if (socketSessionTemplateProvider == null) return;
        SocketSessionTemplate socketSessionTemplate = socketSessionTemplateProvider.getIfAvailable();
        if (socketSessionTemplate == null) return;
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("module", MQTT_MODULE);
            envelope.put("event", MQTT_EVENT_MESSAGE);
            envelope.put("data", Map.of(
                    "connectionId", connectionId,
                    "topic", topic,
                    "payload", new String(payload, StandardCharsets.UTF_8),
                    "qos", qos,
                    "timestamp", Instant.now().toString()
            ));
            envelope.put("timestamp", System.currentTimeMillis());
            socketSessionTemplate.broadcastObject("message", envelope);
        } catch (Exception e) {
            log.warn("[MQTT:{}] 广播消息失败: {}", connectionId, e.getMessage());
        }
    }

    // ── 内部数据结构 ──────────────────────────────────────────────────────

    private record MqttConnection(MqttAsyncClient client, Set<String> subscriptions, MqttMessageCache messageCache) {}

    /**
     * 线程安全的 MQTT 消息环形缓存。
     */
    private static class MqttMessageCache {
        private final int maxSize;
        private final LinkedList<Map<String, Object>> messages = new LinkedList<>();
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

        MqttMessageCache(int maxSize) {
            this.maxSize = maxSize;
        }

        void add(String topic, byte[] payload, int qos) {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("topic", topic);
            msg.put("payload", new String(payload, StandardCharsets.UTF_8));
            msg.put("qos", qos);
            msg.put("timestamp", Instant.now().toString());

            lock.writeLock().lock();
            try {
                messages.addFirst(msg);
                if (messages.size() > maxSize) {
                    messages.removeLast();
                }
            } finally {
                lock.writeLock().unlock();
            }
        }

        List<Map<String, Object>> query(String topic, int limit) {
            lock.readLock().lock();
            try {
                List<Map<String, Object>> result = new ArrayList<>();
                int count = 0;
                for (Map<String, Object> msg : messages) {
                    if (topic != null && !topic.isBlank() && !matchesTopic(topic, (String) msg.get("topic"))) {
                        continue;
                    }
                    result.add(msg);
                    count++;
                    if (count >= limit) break;
                }
                return result;
            } finally {
                lock.readLock().unlock();
            }
        }

        int size() {
            lock.readLock().lock();
            try {
                return messages.size();
            } finally {
                lock.readLock().unlock();
            }
        }
    }

    /**
     * 简单的 MQTT 主题匹配（支持 + 和 # 通配符）。
     */
    private static boolean matchesTopic(String filter, String topic) {
        if (filter == null || topic == null) return false;
        if (filter.equals(topic)) return true;
        // 多级通配符 #
        if (filter.endsWith("/#")) {
            String prefix = filter.substring(0, filter.length() - 2);
            return topic.equals(prefix) || topic.startsWith(prefix + "/");
        }
        if (filter.equals("#")) return true;
        // 单级通配符 +
        String[] filterParts = filter.split("/");
        String[] topicParts = topic.split("/");
        if (filterParts.length != topicParts.length) return false;
        for (int i = 0; i < filterParts.length; i++) {
            if ("+".equals(filterParts[i])) continue;
            if (!filterParts[i].equals(topicParts[i])) return false;
        }
        return true;
    }
}
