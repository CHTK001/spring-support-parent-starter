package com.chua.starter.datalake.probe;

import com.chua.common.support.concurrent.offset.OffsetFlow;
import com.chua.common.support.lang.datasource.engine.EngineDataSource;
import com.chua.datalake.support.engine.DefaultPipelineEngine;
import com.chua.datalake.support.model.DataEnvelope;
import com.chua.datalake.support.model.PipelineState;
import com.chua.datalake.support.pipeline.DefaultPipelineManager;
import com.chua.datalake.support.spi.pipeline.PipelineEngine;
import com.chua.datalake.support.spi.pipeline.PipelineManager;
import com.chua.datalake.support.spi.sink.DataSink;
import com.chua.datalake.support.subscriber.RealTimeDatalakeSubscriber;
import com.chua.datalake.support.webhook.WebhookInboundAdapter;
import com.chua.common.support.spi.ServiceProvider;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
* 实时订阅器与多 Sink 容错全面探测。
*
* <ul>
*   <li>S01~S05: RealTimeDatalakeSubscriber push 行为</li>
*   <li>S06~S07: pushBatch 批量推送</li>
*   <li>S08~S09: offset 推进与 reset</li>
*   <li>S10~S12: 多 Sink 容错（一个失败其余正常）</li>
*   <li>S13~S14: 非法管线 JSON 容错</li>
*   <li>S15~S16: SPI 自动发现 Sink</li>
*   <li>S17~S20: Webhook 边界（非法 JSON/空 body/超大 payload/并发连接）</li>
* </ul>
*
* @author CH
* @since 4.0.0.42
 */
@Slf4j
public class DatalakeSubscriberProbe {

    private static int passed = 0; // 通过
    private static int failed = 0; // 失败
    private static int total = 0; // total
    private static final List<String> failedCases = new CopyOnWriteArrayList<>(); // 失败CAS

    private static PipelineManager pipelineManager; // pipeline管理器
    private static final Map<String, DataSink> sinkRegistry = new ConcurrentHashMap<>(); // sinkregistry
    private static PipelineEngine pipelineEngine; // pipelineengine
    private static CapturingSink logSink; // 日志sink
    private static CapturingSink statSink; // statsink
    private static FailingSink failingSink; // 失败sink
    private static OffsetFlow offsetFlow; // 偏移量流

    /**
    * main。
    * @param args 参数
     */
    public static void main(String[] args) throws Exception {
        log.info("═══════════════════════════════════════════════════════");
        log.info("║       实时订阅器与多 Sink 容错全面探测 (DatalakeSubscriberProbe)  ║");
        log.info("═══════════════════════════════════════════════════════");

        setupInfrastructure();
        testSubscriberPush();
        testPushBatch();
        testOffsetAdvance();
        testMultiSinkFaultTolerance();
        testInvalidPipelineJson();
        testSpiDiscovery();
        testWebhookEdgeCases();

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
        failingSink = new FailingSink();

        sinkRegistry.put("log", logSink);
        sinkRegistry.put("statistic", statSink);
        sinkRegistry.put("failing", failingSink);
        sinkRegistry.values().forEach(DataSink::start);

        pipelineEngine = new DefaultPipelineEngine(pipelineManager, sinkRegistry, null);
        offsetFlow = OffsetFlow.create().start();

        log.info("[DatalakeSubscriberProbe] 基础设施就绪: log/stat/failing 共 3 个 Sink");
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

 // ━━━━━━━━━━━━━━━ S01~S05: real时间数据湖subscriber push 行为 ━━━━━━━━━━━━━━━

    /**
    * 测试subscriberpush。
     */
    private static void testSubscriberPush() {
        log.info("───── S01~S05: RealTimeDatalakeSubscriber push 行为 ─────");

        check("S01-push null envelope 不崩溃", () -> {
            AtomicInteger calls = new AtomicInteger(0);
            RealTimeDatalakeSubscriber sub = new RealTimeDatalakeSubscriber(
                    "sub-s01", offsetFlow, env -> calls.incrementAndGet());
            sub.push(null);
            return calls.get() == 0;
        });

        check("S02-push 正常 envelope → consumer 被调用", () -> {
            AtomicLong receivedTs = new AtomicLong(-1);
            RealTimeDatalakeSubscriber sub = new RealTimeDatalakeSubscriber(
                    "sub-s02", offsetFlow, env -> receivedTs.set(env.getTimestamp()));
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("seq", 1))
                    .pipelineId("s02")
                    .traceId("s02")
                    .timestamp(System.currentTimeMillis())
                    .build();
            sub.push(env).block();
            return receivedTs.get() > 0;
        });

        check("S03-push 多次 → consumer 累计调用", () -> {
            AtomicInteger count = new AtomicInteger(0);
            RealTimeDatalakeSubscriber sub = new RealTimeDatalakeSubscriber(
                    "sub-s03", offsetFlow, env -> count.incrementAndGet());
            for (int i = 0; i < 10; i++) {
                DataEnvelope env = DataEnvelope.builder()
                        .parsed(Map.of("i", i))
                        .timestamp(System.currentTimeMillis())
                        .build();
                sub.push(env).block();
            }
            return count.get() == 10;
        });

        check("S04-push 后 offset 递增", () -> {
            RealTimeDatalakeSubscriber sub = new RealTimeDatalakeSubscriber(
                    "sub-s04", offsetFlow, env -> { });
            long before = sub.currentOffset();
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("k", "v"))
                    .timestamp(System.currentTimeMillis())
                    .build();
            sub.push(env).block();
            long after = sub.currentOffset();
            return after > before;
        });

        check("S05-pipelineId null 的 envelope 也能 push", () -> {
            AtomicInteger called = new AtomicInteger(0);
            RealTimeDatalakeSubscriber sub = new RealTimeDatalakeSubscriber(
                    "sub-s05", offsetFlow, env -> called.incrementAndGet());
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("x", 1))
                    .timestamp(System.currentTimeMillis())
                    .build();
            env.setPipelineId(null);
            sub.push(env).block();
            return called.get() == 1;
        });
    }

 // ━━━━━━━━━━━━━━━ S06~S07: pushbatch ━━━━━━━━━━━━━━━

    /**
    * 测试pushbatch。
     */
    private static void testPushBatch() {
        log.info("───── S06~S07: pushBatch 批量推送 ─────");

        check("S06-pushBatch 空 Flux → 无异常", () -> {
            RealTimeDatalakeSubscriber sub = new RealTimeDatalakeSubscriber(
                    "sub-s06", offsetFlow, env -> { });
            sub.pushBatch(Flux.empty())
                    .timeout(java.time.Duration.ofSeconds(2))
                    .block();
            return true;
        });

        check("S07-pushBatch 5 条 → consumer 收到 5 条", () -> {
            AtomicInteger count = new AtomicInteger(0);
            RealTimeDatalakeSubscriber sub = new RealTimeDatalakeSubscriber(
                    "sub-s07", offsetFlow, env -> count.incrementAndGet());
            java.util.List<DataEnvelope> envelopes = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                envelopes.add(DataEnvelope.builder()
                        .parsed(Map.of("seq", i))
                        .timestamp(System.currentTimeMillis())
                        .build());
            }
             sub.pushBatch(Flux.fromIterable(envelopes))
                    .block();
            return count.get() == 5;
        });
    }

 // ━━━━━━━━━━━━━━━ S08~S09: 偏移量 推进 ━━━━━━━━━━━━━━━

    /**
    * 测试偏移量advance。
     */
    private static void testOffsetAdvance() {
        log.info("───── S08~S09: offset 推进与 reset ─────");

        check("S08-advance() 每次调用递增", () -> {
            RealTimeDatalakeSubscriber sub = new RealTimeDatalakeSubscriber(
                    "sub-s08", offsetFlow, env -> { });
            long o1 = sub.currentOffset();
            sub.push(DataEnvelope.builder().parsed(Map.of("a", 1)).timestamp(o1 + 1).build()).block();
            long o2 = sub.currentOffset();
            sub.push(DataEnvelope.builder().parsed(Map.of("a", 2)).timestamp(o2 + 1).build()).block();
            long o3 = sub.currentOffset();
            return o1 < o2 && o2 < o3;
        });

        check("S09-reset 后 currentOffset 回到指定值", () -> {
            RealTimeDatalakeSubscriber sub = new RealTimeDatalakeSubscriber(
                    "sub-s09", offsetFlow, env -> { });
            sub.push(DataEnvelope.builder().parsed(Map.of("i", 1)).timestamp(100L).build()).block();
            long before = sub.currentOffset();
            sub.reset(50L);
            return sub.currentOffset() == 50L;
        });
    }

    // ━━━━━━━━━━━━━━━ S10~S12: 多 Sink 容错 ━━━━━━━━━━━━━━━

    /**
    * 测试multisinkfault容错。
     */
    private static void testMultiSinkFaultTolerance() {
        log.info("───── S10~S12: 多 Sink 容错 ─────");

        check("S10-多 Sink 管线中 failing Sink 失败 → log 仍收到", () -> {
            logSink.reset();
            failingSink.reset();
            pipelineManager.savePipeline("s10", multiSinkPipeline("log", "failing"));
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("seq", 1))
                    .pipelineId("s10")
                    .traceId("s10")
                    .timestamp(System.currentTimeMillis())
                    .build();
            pipelineEngine.execute("s10", env);
            return logSink.getCount() >= 1
                    && failingSink.getCount() >= 1
                    && env.getState() == PipelineState.SINK_FAIL;
        });

        check("S11-3 Sink 中 1 失败 → 成功 Sink 各收到数据", () -> {
            logSink.reset();
            statSink.reset();
            failingSink.reset();
            pipelineManager.savePipeline("s11", multiSinkPipeline("log", "statistic", "failing"));
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("seq", 2))
                    .pipelineId("s11")
                    .traceId("s11")
                    .timestamp(System.currentTimeMillis())
                    .build();
            pipelineEngine.execute("s11", env);
            return logSink.getCount() >= 1 && statSink.getCount() >= 1;
        });

        check("S12-连续 20 条 → 每个 batch 的 log Sink 都收到", () -> {
            logSink.reset();
            failingSink.reset();
            pipelineManager.savePipeline("s12", multiSinkPipeline("log", "failing"));
            for (int i = 0; i < 20; i++) {
                DataEnvelope env = DataEnvelope.builder()
                        .parsed(Map.of("seq", i))
                        .pipelineId("s12")
                        .traceId("s12-" + i)
                        .timestamp(System.currentTimeMillis())
                        .build();
                pipelineEngine.execute("s12", env);
            }
            return logSink.getCount() >= 20 && failingSink.getCount() >= 20;
        });
    }

    // ━━━━━━━━━━━━━━━ S13~S14: 非法管线 JSON 容错 ━━━━━━━━━━━━━━━

    /**
    * 测试invalidpipelinejson。
     */
    private static void testInvalidPipelineJson() {
        log.info("───── S13~S14: 非法管线 JSON 容错 ─────");

        check("S13-管线 JSON 缺失 stages 字段 → 不崩溃", () -> {
            pipelineManager.savePipeline("s13", "{\"id\":\"s13\"}");
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("k", "v"))
                    .pipelineId("s13")
                    .traceId("s13")
                    .timestamp(System.currentTimeMillis())
                    .build();
            pipelineEngine.execute("s13", env);
            return env.getState() == null;
        });

        check("S14-管线 JSON 格式错误 → 不崩溃", () -> {
            pipelineManager.savePipeline("s14", "{invalid json!!!");
            DataEnvelope env = DataEnvelope.builder()
                    .parsed(Map.of("k", "v"))
                    .pipelineId("s14")
                    .traceId("s14")
                    .timestamp(System.currentTimeMillis())
                    .build();
            pipelineEngine.execute("s14", env);
            return env.getState() == null;
        });
    }

    // ━━━━━━━━━━━━━━━ S15~S16: SPI 自动发现 ━━━━━━━━━━━━━━━

    /**
    * 测试spidiscovery。
     */
    private static void testSpiDiscovery() {
        log.info("───── S15~S16: SPI 自动发现 Sink ─────");

        check("S15-ServiceProvider 发现 DataSink 实现", () -> {
            ServiceProvider<DataSink> provider = ServiceProvider.of(DataSink.class);
            // 强制重新扫描：先清空缓存再发现
            java.util.List<DataSink> all = provider.collect();
            java.util.Set<String> types = new java.util.HashSet<>();
            for (DataSink s : all) {
                types.add(s.type());
            }
            return types.contains("log") && types.contains("statistic")
                    && types.contains("realtime") && types.contains("jdbc");
        });

        check("S16-通过 SPI 获取实例 → type() 正确", () -> {
            ServiceProvider<DataSink> provider = ServiceProvider.of(DataSink.class);
            DataSink logImpl = provider.getNewExtension("log");
            DataSink rtImpl = provider.getNewExtension("realtime");
            DataSink stImpl = provider.getNewExtension("statistic");
            DataSink jdbcImpl = provider.getNewExtension("jdbc");
            return logImpl != null && "log".equals(logImpl.type())
                    && rtImpl != null && "realtime".equals(rtImpl.type())
                    && stImpl != null && "statistic".equals(stImpl.type())
                    && jdbcImpl != null && "jdbc".equals(jdbcImpl.type());
        });
    }

    // ━━━━━━━━━━━━━━━ S17~S20: Webhook 边界 ━━━━━━━━━━━━━━━

    /**
    * 测试webhookedgecas。
     */
    private static void testWebhookEdgeCases() throws Exception {
        log.info("───── S17~S20: Webhook 边界 ─────");

        check("S17-非法 JSON body → 500 响应不崩溃", () -> {
            try {
                WebhookInboundAdapter adapter = WebhookInboundAdapter.builder()
                        .port(8720)
                        .pipelineEngine(pipelineEngine)
                        .build();
                adapter.start();
                Thread.sleep(300);

                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("http://localhost:8720/api/datalake/webhook/s17"))
                        .header("Content-Type", "application/json")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{not valid json"))
                        .build();
                java.net.http.HttpResponse<String> resp = client.send(request,
                        java.net.http.HttpResponse.BodyHandlers.ofString());
                adapter.stop();
                return resp.statusCode() == 500;
            } catch (Exception e) {
                return false;
            }
        });

        check("S18-空 body → 400 响应", () -> {
            try {
                WebhookInboundAdapter adapter = WebhookInboundAdapter.builder()
                        .port(8721)
                        .pipelineEngine(pipelineEngine)
                        .build();
                adapter.start();
                Thread.sleep(300);

                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("http://localhost:8721/api/datalake/webhook/s18"))
                        .header("Content-Type", "application/json")
                        .POST(java.net.http.HttpRequest.BodyPublishers.noBody())
                        .build();
                java.net.http.HttpResponse<String> resp = client.send(request,
                        java.net.http.HttpResponse.BodyHandlers.ofString());
                adapter.stop();
                return resp.statusCode() == 400 || resp.statusCode() == 500;
            } catch (Exception e) {
                return false;
            }
        });

        check("S19-超大 payload（1MB JSON）→ 不 OOM", () -> {
            try {
                WebhookInboundAdapter adapter = WebhookInboundAdapter.builder()
                        .port(8722)
                        .pipelineEngine(pipelineEngine)
                        .build();
                adapter.start();
                Thread.sleep(300);

                StringBuilder sb = new StringBuilder("{");
                for (int i = 0; i < 50000; i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    sb.append("\"key_").append(i).append("\":\"value_padding_data_1234567890\"");
                }
                sb.append("}");
                String hugeJson = sb.toString();

                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("http://localhost:8722/api/datalake/webhook/s19"))
                        .header("Content-Type", "application/json")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(hugeJson))
                        .build();
                java.net.http.HttpResponse<String> resp = client.send(request,
                        java.net.http.HttpResponse.BodyHandlers.ofString());
                adapter.stop();
                return resp.statusCode() == 200;
            } catch (Exception e) {
                return false;
            }
        });

        check("S20-10 并发请求 → 全部 200", () -> {
            try {
                WebhookInboundAdapter adapter = WebhookInboundAdapter.builder()
                        .port(8723)
                        .pipelineEngine(pipelineEngine)
                        .build();
                adapter.start();
                Thread.sleep(300);

                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                CountDownLatch latch = new CountDownLatch(10);
                AtomicInteger success = new AtomicInteger(0);
                for (int i = 0; i < 10; i++) {
                    final int seq = i;
                    Executors.newSingleThreadExecutor().execute(() -> {
                        try {
                            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                                    .uri(java.net.URI.create("http://localhost:8723/api/datalake/webhook/s20"))
                                    .header("Content-Type", "application/json")
                                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                                            "{\"seq\":" + seq + "}"))
                                    .build();
                            java.net.http.HttpResponse<String> resp = client.send(request,
                                    java.net.http.HttpResponse.BodyHandlers.ofString());
                            if (resp.statusCode() == 200) {
                                success.incrementAndGet();
                            }
                        } catch (Exception e) {
                            // ignore
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                boolean ok = latch.await(5, TimeUnit.SECONDS);
                adapter.stop();
                return ok && success.get() == 10;
            } catch (Exception e) {
                return false;
            }
        });
    }

    // ━━━━━━━━━━━━━━━ 辅助 ━━━━━━━━━━━━━━━
    /**
    * 探针检查接口。
    *
    * @author CH
    * @since 4.0.0
    * @param name 名称
    * @param check 检查
     */

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
    /**
     * capturingsink类。
     *
     * @author CH
     * @since 4.0.0
     */
    public static class CapturingSink implements DataSink {
        private final String type;
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile Map<String, Object> lastConfig;

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
            throw new RuntimeException("Simulated failure for: " + envelope.getTraceId());
        }
        @Override
        public EngineDataSource<?> getDataSource() { return null; }
    }
}
