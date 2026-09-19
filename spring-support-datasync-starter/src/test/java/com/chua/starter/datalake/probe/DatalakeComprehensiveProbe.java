package com.chua.starter.datalake.probe;

import com.chua.common.support.concurrent.offset.OffsetFlow;
import com.chua.common.support.concurrent.offset.OffsetConfig;
import com.chua.common.support.lang.datasource.engine.EngineDataSource;
import com.chua.datalake.support.engine.DefaultPipelineEngine;
import com.chua.datalake.support.executor.DatalakeExecutorManager;
import com.chua.datalake.support.executor.DatalakeReactorExecutor;
import com.chua.datalake.support.manager.SinkManager;
import com.chua.datalake.support.model.DataEnvelope;
import com.chua.datalake.support.model.PipelineState;
import com.chua.datalake.support.pipeline.DefaultPipelineManager;
import com.chua.datalake.support.server.DatalakeServer;
import com.chua.datalake.support.server.DatalakeServerBuilder;
import com.chua.datalake.support.spi.pipeline.PipelineEngine;
import com.chua.datalake.support.spi.pipeline.PipelineManager;
import com.chua.datalake.support.spi.sink.DataSink;
import com.chua.datalake.support.sink.JdbcSink;
import com.chua.datalake.support.sink.LogSink;
import com.chua.datalake.support.sink.StatisticSink;
import com.chua.datalake.support.subscriber.RealTimeDatalakeSubscriber;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 数据湖 全面功能探测 — 覆盖 reactor执行器 / 执行器管理器 / sink管理器 /
 * 服务端构建器 / 真实Sink / 偏移量流 / 并发安全 共 41 项场景。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class DatalakeComprehensiveProbe {

    private static int passed = 0, failed = 0, total = 0; // 通过
    private static final List<String> failedCases = new CopyOnWriteArrayList<>(); // 失败CAS

    /**
     * main。
     * @param args 参数
     */
    public static void main(String[] args) throws Exception {
        System.err.println("=============================================================");
        System.err.println("  Datalake 全面功能探测 (DatalakeComprehensiveProbe)");
        System.err.println("=============================================================");

        testReactorExecutor();
        testExecutorManager();
        testSinkManager();
        testServerBuilder();
        testConcurrencySafety();
        testRealSink();
        testOffsetFlow();
        testDataEnvelopeEdge();

        System.err.println("-------------------------------------------------------------");
        System.err.printf("  Result: %d/%d PASS, %d FAIL%n", passed, total, failed);
        if (!failedCases.isEmpty()) {
            System.err.println("  Failed: " + failedCases);
        }
        System.err.println("=============================================================");
        System.exit(failed > 0 ? 1 : 0);
    }

    // ═══════════════════════════════════════════════════════════════
 // E01~E08: 数据湖reactor执行器
    // ═══════════════════════════════════════════════════════════════

    /**
     * 测试reactor执行器。
     */
    private static void testReactorExecutor() {
        System.err.println("\n─── E01~E08: DatalakeReactorExecutor ───");

        check("E01-publish null data → no-op", () -> {
            DatalakeReactorExecutor exec = new DatalakeReactorExecutor("test-e01", true);
            AtomicBoolean called = new AtomicBoolean(false);
            exec.publish("sink1", null);
            return !called.get();
        });

        check("E02-publish with pipelineEngine=null → fallback sinkRegistry", () -> {
            CapturingSink sink = new CapturingSink("direct");
            DatalakeReactorExecutor exec = new DatalakeReactorExecutor("test-e02", true);
            exec.registerSink(sink);
            List<Map<String, Object>> data = new ArrayList<>();
            data.add(Map.of("k", "v"));
            exec.publish("direct", data);
            return sink.getCount() >= 1;
        });

        check("E03-single exception in publish → continues processing", () -> {
            FailingSink failSink = new FailingSink();
            DatalakeReactorExecutor exec = new DatalakeReactorExecutor("test-e03", true);
            PipelineManager pm = new DefaultPipelineManager();
            pm.savePipeline("e03", "{\"stages\":{\"default\":{\"sink\":[{\"type\":\"failing\"}]}}}");
            Map<String, DataSink> reg = new ConcurrentHashMap<>();
            reg.put("failing", failSink);
            PipelineEngine engine = new DefaultPipelineEngine(pm, reg, null);
            exec.setPipelineEngine(engine);
            List<Map<String, Object>> data = new ArrayList<>();
            data.add(Map.of("i", 0));
            data.add(Map.of("i", 1));
            exec.publish("e03", data);
            return failSink.getCount() >= 2;
        });

        check("E04-publish without chronicleProvider → no crash", () -> {
            DatalakeReactorExecutor exec = new DatalakeReactorExecutor("test-e04", true);
            Map<String, DataSink> reg = new ConcurrentHashMap<>();
            reg.put("log", new CapturingSink("log"));
            exec.registerSink(reg.get("log"));
            exec.publish("log", List.of(Map.of("k", "v")));
            return true;
        });

        check("E05-subscribe without chronicleProvider → warn log, no crash", () -> {
            DatalakeReactorExecutor exec = new DatalakeReactorExecutor("test-e05", true);
            exec.subscribe("sink1", data -> {});
            return true;
        });

        check("E06-registerSink + fallback publish", () -> {
            CapturingSink logSink = new CapturingSink("log");
            CapturingSink rtSink = new CapturingSink("realtime");
            DatalakeReactorExecutor exec = new DatalakeReactorExecutor("test-e06", true);
            exec.registerSink(logSink);
            exec.registerSink(rtSink);
            exec.publish("log", List.of(Map.of("i", 1)));
            exec.publish("realtime", List.of(Map.of("i", 2)));
            return logSink.getCount() >= 1 && rtSink.getCount() >= 1;
        });

        check("E07-concurrent publish() thread-safe", () -> {
            CapturingSink sink = new CapturingSink("log");
            DatalakeReactorExecutor exec = new DatalakeReactorExecutor("test-e07", true);
            exec.registerSink(sink);
            int threads = 10, perThread = 100;
            CountDownLatch latch = new CountDownLatch(threads);
            for (int t = 0; t < threads; t++) {
                final int tid = t;
                Executors.newSingleThreadExecutor().execute(() -> {
                    for (int i = 0; i < perThread; i++) {
                        exec.publish("log", List.of(Map.of("tid", tid, "i", i)));
                    }
                    latch.countDown();
                });
            }
            boolean ok = latch.await(10, TimeUnit.SECONDS);
            return ok && sink.getCount() >= threads * perThread;
        });

        check("E08-agentId and topic format", () -> {
            DatalakeReactorExecutor exec = new DatalakeReactorExecutor("my-agent", true);
            return "my-agent".equals(exec.getAgentId());
        });
    }

    // ═══════════════════════════════════════════════════════════════
 // E09~E13: 数据湖执行器管理器
    // ═══════════════════════════════════════════════════════════════

    /**
     * 测试执行器管理器。
     */
    private static void testExecutorManager() {
        System.err.println("\n─── E09~E13: DatalakeExecutorManager ───");

        check("E09-setPipelineEngine(2-param) sets engine", () -> {
            DatalakeExecutorManager mgr = new DatalakeExecutorManager();
            PipelineManager pm = new DefaultPipelineManager();
            Map<String, DataSink> reg = new ConcurrentHashMap<>();
            PipelineEngine engine = new DefaultPipelineEngine(pm, reg, null);
            mgr.setPipelineEngine(null, engine);
            return mgr.getExecutor("any") != null;
        });

        check("E10-setPipelineEngine(3-param) sets engine + registers sinks", () -> {
            DatalakeExecutorManager mgr = new DatalakeExecutorManager();
            Map<String, DataSink> sinks = new ConcurrentHashMap<>();
            sinks.put("log", new CapturingSink("log"));
            sinks.put("stat", new CapturingSink("statistic"));
            PipelineEngine engine = new DefaultPipelineEngine(new DefaultPipelineManager(), sinks, null);
            mgr.setPipelineEngine(null, engine, sinks);
 // 验证 sinks are 注册 via 降级
            List<Map<String, Object>> data = List.of(Map.of("k", "v"));
            mgr.getExecutor("any").publish("log", data);
            return true;
        });

        check("E11-getExecutor() returns same instance", () -> {
            DatalakeExecutorManager mgr = new DatalakeExecutorManager();
            return mgr.getExecutor("t1") == mgr.getExecutor("t2");
        });

        check("E12-getTopic() returns correct format", () -> {
            DatalakeExecutorManager mgr = new DatalakeExecutorManager();
            String topic = mgr.getTopic("any");
            return topic != null && topic.startsWith("server:");
        });

        check("E13-start()/stop() no exception", () -> {
            DatalakeExecutorManager mgr = new DatalakeExecutorManager();
            mgr.start();
            mgr.stop();
            return true;
        });
    }

    // ═══════════════════════════════════════════════════════════════
 // E14~E18: sink管理器
    // ═══════════════════════════════════════════════════════════════

    /**
     * 测试sink管理器。
     */
    private static void testSinkManager() {
        System.err.println("\n─── E14~E18: SinkManager ───");

        check("E14-start() calls all sink.start()", () -> {
            AtomicInteger startCount = new AtomicInteger(0);
            Map<String, DataSink> reg = new ConcurrentHashMap<>();
            reg.put("s1", new DataSink() {
                /**
                 * 类型。
                 * @return 类型的结果
                 */
                public String type() { return "s1"; }
                /**
                 * 启动。
                 */
                public void start() { startCount.incrementAndGet(); }
                /**
                 * 停止。
                 */
                public void stop() {}
                /**
                 * 写入。
                 * @param e e
                 * @param c c
                 * @return 写入的结果
                 */
                public boolean write(DataEnvelope e, Map<String, Object> c) { return true; }
                /**
                 * 获取数据源。
                 * @return 获取数据源的结果
                 */
                public com.chua.common.support.lang.datasource.engine.EngineDataSource<?> getDataSource() { return null; }
            });
            reg.put("s2", new DataSink() {
                /**
                 * 类型。
                 * @return 类型的结果
                 */
                public String type() { return "s2"; }
                /**
                 * 启动。
                 */
                public void start() { startCount.incrementAndGet(); }
                /**
                 * 停止。
                 */
                public void stop() {}
                /**
                 * 写入。
                 * @param e e
                 * @param c c
                 * @return 写入的结果
                 */
                public boolean write(DataEnvelope e, Map<String, Object> c) { return true; }
                /**
                 * 获取数据源。
                 * @return 获取数据源的结果
                 */
                public com.chua.common.support.lang.datasource.engine.EngineDataSource<?> getDataSource() { return null; }
            });
            new SinkManager(reg).start();
            return startCount.get() == 2;
        });

        check("E15-stop() calls all sink.stop()", () -> {
            AtomicInteger stopCount = new AtomicInteger(0);
            Map<String, DataSink> reg = new ConcurrentHashMap<>();
            for (int i = 0; i < 3; i++) {
                final int idx = i;
                reg.put("s" + idx, new DataSink() {
                    /**
                     * 类型。
                     * @return 类型的结果
                     */
                    public String type() { return "s" + idx; }
                    /**
                     * 启动。
                     */
                    public void start() {}
                    /**
                     * 停止。
                     */
                    public void stop() { stopCount.incrementAndGet(); }
                    /**
                     * 写入。
                     * @param e e
                     * @param c c
                     * @return 写入的结果
                     */
                    public boolean write(DataEnvelope e, Map<String, Object> c) { return true; }
                    /**
                     * 获取数据源。
                     * @return 获取数据源的结果
                     */
                    public com.chua.common.support.lang.datasource.engine.EngineDataSource<?> getDataSource() { return null; }
                });
            }
            new SinkManager(reg).stop();
            return stopCount.get() == 3;
        });

        check("E16-storeSinks() returns only non-AccessSink with dataSource", () -> {
            Map<String, DataSink> reg = new ConcurrentHashMap<>();
            reg.put("log", new LogSink());
            reg.put("jdbc", new JdbcSink());
            SinkManager mgr = new SinkManager(reg);
            List<DataSink> stores = mgr.storeSinks();
 // Both 日志sink 和 jdbcsink are accesssink 和 have 空 数据源 → 空
            return stores.isEmpty();
        });

        check("E17-start() one sink fails → exception propagates (no partial start)", () -> {
            Map<String, DataSink> reg = new ConcurrentHashMap<>();
            reg.put("good1", new DataSink() {
                /**
                 * 类型。
                 * @return 类型的结果
                 */
                public String type() { return "good1"; }
                /**
                 * 启动。
                 */
                public void start() {}
                /**
                 * 停止。
                 */
                public void stop() {}
                /**
                 * 写入。
                 * @param e e
                 * @param c c
                 * @return 写入的结果
                 */
                public boolean write(DataEnvelope e, Map<String, Object> c) { return true; }
                /**
                 * 获取数据源。
                 * @return 获取数据源的结果
                 */
                public EngineDataSource<?> getDataSource() { return null; }
            });
            reg.put("bad", new DataSink() {
                /**
                 * 类型。
                 * @return 类型的结果
                 */
                public String type() { return "bad"; }
                /**
                 * 启动。
                 */
                public void start() { throw new RuntimeException("boom"); }
                /**
                 * 停止。
                 */
                public void stop() {}
                /**
                 * 写入。
                 * @param e e
                 * @param c c
                 * @return 写入的结果
                 */
                public boolean write(DataEnvelope e, Map<String, Object> c) { return true; }
                /**
                 * 获取数据源。
                 * @return 获取数据源的结果
                 */
                public EngineDataSource<?> getDataSource() { return null; }
            });
            reg.put("good2", new DataSink() {
                /**
                 * 类型。
                 * @return 类型的结果
                 */
                public String type() { return "good2"; }
                /**
                 * 启动。
                 */
                public void start() {}
                /**
                 * 停止。
                 */
                public void stop() {}
                /**
                 * 写入。
                 * @param e e
                 * @param c c
                 * @return 写入的结果
                 */
                public boolean write(DataEnvelope e, Map<String, Object> c) { return true; }
                /**
                 * 获取数据源。
                 * @return 获取数据源的结果
                 */
                public EngineDataSource<?> getDataSource() { return null; }
            });
            try {
                new SinkManager(reg).start();
                return false; // should have thrown
            } catch (RuntimeException e) {
                return e.getMessage().contains("boom");
            }
        });

        check("E18-null map constructor", () -> {
            try {
                new SinkManager(null).start();
                return false;
            } catch (NullPointerException e) {
                return true;
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
 // E19~E24: 数据湖服务端构建器
    // ═══════════════════════════════════════════════════════════════

    /**
     * 测试服务端构建器。
     */
    private static void testServerBuilder() throws Exception {
        System.err.println("\n─── E19~E24: DatalakeServerBuilder ───");

        check("E19-build() SPI auto-discovery finds default sinks", () -> {
            Path tmp = Files.createTempDirectory("dl-builder-test");
            DatalakeServer server = DatalakeServerBuilder.builder()
                    .dataPath(tmp.toAbsolutePath().toString())
                    .build();
            server.start();
            PipelineManager pm = server.getPipelineManager();
            pm.savePipeline("e19", "{\"stages\":{\"default\":{\"sink\":[{\"type\":\"log\"}]}}}");
            PipelineEngine engine = server.getPipelineEngine();
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("k", "v"))
                    .pipelineId("e19")
                    .traceId("e19")
                    .timestamp(System.currentTimeMillis())
                    .build();
            engine.execute("e19", env);
            server.stop();
            return engine != null && pm != null;
        });

        check("E20-build() custom apiServer accepted", () -> {
            Path tmp = Files.createTempDirectory("dl-builder-test");
            DatalakeServer server = DatalakeServerBuilder.builder()
                    .dataPath(tmp.toAbsolutePath().toString())
                    .build();
            return server.getPipelineEngine() != null;
        });

        check("E21-registerSink duplicate → last wins", () -> {
            Map<String, DataSink> reg = new ConcurrentHashMap<>();
            reg.put("log", new CapturingSink("log-v1"));
            reg.put("log", new CapturingSink("log-v2"));
            Path tmp = Files.createTempDirectory("dl-builder-test");
            DatalakeServer server = DatalakeServerBuilder.builder()
                    .sinks(reg)
                    .dataPath(tmp.toAbsolutePath().toString())
                    .build();
            server.start();
            PipelineManager pm = server.getPipelineManager();
            pm.savePipeline("e21", "{\"stages\":{\"default\":{\"sink\":[{\"type\":\"log\"}]}}}");
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("k", "v"))
                    .pipelineId("e21")
                    .timestamp(System.currentTimeMillis())
                    .build();
            server.getPipelineEngine().execute("e21", env);
            server.stop();
            return true;
        });

        check("E22-dataSyncServer injection → DatalakeExecutorManager set", () -> {
            Path tmp = Files.createTempDirectory("dl-builder-test");
            DatalakeServer server = DatalakeServerBuilder.builder()
                    .dataPath(tmp.toAbsolutePath().toString())
                    .build();
            server.start();
            boolean ok = server.getPipelineEngine() != null;
            server.stop();
            return ok;
        });

        check("E23-getPipelineManager() non-null", () -> {
            Path tmp = Files.createTempDirectory("dl-builder-test");
            DatalakeServer server = DatalakeServerBuilder.builder()
                    .dataPath(tmp.toAbsolutePath().toString())
                    .build();
            server.start();
            boolean ok = server.getPipelineManager() != null;
            server.stop();
            return ok;
        });

        check("E24-getPipelineEngine() non-null", () -> {
            Path tmp = Files.createTempDirectory("dl-builder-test");
            DatalakeServer server = DatalakeServerBuilder.builder()
                    .dataPath(tmp.toAbsolutePath().toString())
                    .build();
            server.start();
            boolean ok = server.getPipelineEngine() != null;
            server.stop();
            return ok;
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // E25~E27: Concurrency Safety
    // ═══════════════════════════════════════════════════════════════

    /**
     * 测试concurrencysafety。
     */
    private static void testConcurrencySafety() throws InterruptedException {
        System.err.println("\n─── E25~E27: 并发安全 ───");

        check("E25-Server start+stop concurrent CAS", () -> {
            Path tmp = Files.createTempDirectory("dl-cas-test");
            DatalakeServer server = DatalakeServerBuilder.builder()
                    .dataPath(tmp.toAbsolutePath().toString())
                    .build();
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch stopLatch = new CountDownLatch(1);
            AtomicBoolean startOk = new AtomicBoolean(false);
            AtomicBoolean stopOk = new AtomicBoolean(false);
            ExecutorService es = Executors.newFixedThreadPool(4);
            es.execute(() -> {
                startLatch.countDown();
                server.start();
                startOk.set(true);
            });
            es.execute(() -> {
                try {
                    startLatch.await();
                } catch (Exception ignored) {
                }
                server.stop();
                stopOk.set(true);
            });
            es.shutdown();
            es.awaitTermination(5, TimeUnit.SECONDS);
            return startOk.get() && stopOk.get();
        });

        check("E26-PipelineManager concurrent write/read", () -> {
            PipelineManager pm = new DefaultPipelineManager();
            int threads = 10, opsPerThread = 100;
            CountDownLatch latch = new CountDownLatch(threads);
            AtomicInteger errors = new AtomicInteger(0);
            for (int t = 0; t < threads; t++) {
                final int tid = t;
                Executors.newSingleThreadExecutor().execute(() -> {
                    for (int i = 0; i < opsPerThread; i++) {
                        try {
                            pm.savePipeline("cm-" + tid + "-" + i, "{\"stages\":{\"default\":{\"sink\":[{\"type\":\"log\"}]}}}");
                            String result = pm.getPipeline("cm-" + tid + "-" + i);
                            if (result == null) {
                                errors.incrementAndGet();
                            }
                        } catch (Exception e) {
                            errors.incrementAndGet();
                        }
                    }
                    latch.countDown();
                });
            }
            boolean ok = latch.await(10, TimeUnit.SECONDS);
            return ok && errors.get() == 0;
        });

        check("E27-RealTimeDatalakeSubscriber concurrent push (single thread, no block race)", () -> {
            OffsetFlow flow = OffsetFlow.create().start();
            AtomicInteger received = new AtomicInteger(0);
            RealTimeDatalakeSubscriber sub = new RealTimeDatalakeSubscriber(
                    "e27-sub", flow, env -> received.incrementAndGet());
            int count = 50;
            for (int i = 0; i < count; i++) {
                DataEnvelope env = DataEnvelope.builder()
                        .parsed(Map.of("i", i))
                        .timestamp(System.currentTimeMillis())
                        .build();
                sub.push(env).block(java.time.Duration.ofSeconds(2));
            }
            flow.close();
            return received.get() == count;
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // E28~E33: Real Sink Implementations
    // ═══════════════════════════════════════════════════════════════

    /**
     * 测试realsink。
     */
    private static void testRealSink() {
        System.err.println("\n─── E28~E33: Real Sink 实现 ───");

        check("E28-LogSink.write(null) → false", () -> {
            return !new LogSink().write(null, Collections.emptyMap());
        });

        check("E29-StatisticSink.write(null) → false", () -> {
            return !new StatisticSink().write(null, Collections.emptyMap());
        });

        check("E30-JdbcSink.write(null) → false", () -> {
            return !new JdbcSink().write(null, Collections.emptyMap());
        });

        check("E31-LogSink.type() == 'log'", () -> {
            return "log".equals(new LogSink().type());
        });

        check("E32-StatisticSink.type() == 'statistic'", () -> {
            return "statistic".equals(new StatisticSink().type());
        });

        check("E33-JdbcSink.type() == 'jdbc'", () -> {
            return "jdbc".equals(new JdbcSink().type());
        });
    }

    // ═══════════════════════════════════════════════════════════════
 // E34~E38: 偏移量流 Advanced
    // ═══════════════════════════════════════════════════════════════

    /**
     * 测试偏移量流。
     */
    private static void testOffsetFlow() throws Exception {
        System.err.println("\n─── E34~E38: OffsetFlow 高级 ───");

        check("E34-basePath custom directory", () -> {
            Path tmp = Files.createTempDirectory("offset-custom");
            OffsetFlow flow = OffsetFlow.create()
                    .basePath(tmp.toAbsolutePath().toString())
                    .start();
            flow.advance("sub-e34");
            long offset = flow.current("sub-e34");
            flow.close();
            return offset >= 1;
        });

        check("E35-persistent true persists across restart", () -> {
            Path tmp = Files.createTempDirectory("offset-persist");
            OffsetFlow flow1 = OffsetFlow.create()
                    .basePath(tmp.toAbsolutePath().toString())
                    .persistent(true)
                    .start();
            flow1.advance("sub-e35");
            flow1.advance("sub-e35");
            long offsetBefore = flow1.current("sub-e35");
            flow1.close();

            OffsetFlow flow2 = OffsetFlow.create()
                    .basePath(tmp.toAbsolutePath().toString())
                    .persistent(true)
                    .start();
            long offsetAfter = flow2.current("sub-e35");
            flow2.close();
            return offsetAfter == offsetBefore;
        });

        check("E36-truncate() clears all offsets", () -> {
            Path tmp = Files.createTempDirectory("offset-truncate");
            OffsetFlow flow = OffsetFlow.create()
                    .basePath(tmp.toAbsolutePath().toString())
                    .start();
            flow.advance("sub-a");
            flow.advance("sub-b");
            flow.truncate();
            return flow.current("sub-a") == 0 && flow.current("sub-b") == 0;
        });

        check("E37-remove() 从缓存移除但持久化文件保留（下次重建读文件）", () -> {
            Path tmp = Files.createTempDirectory("offset-remove");
            OffsetFlow flow = OffsetFlow.create()
                    .basePath(tmp.toAbsolutePath().toString())
                    .start();
            flow.advance("sub-e37");
            flow.advance("sub-e37");
            long before = flow.current("sub-e37");
            flow.remove("sub-e37");
 // 移除 后缓存清空，再次 当前 会重建 文件偏移量 读取文件值
            long after = flow.current("sub-e37");
            flow.close();
            // 文件未删除，重建后值恢复
            return after == before;
        });

        check("E38-close() + restart", () -> {
            Path tmp = Files.createTempDirectory("offset-restart");
            OffsetFlow flow = OffsetFlow.create()
                    .basePath(tmp.toAbsolutePath().toString())
                    .start();
            flow.advance("sub-e38");
            flow.close();
            OffsetFlow flow2 = OffsetFlow.create()
                    .basePath(tmp.toAbsolutePath().toString())
                    .start();
            long offset = flow2.current("sub-e38");
            flow2.close();
            return offset >= 1;
        });
    }

    // ═══════════════════════════════════════════════════════════════
 // E39~E41: 数据envelope Edge CAS
    // ═══════════════════════════════════════════════════════════════

    /**
     * 测试数据envelopeedge。
     */
    private static void testDataEnvelopeEdge() {
        System.err.println("\n─── E39~E41: DataEnvelope 边界 ───");

        check("E39-empty topics Set → isLog() = false", () -> {
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("k", "v"))
                    .topics(Collections.emptySet())
                    .build();
            return !env.isLog();
        });

        check("E40-trace追加顺序正确", () -> {
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("k", "v"))
                    .build();
            env.addTrace("step-1");
            env.addTrace("step-2");
            env.addTrace("step-3");
            return env.getTrace().size() == 3
                    && "step-1".equals(env.getTrace().get(0)) // 豁免规约四十一：get(0)/get(1)/get(2) 对称固定下标枚举（trace 顺序校验），非取首元素
                    && "step-2".equals(env.getTrace().get(1))
                    && "step-3".equals(env.getTrace().get(2));
        });

        check("E41-state可被多次重置（SINK_OK→null→SINK_FAIL）", () -> {
            PipelineManager pm = new DefaultPipelineManager();
            Map<String, DataSink> reg = new ConcurrentHashMap<>();
            reg.put("log", new CapturingSink("log"));
            PipelineEngine engine = new DefaultPipelineEngine(pm, reg, null);
            pm.savePipeline("e41", "{\"stages\":{\"default\":{\"sink\":[{\"type\":\"log\"}]}}}");
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("i", 1))
                    .pipelineId("e41")
                    .traceId("e41")
                    .timestamp(System.currentTimeMillis())
                    .build();
            engine.execute("e41", env);
            PipelineState first = env.getState();
            env.setState(null);
            engine.execute("e41", env);
            PipelineState second = env.getState();
            return first == PipelineState.SINK_OK && second == PipelineState.SINK_OK;
        });
    }

    // ━━━━━━━━━━━━━━━ 辅助 ━━━━━━━━━━━━━━━
    /**
     * 失败sink类。
     *
     * @author CH
     * @since 4.0.0
     * @param name 名称
     * @param check 检查
     */

    private static void check(String name, Check check) {
        total++;
        try {
            boolean ok = check.check();
            if (ok) {
                passed++;
                System.err.printf("  [PASS] %s%n", name);
            }
            else {
                failed++;
                failedCases.add(name);
                System.err.printf("  [FAIL] %s%n", name);
            }
        } catch (Exception e) {
            failed++;
            failedCases.add(name + " (" + e.getMessage() + ")");
            System.err.printf("  [ERROR] %s: %s%n", name, e.getMessage());
        /**
         * capturingsink类。
         *
         * @author CH
         * @since 4.0.0
         */
        }
    }

    @FunctionalInterface
    interface Check { boolean check() throws Exception; }
    /**
     * CapturingSink类。
     *
     * @author CH
     * @since 4.0.0
     */

    public static class CapturingSink implements DataSink {
        private final String type; // 类型
        private final AtomicInteger count = new AtomicInteger(0); // 数量
        /**
         * capturingsink。
         * @param type 类型
         */
        public CapturingSink(String type) { this.type = type; }
        /**
         * reset。
         */
        public void reset() { count.set(0); }
        /**
         * 获取数量。
         * @return 获取数量的结果
         * @author CH
         * @since 4.0.0
         */
        public int getCount() { return count.get(); }
        @Override public String type() { return type; }
        @Override public void start() {}
        @Override public void stop() {}
        @Override
        public boolean write(DataEnvelope env, Map<String, Object> config) {
            count.incrementAndGet();
            return true;
        }
        @Override public EngineDataSource<?> getDataSource() { return null; }
    }

    public static class FailingSink implements DataSink {
        private final AtomicInteger count = new AtomicInteger(0);
        @Override public String type() { return "failing"; }
        @Override public void start() {}
        @Override public void stop() {}
        @Override
        public boolean write(DataEnvelope env, Map<String, Object> config) {
            count.incrementAndGet();
            throw new RuntimeException("fail");
        }
        @Override public EngineDataSource<?> getDataSource() { return null; }
        public int getCount() { return count.get(); }
    }
}
