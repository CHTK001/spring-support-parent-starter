package com.chua.starter.job.test;

import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.SyncServer;
import com.chua.common.support.network.sync.SyncClient;
import com.chua.common.support.network.sync.impl.TcpSyncClient;
import com.chua.common.support.network.sync.impl.TcpSyncServer;
import com.chua.common.support.network.sync.impl.UdpSyncClient;
import com.chua.common.support.network.sync.impl.UdpSyncServer;
import com.chua.common.support.network.sync.netty.NettyTcpSyncClient;
import com.chua.common.support.network.sync.netty.NettyTcpSyncServer;
import com.chua.common.support.network.sync.netty.NettyUdpSyncClient;
import com.chua.common.support.network.sync.netty.NettyUdpSyncServer;
import com.chua.common.support.spi.ServiceProvider;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TCP / UDP 同步协议互操作测试 (纯 JDK socket + Netty NIO 双实现)。
 *
 * <p>验证:</p>
 * <ol>
 *   <li>{@link TcpSyncServer} 与 {@link TcpSyncClient} 注册 + 主题消息互通;</li>
 *   <li>{@link UdpSyncServer} 与 {@link UdpSyncClient} 注册 + 主题消息互通;</li>
 *   <li>{@link NettyTcpSyncServer} 与 {@link NettyTcpSyncClient} 互通;</li>
 *   <li>{@link NettyUdpSyncServer} 与 {@link NettyUdpSyncClient} 互通;</li>
 *   <li>SPI 按别名加载 tcp/udp/netty-tcp/netty-udp 实现。</li>
 * </ol>
 *
 * @author CH
 */
class TcpUdpSyncTest {

    /**
     * 验证 SPI 能按别名加载全部 TCP/UDP 实现。
     */
    @Test
    void spiShouldLoadAllTcpUdpImplementations() {
        SyncServer tcpServer = ServiceProvider.of(SyncServer.class).getExtension("tcp");
        SyncServer udpServer = ServiceProvider.of(SyncServer.class).getExtension("udp");
        SyncServer nettyTcpServer = ServiceProvider.of(SyncServer.class).getExtension("netty-tcp");
        SyncServer nettyUdpServer = ServiceProvider.of(SyncServer.class).getExtension("netty-udp");

        assertNotNull(tcpServer, "SPI tcp SyncServer 应可加载");
        assertNotNull(udpServer, "SPI udp SyncServer 应可加载");
        assertNotNull(nettyTcpServer, "SPI netty-tcp SyncServer 应可加载");
        assertNotNull(nettyUdpServer, "SPI netty-udp SyncServer 应可加载");
        assertTrue(tcpServer instanceof TcpSyncServer, "tcp 别名应映射到 TcpSyncServer");
        assertTrue(udpServer instanceof UdpSyncServer, "udp 别名应映射到 UdpSyncServer");
        assertTrue(nettyTcpServer instanceof NettyTcpSyncServer, "netty-tcp 别名应映射到 NettyTcpSyncServer");
        assertTrue(nettyUdpServer instanceof NettyUdpSyncServer, "netty-udp 别名应映射到 NettyUdpSyncServer");
    }

    /**
     * TCP 同步互通测试 (纯 JDK socket)。
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
     * UDP 同步互通测试 (纯 JDK DatagramSocket)。
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

    /**
     * Netty TCP 同步互通测试。
     */
    @Test
    void nettyTcpSyncShouldExchangeMessages() throws Exception {
        ServerSetting setting = ServerSetting.builder().host("127.0.0.1").port(19492).build();
        SyncServer server = new NettyTcpSyncServer(setting);
        server.start();

        try {
            SyncClient client = new NettyTcpSyncClient("tcp://127.0.0.1:19492");
            AtomicInteger received = new AtomicInteger();
            AtomicReference<String> receivedTopic = new AtomicReference<>();
            client.subscribe("job-cmd", (topic, message) -> {
                receivedTopic.set(topic);
                received.incrementAndGet();
            });
            client.connect();

            server.publish("job-cmd", "hello-netty-tcp");
            Thread.sleep(500L);

            assertTrue(received.get() >= 1, "Netty TCP 客户端应收到主题消息");
            assertEquals("job-cmd", receivedTopic.get());
            assertTrue(server.getConnectedClients().size() >= 1, "Netty TCP 服务端应记录已连接客户端");
        } finally {
            server.stop();
        }
    }

    /**
     * Netty UDP 同步互通测试。
     */
    @Test
    void nettyUdpSyncShouldExchangeMessages() throws Exception {
        ServerSetting setting = ServerSetting.builder().host("127.0.0.1").port(19493).build();
        SyncServer server = new NettyUdpSyncServer(setting);
        server.start();

        try {
            SyncClient client = new NettyUdpSyncClient("udp://127.0.0.1:19493");
            AtomicInteger received = new AtomicInteger();
            client.subscribe("job-log", (topic, message) -> received.incrementAndGet());
            client.connect();

            Thread.sleep(300L);
            server.publish("job-log", "hello-netty-udp");
            Thread.sleep(500L);

            assertTrue(received.get() >= 1, "Netty UDP 客户端应收到主题消息");
            assertTrue(server.getConnectedClients().size() >= 1, "Netty UDP 服务端应记录已注册客户端");
        } finally {
            server.stop();
        }
    }
}



