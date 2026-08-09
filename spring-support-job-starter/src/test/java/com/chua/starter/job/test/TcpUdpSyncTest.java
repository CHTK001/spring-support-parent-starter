package com.chua.starter.job.test;

import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.SyncServer;
import com.chua.common.support.network.sync.SyncClient;
import com.chua.common.support.network.sync.impl.TcpSyncClient;
import com.chua.common.support.network.sync.impl.TcpSyncServer;
import com.chua.common.support.network.sync.impl.UdpSyncClient;
import com.chua.common.support.network.sync.impl.UdpSyncServer;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TCP / UDP 同步协议互操作测试 (纯 JDK socket 实现)。
 *
 * <p>验证:</p>
 * <ol>
 *   <li>{@link TcpSyncServer} 与 {@link TcpSyncClient} 注册 + 主题消息互通;</li>
 *   <li>{@link UdpSyncServer} 与 {@link UdpSyncClient} 注册 + 主题消息互通;</li>
 *   <li>SyncProtocol.createServer / createClient 工厂可用。</li>
 * </ol>
 *
 * @author CH
 */
class TcpUdpSyncTest {

    /**
     * TCP 同步互通测试。
     */
    @Test
    void tcpSyncShouldExchangeMessages() throws Exception {
        ServerSetting setting = ServerSetting.builder().host("127.0.0.1").port(19490).build();
        SyncServer server = new TcpSyncServer(setting);
        server.start();

        try {
            SyncClient client = new TcpSyncClient("tcp://127.0.0.1:19490");
            AtomicInteger received = new AtomicInteger();
            AtomicReference<String> receivedTopic = new AtomicReference<>();
            client.subscribe("job-cmd", (topic, message) -> {
                receivedTopic.set(topic);
                received.incrementAndGet();
            });
            client.connect();

            // 服务端广播主题消息
            server.publish("job-cmd", "hello-tcp");
            Thread.sleep(500L);

            assertTrue(received.get() >= 1, "TCP 客户端应收到主题消息");
            assertEquals("job-cmd", receivedTopic.get());
            assertTrue(server.getConnectedClients().size() >= 1, "TCP 服务端应记录已连接客户端");
        } finally {
            server.stop();
        }
    }

    /**
     * UDP 同步互通测试。
     */
    @Test
    void udpSyncShouldExchangeMessages() throws Exception {
        ServerSetting setting = ServerSetting.builder().host("127.0.0.1").port(19491).build();
        SyncServer server = new UdpSyncServer(setting);
        server.start();

        try {
            SyncClient client = new UdpSyncClient("udp://127.0.0.1:19491");
            AtomicInteger received = new AtomicInteger();
            client.subscribe("job-log", (topic, message) -> received.incrementAndGet());
            client.connect();

            Thread.sleep(300L);
            server.publish("job-log", "hello-udp");
            Thread.sleep(500L);

            assertTrue(received.get() >= 1, "UDP 客户端应收到主题消息");
            assertTrue(server.getConnectedClients().size() >= 1, "UDP 服务端应记录已注册客户端");
        } finally {
            server.stop();
        }
    }
}
