package com.chua.starter.hotsport.support.service;

import com.chua.runtime.apm.storage.ApmStorage;
import com.chua.runtime.apm.storage.LeakRecord;
import com.chua.runtime.apm.storage.LogRecord;
import com.chua.runtime.apm.storage.TransmissionEvent;
import com.chua.runtime.protocol.DependencyEdge;
import com.chua.runtime.protocol.Endpoint;
import com.chua.runtime.protocol.EndpointKind;
import com.chua.runtime.protocol.Protocol;
import com.chua.runtime.protocol.Software;
import com.chua.starter.hotsport.support.config.HotsportProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
* 演示数据种子 — 通过 {@code hotsport.demo.seed=true} 开启。
*
* <p>用途：验收/演示环境下向 {@link ApmStorage} 写入一批真实结构的
* 传输/依赖/泄漏/日志记录，走的是产品真实的存储与查询链路（仅数据来源
* 是模拟的，查询、筛选、分页、导出全部为生产逻辑）。生产环境默认关闭。</p>
*
* @author CH
* @since 4.0.0.42
 */
@Slf4j
@Component
public class DemoSeedRunner implements ApplicationRunner {

    /** 是否启用演示数据种子。 */
    @Value("${hotsport.demo.seed:false}")
    private boolean enabled;

    /**
    * 启动完成后按开关写入演示数据。
    *
    * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        // 存储由异步 attach 线程初始化（ApmBootstrap.start → StorageManager.init），
        // 这里轮询等待就绪，避免与 attach 线程竞态导致种子静默跳过
        ApmStorage storage = null;
        for (int i = 0; i < 30; i++) {
            ApmStorage s = HotsportProperties.storage();
            if (s != null && !"noop".equalsIgnoreCase(s.name())) {
                storage = s;
                break;
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (storage == null) {
            log.warn("[DemoSeed] 等待 60s 存储仍未就绪，跳过种子数据写入");
            return;
        }
        long now = System.currentTimeMillis();
        seedTransmissions(storage, now);
        seedDependencies(storage, now);
        seedLeaks(storage, now);
        seedLogs(storage, now);
        log.info("[DemoSeed] 演示数据写入完成: {}", storage.stats());
    }

    /**
    * 写入传输记录（多协议、含错误、覆盖最近 30 分钟）。
    *
    * @param storage 存储实例
    * @param now 当前毫秒
     */
    private void seedTransmissions(ApmStorage storage, long now) {
        String[][] routes = {
                {"HTTP", "TOMCAT", "GET /api/apm/stats", "gateway:8086", "hotsport:8086"},
                {"HTTP", "TOMCAT", "POST /api/apm/export/leaks", "gateway:8086", "hotsport:8086"},
                {"HTTP", "APACHE_HTTPCLIENT", "GET /api/apm/transmissions", "hotsport:8086", "upstream-demo:9000"},
                {"REDIS", "JEDIS", "GET hotsport:cache:stats", "hotsport:8086", "redis-demo:6379"},
                {"MYSQL", "MYSQL_DRIVER", "SELECT t_transmission", "hotsport:8086", "mysql-demo:3306"},
                {"GRPC", "NETTY", "QueryService/query", "hotsport:8086", "collector-demo:9090"},
                {"KAFKA", "KAFKA_PRODUCER", "apm.transmissions send", "hotsport:8086", "kafka-demo:9092"},
        };
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<TransmissionEvent> events = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            String[] r = routes[i % routes.length];
            boolean error = rnd.nextInt(10) == 0;
            String[] src = r[3].split(":");
            String[] tgt = r[4].split(":");
            long start = now - rnd.nextLong(0, 30 * 60_000L);
            long duration = error ? rnd.nextLong(800, 5000) : rnd.nextLong(3, 220);
            events.add(TransmissionEvent.builder()
                    .traceId("trace-" + String.format("%08x", i / 3) + "-demo")
                    .spanId("span-" + i)
                    .parentSpanId(i % 3 == 0 ? null : "span-" + (i - 1))
                    .sourceProtocol(r[0])
                    .sourceSoftware(r[1])
                    .sourceHost(src[0])
                    .sourcePort(Integer.parseInt(src[1]))
                    .targetProtocol(r[0])
                    .targetSoftware(r[1])
                    .targetHost(tgt[0])
                    .targetPort(Integer.parseInt(tgt[1]))
                    .targetPath("/" + r[2])
                    .protocol(r[0])
                    .software(r[1])
                    .operation(r[2])
                    .status(error ? com.chua.runtime.protocol.StatusCode.ERROR
                            : com.chua.runtime.protocol.StatusCode.OK)
                    .statusCode(error ? 500 : 200)
                    .startTime(start)
                    .endTime(start + duration)
                    .duration(duration)
                    .bytesOut(rnd.nextLong(64, 4096))
                    .bytesIn(rnd.nextLong(128, 65536))
                    .errorType(error ? "TimeoutException" : null)
                    .errorMessage(error ? "upstream " + tgt[0] + " 响应超时（演示数据）" : null)
                    .build());
        }
        events.forEach(storage::appendTransmission);
    }

    /**
    * 写入依赖边（源 → 目标聚合）。
    *
    * @param storage 存储实例
    * @param now 当前毫秒
     */
    private void seedDependencies(ApmStorage storage, long now) {
        Object[][] edges = {
                {Protocol.HTTP, Software.TOMCAT, "gateway", 8086, Protocol.HTTP, Software.TOMCAT, "hotsport", 8086, 420L, 3L},
                {Protocol.HTTP, Software.APACHE_HTTPCLIENT, "hotsport", 8086, Protocol.HTTP, Software.TOMCAT, "upstream-demo", 9000, 380L, 12L},
                {Protocol.REDIS, Software.JEDIS, "hotsport", 8086, Protocol.REDIS, Software.LETTUCE, "redis-demo", 6379, 1560L, 0L},
                {Protocol.MYSQL, Software.MYSQL_DRIVER, "hotsport", 8086, Protocol.MYSQL, Software.MYSQL_DRIVER, "mysql-demo", 3306, 890L, 2L},
                {Protocol.GRPC, Software.NETTY, "hotsport", 8086, Protocol.GRPC, Software.NETTY, "collector-demo", 9090, 240L, 1L},
                {Protocol.KAFKA, Software.KAFKA_PRODUCER, "hotsport", 8086, Protocol.KAFKA, Software.KAFKA_PRODUCER, "kafka-demo", 9092, 610L, 0L},
        };
        for (Object[] e : edges) {
            storage.appendDependency(DependencyEdge.builder()
                    .source(Endpoint.builder()
                            .kind(EndpointKind.CLIENT)
                            .protocol((Protocol) e[0])
                            .software((Software) e[1])
                            .host((String) e[2])
                            .port((int) e[3])
                            .build())
                    .target(Endpoint.builder()
                            .kind(EndpointKind.SERVER)
                            .protocol((Protocol) e[4])
                            .software((Software) e[5])
                            .host((String) e[6])
                            .port((int) e[7])
                            .build())
                    .protocol((Protocol) e[0])
                    .software((Software) e[1])
                    .callCount((Long) e[8])
                    .totalDuration((Long) e[8] * 45L)
                    .errorCount((Long) e[9])
                    .lastError((Long) e[9] > 0 ? "Connection reset by peer（演示数据）" : null)
                    .lastCallTime(now - 60_000L)
                    .build());
        }
    }

    /**
    * 写入资源泄漏记录（部分仍活跃）。
    *
    * @param storage 存储实例
    * @param now 当前毫秒
     */
    private void seedLeaks(ApmStorage storage, long now) {
        Object[][] leaks = {
                {"socket", "SocketChannel[demo-legacy:8443]", "pool-3-thread-2", true},
                {"file", "FileInputStream(/data/demo/export-0909.csv)", "main", false},
                {"thread", "Thread[demo-worker-7]", "scheduler-1", true},
                {"jdbc", "Connection[jdbc:mysql://mysql-demo/demo]", "pool-1-thread-4", false},
                {"socket", "SocketChannel[demo-legacy:8443]", "pool-3-thread-5", true},
        };
        for (int i = 0; i < leaks.length; i++) {
            Object[] l = leaks[i];
            boolean active = (Boolean) l[3];
            long createdAt = now - (i + 1) * 90_000L;
            storage.appendLeak(LeakRecord.builder()
                    .handleId("handle-" + String.format("%04d", i) + "-demo")
                    .kind((String) l[0])
                    .name((String) l[1])
                    .thread((String) l[2])
                    .createdAt(createdAt)
                    .closedAt(active ? 0 : createdAt + 45_000L)
                    .stackTrace("at com.chua.demo.DemoService.open(" + l[0] + ".java:8" + i + ")（演示数据）")
                    .build());
        }
    }

    /**
    * 写入日志记录（多级别）。
    *
    * @param storage 存储实例
    * @param now 当前毫秒
     */
    private void seedLogs(ApmStorage storage, long now) {
        String[][] logs = {
                {"INFO", "c.c.s.h.support.service.ApmQueryService", "ApmQueryService", "stats 查询完成，耗时 12ms"},
                {"WARN", "c.c.s.h.support.service.ApmQueryService", "ApmQueryService", "存储容量接近阈值 80%（演示数据）"},
                {"ERROR", "c.c.demo.DemoClient", "DemoClient", "upstream demo-upstream:9000 连接超时 3000ms（演示数据）"},
                {"INFO", "c.c.demo.Scheduler", "Scheduler", "定时同步任务执行完成，处理 128 条"},
                {"ERROR", "c.c.demo.DemoClient", "DemoClient", "Kafka send 失败：NotLeaderForPartitionException（演示数据）"},
                {"DEBUG", "c.c.demo.TraceFilter", "TraceFilter", "traceId=trace-000000ab-demo 透传完成"},
                {"WARN", "c.c.demo.PoolWatcher", "PoolWatcher", "连接池活跃连接占比 92%（演示数据）"},
                {"INFO", "c.c.s.h.support.SpaWebConfig", "SpaWebConfig", "SPA 路由转发注册完成"},
                {"ERROR", "c.c.demo.DemoClient", "DemoClient", "gRPC collector-demo:9090 UNAVAILABLE（演示数据）"},
                {"INFO", "c.c.demo.Exporter", "Exporter", "CSV 导出完成，行数 120"},
        };
        for (int i = 0; i < 40; i++) {
            String[] l = logs[i % logs.length];
            storage.appendLog(LogRecord.builder()
                    .timestamp(now - i * 45_000L)
                    .level(l[0])
                    .logger(l[1])
                    .className(l[2])
                    .methodName(i % 2 == 0 ? "invoke" : "handle")
                    .message(l[3] + " #" + i)
                    .traceId(i % 3 == 0 ? "trace-" + String.format("%08x", i / 3) + "-demo" : null)
                    .build());
        }
    }
}
