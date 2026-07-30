package com.chua.starter.sync.support;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 监听器管理器，管理连接监听器和主题监听器。
 *
 * @author CH
 * @since 4.0.0.41
 */
@Slf4j
public class ListenerManager {

    private final java.util.List<SyncConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();

    private final java.util.List<TopicListener> topicListeners = new CopyOnWriteArrayList<>();

    public void addConnectionListener(SyncConnectionListener listener) {
        if (listener != null) {
            connectionListeners.add(listener);
        }
    }

    public void addTopicListener(TopicListener listener) {
        if (listener != null) {
            topicListeners.add(listener);
        }
    }

    public void fireConnectionEvent(ConnectionEvent event) {
        for (SyncConnectionListener listener : connectionListeners) {
            try {
                if (event.getType() == ConnectionEvent.Type.CONNECTED) {
                    listener.onConnect(event.getSession());
                } else {
                    listener.onDisconnect(event.getSession());
                }
            } catch (Exception e) {
                log.error("[SyncClient] 连接监听器执行失败", e);
            }
        }
    }

    public void fireDataEvent(String topic, Object data) {
        for (TopicListener listener : topicListeners) {
            try {
                listener.onData(topic, data);
            } catch (Exception e) {
                log.error("[SyncClient] 主题监听器执行失败", e);
            }
        }
    }

    @FunctionalInterface
    public interface TopicListener {
        void onData(String topic, Object data);
    }

    public static class ConnectionEvent {
        private final Type type;
        private final SyncSession session;

        public ConnectionEvent(Type type, SyncSession session) {
            this.type = type;
            this.session = session;
        }

        public Type getType() {
            return type;
        }

        public SyncSession getSession() {
            return session;
        }

        public enum Type {
            CONNECTED, DISCONNECTED, RECONNECTED
        }
    }
}