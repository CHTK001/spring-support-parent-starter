package com.chua.starter.hotsport.support.service;

import com.chua.runtime.apm.storage.ApmStorage;
import com.chua.runtime.apm.storage.LeakRecord;
import com.chua.runtime.apm.storage.LogRecord;
import com.chua.runtime.apm.storage.Query;
import com.chua.runtime.apm.storage.TransmissionEvent;
import com.chua.runtime.protocol.DependencyEdge;
import com.chua.runtime.protocol.Endpoint;
import com.chua.starter.hotsport.support.config.HotsportProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * APM 数据查询服务 — 统一门面，对 {@link StorageManager} 读操作做降级兜底。
 *
 * <p>设计原则：APM 数据"能 attach 就读实时，不能 attach 就返回空 + 明确标记"，
 * 保证前端无论 agent 是否成功注入，页面始终可演示、不报错。</p>
 *
 * <p>Agent 自注入由 {@code spring-support-agent-starter} 自动配置完成
 * （引入依赖即注入），本服务仅读取其注入状态做降级标记。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
public class ApmQueryService {

    /** 是否已尝试自注入 Agent。 */
    private final AtomicBoolean attachAttempted = new AtomicBoolean(false);

    /** Agent 自注入是否成功。 */
    private volatile boolean agentActive = false;

    @Value("${hotsport.agent.enabled:true}")
    private boolean agentEnabled;

    @Value("${hotsport.agent.shell-port:4567}")
    private int shellPort;

    @Value("${hotsport.agent.options:}")
    private String agentOptions;

    /** 时间格式：epoch 毫秒 → 前端可读字符串。 */
    private static final DateTimeFormatter ISO_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.systemDefault());

    /**
     * 启动时异步自注入 Agent 并在本进程启动 APM 引擎。
     *
     * <p>双通道：
     * ① attachSelf —— agentmain 对已加载类做 retransform（拦截 JDK/Tomcat 等启动早期类）；
     * ② 本进程直接 new ApmBootstrap().start() —— 与 host 同 classloader，
     *    handler 写入的 StorageManager 与 stats() 读取的同源（绕开跨加载器静态隔离）。</p>
     */
    @PostConstruct
    public void attachAgent() {
        if (!agentEnabled) {
            log.info("[Hotsport] Agent 自注入被配置禁用 (hotsport.agent.enabled=false)");
            return;
        }
        Thread.ofVirtual().name("hotsport-agent-attach").start(this::doAttachAgent);
    }

    /** 实际自注入 + 本进程 APM 启动（虚拟线程执行）。 */
    private void doAttachAgent() {
        if (!attachAttempted.compareAndSet(false, true)) {
            return;
        }
        // ① 自 attach（retransform 已加载类）
        try {
            Path agentJar = resolveAgentJarFromBootInf();
            com.chua.runtime.starter.RuntimeBoot.create()
                    .withAgent(agentJar)
                    .withAgentOptions(agentOptions.isEmpty() ? "port=" + shellPort : agentOptions)
                    .attachSelf();
            agentActive = true;
            log.info("[Hotsport] Agent 自注入完成，Shell 端口 {}", shellPort);
        } catch (Throwable t) {
            log.warn("[Hotsport] Agent 自注入失败（转仅本进程 APM 模式）: {}", t.getMessage());
        }
        // ② 本进程 APM 引擎（与 stats() 同 classloader，数据同源）
        try {
            com.chua.runtime.apm.ApmBootstrap apm =
                    new com.chua.runtime.apm.ApmBootstrap(java.nio.file.Paths.get(System.getProperty("java.io.tmpdir")));
            apm.addHandler(new com.chua.runtime.apm.handler.LogHandler());
            apm.addHandler(new com.chua.runtime.apm.handler.NetHandler());
            apm.addHandler(new com.chua.runtime.apm.handler.TransmissionHandler());
            apm.addHandler(new com.chua.runtime.apm.handler.DependencyGraphHandler());
            apm.addHandler(new com.chua.runtime.apm.handler.HandleLeakHandler());
            apm.addHandler(new com.chua.runtime.apm.handler.TraceHandler());
            apm.addHandler(new com.chua.runtime.apm.handler.HttpClientHandler());
            apm.addHandler(new com.chua.runtime.apm.handler.JdkHttpServerHandler());
            apm.start();
            log.info("[Hotsport] 本进程 APM 引擎已启动（inmemory 存储，与查询同源）");
        } catch (Throwable t) {
            log.warn("[Hotsport] 本进程 APM 启动失败: {}", t.getMessage());
        }
    }

    /**
     * 从 Spring Boot fat jar 的 BOOT-INF/lib/ 中定位 agent jar 并解压到临时目录。
     *
     * @return agent jar 物理路径；找不到返回 null
     */
    private static Path resolveAgentJarFromBootInf() throws java.io.IOException {
        String[] jars = System.getProperty("java.class.path").split(java.io.File.pathSeparator);
        for (String entry : jars) {
            Path candidate = Path.of(entry);
            if (!java.nio.file.Files.isRegularFile(candidate)) {
                continue;
            }
            try (java.util.jar.JarFile outer = new java.util.jar.JarFile(candidate.toFile())) {
                java.util.Optional<java.util.jar.JarEntry> target = java.util.Optional.empty();
                java.util.Enumeration<java.util.jar.JarEntry> entries = outer.entries();
                while (entries.hasMoreElements()) {
                    java.util.jar.JarEntry e = entries.nextElement();
                    String name = e.getName();
                    if (name.startsWith("BOOT-INF/lib/")
                            && name.contains("utils-support-runtime-agent")
                            && name.endsWith(".jar")) {
                        target = java.util.Optional.of(e);
                        break;
                    }
                }
                if (target.isEmpty()) {
                    continue;
                }
                java.io.File dest = java.nio.file.Files.createTempFile("hotsport-agent-", ".jar").toFile();
                try (java.io.InputStream is = outer.getInputStream(target.get());
                     java.io.OutputStream os = java.nio.file.Files.newOutputStream(dest.toPath())) {
                    is.transferTo(os);
                }
                log.info("[Hotsport] Agent jar 提取自 fat jar {} -> {}", target.get().getName(), dest.getAbsolutePath());
                return dest.toPath();
            }
        }
        return null;
    }

    /** Agent 是否已激活。 */
    public boolean isAgentActive() {
        return agentActive;
    }

    // ------------------------------------------------------------------
    // 统计
    // ------------------------------------------------------------------

    /**
     * 概览统计（各类型总量 + agent 状态）。
     *
     * @return 统计 Map
     */
    public Map<String, Object> stats() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("agentActive", agentActive);
        result.put("agentEnabled", agentEnabled);
        Map<String, Long> raw = new LinkedHashMap<>();
        ApmStorage storage = currentStorage();
        if (storage != null) {
            try {
                raw = storage.stats();
            } catch (Exception e) {
                log.warn("[Hotsport] stats() 读取失败", e);
            }
        }
        result.putAll(raw);
        // 键名映射：storage 原始复数键（transmissions/dependencies/leaks/logs）
        // → 前端契约键（*Count），取原始值兜底而非恒 0
        result.putIfAbsent("transmissionCount", raw.getOrDefault("transmissions", 0L));
        result.putIfAbsent("dependencyCount", raw.getOrDefault("dependencies", 0L));
        result.putIfAbsent("leakCount", raw.getOrDefault("leaks", 0L));
        // traceCount / errorCount 从传输明细真实聚合（storage 不单独暴露）
        if (!raw.containsKey("traces") || !raw.containsKey("errors")) {
            try {
                List<TransmissionEvent> all = currentStorage().queryTransmissions(new Query().setLimit(100000));
                long traces = all.stream().map(TransmissionEvent::getTraceId)
                        .filter(Objects::nonNull).distinct().count();
                long errors = all.stream()
                        .filter(e -> e.getStatus() == com.chua.runtime.protocol.StatusCode.ERROR)
                        .count();
                result.putIfAbsent("traceCount", traces);
                result.putIfAbsent("errorCount", errors);
            } catch (Exception e) {
                log.debug("[Hotsport] trace/error 聚合失败: {}", e.getMessage());
            }
        }
        result.putIfAbsent("traceCount", 0L);
        result.putIfAbsent("errorCount", 0L);
        return result;
    }

    // ------------------------------------------------------------------
    // 传输记录
    // ------------------------------------------------------------------

    /**
     * 分页查询传输记录。
     *
     * @param page      页码（从 1 起）
     * @param size      每页条数
     * @param source    源端过滤（可空）
     * @param target    目标端过滤（可空）
     * @param protocol  协议过滤（可空）
     * @param errorOnly 仅看错误（可空）
     * @param startTime 开始时间毫秒（可空）
     * @param endTime   结束时间毫秒（可空）
     * @return 分页结果
     */
    public Map<String, Object> transmissions(int page, int size, String source, String target,
                                              String protocol, Boolean errorOnly,
                                              Long startTime, Long endTime) {
        int offset = Math.max(0, (page - 1) * size);
        // 全量拉取 + 内存过滤后再切片：total 必须是过滤后总数，
        // 否则被 limit 截断导致前端分页器失效
        Query q = new Query().setLimit(100000);
        if (protocol != null && !protocol.isBlank()) {
            q.setProtocol(protocol);
        }
        if (Boolean.TRUE.equals(errorOnly)) {
            q.setErrorOnly(true);
        }
        if (startTime != null) {
            q.setStartTime(startTime);
        }
        if (endTime != null) {
            q.setEndTime(endTime);
        }
        List<TransmissionEvent> all = queryAll(q, storage -> storage.queryTransmissions(q));
        // source/target 在 TransmissionEvent 里是 sourceHost/targetHost，做内存过滤
        List<Map<String, Object>> items = new ArrayList<>();
        for (TransmissionEvent e : all) {
            if (source != null && !source.isBlank()
                    && !containsIgnoreCase(e.getSourceHost(), source)) {
                continue;
            }
            if (target != null && !target.isBlank()
                    && !containsIgnoreCase(e.getTargetHost(), target)) {
                continue;
            }
            items.add(transmissionDto(e));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", items.size());
        result.put("offset", offset);
        result.put("limit", size);
        result.put("items", items.subList(Math.min(offset, items.size()),
                Math.min(offset + size, items.size())));
        return result;
    }

    /**
     * 传输记录 DTO。
     */
    private Map<String, Object> transmissionDto(TransmissionEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("traceId", e.getTraceId());
        m.put("spanId", e.getSpanId());
        m.put("parentSpanId", e.getParentSpanId());
        m.put("source", endpointLabel(e.getSourceProtocol(), e.getSourceSoftware(),
                e.getSourceHost(), e.getSourcePort(), e.getSourcePath()));
        m.put("target", endpointLabel(e.getTargetProtocol(), e.getTargetSoftware(),
                e.getTargetHost(), e.getTargetPort(), e.getTargetPath()));
        m.put("protocol", e.getProtocol());
        m.put("software", e.getSoftware());
        m.put("operation", e.getOperation());
        m.put("status", e.getStatus() != null ? e.getStatus().name() : "OK");
        m.put("statusCode", e.getStatusCode());
        m.put("startTime", toIso(e.getStartTime()));
        m.put("endTime", toIso(e.getEndTime()));
        m.put("durationMs", e.getDuration());
        m.put("bytesOut", e.getBytesOut());
        m.put("bytesIn", e.getBytesIn());
        m.put("errorType", e.getErrorType());
        m.put("errorMessage", e.getErrorMessage());
        return m;
    }

    // ------------------------------------------------------------------
    // 依赖图
    // ------------------------------------------------------------------

    /**
     * 查询依赖边（source → target 聚合）。
     *
     * @param size 上限
     * @return 依赖边列表
     */
    public List<Map<String, Object>> dependencies(int size) {
        Query q = new Query().setLimit(size);
        List<DependencyEdge> all = queryAll(q, storage -> storage.queryDependencies(q));
        List<Map<String, Object>> items = new ArrayList<>();
        for (DependencyEdge edge : all) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("source", endpointLabel(edge));
            m.put("target", endpointLabel(edge, true));
            m.put("protocol", edge.getProtocol() != null ? edge.getProtocol().name() : null);
            m.put("software", edge.getSoftware() != null ? edge.getSoftware().name() : null);
            m.put("callCount", edge.getCallCount());
            m.put("errorCount", edge.getErrorCount());
            m.put("avgDurationMs", edge.avgDuration());
            m.put("lastError", edge.getLastError());
            m.put("lastCallAt", toIso(edge.getLastCallTime()));
            m.put("edgeId", edge.edgeId());
            items.add(m);
        }
        return items;
    }

    // ------------------------------------------------------------------
    // 资源泄漏
    // ------------------------------------------------------------------

    /**
     * 分页查询资源泄漏。
     *
     * @param page         页码
     * @param size         每页条数
     * @param resourceType 类型过滤（可空）
     * @param minAgeMs     最小存活毫秒（可空）
     * @param startTime    开始时间毫秒（可空）
     * @param endTime      结束时间毫秒（可空）
     * @return 分页结果
     */
    public Map<String, Object> leaks(int page, int size, String resourceType, Long minAgeMs,
                                     Long startTime, Long endTime) {
        int offset = Math.max(0, (page - 1) * size);
        // 全量拉取 + 内存过滤后再切片（total 须为过滤后总数，避免分页器失效）
        Query q = new Query().setLimit(100000);
        if (startTime != null) {
            q.setStartTime(startTime);
        }
        if (endTime != null) {
            q.setEndTime(endTime);
        }
        List<LeakRecord> all = queryAll(q, storage -> storage.queryLeaks(q));
        long now = System.currentTimeMillis();
        List<Map<String, Object>> items = new ArrayList<>();
        for (LeakRecord l : all) {
            if (resourceType != null && !resourceType.isBlank()
                    && !resourceType.equalsIgnoreCase(l.getKind())) {
                continue;
            }
            long age = l.isActive() ? (now - l.getCreatedAt()) : (l.getClosedAt() - l.getCreatedAt());
            if (minAgeMs != null && age < minAgeMs) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.getId());
            m.put("handleId", l.getHandleId());
            m.put("resourceType", l.getKind());
            m.put("resourceName", l.getName());
            m.put("thread", l.getThread());
            m.put("active", l.isActive());
            m.put("ageMs", age);
            m.put("detectedAt", toIso(l.getCreatedAt()));
            m.put("stackTrace", l.getStackTrace());
            items.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", items.size());
        result.put("offset", offset);
        result.put("limit", size);
        result.put("items", items.subList(Math.min(offset, items.size()),
                Math.min(offset + size, items.size())));
        return result;
    }

    // ------------------------------------------------------------------
    // 日志
    // ------------------------------------------------------------------

    /**
     * 分页查询日志。
     *
     * @param page  页码
     * @param size  每页条数
     * @param level 级别过滤（可空）
     * @return 分页结果
     */
    public Map<String, Object> logs(int page, int size, String level) {
        int offset = Math.max(0, (page - 1) * size);
        // 全量拉取 + 内存过滤后再切片（total 须为过滤后总数，避免分页器失效）
        Query q = new Query().setLimit(100000);
        List<LogRecord> all = queryAll(q, storage -> storage.queryLogs(q));
        List<Map<String, Object>> items = new ArrayList<>();
        for (LogRecord r : all) {
            if (level != null && !level.isBlank()
                    && !level.equalsIgnoreCase(r.getLevel())) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("timestamp", toIso(r.getTimestamp()));
            m.put("level", r.getLevel());
            m.put("logger", r.getLogger());
            m.put("className", r.getClassName());
            m.put("methodName", r.getMethodName());
            m.put("message", r.getMessage());
            m.put("traceId", r.getTraceId());
            items.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", items.size());
        result.put("offset", offset);
        result.put("limit", size);
        result.put("items", items.subList(Math.min(offset, items.size()),
                Math.min(offset + size, items.size())));
        return result;
    }

    /**
     * 按 traceId 查询整条链路的全部 span（Trace 详情抽屉数据源）。
     *
     * @param traceId 链路 ID
     * @return 该 trace 下所有传输记录 DTO（按开始时间升序）
     */
    public List<Map<String, Object>> traceDetail(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return new ArrayList<>();
        }
        Query q = new Query().setLimit(500).setTraceId(traceId);
        List<TransmissionEvent> all = queryAll(q, storage -> storage.queryTransmissions(q));
        List<Map<String, Object>> items = new ArrayList<>();
        for (TransmissionEvent e : all) {
            items.add(transmissionDto(e));
        }
        items.sort((a, b) -> String.valueOf(a.get("startTime")).compareTo(String.valueOf(b.get("startTime"))));
        return items;
    }

    /**
     * Agent 实时 Trace Span 列表（Traces 页数据源，新事件在前）。
     *
     * <p>映射传输记录为 span 视图：traceId/spanId 直接取自事件；
     * className/methodName 取自 attributes（插桩注入），缺省时退化为 operation。</p>
     *
     * @param limit 返回上限
     * @return span DTO 列表
     */
    public List<Map<String, Object>> agentTraces(int limit) {
        Query q = new Query().setLimit(Math.max(1, limit));
        List<TransmissionEvent> all = queryAll(q, storage -> storage.queryTransmissions(q));
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0 && items.size() < limit; i--) {
            TransmissionEvent e = all.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("traceId", e.getTraceId());
            m.put("spanId", e.getSpanId());
            String op = e.getOperation() != null ? e.getOperation() : "?";
            m.put("className", attr(e, "class.name", op.split(" ")[0]));
            m.put("methodName", attr(e, "method.name", op));
            m.put("operation", op);
            m.put("durationMs", e.getDuration());
            m.put("status", e.getStatus() != null ? e.getStatus().name() : "OK");
            m.put("startTime", toIso(e.getStartTime()));
            items.add(m);
        }
        return items;
    }

    /**
     * Agent 实时依赖边列表（Dependencies 页数据源）。
     *
     * @param limit 返回上限
     * @return 依赖边 DTO 列表
     */
    public List<Map<String, Object>> agentDependencies(int limit) {
        return dependencies(limit);
    }

    /**
     * 读取事件 attribute（多级 key 回退）。
     *
     * @param e     事件
     * @param key   主键
     * @param fallback 兜底值
     * @return 属性值
     */
    private static String attr(TransmissionEvent e, String key, String fallback) {
        Map<String, String> attrs = e.getAttributes();
        if (attrs != null && attrs.get(key) != null) {
            return attrs.get(key);
        }
        return fallback;
    }

    // ------------------------------------------------------------------
    // 全量导出（不受分页限制）
    // ------------------------------------------------------------------

    /** 单次导出上限 — 防止全量拉爆内存。 */
    private static final int EXPORT_CAP = 50000;

    /**
     * 全量导出传输记录为 CSV 文本。
     */
    public String exportTransmissionsCsv(String source, String target, String protocol,
                                         Boolean errorOnly, Long startTime, Long endTime) {
        Query q = new Query().setLimit(EXPORT_CAP);
        if (protocol != null && !protocol.isBlank()) {
            q.setProtocol(protocol);
        }
        if (Boolean.TRUE.equals(errorOnly)) {
            q.setErrorOnly(true);
        }
        if (startTime != null) {
            q.setStartTime(startTime);
        }
        if (endTime != null) {
            q.setEndTime(endTime);
        }
        List<TransmissionEvent> all = queryAll(q, storage -> storage.queryTransmissions(q));
        String[] header = {"id", "traceId", "spanId", "source", "target", "protocol", "operation",
                "status", "statusCode", "startTime", "durationMs", "bytesOut", "bytesIn", "errorType", "errorMessage"};
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TransmissionEvent e : all) {
            if (source != null && !source.isBlank() && !containsIgnoreCase(e.getSourceHost(), source)) {
                continue;
            }
            if (target != null && !target.isBlank() && !containsIgnoreCase(e.getTargetHost(), target)) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("traceId", e.getTraceId());
            m.put("spanId", e.getSpanId());
            m.put("source", endpointLabel(e.getSourceProtocol(), e.getSourceSoftware(),
                    e.getSourceHost(), e.getSourcePort(), e.getSourcePath()));
            m.put("target", endpointLabel(e.getTargetProtocol(), e.getTargetSoftware(),
                    e.getTargetHost(), e.getTargetPort(), e.getTargetPath()));
            m.put("protocol", e.getProtocol());
            m.put("operation", e.getOperation());
            m.put("status", e.getStatus() != null ? e.getStatus().name() : "OK");
            m.put("statusCode", e.getStatusCode());
            m.put("startTime", toIso(e.getStartTime()));
            m.put("durationMs", e.getDuration());
            m.put("bytesOut", e.getBytesOut());
            m.put("bytesIn", e.getBytesIn());
            m.put("errorType", e.getErrorType());
            m.put("errorMessage", e.getErrorMessage());
            rows.add(m);
        }
        return toCsv(header, rows);
    }

    /**
     * 全量导出资源泄漏为 CSV 文本。
     */
    public String exportLeaksCsv(String resourceType, Long minAgeMs, Long startTime, Long endTime) {
        Query q = new Query().setLimit(EXPORT_CAP);
        if (startTime != null) {
            q.setStartTime(startTime);
        }
        if (endTime != null) {
            q.setEndTime(endTime);
        }
        List<LeakRecord> all = queryAll(q, storage -> storage.queryLeaks(q));
        String[] header = {"id", "handleId", "resourceType", "resourceName", "thread", "active", "ageMs", "detectedAt"};
        long now = System.currentTimeMillis();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LeakRecord l : all) {
            if (resourceType != null && !resourceType.isBlank()
                    && !resourceType.equalsIgnoreCase(l.getKind())) {
                continue;
            }
            long age = l.isActive() ? (now - l.getCreatedAt()) : (l.getClosedAt() - l.getCreatedAt());
            if (minAgeMs != null && age < minAgeMs) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.getId());
            m.put("handleId", l.getHandleId());
            m.put("resourceType", l.getKind());
            m.put("resourceName", l.getName());
            m.put("thread", l.getThread());
            m.put("active", l.isActive());
            m.put("ageMs", age);
            m.put("detectedAt", toIso(l.getCreatedAt()));
            rows.add(m);
        }
        return toCsv(header, rows);
    }

    /**
     * 全量导出日志为 CSV 文本。
     */
    public String exportLogsCsv(String level) {
        Query q = new Query().setLimit(EXPORT_CAP);
        List<LogRecord> all = queryAll(q, storage -> storage.queryLogs(q));
        String[] header = {"id", "timestamp", "level", "logger", "className", "methodName", "message", "traceId"};
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LogRecord r : all) {
            if (level != null && !level.isBlank() && !level.equalsIgnoreCase(r.getLevel())) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("timestamp", toIso(r.getTimestamp()));
            m.put("level", r.getLevel());
            m.put("logger", r.getLogger());
            m.put("className", r.getClassName());
            m.put("methodName", r.getMethodName());
            m.put("message", r.getMessage());
            m.put("traceId", r.getTraceId());
            rows.add(m);
        }
        return toCsv(header, rows);
    }

    /**
     * Map 列表 → CSV 文本（RFC 4180）。
     */
    private static String toCsv(String[] header, List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < header.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(header[i]);
        }
        sb.append("\r\n");
        for (Map<String, Object> row : rows) {
            for (int i = 0; i < header.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                Object v = row.get(header[i]);
                String s = v == null ? "" : String.valueOf(v);
                if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
                    sb.append('"').append(s.replace("\"", "\"\"")).append('"');
                } else {
                    sb.append(s);
                }
            }
            sb.append("\r\n");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /** 当前 APM 存储（兜底 Noop，永不为 null）。 */
    private ApmStorage currentStorage() {
        return HotsportProperties.storage();
    }

    /** 取存储并执行查询；存储不可用返回空列表。 */
    private <T> List<T> queryAll(Query q, java.util.function.Function<ApmStorage, List<T>> fn) {
        ApmStorage storage = currentStorage();
        if (storage == null) {
            return new ArrayList<>();
        }
        try {
            List<T> data = fn.apply(storage);
            return data != null ? data : new ArrayList<>();
        } catch (Exception e) {
            log.warn("[Hotsport] 查询失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && needle != null
                && haystack.toLowerCase().contains(needle.toLowerCase());
    }

    private static String endpointLabel(DependencyEdge edge) {
        return edge.getSource() != null ? edge.getSource().displayLabel() : "?";
    }

    private static String endpointLabel(DependencyEdge edge, boolean target) {
        Endpoint t = target ? edge.getTarget() : edge.getSource();
        return t != null ? t.displayLabel() : "?";
    }

    private static String endpointLabel(String protocol, String software, String host,
                                        int port, String path) {
        StringBuilder sb = new StringBuilder();
        if (protocol != null) {
            sb.append(protocol);
        }
        if (software != null) {
            sb.append('[').append(software).append(']');
        }
        if (host != null) {
            sb.append('@').append(host);
        }
        if (port > 0) {
            sb.append(':').append(port);
        }
        if (path != null && !path.isBlank()) {
            sb.append(path);
        }
        return sb.length() == 0 ? "?" : sb.toString();
    }

    private static String toIso(long epochMillis) {
        if (epochMillis <= 0) {
            return "";
        }
        return ISO_FMT.format(Instant.ofEpochMilli(epochMillis));
    }
}
