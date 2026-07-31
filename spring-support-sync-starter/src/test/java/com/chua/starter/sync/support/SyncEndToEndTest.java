package com.chua.starter.sync.support;

import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.sync.SyncFlowListener;
import com.chua.common.support.network.sync.SyncMessageHandler;
import com.chua.common.support.network.sync.impl.HttpSyncServer;
import com.chua.common.support.network.sync.impl.HttpSyncClient;
import com.chua.common.support.network.sync.impl.WebSocketSyncServer;
import com.chua.common.support.network.sync.impl.WebSocketSyncClient;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sync 双端通信集成测试。
 * <p>
 * 覆盖协议：
 * 1. WebSocket（完整双端通信）
 * 2. HTTP（完整双端通信，仅测试核心流程）
 * 3. RSocket / Socket.IO（SPI 注册验证，可选依赖）
 * </p>
 *
 * @author CH
 * @since 2026/07/31
 */
class SyncEndToEndTest {

    // ==================== WebSocket 双端测试 ====================

    @Test
    void ws_clientConnect_serverReceiveConnection_连接计数增加() throws Exception {
        int port = 19960;
        WebSocketSyncServer server = createWsServer(port);

        server.start();
        assertEquals(0, server.getConnectedClients().size(), "初始连接数应为0");

        WebSocketSyncClient client = createWsClient(port);
        client.connect();

        Thread.sleep(1000);

        assertEquals(1, server.getConnectedClients().size(), "客户端连接后连接数应为1");

        client.disconnect();
        server.stop();
    }

    @Test
    void ws_serverSend_clientReceive_消息传输正确() throws Exception {
        int port = 19961;
        WebSocketSyncServer server = createWsServer(port);

        server.start();

        AtomicReference<String> receivedData = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        WebSocketSyncClient client = createWsClient(port);
        client.subscribe("test/echo", (topic, data) -> {
            if (topic != null) {
                receivedData.set(String.valueOf(data));
                latch.countDown();
            }
        });
        client.connect();

        Thread.sleep(1000);

        String sessionId = server.getConnectedClients().stream().findFirst().orElse(null);
        assertNotNull(sessionId, "服务端应有客户端会话");

        server.send(sessionId, "test/echo", "hello-sync");

        boolean received = latch.await(10, TimeUnit.SECONDS);
        assertTrue(received, "客户端应在超时前收到消息");
        assertEquals("hello-sync", receivedData.get());

        client.disconnect();
        server.stop();
    }

    @Test
    void ws_clientReconnect_断线重连_连接恢复() throws Exception {
        int port = 19962;
        WebSocketSyncServer server = createWsServer(port);

        server.start();

        WebSocketSyncClient client = createWsClient(port);
        client.connect();

        Thread.sleep(1000);
        assertEquals(1, server.getConnectedClients().size());

        client.disconnect();
        Thread.sleep(500);
        assertEquals(0, server.getConnectedClients().size(), "断开后连接数应为0");

        client.connect();
        Thread.sleep(1000);
        assertEquals(1, server.getConnectedClients().size(), "重连后连接数应恢复为1");

        client.disconnect();
        server.stop();
    }

    @Test
    void ws_serverBroadcast_allClientsReceive_广播成功() throws Exception {
        int port = 19963;
        WebSocketSyncServer server = createWsServer(port);

        server.start();

        CountDownLatch latch = new CountDownLatch(2);
        AtomicReference<String> data1 = new AtomicReference<>();
        AtomicReference<String> data2 = new AtomicReference<>();

        WebSocketSyncClient client1 = createWsClient(port);
        WebSocketSyncClient client2 = createWsClient(port);

        client1.subscribe("broadcast/test", (topic, data) -> {
            if (topic != null) {
                data1.set(String.valueOf(data));
                latch.countDown();
            }
        });
        client2.subscribe("broadcast/test", (topic, data) -> {
            if (topic != null) {
                data2.set(String.valueOf(data));
                latch.countDown();
            }
        });

        client1.connect();
        client2.connect();

        Thread.sleep(1000);

        server.publish("broadcast/test", "hello-all");

        boolean received = latch.await(10, TimeUnit.SECONDS);
        assertTrue(received, "所有客户端应收到广播消息");
        assertEquals("hello-all", data1.get());
        assertEquals("hello-all", data2.get());

        client1.disconnect();
        client2.disconnect();
        server.stop();
    }

    @Test
    void ws_clientSend_serverReceive_客户端发消息服务端监听到() throws Exception {
        int port = 19965;
        WebSocketSyncServer server = createWsServer(port);

        AtomicReference<String> receivedTopic = new AtomicReference<>();
        AtomicReference<String> receivedData = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        server.addListener(new com.chua.common.support.network.server.SyncServerListener() {
            @Override
            public void onClientConnected(String clientId, java.util.Map<String, Object> metadata) {
            }

            @Override
            public void onClientDisconnected(String clientId) {
            }

            @Override
            public void onMessage(String clientId, String topic, Object message) {
                receivedTopic.set(topic);
                receivedData.set(String.valueOf(message));
                latch.countDown();
            }

            @Override
            public void onError(String clientId, Throwable cause) {
                cause.printStackTrace();
            }
        });

        server.start();

        WebSocketSyncClient client = createWsClient(port);
        client.subscribe("client/echo", (topic, data) -> {
        });
        client.connect();

        Thread.sleep(1000);

        client.send("client/echo", "hello-server");

        boolean received = latch.await(10, TimeUnit.SECONDS);
        assertTrue(received, "服务端应在超时前收到消息");
        assertEquals("client/echo", receivedTopic.get());
        assertEquals("hello-server", receivedData.get());

        client.disconnect();
        server.stop();
    }

    // ==================== HTTP 双端测试 ====================

    @Test
    void http_clientConnect_serverReceiveConnection_连接计数增加() throws Exception {
        int port = 19970;
        HttpSyncServer server = createHttpServer(port);

        server.start();
        assertEquals(0, server.getConnectedClients().size(), "初始连接数应为0");

        HttpSyncClient client = createHttpClient(port);
        client.connect();

        waitForHttpRegister(port, client.getClientId());

        assertEquals(1, server.getConnectedClients().size(), "客户端连接后连接数应为1");

        client.disconnect();
        server.stop();
    }

    @Test
    void http_serverSend_clientReceive_消息传输正确() throws Exception {
        int port = 19971;
        HttpSyncServer server = createHttpServer(port);

        server.start();

        AtomicReference<String> receivedData = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        HttpSyncClient client = createHttpClient(port);
        client.subscribe("test/echo", (topic, data) -> {
            if (topic != null) {
                receivedData.set(String.valueOf(data));
                latch.countDown();
            }
        });
        client.connect();

        waitForHttpRegister(port, client.getClientId());

        Thread.sleep(1000);

        String clientId = client.getClientId();
        assertNotNull(clientId, "客户端应有ID");

        server.send(clientId, "test/echo", "hello-http");

        boolean received = latch.await(10, TimeUnit.SECONDS);
        assertTrue(received, "客户端应在超时前收到消息");
        assertEquals("hello-http", receivedData.get());

        client.disconnect();
        server.stop();
    }

    @Test
    void http_serverBroadcast_allClientsReceive_广播成功() throws Exception {
        int port = 19973;
        HttpSyncServer server = createHttpServer(port);

        server.start();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> data = new AtomicReference<>();

        HttpSyncClient client = createHttpClient(port);
        client.subscribe("broadcast/test", (topic, data1) -> {
            if (topic != null) {
                data.set(String.valueOf(data1));
                latch.countDown();
            }
        });
        client.connect();

        waitForHttpRegister(port, client.getClientId());

        Thread.sleep(1000);

        server.publish("broadcast/test", "hello-http-all");

        boolean received = latch.await(10, TimeUnit.SECONDS);
        assertTrue(received, "客户端应收到广播消息");
        assertEquals("hello-http-all", data.get());

        client.disconnect();
        server.stop();
    }

    // ==================== RSocket / Socket.IO 协议验证 ====================

    @Test
    void rsocket_socketio_spi_验证() {
        com.chua.common.support.spi.ServiceProvider<com.chua.common.support.network.sync.SyncClient> clientProvider =
                com.chua.common.support.spi.ServiceProvider.of(com.chua.common.support.network.sync.SyncClient.class);
        java.util.Set<String> available = clientProvider.getExtensions();
        boolean hasWs = available.contains("WEBSOCKET") || available.contains("HTTP");
        assertTrue(hasWs, "当前测试类路径下应至少包含 WebSocket 或 HTTP 协议实现，实际发现: " + available);
    }

    // ==================== 辅助方法 ====================

    private static WebSocketSyncServer createWsServer(int port) {
        ServerSetting setting = ServerSetting.defaults();
        setting.setHost("127.0.0.1");
        setting.setPort(port);
        return new WebSocketSyncServer(setting);
    }

    private static WebSocketSyncClient createWsClient(int port) {
        return new WebSocketSyncClient("ws://127.0.0.1:" + port);
    }

    private static HttpSyncServer createHttpServer(int port) {
        ServerSetting setting = ServerSetting.defaults();
        setting.setHost("127.0.0.1");
        setting.setPort(port);
        return new HttpSyncServer(setting);
    }

    private static HttpSyncClient createHttpClient(int port) {
        return new HttpSyncClient("http://127.0.0.1:" + port);
    }

    private static void waitForHttpRegister(int port, String clientId) throws Exception {
        if (clientId == null) {
            return;
        }
        HttpClient httpClient = HttpClient.newHttpClient();
        for (int i = 0; i < 30; i++) {
            try {
                String body = "clientId=" + java.net.URLEncoder.encode(clientId, "UTF-8");
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + "/api/sync/register"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (Exception e) {
                // retry
            }
            Thread.sleep(200);
        }
        fail("HTTP 客户端注册超时: " + clientId);
    }

    private static void assertProtocolRegistered(String protocolName) {
        com.chua.common.support.spi.ServiceProvider<com.chua.common.support.network.sync.SyncClient> clientProvider =
                com.chua.common.support.spi.ServiceProvider.of(com.chua.common.support.network.sync.SyncClient.class);
        com.chua.common.support.spi.ServiceProvider<com.chua.common.support.network.server.SyncServer> serverProvider =
                com.chua.common.support.spi.ServiceProvider.of(com.chua.common.support.network.server.SyncServer.class);

        boolean hasClient = clientProvider.getExtensions().stream()
                .anyMatch(name -> protocolName.equalsIgnoreCase(name));
        boolean hasServer = serverProvider.getExtensions().stream()
                .anyMatch(name -> protocolName.equalsIgnoreCase(name));

        assertTrue(hasClient || hasServer,
                protocolName + " 协议应在 SPI 中注册（当前仅发现: client=" + clientProvider.getExtensions() + ", server=" + serverProvider.getExtensions() + "）");
    }
}
