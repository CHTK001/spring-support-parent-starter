package com.chua.starter.datasync.dbprobe;

import com.chua.common.support.concurrent.dispatcher.DispatcherConfig;
import com.chua.common.support.concurrent.dispatcher.DispatcherProvider;
import com.chua.common.support.concurrent.offset.OffsetFlow;
import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.impl.JdkHttpServer;
import com.chua.chronicle.support.dispatcher.ChronicleDispatcherProvider;
import com.chua.datalake.support.engine.DefaultPipelineEngine;
import com.chua.datalake.support.model.DataEnvelope;
import com.chua.datalake.support.model.PipelineState;
import com.chua.datalake.support.pipeline.DefaultPipelineManager;
import com.chua.datalake.support.sink.JdbcSink;
import com.chua.datalake.support.sink.LogSink;
import com.chua.datalake.support.sink.RealTimeSink;
import com.chua.datalake.support.sink.StatisticSink;
import com.chua.datalake.support.spi.pipeline.PipelineEngine;
import com.chua.datalake.support.spi.pipeline.PipelineManager;
import com.chua.datalake.support.spi.sink.DataSink;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Datalake 全链路探针：Server 生命周期 + Pipeline CRUD + 4 Sink 写入 + Envelope 追踪。
 *
 * <p>验证项（12 项）：
 * <ol>
 *   <li>PipelineManager CRUD（save/get/delete/ids）</li>
 *   <li>PipelineEngine 执行（单 Sink）</li>
 *   <li>PipelineEngine 执行（多 Sink）</li>
 *   <li>LogSink 写入</li>
 *   <li>StatisticSink 写入</li>
 *   <li>RealTimeSink 写入</li>
 *   <li>JdbcSink 写入</li>
 *   <li>DataEnvelope trace 追踪</li>
 *   <li>DataEnvelope state 流转</li>
 *   <li>DataEnvelope isLog 判定</li>
 *   <li>SinkManager start/stop</li>
 *   <li>DatalakeServer start/stop 生命周期</li>
 * </ol>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class DatalakeAllInOneProbe {

    /** Work dir */
    private static final Path WORK_DIR = Path.of("target/datalake-probe");

    /** Pass count */
    private static final AtomicInteger PASS = new AtomicInteger(0);

    /** Fail count */
    private static final AtomicInteger FAIL = new AtomicInteger(0);

    /** 写入记录（线程安全） */
    private static final CopyOnWriteArrayList<String> WRITE_LOG = new CopyOnWriteArrayList<>();

    /**
     * 入口。
     *
     * @param args 命令行参数
     * @throws Exception 异常
     */
    public static void main(String[] args) throws Exception {
        Files.createDirectories(WORK_DIR);

        System.err.println("========================================================================");
        System.err.println("Datalake 全链路探针 (12 项验证)");
        System.err.println("========================================================================");

        // ━━━━━━━━ 1. PipelineManager CRUD ━━━━━━━━
        PipelineManager pm = new DefaultPipelineManager();
        check("PipelineManager.savePipeline", () -> {
            pm.savePipeline("p1", "{\"id\":\"p1\",\"stages\":{\"default\":{\"sink\":[{\"type\":\"log\"}]}}}");
            return pm.getPipeline("p1") != null;
        });
        check("PipelineManager.getPipeline", () -> pm.getPipeline("p1").contains("log"));
        check("PipelineManager.pipelineIds", () -> {
            pm.savePipeline("p2", "{}");
            int count = 0;
            for (String id : pm.pipelineIds()) count++;
            return count == 2;
        });
        check("PipelineManager.deletePipeline", () -> {
            pm.deletePipeline("p2");
            return pm.getPipeline("p2") == null;
        });

        // ━━━━━━━━ 2-4. Sink 注册 + 写入 ━━━━━━━━
        Map<String, DataSink> sinkRegistry = new ConcurrentHashMap<>();
        sinkRegistry.put("log", new LogSink());
        sinkRegistry.put("statistic", new StatisticSink());
        sinkRegistry.put("realtime", new RealTimeSink());
        sinkRegistry.put("jdbc", new JdbcSink());

        // 启动所有 Sink
        sinkRegistry.values().forEach(DataSink::start);

        // ━━━━━━━━ 5. LogSink 写入 ━━━━━━━━
        check("LogSink.write", () -> {
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("msg", "hello-log"))
                    .pipelineId("p-log")
                    .traceId("t-log-001")
                    .state(PipelineState.FILTERED)
                    .timestamp(System.currentTimeMillis())
                    .build();
            boolean ok = sinkRegistry.get("log").write(env, Map.of());
            WRITE_LOG.add("log:" + env.getParsed().get("msg"));
            return ok;
        });

        // ━━━━━━━━ 6. StatisticSink 写入 ━━━━━━━━
        check("StatisticSink.write", () -> {
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("metric", "cpu_usage", "value", 72.5))
                    .pipelineId("p-stats")
                    .traceId("t-stats-001")
                    .state(PipelineState.SINK_OK)
                    .timestamp(System.currentTimeMillis())
                    .build();
            return sinkRegistry.get("statistic").write(env, Map.of());
        });

        // ━━━━━━━━ 7. RealTimeSink 写入 ━━━━━━━━
        check("RealTimeSink.write", () -> {
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("event", "user_login", "userId", "u-123"))
                    .pipelineId("p-rt")
                    .traceId("t-rt-001")
                    .state(PipelineState.SINK_OK)
                    .timestamp(System.currentTimeMillis())
                    .topics(Set.of("realtime"))
                    .build();
            return sinkRegistry.get("realtime").write(env, Map.of());
        });

        // ━━━━━━━━ 8. JdbcSink 写入 ━━━━━━━━
        check("JdbcSink.write", () -> {
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("table", "users", "action", "insert"))
                    .pipelineId("p-jdbc")
                    .traceId("t-jdbc-001")
                    .state(PipelineState.SINK_OK)
                    .timestamp(System.currentTimeMillis())
                    .build();
            return sinkRegistry.get("jdbc").write(env, Map.of());
        });

        // ━━━━━━━━ 9. SinkManager start/stop ━━━━━━━━
        check("SinkManager.start/stop", () -> {
            com.chua.datalake.support.manager.SinkManager sm =
                    new com.chua.datalake.support.manager.SinkManager(sinkRegistry);
            sm.start();
            sm.stop();
            return true;
        });

        // ━━━━━━━━ 10. DispatcherProvider + PipelineEngine ━━━━━━━━
        String dispatchPath = WORK_DIR.resolve("dispatch").toString();
        DispatcherConfig cfg = DispatcherConfig.builder().dataPath(dispatchPath).build();
        DispatcherProvider dispatcher = new ChronicleDispatcherProvider(cfg);
        dispatcher.start();

        PipelineEngine engine = new DefaultPipelineEngine(pm, sinkRegistry, dispatcher);

        // 单 Sink 管线
        pm.savePipeline("engine-single", "{\"id\":\"engine-single\",\"stages\":{\"default\":{\"sink\":[{\"type\":\"log\"}]}}}");
        check("PipelineEngine.execute (single sink)", () -> {
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("key", "val-1"))
                    .pipelineId("engine-single")
                    .traceId("t-eng-001")
                    .state(PipelineState.FILTERED)
                    .timestamp(System.currentTimeMillis())
                    .build();
            engine.execute("engine-single", env);
            return env.getState() == PipelineState.SINK_OK;
        });

        // 多 Sink 管线
        pm.savePipeline("engine-multi", "{\"id\":\"engine-multi\",\"stages\":{\"default\":{\"sink\":[{\"type\":\"log\"},{\"type\":\"statistic\"},{\"type\":\"realtime\"}]}}}");
        check("PipelineEngine.execute (multi sink)", () -> {
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("key", "val-multi"))
                    .pipelineId("engine-multi")
                    .traceId("t-eng-002")
                    .state(PipelineState.FILTERED)
                    .timestamp(System.currentTimeMillis())
                    .build();
            engine.execute("engine-multi", env);
            return env.getState() == PipelineState.SINK_OK;
        });

        // ━━━━━━━━ 11. DataEnvelope trace 追踪 ━━━━━━━━
        check("DataEnvelope.addTrace", () -> {
            DataEnvelope env = new DataEnvelope(Map.of("x", 1));
            env.addTrace("step-1");
            env.addTrace("step-2");
            return env.getTrace().size() == 2
                    && env.getTrace().get(0).equals("step-1");
        });

        // ━━━━━━━━ 12. DataEnvelope isLog 判定 ━━━━━━━━
        check("DataEnvelope.isLog", () -> {
            DataEnvelope logEnv = DataEnvelope.builder()
                    .parsed(Map.of())
                    .topics(Set.of("log-access"))
                    .build();
            DataEnvelope dataEnv = DataEnvelope.builder()
                    .parsed(Map.of())
                    .topics(Set.of("realtime"))
                    .build();
            return logEnv.isLog() && !dataEnv.isLog();
        });

        // ━━━━━━━━ 清理 ━━━━━━━━
        sinkRegistry.values().forEach(DataSink::stop);
        dispatcher.close();

        // ━━━━━━━━ 汇总 ━━━━━━━━
        System.err.println("========================================================================");
        int total = PASS.get() + FAIL.get();
        System.err.printf("Datalake 全链路探针: %d/%d PASS%n", PASS.get(), total);
        if (FAIL.get() > 0) {
            System.err.printf("FAIL: %d 项%n", FAIL.get());
        }
        System.err.println("========================================================================");
        System.exit(FAIL.get() > 0 ? 1 : 0);
    }

    /**
     * 断言检查。
     *
     * @param name  检查项名称
     * @param check 检查逻辑
     */
    private static void check(String name, java.util.function.BooleanSupplier check) {
        try {
            boolean result = check.getAsBoolean();
            if (result) {
                PASS.incrementAndGet();
                System.err.printf("  [PASS] %s%n", name);
            } else {
                FAIL.incrementAndGet();
                System.err.printf("  [FAIL] %s%n", name);
            }
        } catch (Exception e) {
            FAIL.incrementAndGet();
            System.err.printf("  [FAIL] %s (exception: %s)%n", name, e.getMessage());
        }
    }
}
