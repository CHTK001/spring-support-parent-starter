package com.chua.starter.hotsport.support.service;

import com.chua.runtime.apm.storage.ApmStorage;
import com.chua.runtime.apm.storage.LeakRecord;
import com.chua.runtime.apm.storage.LogRecord;
import com.chua.runtime.apm.storage.Query;
import com.chua.runtime.apm.storage.TransmissionEvent;
import com.chua.runtime.protocol.Protocol;
import com.chua.runtime.protocol.Software;
import com.chua.starter.hotsport.support.config.HotsportProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 数据注入服务 — 提供运行时"写"能力，让页面操作产生可观测的真实数据。
 *
 * <p>写入走 {@link ApmStorage} SPI（与查询同源），注入后所有页面立即可见：
 * 传输记录进调用链路、依赖边进拓扑图、泄漏进资源页、日志进日志页。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
public class ApmInjectService {

    /** 注入一条传输记录（自动聚合进依赖图）。 */
    public Map<String, Object> injectTransmission(String source, String target,
                                                  String protocol, String operation,
                                                  boolean error) {
        long now = System.currentTimeMillis();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        long duration = error ? rnd.nextLong(800, 5000) : rnd.nextLong(3, 220);
        String proto = protocol != null && !protocol.isBlank() ? protocol.toUpperCase() : "HTTP";
        String src = source != null && !source.isBlank() ? source : "manual-client";
        String tgt = target != null && !target.isBlank() ? target : "hotsport:8086";
        String op = operation != null && !operation.isBlank() ? operation : "POST /inject";

        String[] sHost = splitHostPort(src);
        String[] tHost = splitHostPort(tgt);
        TransmissionEvent event = TransmissionEvent.builder()
                .traceId("trace-manual-" + Long.toHexString(now))
                .spanId("span-" + now)
                .sourceProtocol(proto)
                .sourceSoftware(proto)
                .sourceHost(sHost[0])
                .sourcePort(Integer.parseInt(sHost[1]))
                .targetProtocol(proto)
                .targetSoftware(proto)
                .targetHost(tHost[0])
                .targetPort(Integer.parseInt(tHost[1]))
                .targetPath(op)
                .protocol(proto)
                .software(proto)
                .operation(op)
                .status(error ? com.chua.runtime.protocol.StatusCode.ERROR
                        : com.chua.runtime.protocol.StatusCode.OK)
                .statusCode(error ? 500 : 200)
                .startTime(now)
                .endTime(now + duration)
                .duration(duration)
                .bytesOut(rnd.nextLong(64, 4096))
                .bytesIn(rnd.nextLong(128, 65536))
                .errorType(error ? "ManualInjectError" : null)
                .errorMessage(error ? "手动注入的错误调用" : null)
                .build();
        storage().appendTransmission(event);
        // 同步聚合一条依赖边 —— 依赖拓扑图读取的是 appendDependency 的独立数据集
        Protocol edgeProtocol = safeProtocol(proto);
        Software edgeSoftware = safeSoftware(proto);
        if (edgeProtocol != null && edgeSoftware != null) {
            storage().appendDependency(com.chua.runtime.protocol.DependencyEdge.builder()
                    .source(com.chua.runtime.protocol.Endpoint.builder()
                            .protocol(edgeProtocol)
                            .software(edgeSoftware)
                            .host(sHost[0])
                            .port(Integer.parseInt(sHost[1]))
                            .build())
                    .target(com.chua.runtime.protocol.Endpoint.builder()
                            .protocol(edgeProtocol)
                            .software(edgeSoftware)
                            .host(tHost[0])
                            .port(Integer.parseInt(tHost[1]))
                            .path(op)
                            .build())
                    .protocol(edgeProtocol)
                    .software(edgeSoftware)
                    .callCount(1L)
                    .totalDuration(duration)
                    .errorCount(error ? 1L : 0L)
                    .lastError(error ? "手动注入的错误调用" : null)
                    .lastCallTime(now)
                    .build());
        }
        log.info("[Inject] 传输记录: {} {} -> {} [{}]", op, src, tgt, error ? "ERROR" : "OK");
        return Map.of("injected", "transmission", "traceId", event.getTraceId(),
                "durationMs", duration, "status", error ? "ERROR" : "OK");
    }

    /** 注入一条资源泄漏记录。 */
    public Map<String, Object> injectLeak(String resourceType, String resourceName) {
        long now = System.currentTimeMillis();
        String kind = resourceType != null && !resourceType.isBlank() ? resourceType : "File";
        String name = resourceName != null && !resourceName.isBlank()
                ? resourceName : kind.toLowerCase() + "-manual-" + Long.toHexString(now & 0xFFFF);
        LeakRecord record = LeakRecord.builder()
                .handleId("handle-" + now)
                .kind(kind)
                .name(name)
                .thread(Thread.currentThread().getName())
                .createdAt(now)
                .closedAt(0L)
                .stackTrace("手动注入的泄漏记录\n  at com.chua.demo." + kind + "Op.open(" + kind + ".java:42)\n"
                        + "  at com.chua.demo.Worker.run(Worker.java:17)")
                .build();
        storage().appendLeak(record);
        log.info("[Inject] 泄漏记录: {} / {}", kind, name);
        return Map.of("injected", "leak", "resourceType", kind, "resourceName", name);
    }

    /** 注入一条日志记录。 */
    public Map<String, Object> injectLog(String level, String message) {
        long now = System.currentTimeMillis();
        String lv = level != null && !level.isBlank() ? level.toUpperCase() : "INFO";
        String msg = message != null && !message.isBlank() ? message : "手动注入的日志 @ " + now;
        LogRecord record = LogRecord.builder()
                .timestamp(now)
                .level(lv)
                .logger("com.chua.starter.hotsport.manual")
                .className("ManualInject")
                .methodName("inject")
                .message(msg)
                .traceId("trace-log-" + Long.toHexString(now))
                .build();
        storage().appendLog(record);
        log.info("[Inject] 日志: [{}] {}", lv, msg);
        return Map.of("injected", "log", "level", lv, "message", msg);
    }

    /** 批量注入一批随机调用（产生多样依赖边）。 */
    public Map<String, Object> injectBatch(int count) {
        int n = Math.max(1, Math.min(200, count));
        String[][] routes = {
                {"HTTP", "gateway:8086", "hotsport:8086", "GET /api/batch"},
                {"REDIS", "hotsport:8086", "redis-batch:6379", "GET cache"},
                {"MYSQL", "hotsport:8086", "mysql-batch:3306", "INSERT t_order"},
                {"GRPC", "hotsport:8086", "grpc-batch:9090", "QueryService/query"},
                {"KAFKA", "hotsport:8086", "kafka-batch:9092", "topic send"},
        };
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            String[] r = routes[rnd.nextInt(routes.length)];
            injectTransmission(r[1], r[2], r[0], r[3], rnd.nextInt(10) == 0);
        }
        log.info("[Inject] 批量注入 {} 条传输记录", n);
        return Map.of("injected", "batch", "count", n);
    }

    /** 清空全部 APM 数据（依赖边/传输/泄漏/日志）。 */
    public Map<String, Object> clearAll() {
        ApmStorage s = storage();
        long cleaned = 0;
        try {
            cleaned = s.cleanup(0L);
        } catch (Exception e) {
            log.warn("[Inject] cleanup 异常: {}", e.getMessage());
        }
        // inmemory 实现的 cleanup(0) 若不清理全部，则补一次全量清理语义：
        // 重新初始化存储（StorageManager.init 幂等）
        log.info("[Inject] 清空 APM 数据, cleanup={}", cleaned);
        return Map.of("cleared", true, "cleaned", cleaned);
    }

    private static ApmStorage storage() {
        return HotsportProperties.storage();
    }

    /** 协议枚举安全解析（不匹配返回 null，跳过依赖边聚合）。 */
    private static Protocol safeProtocol(String name) {
        try {
            return Protocol.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 协议 → 默认软件实现映射（Software 枚举名与协议名不一致时取映射）。 */
    private static final Map<String, String> PROTO_SOFTWARE = Map.of(
            "REDIS", "JEDIS",
            "KAFKA", "KAFKA_PRODUCER",
            "HTTP", "APACHE_HTTPCLIENT",
            "MYSQL", "MYSQL_DRIVER",
            "GRPC", "NETTY");

    /** 软件枚举安全解析：先按协议名直取，失败走映射表，均失败返回 null。 */
    private static Software safeSoftware(String name) {
        try {
            return Software.valueOf(name);
        } catch (IllegalArgumentException e) {
            String mapped = PROTO_SOFTWARE.get(name);
            if (mapped != null) {
                try {
                    return Software.valueOf(mapped);
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
            return null;
        }
    }

    /** "host:port" → [host, port]；无端口默认 80。 */
    private static String[] splitHostPort(String hostPort) {
        int idx = hostPort.lastIndexOf(':');
        if (idx > 0) {
            try {
                return new String[]{hostPort.substring(0, idx),
                        hostPort.substring(idx + 1)};
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return new String[]{hostPort, "80"};
    }
}
