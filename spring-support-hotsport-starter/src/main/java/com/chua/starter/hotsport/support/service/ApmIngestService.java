// [P3C A20 豁免] A20/A30/A31: 含日志/网络字符串字面量等非业务魔法值场景，豁免。
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
import com.chua.runtime.protocol.StatusCode;
import com.chua.starter.hotsport.support.config.HotsportProperties;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * APM 数据上报接入服务 — 仿 logger {@code /ingest} 的推送通道。
 *
 * <p><b>链路</b>：采集端（浏览器 SDK / 远程 Agent / 任意脚本）→
 * {@code POST /api/apm/ingest}（或 TCP 接收端）→ 本服务解析 + 补缺省 →
 * {@code StorageManager} 落库 → 既有查询 / SSE 轮询自然可见。
 * 本服务只做「接收与写入」，不做任何查询编排，存储实现由 APM 存储层 SPI 决定
 * （SQLite / MySQL / 内存 / Noop），与 {@code ApmQueryService} 读取同源。</p>
 *
 * <p><b>载荷形态</b>（与 logger 同构的宽容解析）：</p>
 * <ul>
 *   <li>传输记录数组：{@code [ {...}, ... ]}；</li>
 *   <li>单条传输记录对象；</li>
 *   <li>混合对象：{@code {"transmissions":[...],"dependencies":[...],
 *       "leaks":[...],"logs":[...]}}，四类键任选，每类可为数组或单对象。</li>
 * </ul>
 *
 * <p><b>缺省补齐</b>：traceId（x-trace-id 头 → 自动生成 32 位十六进制）、
 * spanId（自动生成 16 位十六进制）、startTime/endTime/duration（缺省按当前时间互推）、
 * status（按 statusCode/errorType 推断 OK/ERROR）、日志时间戳与级别、
 * 泄漏记录的 createdAt/handleId。单批每类上限
 * {@code plugin.hotsport.apm.ingest.max-batch}（默认 1000），超出截断。</p>
 *
 * <p><b>鉴权</b>：{@code plugin.hotsport.apm.ingest.token} 非空时校验请求头
 * {@code x-hotsport-token}，不匹配返回 401；为空（默认）不校验。</p>
 *
 * @author CH
 * @since 4.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApmIngestService {

    /**
     * 上报鉴权请求头。
    */
    private static final String HEADER_TOKEN = "x-hotsport-token";

    /**
     * 链路标识请求头（与后端 MDC 链路对齐）。
    */
    private static final String HEADER_TRACE_ID = "x-trace-id";

    /**
     * 应用标识请求头（缺省时写入传输记录 attributes.app）。
    */
    private static final String HEADER_APP = "x-hotsport-app";

    /**
     * maxBatch 未配置时的兜底上限。
    */
    private static final int DEFAULT_MAX_BATCH = 1000;

    /**
     * Hotsport 配置（读取 plugin.hotsport.apm.ingest.*）。
    */
    private final HotsportProperties hotsportProperties;

    /**
     * HTTP 上报入口：先鉴权，再解析入库。
     *
     * @param request 请求（取鉴权 / 链路 / 应用头）
     * @param body    上报载荷
     * @return 各类型接收条数
     */
    public Map<String, Object> ingest(HttpServletRequest request, JsonNode body) {
        checkToken(request);
        return doIngest(body,
                request.getHeader(HEADER_TRACE_ID),
                request.getHeader(HEADER_APP),
                System.currentTimeMillis());
    }

    /**
     * TCP 通道上报入口（无请求头上下文，链路 / 应用头按缺失处理）。
     *
     * @param body 上报载荷
     * @return 各类型接收条数
     */
    public Map<String, Object> ingest(JsonNode body) {
        return doIngest(body, null, null, System.currentTimeMillis());
    }

    /**
     * 解析 → 补缺省 → 落库 → 返回计数。
     */
    private Map<String, Object> doIngest(JsonNode body, String headerTraceId,
                                         String headerApp, long now) {
        List<TransmissionEvent> transmissions = new ArrayList<>();
        List<DependencyEdge> dependencies = new ArrayList<>();
        List<LeakRecord> leaks = new ArrayList<>();
        List<LogRecord> logs = new ArrayList<>();
        parseBody(body, transmissions, dependencies, leaks, logs);
        applyDefaults(transmissions, dependencies, leaks, logs, headerTraceId, headerApp, now);

        ApmStorage storage = HotsportProperties.storage();
        int okTransmissions = writeAll(transmissions, storage::appendTransmission, "transmission");
        int okDependencies = writeAll(dependencies, storage::appendDependency, "dependency");
        int okLeaks = writeAll(leaks, storage::appendLeak, "leak");
        int okLogs = writeAll(logs, storage::appendLog, "log");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", okTransmissions + okDependencies + okLeaks + okLogs);
        result.put("transmissions", okTransmissions);
        result.put("dependencies", okDependencies);
        result.put("leaks", okLeaks);
        result.put("logs", okLogs);
        result.put("storage", storage.name());
        return result;
    }

    /**
     * 鉴权：token 非空时校验 x-hotsport-token 请求头。
     */
    private void checkToken(HttpServletRequest request) {
        String expected = ingestToken();
        if (expected == null || expected.isBlank()) {
            return;
        }
        String token = request.getHeader(HEADER_TOKEN);
        if (!expected.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ingest token 不匹配");
        }
    }

    /**
     * 按四类键解析混合载荷；兼容纯数组（视为传输记录）与单条对象。
     */
    private void parseBody(JsonNode body,
                           List<TransmissionEvent> transmissions,
                           List<DependencyEdge> dependencies,
                           List<LeakRecord> leaks,
                           List<LogRecord> logs) {
        if (body == null || body.isNull()) {
            return;
        }
        int maxBatch = maxBatch();
        if (body.isArray()) {
            parseEach(body, maxBatch, this::toTransmission, transmissions);
            return;
        }
        if (!body.isObject()) {
            return;
        }
        boolean any = false;
        if (body.has("transmissions")) {
            parseEach(body.get("transmissions"), maxBatch, this::toTransmission, transmissions);
            any = true;
        }
        if (body.has("dependencies")) {
            parseEach(body.get("dependencies"), maxBatch, this::toDependency, dependencies);
            any = true;
        }
        if (body.has("leaks")) {
            parseEach(body.get("leaks"), maxBatch, this::toLeak, leaks);
            any = true;
        }
        if (body.has("logs")) {
            parseEach(body.get("logs"), maxBatch, this::toLog, logs);
            any = true;
        }
        if (!any && looksLikeTransmission(body)) {
            parseEach(body, maxBatch, this::toTransmission, transmissions);
        }
    }

    /**
     * 逐条解析一个数组 / 单对象节点，单条失败跳过，不阻断整批。
     */
    private <T> void parseEach(JsonNode node, int maxBatch,
                               java.util.function.Function<JsonNode, T> converter,
                               List<T> out) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (out.size() >= maxBatch) {
                    break;
                }
                T value = converter.apply(item);
                if (value != null) {
                    out.add(value);
                }
            }
            return;
        }
        if (out.size() < maxBatch) {
            T value = converter.apply(node);
            if (value != null) {
                out.add(value);
            }
        }
    }

    /**
     * 判断裸对象是否像一条传输记录（无四类容器键时的单条上报兜底）。
     */
    private boolean looksLikeTransmission(JsonNode node) {
        return node.has("operation") || node.has("spanId") || node.has("targetHost")
                || node.has("sourceHost") || node.has("targetPath") || node.has("sourcePath");
    }

    /**
     * 解析单条传输记录（字段与 {@link TransmissionEvent} 扁平模型一致）。
     */
    private TransmissionEvent toTransmission(JsonNode n) {
        if (n == null || !n.isObject()) {
            return null;
        }
        TransmissionEvent e = new TransmissionEvent();
        e.setTraceId(text(n, "traceId"));
        e.setSpanId(text(n, "spanId"));
        e.setParentSpanId(text(n, "parentSpanId"));
        e.setSourceProtocol(text(n, "sourceProtocol"));
        e.setSourceSoftware(text(n, "sourceSoftware"));
        e.setSourceHost(text(n, "sourceHost"));
        e.setSourcePort(intValue(n, "sourcePort"));
        e.setSourcePath(text(n, "sourcePath"));
        e.setTargetProtocol(text(n, "targetProtocol"));
        e.setTargetSoftware(text(n, "targetSoftware"));
        e.setTargetHost(text(n, "targetHost"));
        e.setTargetPort(intValue(n, "targetPort"));
        e.setTargetPath(text(n, "targetPath"));
        e.setProtocol(text(n, "protocol"));
        e.setSoftware(text(n, "software"));
        e.setOperation(text(n, "operation"));
        e.setStatusCode(intValue(n, "statusCode"));
        e.setStatus(parseStatus(text(n, "status"), e.getStatusCode(), text(n, "errorType")));
        e.setBytesOut(longValue(n, "bytesOut"));
        e.setBytesIn(longValue(n, "bytesIn"));
        e.setErrorType(text(n, "errorType"));
        e.setErrorMessage(text(n, "errorMessage"));
        JsonNode attrs = n.get("attributes");
        if (attrs != null && attrs.isObject()) {
            Map<String, String> map = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> it = attrs.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                if (entry.getValue() != null && !entry.getValue().isNull()) {
                    map.put(entry.getKey(), entry.getValue().asText());
                }
            }
            e.setAttributes(map);
        }
        return e;
    }

    /**
     * 解析单条依赖边（source / target 为端点对象，协议 / 软件栈按枚举名宽容解析）。
     */
    private DependencyEdge toDependency(JsonNode n) {
        if (n == null || !n.isObject()) {
            return null;
        }
        DependencyEdge e = new DependencyEdge();
        e.setSource(parseEndpoint(n.get("source"), EndpointKind.CLIENT));
        e.setTarget(parseEndpoint(n.get("target"), EndpointKind.SERVER));
        e.setProtocol(parseEnum(text(n, "protocol"), Protocol.class, Protocol.UNKNOWN));
        e.setSoftware(parseEnum(text(n, "software"), Software.class, Software.UNKNOWN));
        e.setCallCount(longValue(n, "callCount"));
        e.setTotalDuration(longValue(n, "totalDuration"));
        e.setErrorCount(longValue(n, "errorCount"));
        e.setLastError(text(n, "lastError"));
        e.setLastCallTime(longValue(n, "lastCallTime"));
        if (e.getCallCount() <= 0) {
            e.setCallCount(1);
        }
        if (e.getLastCallTime() <= 0) {
            e.setLastCallTime(System.currentTimeMillis());
        }
        return e;
    }

    /**
     * 解析端点对象（host/port/path/protocol/software/kind）。
     */
    private Endpoint parseEndpoint(JsonNode n, EndpointKind defaultKind) {
        if (n == null || !n.isObject()) {
            return null;
        }
        return Endpoint.builder()
                .kind(parseEnum(text(n, "kind"), EndpointKind.class, defaultKind))
                .protocol(parseEnum(text(n, "protocol"), Protocol.class, Protocol.UNKNOWN))
                .software(parseEnum(text(n, "software"), Software.class, Software.UNKNOWN))
                .host(text(n, "host"))
                .port(intValue(n, "port"))
                .path(text(n, "path"))
                .build();
    }

    /**
     * 解析单条句柄泄漏记录。
     */
    private LeakRecord toLeak(JsonNode n) {
        if (n == null || !n.isObject()) {
            return null;
        }
        LeakRecord r = new LeakRecord();
        r.setHandleId(text(n, "handleId"));
        r.setKind(text(n, "kind"));
        r.setName(text(n, "name"));
        r.setThread(text(n, "thread"));
        r.setCreatedAt(longValue(n, "createdAt"));
        r.setClosedAt(longValue(n, "closedAt"));
        r.setStackTrace(text(n, "stackTrace"));
        return r;
    }

    /**
     * 解析单条日志记录。
     */
    private LogRecord toLog(JsonNode n) {
        if (n == null || !n.isObject()) {
            return null;
        }
        LogRecord r = new LogRecord();
        r.setLevel(text(n, "level"));
        r.setLogger(text(n, "logger"));
        r.setClassName(text(n, "className"));
        r.setMethodName(text(n, "methodName"));
        r.setMessage(text(n, "message"));
        r.setTraceId(text(n, "traceId"));
        r.setTimestamp(longValue(n, "timestamp"));
        return r;
    }

    /**
     * 缺省补齐：标识、时间、状态、级别等，保证入库数据自洽可查。
     */
    private void applyDefaults(List<TransmissionEvent> transmissions,
                               List<DependencyEdge> dependencies,
                               List<LeakRecord> leaks,
                               List<LogRecord> logs,
                               String headerTraceId, String headerApp, long now) {
        for (TransmissionEvent e : transmissions) {
            if (isBlank(e.getTraceId())) {
                e.setTraceId(isBlank(headerTraceId) ? newId(32) : headerTraceId.trim());
            }
            if (isBlank(e.getSpanId())) {
                e.setSpanId(newId(16));
            }
            long start = e.getStartTime();
            long end = e.getEndTime();
            long duration = e.getDuration();
            if (start <= 0) {
                start = now;
            }
            if (end <= 0) {
                end = start + Math.max(0, duration);
            }
            if (duration <= 0) {
                duration = Math.max(0, end - start);
            }
            e.setStartTime(start);
            e.setEndTime(end);
            e.setDuration(duration);
            if (e.getStatus() == null) {
                e.setStatus(StatusCode.OK);
            }
            if (!isBlank(headerApp) && (e.getAttributes() == null
                    || !e.getAttributes().containsKey("app"))) {
                if (e.getAttributes() == null) {
                    e.setAttributes(new LinkedHashMap<>());
                }
                e.getAttributes().put("app", headerApp.trim());
            }
        }
        for (LeakRecord r : leaks) {
            if (r.getCreatedAt() <= 0) {
                r.setCreatedAt(now);
            }
            if (isBlank(r.getHandleId())) {
                r.setHandleId("h-" + newId(8));
            }
        }
        for (LogRecord r : logs) {
            if (r.getTimestamp() <= 0) {
                r.setTimestamp(now);
            }
            if (isBlank(r.getLevel())) {
                r.setLevel("INFO");
            }
            if (isBlank(r.getTraceId()) && !isBlank(headerTraceId)) {
                r.setTraceId(headerTraceId.trim());
            }
        }
    }

    /**
     * 解析状态码：显式 status 优先，其次按 statusCode / errorType 推断。
     */
    private StatusCode parseStatus(String status, int statusCode, String errorType) {
        if (!isBlank(status)) {
            try {
                return StatusCode.valueOf(status.trim().toUpperCase());
            } catch (Exception ignored) {
                // 落到推断逻辑
            }
        }
        if (statusCode >= 400 || !isBlank(errorType)) {
            return StatusCode.ERROR;
        }
        return StatusCode.OK;
    }

    /**
     * 枚举名宽容解析（忽略大小写），失败返回兜底值。
     */
    private <E extends Enum<E>> E parseEnum(String value, Class<E> type, E fallback) {
        if (isBlank(value)) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * 逐条写入并统计成功条数，单条失败记 debug 后继续，不阻断整批。
     */
    private <T> int writeAll(List<T> items, Consumer<T> writer, String kind) {
        int ok = 0;
        for (T item : items) {
            try {
                writer.accept(item);
                ok++;
            } catch (Exception e) {
                log.debug("[Hotsport] ingest 写入 {} 失败（跳过）: {}", kind, e.getMessage());
            }
        }
        return ok;
    }

    /**
     * 读取上报共享密钥（未配置返回空串 = 不校验）。
     */
    private String ingestToken() {
        HotsportProperties.Apm apm = hotsportProperties == null ? null : hotsportProperties.getApm();
        return apm == null || apm.getIngest() == null ? "" : apm.getIngest().getToken();
    }

    /**
     * 读取单批上限（未配置或非法时用默认值）。
     */
    private int maxBatch() {
        HotsportProperties.Apm apm = hotsportProperties == null ? null : hotsportProperties.getApm();
        int max = apm == null || apm.getIngest() == null ? 0 : apm.getIngest().getMaxBatch();
        return max > 0 ? max : DEFAULT_MAX_BATCH;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static int intValue(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? 0 : v.asInt();
    }

    private static long longValue(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? 0L : v.asLong();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * 生成十六进制随机标识（traceId 32 位 / spanId 16 位）。
     */
    private static String newId(int chars) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return uuid.substring(0, Math.min(chars, uuid.length()));
    }
}
