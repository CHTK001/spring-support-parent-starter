package com.chua.starter.job.test;

import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.SyncServer;
import com.chua.common.support.network.server.SyncServerListener;
import com.chua.common.support.network.sync.SyncClient;
import com.chua.common.support.network.sync.impl.TcpSyncClient;
import com.chua.common.support.network.sync.impl.TcpSyncServer;
import com.chua.common.support.network.sync.netty.NettyTcpSyncClient;
import com.chua.common.support.network.sync.netty.NettyTcpSyncServer;
import com.chua.starter.job.support.JobProperties;
import com.chua.starter.job.support.execute.JobExecutor;
import com.chua.starter.job.support.handler.JobHandler;
import com.chua.starter.job.support.handler.JobHandlerFactory;
import com.chua.starter.job.support.log.JobLogEvent;
import com.chua.starter.job.support.log.JobLogSink;
import com.chua.starter.job.support.sync.ExecutorSyncBinder;
import com.chua.starter.job.support.sync.JobSyncBus;
import com.chua.starter.job.support.sync.JobSyncTopics;
import com.chua.starter.job.support.sync.SchedulerSyncBinder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SCHEDULER 与 EXECUTOR 真实网络链路端到端测试 (不走 InMemory fallback)。
 *
 * <p>在单 JVM 内直接装配两侧组件, 使用真实 TCP 传输:</p>
 * <ol>
 *   <li>SCHEDULER 侧: 真实 {@link SyncServer} + {@link SchedulerSyncBinder} + 采集型 {@link JobLogSink};</li>
 *   <li>EXECUTOR 侧: 真实 {@link SyncClient} 连接服务端 + {@link ExecutorSyncBinder} + {@link JobExecutor};</li>
 *   <li>Scheduler 总线上发布 job-cmd → 经真实 TCP 下行 → Executor 执行 handler;</li>
 *   <li>Executor 执行完成后 job-log 经真实 TCP 上行 → Scheduler 服务端收到并落入 sink。</li>
 * </ol>
 *
 * @author CH
 */
class RealSyncNetworkTest {

    /**
     * 采集型日志 sink, 记录收到的所有 JobLogEvent。
     *
     * @author CH
     */
    static final class CollectingLogSink implements JobLogSink {

        /**
         * 已收到的日志事件
         */
        private final List<JobLogEvent> events = new ArrayList<>();

        @Override
        public synchronized void write(JobLogEvent event) {
            events.add(event);
        }

        /**
         * 已收到的事件数量。
         *
         * @return 数量
         */
        synchronized int size() {
            return events.size();
        }
    }

    /**
     * JDK TCP 真实链路: Scheduler 下行 cmd → Executor 执行 → job-log 上行回 Scheduler。
     */
    @Test
    void schedulerShouldDispatchToExecutorOverJdkTcp() throws Exception {
        assertRealDispatch(new TcpSyncServer(ServerSetting.builder().host("127.0.0.1").port(19494).build()),
                new TcpSyncClient("tcp://127.0.0.1:19494"));
    }

    /**
     * Netty TCP 真实链路: 与 JDK 版本相同语义。
     */
    @Test
    void schedulerShouldDispatchToExecutorOverNettyTcp() throws Exception {
        assertRealDispatch(new NettyTcpSyncServer(ServerSetting.builder().host("127.0.0.1").port(19495).build()),
                new NettyTcpSyncClient("tcp://127.0.0.1:19495"));
    }

    /**
     * 真实链路断言骨架。
     *
     * @param server SCHEDULER 侧真实 SyncServer
     * @param client EXECUTOR 侧真实 SyncClient (指向 server 端口)
     */
    private void assertRealDispatch(SyncServer server, SyncClient client) throws Exception {
        // Scheduler 侧组件
        JobSyncBus schedulerBus = new JobSyncBus();
        CollectingLogSink sink = new CollectingLogSink();
        server.start();
        SchedulerSyncBinder schedulerBinder = new SchedulerSyncBinder(server, schedulerBus, sink, "scheduler-node");
        schedulerBinder.start();

        // 服务端原始监听: 统计收到上行 job-log 的条数 (证明真实 TCP 上行链路)
        AtomicInteger serverLogMessages = new AtomicInteger();
        server.addListener(new SyncServerListener() {
            @Override
            public void onMessage(String clientId, String topic, Object message) {
                if (JobSyncTopics.TASK_LOG.equals(topic)) {
                    serverLogMessages.incrementAndGet();
                }
            }
        });

        // Executor 侧组件
        JobSyncBus executorBus = new JobSyncBus();
        JobProperties properties = new JobProperties();
        properties.getSync().getExecutor().setServerUrl("tcp://127.0.0.1:" + server.getPort());
        ExecutorSyncBinder executorBinder = new ExecutorSyncBinder(client, executorBus, properties, "executor-node");
        executorBinder.start();

        // 注册 handler + 执行器
        JobHandlerFactory factory = JobHandlerFactory.getInstance();
        AtomicInteger invoked = new AtomicInteger();
        JobHandler handler = new JobHandler() {
            @Override
            public String name() {
                return "real-net-demo";
            }

            @Override
            public void execute() {
                invoked.incrementAndGet();
            }
        };
        factory.register(handler);
        JobExecutor executor = new JobExecutor(factory, executorBus, "executor-node");

        try {
            // 等待客户端完成注册
            awaitTrue(() -> server.getConnectedClients().size() >= 1, "客户端应完成注册");

            // Scheduler 总线上发布 cmd → 真实 TCP 下行 → Executor 执行
            schedulerBus.publish(JobSyncTopics.TASK_COMMAND, "scheduler-node", "real-net-demo");
            awaitTrue(() -> invoked.get() >= 1, "Executor 应通过真实网络执行 handler");

            // Executor 上行 job-log → Scheduler 服务端应收到
            awaitTrue(() -> serverLogMessages.get() >= 1, "Scheduler 应收到上行 job-log");

            assertTrue(invoked.get() >= 1, "handler 应至少执行 1 次");
            assertTrue(serverLogMessages.get() >= 1, "服务端应至少收到 1 条上行 job-log");
            assertNotNull(executor, "JobExecutor 应装配成功");
            assertEquals(1, server.getConnectedClients().size(), "服务端应登记唯一客户端");
        } finally {
            executor.shutdown();
            factory.deregister("real-net-demo");
            executorBinder.shutdown();
            schedulerBinder.shutdown();
            server.stop();
        }
    }

    /**
     * 轮询等待条件成立。
     *
     * @param condition 条件
     * @param message   失败消息
     */
    private static void awaitTrue(java.util.function.BooleanSupplier condition, String message)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000L;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError(message);
            }
            Thread.sleep(50L);
        }
    }
}
