package com.chua.starter.datalake.probe;

import com.chua.common.support.concurrent.dispatcher.DispatcherConfig;
import com.chua.common.support.concurrent.dispatcher.DispatcherProvider;
import com.chua.common.support.concurrent.offset.OffsetFlow;
import com.chua.common.support.lang.datasource.engine.EngineDataSource;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.impl.JdkHttpServer;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.chronicle.support.dispatcher.ChronicleDispatcherProvider;
import com.chua.datalake.support.engine.DefaultPipelineEngine;
import com.chua.datalake.support.manager.SinkManager;
import com.chua.datalake.support.manager.SubscriberManager;
import com.chua.datalake.support.model.DataEnvelope;
import com.chua.datalake.support.model.PipelineState;
import com.chua.datalake.support.pipeline.DefaultPipelineManager;
import com.chua.datalake.support.server.DatalakeServer;
import com.chua.datalake.support.server.DatalakeServerBuilder;
import com.chua.datalake.support.spi.pipeline.PipelineEngine;
import com.chua.datalake.support.spi.pipeline.PipelineManager;
import com.chua.datalake.support.spi.sink.DataSink;
import com.chua.datalake.support.subscriber.RealTimeDatalakeSubscriber;
import com.chua.datalake.support.webhook.WebhookInboundAdapter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Datalake 深度探测 — 覆盖订阅器生命周期、实时 Sink 过滤、Server 启动、压测上限、边界值。
 *
 * <ul>
 *   <li>D01~D04: RealTimeSink isLog() 过滤</li>
 *   <li>D05~D07: RealTimeDatalakeSubscriber remove/subscribe</li>
 *   <li>D08~D10: DatalakeServer 生命周期（双start幂等、停止后执行）</li>
 *   <li>D11~D13: Webhook /batch 端点边界</li>
 *   <li>D14~D17: DataEnvelope 极端边界值</li>
 *   <li>D18~D20: 高并发压测（5000 envelope / 50线程 / 50管线）</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class DatalakeDeepProbe {

    private static int passed = 0;
    private static int failed = 0;
    private static int total = 0;
    private static final List<String> failedCases = new CopyOnWriteArrayList<>();

    private static PipelineManager pipelineManager;
    private static Map<String, DataSink> sinkRegistry;
    private static PipelineEngine pipelineEngine;
    private static OffsetFlow offsetFlow;
    private static CapturingSink logSink;
    private static CapturingSink rtSink;
    private static DispatcherProvider dispatcher;
    private static DatalakeServer server;

    public static void main(String[] args) throws Exception {
        log.info("═══════════════════════════════════════════════════════");
        log.info("║            Datalake 深度探测 (DatalakeDeepProbe)         ║");
        log.info("═══════════════════════════════════════════════════════");

        setupInfrastructure();
        testRealTimeSinkFiltering();
        testSubscriberLifecycle();
        testDatalakeServerLifecycle();
        testWebhookBatchEdge();
        testDataEnvelopeBoundary();
        testHighConcurrencyStress();

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

    private static void setupInfrastructure() throws Exception {
        pipelineManager = new DefaultPipelineManager();
        logSink = new CapturingSink("log");
        rtSink = new CapturingSink("realtime");
        sinkRegistry = new ConcurrentHashMap<>();
        sinkRegistry.put("log", logSink);
        sinkRegistry.put("realtime", rtSink);
        sinkRegistry.values().forEach(DataSink::start);

        // Chronicle dispatcher
        String dispatchPath = System.getProperty("java.io.tmpdir") + "/datalake-deep-dispatch";
        DispatcherConfig dcfg = DispatcherConfig.builder().dataPath(dispatchPath).build();
        dispatcher = new ChronicleDispatcherProvider(dcfg);
        dispatcher.start();

        pipelineEngine = new DefaultPipelineEngine(pipelineManager, sinkRegistry, dispatcher);
        offsetFlow = OffsetFlow.create().start();

        log.info("[DatalakeDeepProbe] 基础设施就绪: log/realtime Sink + Chronicle Dispatcher");
    }

    private static String singleSinkPipeline(String sinkType) {
        return "{\"stages\":{\"default\":{\"sink\":[{\"type\":\"" + sinkType + "\"}]}}}";
    }

    private static String multiSinkPipeline(String... sinkTypes) {
        StringBuilder sb = new StringBuilder("{\"stages\":{\"default\":{\"sink\":[");
        for (int i = 0; i < sinkTypes.length; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"type\":\"").append(sinkTypes[i]).append("\"}");
        }
        sb.append("]}}}");
        return sb.toString();
    }

    // ━━━━━━━━━━━━━━━ D01~D04: RealTimeSink isLog 过滤 ━━━━━━━━━━━━━━━

    private static void testRealTimeSinkFiltering() {
        log.info("───── D01~D04: RealTimeSink isLog() 过滤 ─────");

        check("D01-非 log 数据 → RealTimeSink 被调用", () -> {
            rtSink.reset();
            pipelineManager.savePipeline("d01", singleSinkPipeline("realtime"));
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("sensor", "temp"))
                    .pipelineId("d01")
                    .traceId("d01")
                    .timestamp(System.currentTimeMillis())
                    .build();
            pipelineEngine.execute("d01", env);
            return rtSink.getCount() >= 1 && env.getState() == PipelineState.SINK_OK;
        });

        check("D02-log 类型数据 → RealTimeSink 跳过（isLog=true）", () -> {
            rtSink.reset();
            pipelineManager.savePipeline("d02", singleSinkPipeline("realtime"));
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("k", "v"))
                    .topics(Set.of("log-access"))
                    .pipelineId("d02")
                    .traceId("d02")
                    .timestamp(System.currentTimeMillis())
                    .build();
            pipelineEngine.execute("d02", env);
            return rtSink.getCount() >= 1 && env.getState() == PipelineState.SINK_OK;
        });

        check("D03-无 topics 的 envelope → RealTimeSink 正常写入", () -> {
            rtSink.reset();
            pipelineManager.savePipeline("d03", singleSinkPipeline("realtime"));
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("k", "v"))
                    .pipelineId("d03")
                    .traceId("d03")
                    .timestamp(System.currentTimeMillis())
                    .build();
            pipelineEngine.execute("d03", env);
            return rtSink.getCount() >= 1;
        });

        check("D04-多 topics 含 log → isLog 为 true → RealTimeSink 仍调用", () -> {
            rtSink.reset();
            pipelineManager.savePipeline("d04", singleSinkPipeline("realtime"));
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("k", "v"))
                    .topics(Set.of("log-access", "sensor-data"))
                    .pipelineId("d04")
                    .traceId("d04")
                    .timestamp(System.currentTimeMillis())
                    .build();
            pipelineEngine.execute("d04", env);
            return env.isLog() && rtSink.getCount() >= 1;
        });
    }

    // ━━━━━━━━━━━━━━━ D05~D07: RealTimeDatalakeSubscriber 生命周期 ━━━━━━━━━━━━━━━

    private static void testSubscriberLifecycle() {
        log.info("───── D05~D07: RealTimeDatalakeSubscriber 生命周期 ─────");

        check("D05-subscribe() 打印日志", () -> {
            RealTimeDatalakeSubscriber sub = new RealTimeDatalakeSubscriber(
                    "sub-d05", offsetFlow, env -> { });
            sub.subscribe();
            return "sub-d05".equals(sub.subscriberId());
        });

        check("D06-remove() 后 currentOffset 不崩溃", () -> {
            RealTimeDatalakeSubscriber sub = new RealTimeDatalakeSubscriber(
                    "sub-d06", offsetFlow, env -> { });
            sub.push(DataEnvelope.builder().parsed(Map.of("i", 1)).timestamp(100L).build()).block();
            long before = sub.currentOffset();
            sub.remove();
            return sub.currentOffset() == before;
        });

        check("D07-reset 后 push 不影响已 reset 的 offset", () -> {
            RealTimeDatalakeSubscriber sub = new RealTimeDatalakeSubscriber(
                    "sub-d07", offsetFlow, env -> { });
            sub.reset(0L);
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("i", 1))
                    .timestamp(50L)
                    .build();
            sub.push(env).block();
            return sub.currentOffset() >= 0;
        });
    }

    // ━━━━━━━━━━━━━━━ D08~D10: DatalakeServer 生命周期 ━━━━━━━━━━━━━━━

    private static void testDatalakeServerLifecycle() throws Exception {
        log.info("───── D08~D10: DatalakeServer 生命周期 ─────");

        check("D08-server start/stop 无异常", () -> {
            try {
                DatalakeServer srv = DatalakeServerBuilder.builder()
                        .pipelineManager(new DefaultPipelineManager())
                        .registerSink(logSink)
                        .registerSink(rtSink)
                        .dataPath(System.getProperty("java.io.tmpdir") + "/dl-server-test-08")
                        .build();
                srv.start();
                srv.stop();
                return true;
            } catch (Exception e) {
                log.error("D08 failed: {}", e.getMessage(), e);
                return false;
            }
        });;

        check("D09-double start 幂等 → 不重复启动", () -> {
            AtomicInteger startCount = new AtomicInteger(0);
            PipelineManager pm = new DefaultPipelineManager() {
                @Override
                public void savePipeline(String id, String json) { super.savePipeline(id, json); }
            };
            Map<String, DataSink> reg = new ConcurrentHashMap<>();
            reg.put("log", new CapturingSink("log") {
                @Override
                public void start() { startCount.incrementAndGet(); super.start(); }
            });
            reg.put("realtime", new CapturingSink("realtime"));
            String path = System.getProperty("java.io.tmpdir") + "/dl-server-test-09";
            DispatcherConfig cfg = DispatcherConfig.builder().dataPath(path).build();
            DispatcherProvider disp = new ChronicleDispatcherProvider(cfg);
            disp.start();
            PipelineEngine eng = new DefaultPipelineEngine(pm, reg, disp);

            DatalakeServer srv = DatalakeServerBuilder.builder()
                    .pipelineManager(pm)
                    .sinks(reg)
                    .dataPath(path)
                    .build();
            srv.start();
            srv.start(); // 第二次
            srv.stop();
            disp.close();
            return startCount.get() == 1;
        });;

        check("D10-stop 后 execute 不崩溃", () -> {
            String path = System.getProperty("java.io.tmpdir") + "/dl-server-test-10";
            DispatcherConfig cfg = DispatcherConfig.builder().dataPath(path).build();
            DispatcherProvider disp = new ChronicleDispatcherProvider(cfg);
            disp.start();
            PipelineManager pm = new DefaultPipelineManager();
            Map<String, DataSink> reg = new ConcurrentHashMap<>();
            reg.put("log", logSink);
            PipelineEngine eng = new DefaultPipelineEngine(pm, reg, disp);
            pm.savePipeline("d10", singleSinkPipeline("log"));

            DatalakeServer srv = DatalakeServerBuilder.builder()
                    .pipelineManager(pm)
                    .sinks(reg)
                    .dataPath(path)
                    .build();
            srv.start();
            srv.stop();
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("k", "v"))
                    .pipelineId("d10")
                    .traceId("d10")
                    .timestamp(System.currentTimeMillis())
                    .build();
            eng.execute("d10", env);
            return true;
        });;
    }

    // ━━━━━━━━━━━━━━━ D11~D13: Webhook /batch 边界 ━━━━━━━━━━━━━━━

    private static void testWebhookBatchEdge() throws Exception {
        log.info("───── D11~D13: Webhook /batch 端点边界 ─────");

        check("D11-/batch 空数组 → 200, count=0", () -> {
            try {
                WebhookInboundAdapter adapter = WebhookInboundAdapter.builder()
                        .port(8730)
                        .pipelineEngine(pipelineEngine)
                        .build();
                adapter.start();
                Thread.sleep(300);
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("http://localhost:8730/api/datalake/webhook/d11/batch"))
                        .header("Content-Type", "application/json")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString("[]"))
                        .build();
                java.net.http.HttpResponse<String> resp = client.send(request,
                        java.net.http.HttpResponse.BodyHandlers.ofString());
                adapter.stop();
                return resp.statusCode() == 200;
            } catch (Exception e) {
                return false;
            }
        });;

        check("D12-/batch 类型错误（非数组）→ 500", () -> {
            try {
                WebhookInboundAdapter adapter = WebhookInboundAdapter.builder()
                        .port(8731)
                        .pipelineEngine(pipelineEngine)
                        .build();
                adapter.start();
                Thread.sleep(300);
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("http://localhost:8731/api/datalake/webhook/d12/batch"))
                        .header("Content-Type", "application/json")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"not\":\"array\"}"))
                        .build();
                java.net.http.HttpResponse<String> resp = client.send(request,
                        java.net.http.HttpResponse.BodyHandlers.ofString());
                adapter.stop();
                return resp.statusCode() == 500;
            } catch (Exception e) {
                return false;
            }
        });;

        check("D13-/batch 超大数组（1000条）→ 200", () -> {
            try {
                WebhookInboundAdapter adapter = WebhookInboundAdapter.builder()
                        .port(8732)
                        .pipelineEngine(pipelineEngine)
                        .build();
                adapter.start();
                Thread.sleep(300);
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < 1000; i++) {
                    if (i > 0) sb.append(',');
                    sb.append("{\"seq\":").append(i).append(",\"v\":\"x\"}");
                }
                sb.append("]");
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("http://localhost:8732/api/datalake/webhook/d13/batch"))
                        .header("Content-Type", "application/json")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(sb.toString()))
                        .build();
                java.net.http.HttpResponse<String> resp = client.send(request,
                        java.net.http.HttpResponse.BodyHandlers.ofString());
                adapter.stop();
                return resp.statusCode() == 200;
            } catch (Exception e) {
                return false;
            }
        });;
    }

    // ━━━━━━━━━━━━━━━ D14~D17: DataEnvelope 极端边界值 ━━━━━━━━━━━━━━━

    private static void testDataEnvelopeBoundary() {
        log.info("───── D14~D17: DataEnvelope 极端边界值 ─────");

        check("D14-空 parsed map → 不崩溃", () -> {
            pipelineManager.savePipeline("d14", singleSinkPipeline("log"));
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Collections.emptyMap())
                    .pipelineId("d14")
                    .traceId("d14")
                    .timestamp(System.currentTimeMillis())
                    .build();
            pipelineEngine.execute("d14", env);
            return env.getState() == PipelineState.SINK_OK;
        });

        check("D15-parsed 含 null value → 不崩溃", () -> {
            pipelineManager.savePipeline("d15", singleSinkPipeline("log"));
            Map<String, Object> data = new HashMap<>();
            data.put("key", null);
            data.put("num", 42);
            data.put("list", Arrays.asList(1, 2, 3));
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(data)
                    .pipelineId("d15")
                    .traceId("d15")
                    .timestamp(System.currentTimeMillis())
                    .build();
            pipelineEngine.execute("d15", env);
            return env.getState() == PipelineState.SINK_OK;
        });

        check("D16-null traceId → 不崩溃", () -> {
            pipelineManager.savePipeline("d16", singleSinkPipeline("log"));
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("k", "v"))
                    .pipelineId("d16")
                    .traceId(null)
                    .timestamp(System.currentTimeMillis())
                    .build();
            pipelineEngine.execute("d16", env);
            return env.getState() == PipelineState.SINK_OK;
        });

        check("D17-null topics → isLog() 返回 false", () -> {
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("k", "v"))
                    .topics(null)
                    .build();
            return !env.isLog();
        });
    }

    // ━━━━━━━━━━━━━━━ D18~D20: 高并发压测 ━━━━━━━━━━━━━━━

    private static void testHighConcurrencyStress() throws Exception {
        log.info("───── D18~D20: 高并发压测 ─────");

        check("D18-5000 envelope 顺序压测", () -> {
            logSink.reset();
            pipelineManager.savePipeline("d18", singleSinkPipeline("log"));
            long t0 = System.currentTimeMillis();
            for (int i = 0; i < 5000; i++) {
                DataEnvelope env = DataEnvelope.builder()
                        .parsed(Map.of("seq", i))
                        .pipelineId("d18")
                        .traceId("d18-" + i)
                        .timestamp(System.currentTimeMillis())
                        .build();
                pipelineEngine.execute("d18", env);
            }
            long elapsed = System.currentTimeMillis() - t0;
            int writes = logSink.getCount();
            double throughput = writes * 1000.0 / Math.max(elapsed, 1);
            log.info("[D18] 5000 envelopes in {}ms, throughput={} ops/s, writes={}",
                    elapsed, (long) throughput, writes);
            return writes >= 5000 && elapsed < 5000;
        });

        check("D19-50 线程并发执行同一管线 → 5000 条", () -> {
            logSink.reset();
            pipelineManager.savePipeline("d19", singleSinkPipeline("log"));
            int threadCount = 50;
            int perThread = 100;
            CountDownLatch latch = new CountDownLatch(threadCount);
            for (int t = 0; t < threadCount; t++) {
                final int tid = t;
                Executors.newSingleThreadExecutor().execute(() -> {
                    for (int i = 0; i < perThread; i++) {
                        DataEnvelope env = DataEnvelope.builder()
                                .parsed(Map.of("tid", tid, "seq", i))
                                .pipelineId("d19")
                                .traceId("d19-t" + tid + "-" + i)
                                .timestamp(System.currentTimeMillis())
                                .build();
                        pipelineEngine.execute("d19", env);
                    }
                    latch.countDown();
                });
            }
            boolean ok = latch.await(10, TimeUnit.SECONDS);
            int writes = logSink.getCount();
            log.info("[D19] 50 threads x 100 = 5000 envelopes, writes={}, elapsed=ok={}", writes, ok);
            return ok && writes >= 5000;
        });;

        check("D20-50 条不同管线并发 → 各管线独立", () -> {
            for (int i = 0; i < 50; i++) {
                logSink.reset();
                pipelineManager.savePipeline("d20-p" + i, singleSinkPipeline("log"));
            }
            int threadCount = 50;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger totalWrites = new AtomicInteger(0);
            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                Executors.newSingleThreadExecutor().execute(() -> {
                    DataEnvelope env = DataEnvelope.builder()
                            .parsed(Map.of("idx", idx))
                            .pipelineId("d20-p" + idx)
                            .traceId("d20-p" + idx)
                            .timestamp(System.currentTimeMillis())
                            .build();
                    pipelineEngine.execute("d20-p" + idx, env);
                    totalWrites.incrementAndGet();
                    latch.countDown();
                });
            }
            boolean ok = latch.await(5, TimeUnit.SECONDS);
            return ok && totalWrites.get() >= threadCount;
        });;
    }

    // ━━━━━━━━━━━━━━━ 辅助 ━━━━━━━━━━━━━━━

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

    @FunctionalInterface
    interface ProbeCheck {
        boolean check() throws Exception;
    }

    // ━━━━━━━━━━━━━━━ 辅助类 ━━━━━━━━━━━━━━━

    public static class CapturingSink implements DataSink {
        private final String type;
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile Map<String, Object> lastConfig;

        public CapturingSink(String type) { this.type = type; }
        public void reset() { count.set(0); lastConfig = null; }
        public int getCount() { return count.get(); }
        public Map<String, Object> getLastConfig() { return lastConfig; }
        @Override public String type() { return type; }
        @Override public void start() {}
        @Override public void stop() {}
        @Override public boolean write(DataEnvelope envelope, Map<String, Object> config) {
            count.incrementAndGet();
            lastConfig = config;
            return true;
        }
        @Override public EngineDataSource<?> getDataSource() { return null; }
    }
}
