// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datalake.probe;

import com.chua.common.support.concurrent.dispatcher.DispatcherConfig;
import com.chua.common.support.concurrent.dispatcher.DispatcherProvider;
import com.chua.common.support.lang.datasource.engine.EngineDataSource;
import com.chua.chronicle.support.dispatcher.ChronicleDispatcherProvider;
import com.chua.datalake.support.engine.DefaultPipelineEngine;
import com.chua.datalake.support.model.DataEnvelope;
import com.chua.datalake.support.pipeline.DefaultPipelineManager;
import com.chua.datalake.support.spi.pipeline.PipelineEngine;
import com.chua.datalake.support.spi.pipeline.PipelineManager;
import com.chua.datalake.support.spi.sink.DataSink;
import com.chua.datalake.support.webhook.WebhookInboundAdapter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
/**
   * 数据湖perf探针类。
   *
   * @author CH
   * @since 4.0.0
   */

@Slf4j
public class DatalakePerfProbe {

    private static final Map<String, Object> RESULT = new LinkedHashMap<>(); // 结果

      /**
       * main。
       * @param args 参数
       */
    public static void main(String[] args) throws Exception {
        long globalStart = System.currentTimeMillis();
        System.err.println("============================================================="); // [P3C E1 豁免]
        System.err.println("  Datalake Performance Benchmark (DatalakePerfProbe)"); // [P3C E1 豁免]
        System.err.println("============================================================="); // [P3C E1 豁免]

        PipelineManager pm = new DefaultPipelineManager();
        Map<String, DataSink> sinkRegistry = new ConcurrentHashMap<>();
        sinkRegistry.put("log", new CapturingSink("log"));
        sinkRegistry.put("statistic", new CapturingSink("statistic"));
        sinkRegistry.put("realtime", new CapturingSink("realtime"));
        sinkRegistry.values().forEach(DataSink::start);

        String dispatchPath = System.getProperty("java.io.tmpdir") + "/datalake-perf-dispatch";
        DispatcherConfig dcfg = DispatcherConfig.builder().dataPath(dispatchPath).build();
        DispatcherProvider dispatcher = new ChronicleDispatcherProvider(dcfg);
        dispatcher.start();

        PipelineEngine engine = new DefaultPipelineEngine(pm, sinkRegistry, dispatcher);
        pm.savePipeline("p-single", "{\"stages\":{\"default\":{\"sink\":[{\"type\":\"log\"}]}}}");
        pm.savePipeline("p-multi",  "{\"stages\":{\"default\":{\"sink\":[{\"type\":\"log\"},{\"type\":\"statistic\"},{\"type\":\"realtime\"}]}}}");
        pm.savePipeline("p-concurrent", "{\"stages\":{\"default\":{\"sink\":[{\"type\":\"log\"}]}}}");

        System.err.printf("%-4s  %-26s  %-8s  %-10s  %-12s  %-14s  %s%n", // [P3C E1 豁免]
                "#", "Scenario", "Count", "Time(ms)", "Ops/s", "Details", "Status");
        System.err.println("--------------------------------------------------------------"); // [P3C E1 豁免]

        int ni = 0;
   // Sequential 吞吐量
        benchSeq(engine, "p-single", "Sequential 1K log", 1_000);
        benchSeq(engine, "p-single", "Sequential 10K log", 10_000);
        benchSeq(engine, "p-single", "Sequential 100K log", 100_000);
        benchSeq(engine, "p-single", "Sequential 1M log", 1_000_000);
        benchSeq(engine, "p-multi", "Sequential 100K 3-sink", 100_000);
        benchSeq(engine, "p-multi", "Sequential 1M 3-sink", 1_000_000);

   // 延迟 distribution
        benchLatency(engine, "p-single", "Latency p50/p95/p99/max", 100_000);

          // Engine overhead
        benchOverhead(pm, sinkRegistry);

   // Webhook HTTP 吞吐量
        benchWebhook(engine);

   // Multi-thread 并发
        benchConcurrent(engine, "p-concurrent", "16 threads x 100K", 16, 100_000);
        benchConcurrent(engine, "p-concurrent", "32 threads x 100K", 32, 100_000);
        benchConcurrent(engine, "p-concurrent", "64 threads x 100K", 64, 100_000);

          // Multi-pipeline scaling
        benchMultiPipeline(engine, pm);

          // Dispatcher overhead
        benchDispatcher(engine, pm, sinkRegistry, dispatcher);

        System.err.println("--------------------------------------------------------------"); // [P3C E1 豁免]
        double totalSec = (System.currentTimeMillis() - globalStart) / 1000.0;
        System.err.printf("%nTotal time: %.2f seconds%n%n", totalSec); // [P3C E1 豁免]
        for (Map.Entry<String, Object> e : RESULT.entrySet()) {
            System.err.printf("  %-32s => %s%n", e.getKey(), e.getValue()); // [P3C E1 豁免]
        }
        System.err.println("============================================================="); // [P3C E1 豁免]

        sinkRegistry.values().forEach(DataSink::stop);
        dispatcher.close();
    }

   // ── Sequential 吞吐量 ──────────────────────────────────────

      /**
       * benchseq。
       * @param engine engine
       * @param pipeId 管道标识
       * @param label 标签
       * @param count 数量
       */
    private static void benchSeq(PipelineEngine engine, String pipeId, String label, int count) {
        long t0 = System.nanoTime();
        for (int i = 0; i < count; i++) {
            engine.execute(pipeId, buildEnv(pipeId, i));
        }
        double ms = (System.nanoTime() - t0) / 1_000_000.0;
        double ops = count / (ms / 1000.0);
        String status = ops >= 100_000 ? "FAST" : "OK";
        System.err.printf("%-4d  %-26s  %-8d  %-10.1f  %-12.0f  %-14s  %s%n", // [P3C E1 豁免]
                RESULT.size() + 1, label, count, ms, ops, "", status);
        RESULT.put(label, String.format("%.0f ops/s (%.1f ms for %d ops)", ops, ms, count));
    }

   // ── 延迟 Distribution ───────────────────────────────────────

      /**
       * bench延迟。
       * @param engine engine
       * @param pipeId 管道标识
       * @param label 标签
       * @param count 数量
       */
    private static void benchLatency(PipelineEngine engine, String pipeId, String label, int count) {
        long[] lat = new long[count];
        long t0 = System.nanoTime();
        for (int i = 0; i < count; i++) {
            long s = System.nanoTime();
            engine.execute(pipeId, buildEnv(pipeId, i));
            lat[i] = System.nanoTime() - s;
        }
        double ms = (System.nanoTime() - t0) / 1_000_000.0;
        Arrays.sort(lat);
        long p50 = lat[count * 50 / 100];
        long p95 = lat[count * 95 / 100];
        long p99 = lat[count * 99 / 100];
        long max = lat[count - 1];
        double ops = count / (ms / 1000.0);
        String detail = String.format("p50=%dus p95=%dus p99=%dus max=%dus", p50 / 1000, p95 / 1000, p99 / 1000, max / 1000);
        System.err.printf("%-4d  %-26s  %-8d  %-10.1f  %-12.0f  %-14s  %s%n", // [P3C E1 豁免]
                RESULT.size() + 1, label, count, ms, ops, detail, "OK");
        RESULT.put(label, String.format("p50=%.1fus p95=%.1fus p99=%.1fus max=%.1fus | %.0f ops/s",
                p50 / 1000.0, p95 / 1000.0, p99 / 1000.0, max / 1000.0, ops));
    }

      // ── Engine Overhead vs Direct Sink ─────────────────────────────

      /**
       * benchoverhead。
       * @param pm pm
       * @param reg reg
       */
    private static void benchOverhead(PipelineManager pm, Map<String, DataSink> reg) {
        DataSink logSink = reg.get("log");
        int count = 100_000;

        long t0 = System.nanoTime();
        for (int i = 0; i < count; i++) {
            logSink.write(buildEnv("direct", i), Collections.emptyMap());
        }
        double directMs = (System.nanoTime() - t0) / 1_000_000.0;
        double directOps = count / (directMs / 1000.0);

        pm.savePipeline("p-overhead", "{\"stages\":{\"default\":{\"sink\":[{\"type\":\"log\"}]}}}");
        PipelineEngine engine = new DefaultPipelineEngine(pm, reg, null);
        t0 = System.nanoTime();
        for (int i = 0; i < count; i++) {
            engine.execute("p-overhead", buildEnv("p-overhead", i));
        }
        double engineMs = (System.nanoTime() - t0) / 1_000_000.0;
        double engineOps = count / (engineMs / 1000.0);
        double overhead = ((engineMs - directMs) * 100.0) / Math.max(directMs, 0.001);

        System.err.printf("%-4d  %-26s  %-8d  %-10.1f  %-12.0f  %-14s  %s%n", // [P3C E1 豁免]
                RESULT.size() + 1, "Engine vs Direct", count, engineMs, engineOps,
                String.format("Direct=%.0f Overhead=%.1f%%", directOps, overhead), overhead < 50 ? "OK" : "WARN");
        RESULT.put("PipelineEngine overhead", String.format("Direct=%.0f/s Engine=%.0f/s Overhead=%.1f%%",
                directOps, engineOps, overhead));
    }

   // ── Webhook HTTP 吞吐量 ────────────────────────────────────

      /**
       * benchwebhook。
       * @param engine engine
       */
    private static void benchWebhook(PipelineEngine engine) throws Exception {
        int[] counts = {5_000, 50_000, 100_000};
        for (int count : counts) {
            WebhookInboundAdapter adapter = WebhookInboundAdapter.builder()
                    .port(8800).pipelineEngine(engine).build();
            adapter.start();
            Thread.sleep(200);
            long t0 = System.nanoTime();
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            for (int i = 0; i < count; i++) {
                java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("http://localhost:8800/api/datalake/webhook/bench"))
                        .header("Content-Type", "application/json")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"seq\":" + i + "}"))
                        .build();
                client.send(req, java.net.http.HttpResponse.BodyHandlers.discarding());
            }
            double ms = (System.nanoTime() - t0) / 1_000_000.0;
            double ops = count / (ms / 1000.0);
            adapter.stop();
            System.err.printf("%-4d  %-26s  %-8d  %-10.1f  %-12.0f  %-14s  %s%n", // [P3C E1 豁免]
                    RESULT.size() + 1, "Webhook HTTP", count, ms, ops, "", ops > 2000 ? "FAST" : "OK");
            RESULT.put("Webhook HTTP " + fmtCount(count), String.format("%.0f req/s", ops));
        }
    }

   // ── Multi-thread 并发 ────────────────────────────────────

      /**
       * bench并发。
       * @param engine engine
       * @param pipeId 管道标识
       * @param label 标签
       * @param threads threads
       * @param totalOps totalops
       */
    private static void benchConcurrent(PipelineEngine engine, String pipeId, String label, int threads, int totalOps
        ) throws InterruptedException {
        int perThread = totalOps / threads;
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicLong writes = new AtomicLong(0);
        long t0 = System.nanoTime();
        for (int t = 0; t < threads; t++) {
            final int tid = t;
// [P3C C1 豁免] 场景化创建线程，需文档化
            Executors.newSingleThreadExecutor().execute(() -> {
                for (int i = 0; i < perThread; i++) {
                    engine.execute(pipeId, buildEnv(pipeId, tid * perThread + i));
                    writes.incrementAndGet();
                }
                latch.countDown();
            });
        }
        boolean ok = latch.await(30, TimeUnit.SECONDS);
        double ms = (System.nanoTime() - t0) / 1_000_000.0;
        double ops = writes.get() / (ms / 1000.0);
        System.err.printf("%-4d  %-26s  %-8d  %-10.1f  %-12.0f  %-14s  %s%n", // [P3C E1 豁免]
                RESULT.size() + 1, label, totalOps, ms, ops, threads + " threads", ok ? "OK" : "TIMEOUT");
        RESULT.put(label, String.format("%.0f ops/s (%d threads)", ops, threads));
    }

      // ── Multi-pipeline Scaling ─────────────────────────────────────

      /**
       * benchmultipipeline。
       * @param engine engine
       * @param pm pm
       */
    private static void benchMultiPipeline(PipelineEngine engine, PipelineManager pm) throws InterruptedException {
        int[] pipelineCounts = {1, 10, 50};
        int opsPerPipeline = 10_000;
        int threads = 10;
        for (int pc : pipelineCounts) {
            for (int i = 0; i < pc; i++) {
                pm.savePipeline("mp-" + i, "{\"stages\":{\"default\":{\"sink\":[{\"type\":\"log\"}]}}}");
            }
            CountDownLatch latch = new CountDownLatch(threads);
            AtomicLong totalWrites = new AtomicLong(0);
            long t0 = System.nanoTime();
            for (int t = 0; t < threads; t++) {
                final int tid = t;
// [P3C C1 豁免] 场景化创建线程，需文档化
                Executors.newSingleThreadExecutor().execute(() -> {
                    for (int i = 0; i < opsPerPipeline; i++) {
                        int pid = (tid * opsPerPipeline + i) % pc;
                        engine.execute("mp-" + pid, buildEnv("mp-" + pid, i));
                        totalWrites.incrementAndGet();
                    }
                    latch.countDown();
                });
            }
            boolean ok = latch.await(30, TimeUnit.SECONDS);
            double ms = (System.nanoTime() - t0) / 1_000_000.0;
            double ops = totalWrites.get() / (ms / 1000.0);
            System.err.printf("%-4d  %-26s  %-8d  %-10.1f  %-12.0f  %-14s  %s%n", // [P3C E1 豁免]
                    RESULT.size() + 1, pc + " pipelines concurrent", opsPerPipeline * threads, ms, ops,
                    pc + " pipelines x " + opsPerPipeline + " ops", ok ? "OK" : "TIMEOUT");
            RESULT.put("Multi-pipeline " + pc + " pipelines", String.format("%.0f ops/s (%d pipelines)", ops, pc));
        }
    }

      // ── Dispatcher Overhead ────────────────────────────────────────

    private static void benchDispatcher(PipelineEngine engine, PipelineManager pm,
                                         Map<String, DataSink> reg, DispatcherProvider dispatcher) {
        int count = 100_000;
        PipelineEngine noDisp = new DefaultPipelineEngine(pm, reg, null);
        pm.savePipeline("p-nodisp", "{\"stages\":{\"default\":{\"sink\":[{\"type\":\"log\"}]}}}");
        long t0 = System.nanoTime();
        for (int i = 0; i < count; i++) {
            noDisp.execute("p-nodisp", buildEnv("p-nodisp", i));
        }
        double noDispMs = (System.nanoTime() - t0) / 1_000_000.0;
        double noDispOps = count / (noDispMs / 1000.0);

        pm.savePipeline("p-withdisp", "{\"stages\":{\"default\":{\"sink\":[{\"type\":\"log\"}]}}}");
        t0 = System.nanoTime();
        for (int i = 0; i < count; i++) {
            engine.execute("p-withdisp", buildEnv("p-withdisp", i));
        }
        double withDispMs = (System.nanoTime() - t0) / 1_000_000.0;
        double withDispOps = count / (withDispMs / 1000.0);
        double ratio = withDispMs * 100.0 / Math.max(noDispMs, 0.001);

        System.err.printf("%-4d  %-26s  %-8d  %-10.1f  %-12.0f  %-14s  %s%n", // [P3C E1 豁免]
                RESULT.size() + 1, "With Dispatcher", count, withDispMs, withDispOps,
                String.format("NoDisp=%.0f WithDisp=%.0f Ratio=%.1f%%", noDispOps, withDispOps, ratio),
                ratio < 3 ? "OK" : "WARN");
        RESULT.put("Dispatcher overhead", String.format("NoDisp=%.0f/s WithDisp=%.0f/s Ratio=%.1f%%",
                noDispOps, withDispOps, ratio));
    }

   // ── 助手 ────────────────────────────────────────────────────
      /**
       * 构建env。
       * @param pipeId 管道标识
       * @param i i
       * @return 构建env的结果
       */
    private static DataEnvelope buildEnv(String pipeId, int i) {
        return DataEnvelope.builder()
                .parsed(Map.of("i", i, "v", "bench", "ts", System.currentTimeMillis()))
                .pipelineId(pipeId)
                .traceId(pipeId + "-" + i)
                .timestamp(System.currentTimeMillis())
                .build();
    }

      /**
       * 格式化数量为 K/M 缩写。
       * @param n 数量
       * @return 格式化后的数量
       */
    private static String fmtCount(int n) {
        if (n >= 1_000_000) {
            return (n / 1_000_000) + "M";
        }
        if (n >= 1_000) {
            return (n / 1_000) + "K";
        }
        return String.valueOf(n);
    }

      /**
       * capturingsink类。
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
           */
        public int getCount() { return count.get(); }
        @Override public String type() { return type; }
        @Override public void start() {}
        @Override public void stop() {}
        @Override public boolean write(DataEnvelope env, Map<String, Object> config) {
            count.incrementAndGet();
            return true;
        }
        @Override public EngineDataSource<?> getDataSource() { return null; }    }}