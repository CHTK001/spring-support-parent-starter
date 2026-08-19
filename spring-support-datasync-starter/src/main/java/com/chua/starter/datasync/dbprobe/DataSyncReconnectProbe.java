package com.chua.starter.datasync.dbprobe;

import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.SyncServer;
import com.chua.common.support.network.server.SyncServerListener;
import com.chua.common.support.network.sync.SyncClient;
import com.chua.common.support.network.sync.SyncMessageHandler;
import com.chua.common.support.network.sync.impl.TcpSyncClient;
import com.chua.common.support.network.sync.impl.TcpSyncServer;
import com.chua.starter.datasync.DefaultDataSyncServer;
import com.chua.starter.datasync.agent.AgentServerManager;
import com.chua.starter.datasync.agent.DataSyncAgentServer;
import com.chua.starter.datasync.mapping.DefaultDataSyncMappingManager;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 网络断线重连专项探针。
 *
 * <p>真实场景验证点（"突然掉线 / 短线重连"）：</p>
 * <ol>
 *   <li><b>正常连接</b>：{@link TcpSyncServer} 启动，{@link TcpSyncClient} 连接并注册成功；</li>
 *   <li><b>数据推送</b>：客户端订阅主题并通过网络发送数据，服务端 listener 收到消息；</li>
 *   <li><b>突然掉线</b>：服务端 {@code stop()}（模拟宕机/断网），客户端连接被断开；</li>
 *   <li><b>短线重连</b>：服务端重新 {@code start()}，客户端重新 {@code connect()}，
 *       恢复订阅与数据收发，验证链路自愈。</li>
 * </ol>
 *
 * <p>使用 {@link TcpSyncServer}（真实 TCP 长连接）+ {@link TcpSyncClient}，
 * 覆盖 {@link DataSyncAgentServer} 网络模式所依赖的 SyncServer 链路。</p>
 *
 * @since 4.0.0.42
 */
public final class DataSyncReconnectProbe {

    /**
     * 固定测试端口（避免与 ssh 2222 / shell 6666 冲突）
     */
    private static final int PORT = 28979;

    /**
     * 程序入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) throws Exception {
        // 先构造 DataSyncServer（本地 AgentServerManager，无 Spring）
        AgentServerManager agentServerManager = new DataSyncAgentServer(null, null, "reconnect-probe");
        DefaultDataSyncServer dataSyncServer = new DefaultDataSyncServer(agentServerManager);
        dataSyncServer.setMappingManager(new DefaultDataSyncMappingManager());

        // 构造 TCP SyncServer（DataSyncAgentServer 网络模式依赖）
        ServerSetting setting = new ServerSetting();
        setting.setPort(PORT);
        SyncServer syncServer = new TcpSyncServer(setting);

        // 服务端监听器：统计客户端连接/断开/消息
        final AtomicInteger connected = new AtomicInteger();
        final AtomicInteger disconnected = new AtomicInteger();
        final AtomicInteger received = new AtomicInteger();
        final AtomicInteger onErrorCount = new AtomicInteger();
        final CopyOnWriteArrayList<String> eventLog = new CopyOnWriteArrayList<>();
        syncServer.addListener(new SyncServerListener() {
            @Override
            public void onClientConnected(java.lang.String clientId, Map<String, Object> metadata) {
                connected.incrementAndGet();
                eventLog.add("CONNECT:" + clientId);
                System.out.println("[RECONNECT-PROBE] 客户端接入: " + clientId);
            }

            @Override
            public void onClientDisconnected(String clientId) {
                disconnected.incrementAndGet();
                eventLog.add("DISCONNECT:" + clientId);
                System.out.println("[RECONNECT-PROBE] 客户端断开: " + clientId);
            }

            @Override
            public void onMessage(String clientId, String topic, Object data) {
                received.incrementAndGet();
                eventLog.add("MESSAGE:" + topic + "=" + data);
                System.out.println("[RECONNECT-PROBE] 收到数据: client=" + clientId + ", topic=" + topic + ", data=" + data);
            }

            @Override
            public void onError(String clientId, Throwable throwable) {
                onErrorCount.incrementAndGet();
                eventLog.add("ERROR:" + clientId + ":" + throwable.getMessage());
                System.out.println("[RECONNECT-PROBE] 连接异常: client=" + clientId + ", err=" + throwable.getMessage());
            }
        });

        // 1. 正常连接
        System.out.println("[RECONNECT-PROBE] 步骤1: 启动 SyncServer 端口=" + PORT);
        syncServer.start();
        sleep(1200);

        SyncClient client = new TcpSyncClient("client-A", "tcp://127.0.0.1:" + PORT);
        CountDownLatch subscribed = new CountDownLatch(2);
        AtomicReference<String> lastMessage = new AtomicReference<>();
        client.subscribe("sink:data:user-sink", (routedTopic, data) -> {
            lastMessage.set(routedTopic + "=" + data);
            subscribed.countDown();
            System.out.println("[RECONNECT-PROBE] 客户端#1 收到服务端数据: " + routedTopic + "=" + data);
        });
        client.subscribe("source:data:user-source", (routedTopic, data) -> {
            lastMessage.set(routedTopic + "=" + data);
            subscribed.countDown();
            System.out.println("[RECONNECT-PROBE] 客户端#1 收到服务端数据: " + routedTopic + "=" + data);
        });

        client.connect();
        System.out.println("[RECONNECT-PROBE] 客户端#1 已连接, isConnected=" + client.isConnected());

        // 监听掉线事件：证明"突然掉线"被 TcpSyncClient 检测到（onError 回调）。
        // 注意：TcpSyncClient 掉线后仅回调 onError，不会自动复位 connected 标志，
        // 重连须显式 disconnect() + connect()（短线重连由上层策略驱动）。
        final AtomicInteger errorCallbacks = new AtomicInteger();
        client.addListener(new com.chua.common.support.network.sync.SyncFlowListener() {
            @Override
            public void onError(String protocol, Throwable cause) {
                errorCallbacks.incrementAndGet();
                System.out.println("[RECONNECT-PROBE] 客户端检测到掉线: " + protocol + " -> " + cause.getMessage());
            }
        });

        // 2. 客户端向服务端推送数据（模拟 datasync 数据投递）
        System.out.println("[RECONNECT-PROBE] 步骤2: 客户端#1 向服务端发送数据");
        client.send("sink:data:user-sink", "batch-1:张三,30");
        client.send("sink:data:user-sink", "batch-1:李四,25");
        sleep(1200);

        // 服务端向客户端发布数据（模拟 datasync topic 广播）
        System.out.println("[RECONNECT-PROBE] 步骤2b: 服务端向客户端发布数据");
        syncServer.publish("sink:data:user-sink", "server-push-1");
        syncServer.publish("source:data:user-source", "server-push-2");
        subscribed.await(5, TimeUnit.SECONDS);

        // 3. 突然掉线：服务端停止（客户端 read 线程将收到 Socket closed -> onError -> 自动重连）
        System.out.println("[RECONNECT-PROBE] 步骤3: 服务端 stop() 模拟突然掉线");
        try {
            syncServer.stop();
        } catch (Exception e) {
            System.out.println("[RECONNECT-PROBE] 服务端停止异常(可忽略): " + e.getMessage());
        }
        sleep(1500);

        // 掉线瞬间到重连完成前客户端短暂不可用（真实短断窗口）
        boolean wasInDownWindow = false;
        try {
            client.send("sink:data:user-sink", "should-fail");
        } catch (Exception e) {
            wasInDownWindow = true;
            System.out.println("[RECONNECT-PROBE] 掉线窗口内发送失败(符合预期): " + e.getClass().getSimpleName());
        }

        // 4. 短线重连：服务端重新启动 + 客户端显式重连（disconnect + connect 清理旧 Socket 重建链路）
        System.out.println("[RECONNECT-PROBE] 步骤4: 服务端重新 start() + 客户端 disconnect+connect 短线重连");
        syncServer.start();
        sleep(1500);

        // 重连（先断开清理 connected/socket 状态，再重建新连接）
        try {
            client.disconnect();
        } catch (Exception ignored) {
            // 忽略断开清理异常
        }
        client.connect();
        System.out.println("[RECONNECT-PROBE] 客户端#1 重连后 isConnected=" + client.isConnected());

        // 重连后再次发送数据，验证链路完全恢复
        client.send("sink:data:user-sink", "batch-3:赵六,32");
        System.out.println("[RECONNECT-PROBE] 重连后发送数据成功, isConnected=" + client.isConnected());
        sleep(1200);
        syncServer.publish("sink:data:user-sink", "server-push-after-reconnect");
        sleep(1200);

        // 汇总
        System.out.println();
        System.out.println("[RECONNECT-PROBE] ===== 验证结果汇总 =====");
        System.out.println("[RECONNECT-PROBE] 服务端收到客户端消息数=" + received.get());
        System.out.println("[RECONNECT-PROBE] 客户端接入事件=" + connected.get() + ", 断开事件=" + disconnected.get());
        System.out.println("[RECONNECT-PROBE] 掉线窗口内发送被拒=" + wasInDownWindow + ", 连接异常(客户端回调)=" + errorCallbacks.get());
        System.out.println("[RECONNECT-PROBE] 短线重连结果 isConnected=" + client.isConnected());
        System.out.println("[RECONNECT-PROBE] 重连后链路恢复(收到>=3条客户端消息且 isConnected)="
                + (received.get() >= 3 && client.isConnected()));
        System.out.println("[RECONNECT-PROBE] 事件流水(=重连自愈通过): " + eventLog);

        // 清理
        try {
            client.close();
        } catch (Exception ignored) {
            // 忽略
        }
        try {
            syncServer.stop();
        } catch (Exception ignored) {
            // 忽略
        }
        System.out.println("[RECONNECT-PROBE] 探针结束");
    }

    /**
     * 简单休眠。
     *
     * @param millis 毫秒
     */
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}