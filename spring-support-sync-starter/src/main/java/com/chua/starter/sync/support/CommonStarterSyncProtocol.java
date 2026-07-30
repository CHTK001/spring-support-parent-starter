package com.chua.starter.sync.support;

import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.SyncServerListener;
import com.chua.common.support.network.sync.SyncFlowListener;
import com.chua.common.support.network.sync.SyncMessageHandler;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.sync.support.SyncServer;
import com.chua.starter.sync.support.SyncClient;
import com.chua.starter.sync.support.SyncProtocol;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 基于 common-starter 的同步协议实现。
 * <p>
 * 通过 {@link SyncProtocol} SPI 注册，支持 http 和 websocket 协议。
 * 内部委托给 common-starter 的 {@link com.chua.common.support.network.sync.SyncClient}
 * 和 {@link com.chua.common.support.network.server.SyncServer} 实现。
 * </p>
 *
 * @author CH
 * @since 2026-07-25
 */
@Slf4j
@Spi({"http", "http-sync", "websocket", "ws"})
public class CommonStarterSyncProtocol implements SyncProtocol {

    /**
     * 协议类型
     */
    private final String protocol;

    /**
     * 构造同步协议实例。
     *
     * @param protocol 协议类型
     */
    public CommonStarterSyncProtocol(String protocol) {
        this.protocol = protocol;
    }

    @Override
    public String getProtocol() {
        return protocol;
    }

    @Override
    public SyncServer createServer(Object setting) {
        ServerSetting serverSetting = toServerSetting(setting);
        com.chua.common.support.network.server.SyncServer commonServer = createCommonServer(serverSetting);
        return new ServerAdapter(commonServer);
    }

    @Override
    public SyncClient createClient(Object setting) {
        String serverUrl = toServerUrl(setting);
        com.chua.common.support.network.sync.SyncClient commonClient = createCommonClient(serverUrl);
        return new ClientAdapter(commonClient);
    }

    // ==================== 底层实例创建 ====================

    private com.chua.common.support.network.server.SyncServer createCommonServer(ServerSetting setting) {
        if ("websocket".equalsIgnoreCase(protocol) || "ws".equalsIgnoreCase(protocol)) {
            return new com.chua.common.support.network.sync.impl.WebSocketSyncServer(setting);
        }
        return new com.chua.common.support.network.sync.impl.HttpSyncServer(setting);
    }

    private com.chua.common.support.network.sync.SyncClient createCommonClient(String serverUrl) {
        if ("websocket".equalsIgnoreCase(protocol) || "ws".equalsIgnoreCase(protocol)) {
            return new com.chua.common.support.network.sync.impl.WebSocketSyncClient(serverUrl);
        }
        return new com.chua.common.support.network.sync.impl.HttpSyncClient(serverUrl);
    }

    // ==================== 参数转换 ====================

    private ServerSetting toServerSetting(Object setting) {
        if (setting instanceof ServerSetting ss) {
            return ss;
        }
        ServerSetting serverSetting = ServerSetting.defaults();
        if (setting instanceof String s) {
            String[] parts = s.split(":");
            if (parts.length >= 2) {
                serverSetting.setHost(parts[0]);
                try {
                    serverSetting.setPort(Integer.parseInt(parts[1]));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return serverSetting;
    }

    private String toServerUrl(Object setting) {
        if (setting instanceof String s) {
            return s;
        }
        if (setting instanceof ServerSetting ss) {
            return ss.getProtocol() + "://" + ss.getHost() + ":" + ss.getPort();
        }
        return "http://localhost:8080";
    }

    // ==================== 服务端适配器 ====================

    static class ServerAdapter extends AbstractSyncServer {

        private final com.chua.common.support.network.server.SyncServer delegate;

        ServerAdapter(com.chua.common.support.network.server.SyncServer delegate) {
            this.delegate = delegate;
            if (delegate != null) {
                this.protocol = delegate.getProtocol();
                this.host = delegate.getHost();
                this.port = delegate.getPort();
                delegate.addListener(new InternalSyncServerListener());
            }
        }

        @Override
        protected void doStart() {
            if (delegate == null) {
                return;
            }
            delegate.start();
        }

        @Override
        protected void doStop() {
            if (delegate == null) {
                return;
            }
            delegate.stop();
        }

        @Override
        protected void doSend(String sessionId, String topic, Object data) {
            if (delegate == null) {
                return;
            }
            delegate.send(sessionId, topic, data);
        }

        @Override
        public void broadcast(String topic, Object data) {
            if (delegate == null) {
                return;
            }
            delegate.publish(topic, data);
        }

        @Override
        public void broadcastToOthers(String excludeSessionId, String topic, Object data) {
            if (delegate == null) {
                return;
            }
            for (String clientId : delegate.getConnectedClients()) {
                if (!clientId.equals(excludeSessionId)) {
                    delegate.send(clientId, topic, data);
                }
            }
        }

        @Override
        public int getConnectionCount() {
            if (delegate == null) {
                return 0;
            }
            return delegate.getConnectedClients().size();
        }

        /**
         * 内部监听器，将 common-starter 事件桥接到 AbstractSyncServer。
         */
        private class InternalSyncServerListener implements SyncServerListener {

            @Override
            public void onClientConnected(String clientId, Map<String, Object> metadata) {
                SyncSession session = new SyncSession(clientId);
                sessions.put(clientId, session);
                fireConnectionEstablished(session);
            }

            @Override
            public void onClientDisconnected(String clientId) {
                SyncSession session = sessions.remove(clientId);
                if (session != null) {
                    fireConnectionClosed(session);
                }
            }

            @Override
            public void onMessage(String clientId, String topic, Object message) {
                SyncSession session = sessions.get(clientId);
                if (session == null) {
                    session = new SyncSession(clientId);
                    sessions.put(clientId, session);
                }
                fireMessageReceived(session, topic, message);
            }

            @Override
            public void onError(String clientId, Throwable cause) {
                log.error("[Sync服务端适配器] 客户端错误: clientId={}", clientId, cause);
            }
        }
    }

    // ==================== 客户端适配器 ====================

    static class ClientAdapter implements SyncClient {

        private final com.chua.common.support.network.sync.SyncClient delegate;
        private final ListenerManager listenerManager = new ListenerManager();
        private volatile boolean connected;

        ClientAdapter(com.chua.common.support.network.sync.SyncClient delegate) {
            this.delegate = delegate;
            this.delegate.addListener(new SyncFlowListener() {
                @Override
                public void onStart() {
                    connected = true;
                    listenerManager.fireConnectionEvent(
                            new ListenerManager.ConnectionEvent(ListenerManager.ConnectionEvent.Type.CONNECTED,
                                    new SyncSession(delegate.getClientId())));
                }

                @Override
                public void onStop() {
                    connected = false;
                    listenerManager.fireConnectionEvent(
                            new ListenerManager.ConnectionEvent(ListenerManager.ConnectionEvent.Type.DISCONNECTED,
                                    new SyncSession(delegate.getClientId())));
                }

                @Override
                public void onMessage(String topic, Object message) {
                    listenerManager.fireDataEvent(topic, message);
                }

                @Override
                public void onError(String topic, Throwable cause) {
                    log.error("[Sync客户端适配器] 错误: topic={}", topic, cause);
                }
            });
        }

        @Override
        public void connect() {
            delegate.connect();
        }

        @Override
        public void disconnect() {
            delegate.disconnect();
            connected = false;
        }

        @Override
        public boolean isConnected() {
            return connected || delegate.isConnected();
        }

        @Override
        public String getClientId() {
            return delegate.getClientId();
        }

        @Override
        public void send(String topic, Object message) {
            delegate.send(topic, message);
        }

        @Override
        public void subscribe(String[] topics) {
            if (topics == null || delegate == null) {
                return;
            }
            for (String topic : topics) {
                if (topic == null || topic.isBlank()) {
                    continue;
                }
                com.chua.common.support.network.sync.SyncMessageHandler handler =
                        (t, msg) -> listenerManager.fireDataEvent(t, msg);
                delegate.subscribe(topic, handler);
            }
        }

        @Override
        public ListenerManager getListenerManager() {
            return listenerManager;
        }

        @Override
        public void close() {
            delegate.close();
            connected = false;
        }
    }
}
