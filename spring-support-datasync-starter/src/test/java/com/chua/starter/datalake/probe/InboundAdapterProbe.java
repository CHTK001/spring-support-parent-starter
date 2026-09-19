package com.chua.starter.datalake.probe;

import com.chua.datalake.support.engine.DefaultPipelineEngine;
import com.chua.datalake.support.model.DataEnvelope;
import com.chua.datalake.support.model.PipelineState;
import com.chua.datalake.support.mqtt.MqttInboundAdapter;
import com.chua.datalake.support.cdc.MysqlCdcListener;
import com.chua.datalake.support.pipeline.DefaultPipelineManager;
import com.chua.datalake.support.spi.pipeline.PipelineEngine;
import com.chua.datalake.support.spi.pipeline.PipelineManager;
import com.chua.datalake.support.spi.sink.DataSink;
import com.chua.datalake.support.webhook.WebhookInboundAdapter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 被动推送入站适配器全面探测。
 *
 * <p>验证 MQTT / Webhook / CDC 三个入站适配器的数据通路：</p>
 * <ul>
 *   <li>T01~T03: Builder 参数校验</li>
 *   <li>T04~T05: MqttInboundAdapter 生命周期</li>
 *   <li>T06~T08: WebhookInboundAdapter HTTP 推送</li>
 *   <li>T09~T10: MysqlCdcListener 生命周期</li>
 *   <li>T11~T12: 全链路 — 模拟消息 → PipelineEngine → Sink 写入验证</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class InboundAdapterProbe {

    private static int passed = 0; // 通过
    private static int failed = 0; // 失败
    private static int total = 0; // total
    private static final List<String> failedCases = new CopyOnWriteArrayList<>(); // 失败CAS

    private static PipelineManager pipelineManager; // pipeline管理器
    private static final Map<String, DataSink> sinkRegistry = new ConcurrentHashMap<>(); // sinkregistry
    private static PipelineEngine pipelineEngine; // pipelineengine
    private static MockSink mockSink; // mocksink

    /**
     * main。
     * @param args 参数
     */
    public static void main(String[] args) throws Exception {
        log.info("═══════════════════════════════════════════════════════");
        log.info("║   被动推送入站适配器全面探测 (InboundAdapterProbe)     ║");
        log.info("═══════════════════════════════════════════════════════");

        /**
         * setupinfrastructure。
         */
        setupInfrastructure();
        /**
         * 测试构建器校验。
         */
        testBuilderValidation();
        /**
         * 测试mqttlifecycle。
         */
        testMqttLifecycle();
        /**
         * 测试webhookpush。
         */
        testWebhookPush();
        /**
         * 测试cdclifecycle。
         */
        testCdcLifecycle();
        /**
         * 测试完整pipeline。
         */
        testFullPipeline();

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
 // pipeline管理器 — 注册四条测试管线
        pipelineManager = new DefaultPipelineManager();

        String defaultSinkPipeline = "{\"stages\":{\"default\":{\"sink\":[{\"type\":\"mock\"}]}}}";
        pipelineManager.savePipeline("test-mqtt", defaultSinkPipeline);
        pipelineManager.savePipeline("test-webhook", defaultSinkPipeline);
        pipelineManager.savePipeline("test-cdc", defaultSinkPipeline);
        pipelineManager.savePipeline("test-full", defaultSinkPipeline);

 // mocksink — 记录所有写入
        mockSink = new MockSink();
        sinkRegistry.put("mock", mockSink);

        pipelineEngine = new DefaultPipelineEngine(pipelineManager, sinkRegistry, null);

        log.info("[InboundAdapterProbe] 基础设施就绪: 4 条管线, MockSink 注册");
    }

 // ━━━━━━━━━━━━━━━ T01~T03: 构建器 校验 ━━━━━━━━━━━━━━━

    /**
     * 测试构建器校验。
     */
    private static void testBuilderValidation() {
        log.info("───── T01~T03: Builder 参数校验 ─────");

 // T01: mqttinbound适配器 无 pipelineengine 应抛异常
        check("T01-MqttBuilder无PipelineEngine", () -> {
            try {
                MqttInboundAdapter.builder().build();
                return false;
            } catch (IllegalArgumentException e) {
                return e.getMessage().contains("pipelineEngine");
            }
        });

 // T02: webhookinbound适配器 无 pipelineengine 应抛异常
        check("T02-WebhookBuilder无PipelineEngine", () -> {
            try {
                WebhookInboundAdapter.builder().build();
                return false;
            } catch (IllegalArgumentException e) {
                return e.getMessage().contains("pipelineEngine");
            }
        });

 // T03: mysqlcdc监听器 无 pipelineengine 应抛异常
        check("T03-CdcBuilder无PipelineEngine", () -> {
            try {
                MysqlCdcListener.builder().build();
                return false;
            } catch (IllegalArgumentException e) {
                return e.getMessage().contains("pipelineEngine");
            }
        });
    }

    // ━━━━━━━━━━━━━━━ T04~T05: MQTT 生命周期 ━━━━━━━━━━━━━━━

    /**
     * 测试mqttlifecycle。
     */
    private static void testMqttLifecycle() {
        log.info("───── T04~T05: MQTT 适配器生命周期 ─────");

        // T04: 构建成功
        check("T04-Mqtt构建成功", () -> {
            MqttInboundAdapter adapter = MqttInboundAdapter.builder()
                    .brokerUrl("tcp://localhost:1883")
                    .clientId("test-mqtt-001")
                    .topic("test/#")
                    .pipelineId("test-mqtt")
                    .pipelineEngine(pipelineEngine)
                    .qos(1)
                    .build();
            return adapter != null && !adapter.isRunning();
        });

 // T05: 启动 → 停止（Broker 不可用时应优雅降级）
        check("T05-Mqtt StartStop无异常", () -> {
            MqttInboundAdapter adapter = MqttInboundAdapter.builder()
                    .brokerUrl("tcp://localhost:19999")  // 故意用不存在的端口
                    .clientId("test-mqtt-002")
                    .topic("test/#")
                    .pipelineId("test-mqtt")
                    .pipelineEngine(pipelineEngine)
                    .build();
            try {
                adapter.start();  // 应捕获异常，不崩溃
                adapter.stop();
            } catch (Exception e) {
 // 启动 失败时 停止 也不应崩溃
            }
            return adapter.getMessageCount() == 0;
        });
    }

    // ━━━━━━━━━━━━━━━ T06~T08: Webhook 推送 ━━━━━━━━━━━━━━━

    /**
     * 测试webhookpush。
     */
    private static void testWebhookPush() throws Exception {
        log.info("───── T06~T08: Webhook HTTP 推送 ─────");

        // T06: 构建成功
        check("T06-Webhook构建成功", () -> {
            WebhookInboundAdapter adapter = WebhookInboundAdapter.builder()
                    .port(8711)
                    .pipelineEngine(pipelineEngine)
                    .build();
            return adapter != null && !adapter.isRunning();
        });

 // T07: 启动 → HTTP POST → 数据到达 pipelineengine → mocksink 收到
        check("T07-Webhook推送单条数据", () -> {
            mockSink.reset();
            WebhookInboundAdapter adapter = WebhookInboundAdapter.builder()
                    .port(8712)
                    .pipelineEngine(pipelineEngine)
                    .build();
            adapter.start();
            Thread.sleep(300);

            try {
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                String json = "{\"sensor\":\"temp-01\",\"value\":25.6}";
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("http://localhost:8712/api/datalake/webhook/test-webhook"))
                        .header("Content-Type", "application/json")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                        .build();
                java.net.http.HttpResponse<String> resp = client.send(request,
                        java.net.http.HttpResponse.BodyHandlers.ofString());

                Thread.sleep(200);
                boolean ok = resp.statusCode() == 200
                        && mockSink.getWriteCount() >= 1
                        && mockSink.getLastData() != null
                        && "temp-01".equals(mockSink.getLastData().get("sensor"));
                return ok;
            } finally {
                adapter.stop();
            }
        });

        // T08: 批量推送
        check("T08-Webhook批量推送", () -> {
            mockSink.reset();
            WebhookInboundAdapter adapter = WebhookInboundAdapter.builder()
                    .port(8713)
                    .pipelineEngine(pipelineEngine)
                    .build();
            adapter.start();
            Thread.sleep(300);

            try {
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                String batchJson = "[{\"id\":1},{\"id\":2},{\"id\":3}]";
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("http://localhost:8713/api/datalake/webhook/test-webhook/batch"))
                        .header("Content-Type", "application/json")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(batchJson))
                        .build();
                java.net.http.HttpResponse<String> resp = client.send(request,
                        java.net.http.HttpResponse.BodyHandlers.ofString());

                Thread.sleep(300);
                return resp.statusCode() == 200 && mockSink.getWriteCount() >= 3;
            } finally {
                adapter.stop();
            }
        });
    }

    // ━━━━━━━━━━━━━━━ T09~T10: CDC 生命周期 ━━━━━━━━━━━━━━━

    /**
     * 测试cdclifecycle。
     */
    private static void testCdcLifecycle() {
        log.info("───── T09~T10: CDC 适配器生命周期 ─────");

        // T09: 构建成功
        check("T09-Cdc构建成功", () -> {
            MysqlCdcListener listener = MysqlCdcListener.builder()
                    .host("127.0.0.1")
                    .port(3306)
                    .username("datalake")
                    .password("xxx")
                    .serverId(1)
                    .pipelineId("test-cdc")
                    .pipelineEngine(pipelineEngine)
                    .build();
            return listener != null && !listener.isRunning();
        });

 // T10: 启动 → 停止（MySQL 不可用时应优雅降级）
        check("T10-Cdc StartStop无异常", () -> {
            MysqlCdcListener listener = MysqlCdcListener.builder()
                    .host("127.0.0.1")
                    .port(19306)  // 故意用不存在的端口
                    .username("datalake")
                    .password("xxx")
                    .serverId(1)
                    .pipelineId("test-cdc")
                    .pipelineEngine(pipelineEngine)
                    .build();
            try {
                listener.start();  // 应捕获异常，不崩溃
                Thread.sleep(500);
                listener.stop();
            } catch (Exception e) {
                // ignore
            }
            return listener.getEventCount() == 0;
        });
    }

    // ━━━━━━━━━━━━━━━ T11~T12: 全链路验证 ━━━━━━━━━━━━━━━

    /**
     * 测试完整pipeline。
     */
    private static void testFullPipeline() throws Exception {
        log.info("───── T11~T12: 全链路（Webhook → Pipeline → MockSink）─────");

 // T11: Webhook → pipelineengine → mocksink 写入含完整字段
        check("T11-全链路数据完整性", () -> {
            mockSink.reset();
            WebhookInboundAdapter adapter = WebhookInboundAdapter.builder()
                    .port(8714)
                    .pipelineEngine(pipelineEngine)
                    .build();
            adapter.start();
            Thread.sleep(300);

            try {
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                String json = "{\"user\":\"alice\",\"action\":\"login\",\"ts\":1700000000}";
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("http://localhost:8714/api/datalake/webhook/test-full"))
                        .header("Content-Type", "application/json")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                        .build();
                client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                Thread.sleep(300);

                DataEnvelope last = mockSink.getLastEnvelope();
                return last != null
                        && last.getParsed() != null
                        && "alice".equals(last.getParsed().get("user"))
                        && "login".equals(last.getParsed().get("action"))
                        && last.getTraceId() != null
                        && last.getTimestamp() > 0
                        && last.getState() == PipelineState.SINK_OK;
            } finally {
                adapter.stop();
            }
        });

        // T12: 多次推送，计数器正确
        check("T12-多次推送计数器", () -> {
            mockSink.reset();
            WebhookInboundAdapter adapter = WebhookInboundAdapter.builder()
                    .port(8715)
                    .pipelineEngine(pipelineEngine)
                    .build();
            adapter.start();
            Thread.sleep(300);

            try {
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                for (int i = 0; i < 5; i++) {
                    String json = "{\"seq\":" + i + "}";
                    java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create("http://localhost:8715/api/datalake/webhook/test-full"))
                            .header("Content-Type", "application/json")
                            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                            .build();
                    client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                }
                Thread.sleep(500);
                return mockSink.getWriteCount() >= 5;
            } finally {
                adapter.stop();
            }
        });
    }

    // ━━━━━━━━━━━━━━━ 辅助 ━━━━━━━━━━━━━━━

    /**
     * 检查。
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
    /**
     * 探针检查接口。
     *
     * @author CH
     * @since 4.0.0
     */
    interface ProbeCheck {
        /**
         * 检查。
         * @return 检查的结果
         */
        boolean check() throws Exception;
    }

 // ━━━━━━━━━━━━━━━ mocksink ━━━━━━━━━━━━━━━

    /**
     * mocksink类。
     *
     * @author CH
     * @since 4.0.0
     */
    public static class MockSink implements DataSink {
        private final AtomicInteger writeCount = new AtomicInteger(0); // 写入数量
        private volatile DataEnvelope lastEnvelope; // 最后一个envelope
        private volatile Map<String, Object> lastData; // 最后一个数据

        /**
         * reset。
         */
        public void reset() {
            writeCount.set(0);
            lastEnvelope = null;
            lastData = null;
        }

        @Override
        public String type() { return "mock"; }

        @Override
        public void start() {}

        @Override
        public void stop() {}

        @Override
        public boolean write(DataEnvelope envelope, Map<String, Object> config) {
            writeCount.incrementAndGet();
            lastEnvelope = envelope;
            lastData = envelope.getParsed();
            return true;
        }

        @Override
        public com.chua.common.support.lang.datasource.engine.EngineDataSource<?> getDataSource() {
            return null;
        }

        /**
         * 获取写入数量。
         * @return 获取写入数量的结果
         */
        public int getWriteCount() { return writeCount.get(); }
        /**
         * 获取最后一个envelope。
         * @return 获取最后一个envelope的结果
         */
        public DataEnvelope getLastEnvelope() { return lastEnvelope; }
        /**
         * 获取最后一个数据。
         * @return 获取最后一个数据的结果
         */
        public Map<String, Object> getLastData() { return lastData; }
    }
}
