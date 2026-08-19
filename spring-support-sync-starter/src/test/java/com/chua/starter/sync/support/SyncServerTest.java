package com.chua.starter.sync.support;

import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.sync.impl.WebSocketSyncServer;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SyncServer 独立单元测试。
 * <p>
 * 基于 common-starter WebSocketSyncServer 验证服务端核心行为。
 * </p>
 *
 * @since 2026/07/31
 * @author CH
 */
class SyncServerTest {

    @Test
    void createServer_返回可用的SyncServer实例() {
        WebSocketSyncServer server = new WebSocketSyncServer(ServerSetting.defaults());

        assertNotNull(server);
        assertFalse(server.isRunning());
    }

    @Test
    void startServer_随后stop_运行状态翻转正确() throws Exception {
        ServerSetting setting = ServerSetting.defaults();
        setting.setHost("127.0.0.1");
        setting.setPort(19889);
        WebSocketSyncServer server = new WebSocketSyncServer(setting);

        server.start();
        assertTrue(server.isRunning(), "服务端应处于运行中");

        server.stop();
        assertFalse(server.isRunning(), "服务端应已停止");
    }

    @Test
    void startStop_广播消息不抛异常() throws Exception {
        ServerSetting setting = ServerSetting.defaults();
        setting.setHost("127.0.0.1");
        setting.setPort(19890);
        WebSocketSyncServer server = new WebSocketSyncServer(setting);

        server.start();
        server.publish("test/topic", "hello world");
        assertEquals(0, server.getConnectedClients().size());

        server.stop();
    }

    @Test
    void startStop_sendMessage_客户端不存在不抛异常() throws Exception {
        ServerSetting setting = ServerSetting.defaults();
        setting.setHost("127.0.0.1");
        setting.setPort(19891);
        WebSocketSyncServer server = new WebSocketSyncServer(setting);

        server.start();
        server.send("non-existent-client-id", "test/topic", "hello");

        server.stop();
    }

    @Test
    void start_多次调用_幂等不抛异常() throws Exception {
        ServerSetting setting = ServerSetting.defaults();
        setting.setHost("127.0.0.1");
        setting.setPort(19894);
        WebSocketSyncServer server = new WebSocketSyncServer(setting);

        server.start();
        server.start();
        server.start();

        server.stop();
        assertFalse(server.isRunning());
    }

    @Test
    void broadcastToOthers_send_无客户端时不抛异常() throws Exception {
        ServerSetting setting = ServerSetting.defaults();
        setting.setHost("127.0.0.1");
        setting.setPort(19895);
        WebSocketSyncServer server = new WebSocketSyncServer(setting);

        server.start();
        server.publish("test/topic", "hello world");
        server.send("any-client-id", "test/topic", "hello");

        server.stop();
    }

    @Test
    void addListener_添加监听器_不抛异常() throws Exception {
        ServerSetting setting = ServerSetting.defaults();
        setting.setHost("127.0.0.1");
        setting.setPort(19896);
        WebSocketSyncServer server = new WebSocketSyncServer(setting);

        server.start();
        server.addListener(new com.chua.common.support.network.server.SyncServerListener() {
            @Override
            /** OnClientConnected */
            public void onClientConnected(String clientId, java.util.Map<String, Object> metadata) {
                System.out.println("connected: " + clientId);
            }

            @Override
            /** OnClientDisconnected */
            public void onClientDisconnected(String clientId) {
                System.out.println("disconnected: " + clientId);
            }

            @Override
            /** OnMessage */
            public void onMessage(String clientId, String topic, Object message) {
                System.out.println("message: " + topic + " -> " + message);
            }

            @Override
            /** On记录错误 */
            public void onError(String clientId, Throwable cause) {
                System.err.println("error: " + clientId);
            }
        });
        server.publish("test/topic", "hello");

        server.stop();
    }

    @Test
    void getConnectedClients_无客户端_返回空列表() throws Exception {
        ServerSetting setting = ServerSetting.defaults();
        setting.setHost("127.0.0.1");
        setting.setPort(19898);
        WebSocketSyncServer server = new WebSocketSyncServer(setting);

        server.start();
        assertTrue(server.getConnectedClients().isEmpty());

        server.stop();
    }
}
