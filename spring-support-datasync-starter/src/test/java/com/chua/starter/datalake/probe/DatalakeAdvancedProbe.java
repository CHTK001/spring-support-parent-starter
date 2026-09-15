package com.chua.starter.datalake.probe;

import com.chua.common.support.lang.datasource.engine.EngineDataSource;
import com.chua.datalake.support.engine.DefaultPipelineEngine;
import com.chua.datalake.support.model.DataEnvelope;
import com.chua.datalake.support.model.PipelineState;
import com.chua.datalake.support.pipeline.DefaultPipelineManager;
import com.chua.datalake.support.spi.pipeline.PipelineEngine;
import com.chua.datalake.support.spi.pipeline.PipelineManager;
import com.chua.datalake.support.spi.sink.DataSink;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
* 数据湖 管线进阶探测 — 覆盖 20 项场景：
* <ul>
*   <li>A01~A05: Null/缺失安全</li>
*   <li>A06~A09: Sink 状态流转</li>
*   <li>A10~A13: DataEnvelope 属性</li>
*   <li>A14~A15: 并发执行</li>
*   <li>A16~A18: 管线生命周期</li>
*   <li>A19~A20: Sink 配置传递 + 压测</li>
* </ul>
*
* @author CH
* @since 4.0.0.42
 */
@Slf4j
public class DatalakeAdvancedProbe {

    private static int passed = 0; // 通过
    private static int failed = 0; // 失败
    private static int total = 0; // total
    private static final List<String> failedCases = new CopyOnWriteArrayList<>(); // 失败CAS

    private static PipelineManager pipelineManager; // pipeline管理器
    private static final Map<String, DataSink> sinkRegistry = new ConcurrentHashMap<>(); // sinkregistry
    private static PipelineEngine pipelineEngine; // pipelineengine
    private static CapturingSink logSink; // 日志sink
    private static CapturingSink statSink; // statsink
    private static CapturingSink jdbcSink; // jdbcsink
    private static FailingSink failingSink; // 失败sink
    private static long startTime; // 启动时间

    /**
    * main。
    * @param args 参数
     */
    public static void main(String[] args) throws Exception {
        log.info("═══════════════════════════════════════════════════════");
        log.info("║         Datalake 管线进阶探测 (DatalakeAdvancedProbe)     ║");
        log.info("═══════════════════════════════════════════════════════");

        setupInfrastructure();
        testNullSafety();
        testSinkStateTransition();
        testDataEnvelopeProperties();
        testConcurrency();
        testPipelineLifecycle();
        testSinkConfigAndStress();

        log.info("═══════════════════════════════════════════════════════");
        log.info("║  结果: {}/{} 通过, {} 失败", passed, total, failed);
        if (!failedCases.isEmpty()) {
            log.info("║  失败: {}", failedCases);
        }
        log.info("═══════════════════════════════════════════════════════");

        if (failed > 0) {
            System.exit(1);
        }
    }

    // ━━━━━━━━━━━━━━━ 基础设施 ━━━━━━━━━━━━━━━

    /**
    * setupinfrastructure。
     */
    private static void setupInfrastructure() {
        pipelineManager = new DefaultPipelineManager();
        logSink = new CapturingSink("log");
        statSink = new CapturingSink("statistic");
        jdbcSink = new CapturingSink("jdbc");
        failingSink = new FailingSink();

        sinkRegistry.put("log", logSink);
        sinkRegistry.put("statistic", statSink);
        sinkRegistry.put("jdbc", jdbcSink);
        sinkRegistry.put("failing", failingSink);
        sinkRegistry.values().forEach(DataSink::start);

        pipelineEngine = new DefaultPipelineEngine(pipelineManager, sinkRegistry, null);
        ((DefaultPipelineManager) pipelineManager).setEngine((DefaultPipelineEngine) pipelineEngine);
        startTime = System.currentTimeMillis();

        log.info("[DatalakeAdvancedProbe] 基础设施就绪: log/stat/jdbc/failing 共 4 个 Sink");
    }

    /**
    * 单个sinkpipeline。
    * @param sinkType sink类型
    * @return 单个sinkpipeline的结果
     */
    private static String singleSinkPipeline(String sinkType) {
        return "{\"stages\":{\"default\":{\"sink\":[{\"type\":\"" + sinkType + "\"}]}}}";
    }

    /**
    * multisinkpipeline。
    * @param sinkTypes sink类型
    * @return multiSinkPipeline的结果
     */
    private static String multiSinkPipeline(String... sinkTypes) {
        StringBuilder sb = new StringBuilder("{\"stages\":{\"default\":{\"sink\":[");
        for (int i = 0; i < sinkTypes.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"type\":\"").append(sinkTypes[i]).append("\"}");
        }
        sb.append("]}}}");
        return sb.toString();
    }

 // ━━━━━━━━━━━━━━━ A01~A05: 空/缺失安全 ━━━━━━━━━━━━━━━

    /**
    * 测试空safety。
     */
    private static void testNullSafety() {
        log.info("───── A01~A05: Null/缺失安全 ─────");

        check("A01-null envelope 不崩溃", () -> {
            pipelineEngine.execute("test-null", null);
            return true;
        });

        check("A02-null pipelineId 不崩溃", () -> {
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("key", "val"))
                    .timestamp(System.currentTimeMillis())
                    .build();
            pipelineEngine.execute(null, env);
            return true;
        });

        check("A03-未注册 pipelineId → 状态不变", () -> {
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("key", "val"))
                    .pipelineId("nonexistent-pipeline")
                    .timestamp(System.currentTimeMillis())
                    .build();
            pipelineEngine.execute("nonexistent-pipeline", env);
            return env.getState() == null && env.getParsed() != null;
        });

        check("A04-缺少 default stage → 状态不变", () -> {
            pipelineManager.savePipeline("missing-stage", "{\"stages\":{}}");
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("key", "val"))
                    .pipelineId("missing-stage")
                    .timestamp(System.currentTimeMillis())
                    .build();
            pipelineEngine.execute("missing-stage", env);
            return env.getState() == null;
        });

        check("A05-空 sink 列表 → 状态不变", () -> {
            pipelineManager.savePipeline("empty-sink", "{\"stages\":{\"default\":{\"sink\":[]}}}");
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("key", "val"))
                    .pipelineId("empty-sink")
                    .timestamp(System.currentTimeMillis())
                    .build();
            pipelineEngine.execute("empty-sink", env);
            return env.getState() == null;
        });
    }

    // ━━━━━━━━━━━━━━━ A06~A09: Sink 状态流转 ━━━━━━━━━━━━━━━

    /**
    * 测试sink状态过渡。
     */
    private static void testSinkStateTransition() {
        log.info("───── A06~A09: Sink 状态流转 ─────");

        check("A06-单 Sink 成功 → SINK_OK", () -> {
            logSink.reset();
            pipelineManager.savePipeline("a06", singleSinkPipeline("log"));
            DataEnvelope env = buildEnv("a06", "A06");
            pipelineEngine.execute("a06", env);
            return env.getState() == PipelineState.SINK_OK
                    && logSink.getCount() >= 1
                    && env.getTrace().size() >= 1;
        });

        check("A07-多 Sink 全部成功 → SINK_OK", () -> {
            logSink.reset();
            statSink.reset();
            pipelineManager.savePipeline("a07", multiSinkPipeline("log", "statistic"));
            DataEnvelope env = buildEnv("a07", "A07");
            pipelineEngine.execute("a07", env);
            return env.getState() == PipelineState.SINK_OK
                    && logSink.getCount() >= 1
                    && statSink.getCount() >= 1;
        });

        check("A08-Sink 抛出异常 → SINK_FAIL", () -> {
            failingSink.reset();
            pipelineManager.savePipeline("a08", singleSinkPipeline("failing"));
            DataEnvelope env = buildEnv("a08", "A08");
            pipelineEngine.execute("a08", env);
            return env.getState() == PipelineState.SINK_FAIL
                    && failingSink.getCount() >= 1
                    && env.getTrace().stream().anyMatch(t -> t.contains("ERROR"));
        });

        check("A09-未注册 Sink type → 状态不变", () -> {
            pipelineManager.savePipeline("a09", singleSinkPipeline("unknown-type"));
            DataEnvelope env = buildEnv("a09", "A09");
            pipelineEngine.execute("a09", env);
            return env.getState() == null;
        });
    }

 // ━━━━━━━━━━━━━━━ A10~A13: 数据envelope 属性 ━━━━━━━━━━━━━━━

    /**
    * 测试数据envelope属性。
     */
    private static void testDataEnvelopeProperties() {
        log.info("───── A10~A13: DataEnvelope 属性 ─────");

        check("A10-traceId 正确", () -> {
            logSink.reset();
            pipelineManager.savePipeline("a10", singleSinkPipeline("log"));
            String traceId = "trace-12345";
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("seq", 1))
                    .pipelineId("a10")
                    .traceId(traceId)
                    .timestamp(System.currentTimeMillis())
                    .build();
            pipelineEngine.execute("a10", env);
            return traceId.equals(env.getTraceId());
        });

        check("A11-timestamp > 0", () -> {
            logSink.reset();
            pipelineManager.savePipeline("a11", singleSinkPipeline("log"));
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("seq", 2))
                    .pipelineId("a11")
                    .traceId("trace-11")
                    .timestamp(System.currentTimeMillis())
                    .build();
            pipelineEngine.execute("a11", env);
            return env.getTimestamp() > 0;
        });

        check("A12-trace 列表含 [Sink]", () -> {
            logSink.reset();
            pipelineManager.savePipeline("a12", singleSinkPipeline("log"));
            DataEnvelope env = buildEnv("a12", "A12");
            pipelineEngine.execute("a12", env);
            return env.getTrace().stream().anyMatch(t -> t.contains("[Sink]"));
        });

        check("A13-isLog() 与 topics", () -> {
            Set<String> topics = Set.of("log-access", "sensor");
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("k", "v"))
                    .topics(topics)
                    .build();
            return env.isLog();
        });
    }

    // ━━━━━━━━━━━━━━━ A14~A15: 并发 ━━━━━━━━━━━━━━━

    /**
    * 测试concurrency。
     */
    private static void testConcurrency() throws Exception {
        log.info("───── A14~A15: 并发执行 ─────");

        check("A14-100 envelope 并发 → 同一管线", () -> {
            logSink.reset();
            pipelineManager.savePipeline("a14", singleSinkPipeline("log"));
            int count = 100;
            CountDownLatch latch = new CountDownLatch(count);
            for (int i = 0; i < count; i++) {
                final int seq = i;
                Executors.newSingleThreadExecutor().execute(() -> {
                    DataEnvelope env = DataEnvelope.builder()
                            .parsed(Map.of("seq", seq))
                            .pipelineId("a14")
                            .traceId("a14-" + seq)
                            .timestamp(System.currentTimeMillis())
                            .build();
                    pipelineEngine.execute("a14", env);
                    latch.countDown();
                });
            }
            boolean ok = latch.await(5, TimeUnit.SECONDS);
            return ok && logSink.getCount() >= count;
        });

        check("A15-50 envelope 并发 → 5 条不同管线", () -> {
            for (int i = 0; i < 5; i++) {
                logSink.reset();
                pipelineManager.savePipeline("a15-p" + i, singleSinkPipeline("log"));
            }
            int count = 50;
            AtomicInteger totalWrites = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(count);
            for (int i = 0; i < count; i++) {
                final int seq = i;
                final int pipelineIdx = i % 5;
                Executors.newSingleThreadExecutor().execute(() -> {
                    DataEnvelope env = DataEnvelope.builder()
                            .parsed(Map.of("seq", seq))
                            .pipelineId("a15-p" + pipelineIdx)
                            .traceId("a15-" + seq)
                            .timestamp(System.currentTimeMillis())
                            .build();
                    pipelineEngine.execute("a15-p" + pipelineIdx, env);
                    totalWrites.incrementAndGet();
                    latch.countDown();
                });
            }
            boolean ok = latch.await(5, TimeUnit.SECONDS);
            return ok && totalWrites.get() >= count;
        });
    }

    // ━━━━━━━━━━━━━━━ A16~A18: 管线生命周期 ━━━━━━━━━━━━━━━

    /**
    * 测试pipelinelifecycle。
     */
    private static void testPipelineLifecycle() {
        log.info("───── A16~A18: 管线生命周期 ─────");

        check("A16-删除管线后 → 状态不变", () -> {
            logSink.reset();
            pipelineManager.savePipeline("a16", singleSinkPipeline("log"));
            pipelineManager.deletePipeline("a16");
            DataEnvelope env = buildEnv("a16", "A16");
            pipelineEngine.execute("a16", env);
            return env.getState() == null && logSink.getCount() == 0;
        });

        check("A17-执行中新增管线 → 可被路由", () -> {
            logSink.reset();
            pipelineManager.savePipeline("a17", singleSinkPipeline("log"));
            DataEnvelope env = buildEnv("a17", "A17");
            pipelineEngine.execute("a17", env);
            return env.getState() == PipelineState.SINK_OK;
        });

        check("A18-重复注册覆盖旧配置", () -> {
            logSink.reset();
            statSink.reset();
 // 先用 日志 注册
            pipelineManager.savePipeline("a18", singleSinkPipeline("log"));
            DataEnvelope env1 = buildEnv("a18", "A18-v1");
            pipelineEngine.execute("a18", env1);

            // 覆盖为 statistic
            pipelineManager.savePipeline("a18", singleSinkPipeline("statistic"));
            DataEnvelope env2 = buildEnv("a18", "A18-v2");
            pipelineEngine.execute("a18", env2);

            return env1.getState() == PipelineState.SINK_OK
                    && env2.getState() == PipelineState.SINK_OK
                    && logSink.getCount() >= 1
                    && statSink.getCount() >= 1;
        });
    }

    // ━━━━━━━━━━━━━━━ A19~A20: Sink 配置传递 + 压测 ━━━━━━━━━━━━━━━

    /**
    * 测试sink配置和stress。
     */
    private static void testSinkConfigAndStress() throws Exception {
        log.info("───── A19~A20: Sink 配置传递 + 压测 ─────");

        check("A19-Sink config 参数传递", () -> {
            jdbcSink.reset();
            String pipelineJson = "{\"stages\":{\"default\":{\"sink\":[{\"type\":\"jdbc\",\"table\":\"users\",\"db\":\"main\"}]}}}";
            pipelineManager.savePipeline("a19", pipelineJson);
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("name", "alice"))
                    .pipelineId("a19")
                    .traceId("a19")
                    .timestamp(System.currentTimeMillis())
                    .build();
            pipelineEngine.execute("a19", env);
            return jdbcSink.getCount() >= 1
                    && jdbcSink.getLastConfig() != null
                    && "users".equals(jdbcSink.getLastConfig().get("table"))
                    && "main".equals(jdbcSink.getLastConfig().get("db"));
        });

        check("A20-500 envelope 压测 (2s)", () -> {
            logSink.reset();
            pipelineManager.savePipeline("a20", singleSinkPipeline("log"));
            int count = 500;
            long t0 = System.currentTimeMillis();
            for (int i = 0; i < count; i++) {
                DataEnvelope env = DataEnvelope.builder()
                        .parsed(Map.of("seq", i, "value", "stress-" + i))
                        .pipelineId("a20")
                        .traceId("a20-" + i)
                        .timestamp(System.currentTimeMillis())
                        .build();
                pipelineEngine.execute("a20", env);
            }
            long elapsed = System.currentTimeMillis() - t0;
            int writes = logSink.getCount();
            double throughput = writes * 1000.0 / Math.max(elapsed, 1);
            log.info("[A20] 500 envelopes in {}ms, throughput={} ops/s, writes={}",
                    elapsed, (long) throughput, writes);
            return writes >= count && elapsed < 2000;
        });
    }

    // ━━━━━━━━━━━━━━━ 辅助 ━━━━━━━━━━━━━━━
    /**
     * 构建env。
     * @param pipelineId pipelineId
     * @param traceTag 追踪标签
     * @return 构建env的结果
     */
    private static DataEnvelope buildEnv(String pipelineId, String traceTag) {
        return DataEnvelope.builder()
                .parsed(Map.of("tag", traceTag, "ts", System.currentTimeMillis()))
                .pipelineId(pipelineId)
                .traceId(traceTag + "-" + System.nanoTime())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    private static void check(String name, ProbeCheck check) {
        total++;
        try {
            boolean ok = check.check();
            if (ok) {
                passed++;
                log.info("  ✅ {} PASS", name);
            } else {
                failed++;
                failedCases.add(name);
                log.error("  ❌ {} FAIL", name);
            }
        } catch (Exception e) {
            failed++;
            failedCases.add(name + " (" + e.getMessage() + ")");
            log.error("  ❌ {} ERROR: {}", name, e.getMessage(), e);
        }
    }

    /**
     * 探针检查接口。
     *
     * @author CH
     * @since 4.0.0
     */
    @FunctionalInterface
    interface ProbeCheck {
        boolean check() throws Exception;
    }

    // ━━━━━━━━━━━━━━━ 模拟 Sink ━━━━━━━━━━━━━━━
    /**
     * capturingsink类。
     *
     * @author CH
     * @since 4.0.0
     */
    public static class CapturingSink implements DataSink {
        private final String type; // 类型
        private final AtomicInteger count = new AtomicInteger(0); // 数量
        private volatile Map<String, Object> lastConfig; // 最后一个配置

        /**
        * capturingsink。
        * @param type 类型
         */
        public CapturingSink(String type) { this.type = type; }

        /**
        * reset。
         */
        public void reset() { count.set(0); lastConfig = null; }
        /**
        * 获取数量。
        * @return 获取数量的结果
         */
        public int getCount() { return count.get(); }
        /**
        * 获取最后一个配置。
        * @return 获取最后一个配置的结果
        * @author CH
        * @since 4.0.0
         */
        public Map<String, Object> getLastConfig() { return lastConfig; }

        @Override
        public String type() { return type; }
        @Override
        public void start() {}
        @Override
        public void stop() {}
        @Override
        public boolean write(DataEnvelope envelope, Map<String, Object> config) {
            count.incrementAndGet();
            lastConfig = config;
            return true;
        }
        @Override
        public EngineDataSource<?> getDataSource() { return null; }
    }

    /**
     * 失败sink类。
     *
     * @author CH
     * @since 4.0.0
     */
    public static class FailingSink implements DataSink {
        private final AtomicInteger count = new AtomicInteger(0);

        /**
        * reset。
         */
        public void reset() { count.set(0); }
        /**
        * 获取数量。
        * @return 获取数量的结果
         */
        public int getCount() { return count.get(); }

        @Override
        public String type() { return "failing"; }
        @Override
        public void start() {}
        @Override
        public void stop() {}
        @Override
        public boolean write(DataEnvelope envelope, Map<String, Object> config) {
            count.incrementAndGet();
            throw new RuntimeException("Simulated sink failure for envelope: " + envelope.getTraceId());
        }
        @Override
        public EngineDataSource<?> getDataSource() { return null; }
    }
}
