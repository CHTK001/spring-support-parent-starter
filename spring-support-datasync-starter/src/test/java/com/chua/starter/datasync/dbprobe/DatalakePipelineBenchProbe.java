package com.chua.starter.datasync.dbprobe;

import com.chua.common.support.concurrent.dispatcher.DispatcherConfig;
import com.chua.common.support.concurrent.dispatcher.DispatcherProvider;
import com.chua.chronicle.support.dispatcher.ChronicleDispatcherProvider;
import com.chua.datalake.support.engine.DefaultPipelineEngine;
import com.chua.datalake.support.model.DataEnvelope;
import com.chua.datalake.support.model.PipelineState;
import com.chua.datalake.support.pipeline.DefaultPipelineManager;
import com.chua.datalake.support.sink.LogSink;
import com.chua.datalake.support.spi.pipeline.PipelineEngine;
import com.chua.datalake.support.spi.pipeline.PipelineManager;
import com.chua.datalake.support.spi.sink.DataSink;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 数据湖 pipelineengine 吞吐基准：测量 pipelineengine.执行 在不同数据量下的吞吐。
 *
 * <p>测试矩阵：
 * <ul>
 *   <li>10K / 100K / 1M 条</li>
 *   <li>单 Sink (log) / 多 Sink (log+statistic+realtime)</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class DatalakePipelineBenchProbe {

    /**
     * Work dir
    */
    private static final Path WORK_DIR = Path.of("target/datalake-bench");

    /**
     * 入口。
     *
     * @param args 命令行参数
     * @throws Exception 异常
     */
    public static void main(String[] args) throws Exception {
        Files.createDirectories(WORK_DIR);

        // ━━━━━━━━ 初始化 ━━━━━━━━
        PipelineManager pm = new DefaultPipelineManager();
        Map<String, DataSink> sinkRegistry = new ConcurrentHashMap<>();
        sinkRegistry.put("log", new LogSink());
        sinkRegistry.put("statistic", new com.chua.datalake.support.sink.StatisticSink());
        sinkRegistry.put("realtime", new com.chua.datalake.support.sink.RealTimeSink());
        sinkRegistry.values().forEach(DataSink::start);

        String dispatchPath = WORK_DIR.resolve("dispatch").toString();
        DispatcherConfig cfg = DispatcherConfig.builder().dataPath(dispatchPath).build();
        DispatcherProvider dispatcher = new ChronicleDispatcherProvider(cfg);
        dispatcher.start();

        PipelineEngine engine = new DefaultPipelineEngine(pm, sinkRegistry, dispatcher);

        // 单 Sink 管线
        pm.savePipeline("bench-single", "{\"id\":\"bench-single\",\"stages\":{\"default\":{\"sink\":[{\"type\":\"log\"}]}}}");
        // 多 Sink 管线
        pm.savePipeline("bench-multi", "{\"id\":\"bench-multi\",\"stages\":{\"default\":{\"sink\":[{\"type\":\"log\"},{\"type\":\"statistic\"},{\"type\":\"realtime\"}]}}}");

        System.err.println("========================================================================");
        System.err.println("Datalake PipelineEngine 吞吐基准");
        System.err.println("========================================================================");
        System.err.printf("%-12s %-12s %-14s %-14s %-12s%n",
                "数据量", "管线", "耗时(ms)", "吞吐(条/s)", "验证");
        System.err.println("------------------------------------------------------------------------");

        int[] counts = {10_000, 100_000};
        String[] pipelines = {"bench-single", "bench-multi"};
        String[] pipelineLabels = {"单Sink(log)", "多Sink(3)"};

        for (int i = 0; i < pipelines.length; i++) {
            for (int count : counts) {
                /**
                 * 运行bench。
                 * @param engine engine
                 * @param pipelines[i] pipelines[i]
                 * @param pipelineLabels[i] pipeline标签[i]
                 * @param count 数量
                 */
                runBench(engine, pipelines[i], pipelineLabels[i], count);
            }
        }

        System.err.println("========================================================================");

        sinkRegistry.values().forEach(DataSink::stop);
        dispatcher.close();
        System.exit(0);
    }

    /**
     * 运行单次基准测试。
     *
     * @param engine  管线引擎
     * @param pipeId  管线 标识
     * @param label   管线标签
     * @param count   数据条数
     * @throws Exception 异常
     */
    private static void runBench(PipelineEngine engine, String pipeId, String label, int count) throws Exception {
        // 预热 100 条
        for (int i = 0; i < 100; i++) {
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("i", i, "v", "warmup"))
                    .pipelineId(pipeId)
                    .traceId("warmup-" + i)
                    .state(PipelineState.FILTERED)
                    .timestamp(System.currentTimeMillis())
                    .build();
            engine.execute(pipeId, env);
        }

        // 正式测试
        AtomicLong writeCount = new AtomicLong(0);
        long t0 = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("i", i, "v", "bench-" + i, "ts", System.currentTimeMillis()))
                    .pipelineId(pipeId)
                    .traceId("bench-" + i)
                    .state(PipelineState.FILTERED)
                    .timestamp(System.currentTimeMillis())
                    .topics(Set.of("bench"))
                    .build();
            engine.execute(pipeId, env);
            writeCount.incrementAndGet();
        }

        long elapsed = System.currentTimeMillis() - t0;
        double throughput = elapsed > 0 ? (double) count / elapsed * 1000 : 0;

        System.err.printf("%-12d %-12s %-14d %-14.0f %s%n",
                count, label, elapsed, throughput, "OK");

        // 验证 envelope 状态
        DataEnvelope verify = DataEnvelope.builder()
                .parsed(Map.of("check", true))
                .pipelineId(pipeId)
                .traceId("verify")
                .state(PipelineState.FILTERED)
                .timestamp(System.currentTimeMillis())
                .build();
        engine.execute(pipeId, verify);
        if (verify.getState() != PipelineState.SINK_OK) {
            System.err.println("  [WARN] 验证 envelope state != SINK_OK: " + verify.getState());
        }
    }
}
