package com.chua.starter.hotsport.support.service;

import com.chua.runtime.apm.storage.ApmStorage;
import com.chua.runtime.apm.storage.LeakRecord;
import com.chua.runtime.apm.storage.LogRecord;
import com.chua.runtime.apm.storage.Query;
import com.chua.runtime.apm.storage.TransmissionEvent;
import com.chua.runtime.protocol.DependencyEdge;
import com.chua.runtime.protocol.Endpoint;
import com.chua.runtime.protocol.StatusCode;
import com.chua.starter.hotsport.support.config.HotsportProperties;
import com.chua.starter.hotsport.support.model.LeakQuery;
import com.chua.starter.hotsport.support.model.LogQuery;
import com.chua.starter.hotsport.support.model.TransmissionQuery;
import com.chua.starter.agent.support.service.AgentAttachService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

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
@RequiredArgsConstructor
public class ApmQueryService {

    /**
     * 是否已尝试自注入 Agent。
    */
    private final AgentAttachService agentAttachService;

    /**
     * Hotsport 配置（读取 plugin.hotsport.apm.storage.* 交给本地 APM 引擎）。
     */
    private final com.chua.starter.hotsport.support.config.HotsportProperties hotsportProperties;

    /**
     * Agent 自注入是否成功。
    */
    


    /**
    /**
     * agentEnabled
    */
    


    /**
     * shell端口
    */
    

    
    /**
     * agent选项
    */
    

    /**
     * 时间格式：epoch 毫秒 → 前端可读字符串。
    */
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
        // 字节码注入统一由 AgentAttachService 完成（含幂等、失败降级、运行时重注入）
        agentAttachService.attach();
        Thread.ofVirtual().name("hotsport-apm").start(this::startLocalApmEngine);
    }

    /**
     * 实际自注入 + 本进程 APM 启动（虚拟线程执行）。
    */
    private void startLocalApmEngine() {
        // ② 本进程 APM 引擎（与 stats() 同 classloader，数据同源）
        try {
            com.chua.runtime.apm.ApmBootstrap apm =
                    new com.chua.runtime.apm.ApmBootstrap(java.nio.file.Paths.get(System.getProperty("java.io.tmpdir")));
            // 把 plugin.hotsport.apm.storage.* 交给存储层：类型/保留期/容量/落盘目录
            if (hotsportProperties != null && hotsportProperties.getApm() != null) {
                apm.storageConfig(hotsportProperties.getApm().toStorageConfig());
            }
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

    /**
     * Agent 是否已激活。
     * @return 是否成功（true 表示成功）
     */
    public boolean isAgentActive() {
        return agentAttachService.isAgentActive();
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
        result.put("agentActive", agentAttachService.isAgentActive());
        result.put("agentEnabled", agentAttachService.isAgentEnabled());
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
     * @param query 查询参数对象（分页 + 过滤条件，均可空字段走缺省）
     * @return 分页结果
     */
    public Map<String, Object> transmissions(TransmissionQuery query) {
        int page = defaultInt(query.getPage(), 1);
        int size = defaultInt(query.getSize(), 20);
        String source = query.getSource();
        String target = query.getTarget();
        String protocol = query.getProtocol();
        Boolean errorOnly = query.getErrorOnly();
        Long startTime = query.getStartTime();
        Long endTime = query.getEndTime();
        String traceId = query.getTraceId();
        Long durationMin = query.getDurationMin();
        Long durationMax = query.getDurationMax();
        String operation = query.getOperation();
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
            if (traceId != null && !traceId.isBlank()
                    && !traceId.equals(e.getTraceId())) {
                continue;
            }
            long duration = e.getDuration();
            if (durationMin != null && duration < durationMin) {
                continue;
            }
            if (durationMax != null && duration > durationMax) {
                continue;
            }
            if (operation != null && !operation.isBlank()
                    && !containsIgnoreCase(e.getOperation(), operation)) {
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
     * @param e 方法入参 e
     * @return 结果映射，无数据时为空映射
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
     * @param query 查询参数对象（分页 + 过滤条件，均可空字段走缺省）
     * @return 分页结果
     */
    public Map<String, Object> leaks(LeakQuery query) {
        int page = defaultInt(query.getPage(), 1);
        int size = defaultInt(query.getSize(), 20);
        String resourceType = query.getResourceType();
        Long minAgeMs = query.getMinAgeMs();
        Long startTime = query.getStartTime();
        Long endTime = query.getEndTime();
        String resourceName = query.getResourceName();
        String thread = query.getThread();
        Boolean activeOnly = query.getActiveOnly();
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
            if (Boolean.TRUE.equals(activeOnly) && !l.isActive()) {
                continue;
            }
            if (resourceName != null && !resourceName.isBlank()
                    && !containsIgnoreCase(l.getName(), resourceName)) {
                continue;
            }
            if (thread != null && !thread.isBlank()
                    && !containsIgnoreCase(l.getThread(), thread)) {
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
     * @param query 查询参数对象（分页 + 过滤条件，均可空字段走缺省）
     * @return 分页结果
     */
    public Map<String, Object> logs(LogQuery query) {
        int page = defaultInt(query.getPage(), 1);
        int size = defaultInt(query.getSize(), 20);
        String level = query.getLevel();
        String keyword = query.getKeyword();
        String traceId = query.getTraceId();
        Long startTime = query.getStartTime();
        Long endTime = query.getEndTime();
        String className = query.getClassName();
        int offset = Math.max(0, (page - 1) * size);
        // 全量拉取 + 内存过滤后再切片（total 须为过滤后总数，避免分页器失效）
        Query q = new Query().setLimit(100000);
        if (startTime != null) {
            q.setStartTime(startTime);
        }
        if (endTime != null) {
            q.setEndTime(endTime);
        }
        if (traceId != null && !traceId.isBlank()) {
            q.setTraceId(traceId);
        }
        List<LogRecord> all = queryAll(q, storage -> storage.queryLogs(q));
        List<Map<String, Object>> items = new ArrayList<>();
        for (LogRecord r : all) {
            if (level != null && !level.isBlank()
                    && !level.equalsIgnoreCase(r.getLevel())) {
                continue;
            }
            if (keyword != null && !keyword.isBlank()
                    && !containsIgnoreCase(r.getMessage(), keyword)
                    && !containsIgnoreCase(r.getLogger(), keyword)) {
                continue;
            }
            if (className != null && !className.isBlank()
                    && !containsIgnoreCase(r.getClassName(), className)) {
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
     * 全量句柄快照（含分配堆栈 / FD / 线程）。
     *
     * @param limit 每分类上限
     * @return 句柄清单
     */
    public Map<String, Object> handleInventory(int limit) {
        com.chua.runtime.apm.handler.HandleInventoryHandler handler =
                com.chua.runtime.apm.ApmBootstrap.getGlobalHandler(
                        com.chua.runtime.apm.handler.HandleInventoryHandler.class);
        if (handler == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("ts", System.currentTimeMillis());
            empty.put("trackedHandles", List.of());
            empty.put("fileDescriptors", List.of());
            empty.put("threads", List.of());
            empty.put("summary", Map.of());
            return empty;
        }
        return handler.snapshot(limit);
    }

    /**
     * System.out / System.err 实时捕获日志。
     *
     * @param limit 条数上限
     * @return 系统输出条目
     */
    public List<Map<String, Object>> systemOut(int limit) {
        com.chua.runtime.apm.handler.SystemOutCaptureHandler handler =
                com.chua.runtime.apm.ApmBootstrap.getGlobalHandler(
                        com.chua.runtime.apm.handler.SystemOutCaptureHandler.class);
        if (handler == null) {
            return new ArrayList<>();
        }
        int n = limit > 0 ? limit : 200;
        List<Map<String, Object>> items = new ArrayList<>();
        for (com.chua.runtime.apm.handler.SystemOutCaptureHandler.SystemOutEntry e : handler.tail(n)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("timestamp", toIso(e.getTimestamp()));
            m.put("source", e.getSource());
            m.put("level", e.getLevel());
            m.put("line", e.getLine());
            items.add(m);
        }
        return items;
    }

    /**
     * 方法热点排行 — 按类.方法聚合 Span 的耗时/错误/阶段。
     *
     * @param limit 返回上限
     * @return 热点方法列表（按总耗时降序）
     */
    public List<Map<String, Object>> traceHotspots(int limit) {
        com.chua.runtime.apm.handler.TraceHandler handler =
                com.chua.runtime.apm.ApmBootstrap.getGlobalHandler(
                        com.chua.runtime.apm.handler.TraceHandler.class);
        if (handler == null) {
            return new ArrayList<>();
        }
        Map<String, Map<String, Object>> agg = new LinkedHashMap<>();
        for (com.chua.runtime.apm.handler.TraceHandler.Span span : handler.getSpans()) {
            if (span == null || span.getDuration() < 0) {
                continue;
            }
            String key = (span.getClassName() == null ? "" : span.getClassName())
                    + "." + (span.getMethodName() == null ? "" : span.getMethodName())
                    + "|" + (span.getPhase() == null ? "method" : span.getPhase());
            Map<String, Object> row = agg.computeIfAbsent(key, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("className", span.getClassName());
                m.put("methodName", span.getMethodName());
                m.put("phase", span.getPhase());
                m.put("count", 0L);
                m.put("totalMs", 0L);
                m.put("maxMs", 0L);
                m.put("errorCount", 0L);
                m.put("selfTimeMs", 0L);
                return m;
            });
            row.put("count", ((Number) row.get("count")).longValue() + 1);
            row.put("totalMs", ((Number) row.get("totalMs")).longValue() + span.getDuration());
            row.put("maxMs", Math.max(((Number) row.get("maxMs")).longValue(), span.getDuration()));
            row.put("errorCount", "ERROR".equals(span.getStatus())
                    ? ((Number) row.get("errorCount")).longValue() + 1
                    : row.get("errorCount"));
            row.put("selfTimeMs", ((Number) row.get("selfTimeMs")).longValue() + span.getSelfTime());
        }
        List<Map<String, Object>> list = new ArrayList<>(agg.values());
        for (Map<String, Object> row : list) {
            long count = ((Number) row.get("count")).longValue();
            long total = ((Number) row.get("totalMs")).longValue();
            row.put("avgMs", count > 0 ? Math.round(total * 10.0 / count) / 10.0 : 0);
        }
        list.sort((a, b) -> Long.compare(((Number) b.get("totalMs")).longValue(),
                ((Number) a.get("totalMs")).longValue()));
        if (limit > 0 && list.size() > limit) {
            return new ArrayList<>(list.subList(0, limit));
        }
        return list;
    }

    /**
     * 链路阶段耗时汇总 — 按 phase 聚合（http/network/db/cache/mq/rpc/method）。
     *
     * @return 阶段汇总列表
     */
    public List<Map<String, Object>> tracePhases() {
        com.chua.runtime.apm.handler.TraceHandler handler =
                com.chua.runtime.apm.ApmBootstrap.getGlobalHandler(
                        com.chua.runtime.apm.handler.TraceHandler.class);
        if (handler == null) {
            return new ArrayList<>();
        }
        Map<String, Map<String, Object>> agg = new LinkedHashMap<>();
        for (com.chua.runtime.apm.handler.TraceHandler.Span span : handler.getSpans()) {
            if (span == null || span.getDuration() < 0) {
                continue;
            }
            String phase = span.getPhase() == null ? "method" : span.getPhase();
            Map<String, Object> row = agg.computeIfAbsent(phase, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("phase", phase);
                m.put("count", 0L);
                m.put("totalMs", 0L);
                m.put("maxMs", 0L);
                m.put("selfTimeMs", 0L);
                return m;
            });
            row.put("count", ((Number) row.get("count")).longValue() + 1);
            row.put("totalMs", ((Number) row.get("totalMs")).longValue() + span.getDuration());
            row.put("maxMs", Math.max(((Number) row.get("maxMs")).longValue(), span.getDuration()));
            row.put("selfTimeMs", ((Number) row.get("selfTimeMs")).longValue() + span.getSelfTime());
        }
        List<Map<String, Object>> list = new ArrayList<>(agg.values());
        for (Map<String, Object> row : list) {
            long count = ((Number) row.get("count")).longValue();
            long total = ((Number) row.get("totalMs")).longValue();
            row.put("avgMs", count > 0 ? Math.round(total * 10.0 / count) / 10.0 : 0);
        }
        list.sort((a, b) -> Long.compare(((Number) b.get("totalMs")).longValue(),
                ((Number) a.get("totalMs")).longValue()));
        return list;
    }

    /**
     * SkyWalking 兼容导出 — 把 TraceHandler Span 转为 segment/span 结构。
     *
     * @return SkyWalking 兼容 Segment 列表
     */
    public List<Map<String, Object>> skywalkingExport() {
        com.chua.runtime.apm.handler.TraceHandler handler =
                com.chua.runtime.apm.ApmBootstrap.getGlobalHandler(
                        com.chua.runtime.apm.handler.TraceHandler.class);
        if (handler == null) {
            return new ArrayList<>();
        }
        Map<String, List<Map<String, Object>>> segments = new LinkedHashMap<>();
        for (com.chua.runtime.apm.handler.TraceHandler.Span span : handler.getSpans()) {
            if (span == null) {
                continue;
            }
            String traceId = span.getTraceId() == null ? "" : span.getTraceId();
            List<Map<String, Object>> spans = segments.computeIfAbsent(traceId, k -> new ArrayList<>());
            Map<String, Object> sw = new LinkedHashMap<>();
            sw.put("spanId", span.getSpanId());
            sw.put("parentSpanId", span.getParentSpanId());
            sw.put("operationName", (span.getClassName() == null ? "" : span.getClassName())
                    + "." + (span.getMethodName() == null ? "" : span.getMethodName()));
            sw.put("spanType", span.getParentSpanId() == null ? "Entry" : "Local");
            sw.put("spanLayer", span.getPhase() == null ? "Unknown" : span.getPhase());
            sw.put("startTime", span.getStartTime());
            sw.put("endTime", span.getEndTime());
            sw.put("isError", "ERROR".equals(span.getStatus()));
            sw.put("logs", "ERROR".equals(span.getStatus()) ? span.getException() : null);
            sw.put("peer", span.getClientIp());
            spans.add(sw);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> e : segments.entrySet()) {
            Map<String, Object> segment = new LinkedHashMap<>();
            segment.put("traceId", e.getKey());
            segment.put("spanCount", e.getValue().size());
            segment.put("spans", e.getValue());
            result.add(segment);
        }
        return result;
    }

    /**
     * 根因路径自动定位 — 从链路树的最慢子 Span 逐层向下，找出耗时瓶颈路径。
     *
     * @param traceId 链路 ID
     * @return 根因路径（含 traceId / rootSpan / path / bottleneck）
     */
    public Map<String, Object> rootCausePath(String traceId) {
        com.chua.runtime.apm.handler.TraceHandler handler =
                com.chua.runtime.apm.ApmBootstrap.getGlobalHandler(
                        com.chua.runtime.apm.handler.TraceHandler.class);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("traceId", traceId);
        result.put("found", false);
        if (handler == null || traceId == null || traceId.isBlank()) {
            return result;
        }

        // 收集该 trace 的 Span，并按 spanId 建索引
        List<com.chua.runtime.apm.handler.TraceHandler.Span> spans = new ArrayList<>();
        for (com.chua.runtime.apm.handler.TraceHandler.Span span : handler.getSpans()) {
            if (span == null || !traceId.equals(span.getTraceId())) {
                continue;
            }
            spans.add(span);
        }
        if (spans.isEmpty()) {
            return result;
        }

        Map<String, com.chua.runtime.apm.handler.TraceHandler.Span> spanById = new LinkedHashMap<>();
        for (com.chua.runtime.apm.handler.TraceHandler.Span s : spans) {
            spanById.put(s.getSpanId(), s);
        }

        // 找根 Span：parentSpanId 不在本 trace（null 或外部）
        com.chua.runtime.apm.handler.TraceHandler.Span root = null;
        for (com.chua.runtime.apm.handler.TraceHandler.Span s : spans) {
            String parent = s.getParentSpanId();
            if (parent == null || !spanById.containsKey(parent)) {
                root = s;
                break;
            }
        }
        if (root == null && !spans.isEmpty()) {
            // 退化为最早开始的 Span
            root = spans.stream()
                    .min(Comparator.comparingLong(com.chua.runtime.apm.handler.TraceHandler.Span::getStartTime))
                    .orElse(spans.get(0));
        }

        // 沿最慢子 Span 向下
        List<Map<String, Object>> path = new ArrayList<>();
        com.chua.runtime.apm.handler.TraceHandler.Span cursor = root;
        while (cursor != null) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("spanId", cursor.getSpanId());
            node.put("operation", (cursor.getClassName() == null ? "" : cursor.getClassName())
                    + "." + (cursor.getMethodName() == null ? "" : cursor.getMethodName()));
            node.put("phase", cursor.getPhase());
            node.put("durationMs", cursor.getDuration());
            node.put("selfTimeMs", cursor.getSelfTime());
            node.put("status", cursor.getStatus());
            node.put("startTime", cursor.getStartTime());
            path.add(node);

            // 选最慢的直接子 Span
            com.chua.runtime.apm.handler.TraceHandler.Span slowestChild = null;
            for (com.chua.runtime.apm.handler.TraceHandler.Span s : spans) {
                if (cursor.getSpanId() != null && cursor.getSpanId().equals(s.getParentSpanId())) {
                    if (slowestChild == null || s.getDuration() > slowestChild.getDuration()) {
                        slowestChild = s;
                    }
                }
            }
            cursor = slowestChild;
        }

        result.put("found", true);
        result.put("spanCount", spans.size());
        result.put("rootSpan", root == null ? null : root.getSpanId());
        result.put("path", path);
        if (!path.isEmpty()) {
            Map<String, Object> bottleneck = path.get(path.size() - 1);
            result.put("bottleneck", bottleneck);
            result.put("totalDurationMs", path.get(0).get("durationMs"));
        }
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

    /**
     * 单次导出上限 — 防止全量拉爆内存。
    */
    private static final int EXPORT_CAP = 50000;

    /**
     * 分页参数归一：null 或小于 1 时取缺省值。
     *
     * @param value      原始值（可空）
     * @param defaultVal 缺省值
     * @return 归一后的分页值
     */
    private static int defaultInt(Integer value, int defaultVal) {
        return value == null || value < 1 ? defaultVal : value;
    }

    /**
     * 全量导出传输记录为 CSV 文本。
     *
     * @param query 过滤条件参数对象（分页字段忽略，全量导出）
     * @return CSV 文本
     */
    public String exportTransmissionsCsv(TransmissionQuery query) {
        String source = query.getSource();
        String target = query.getTarget();
        String protocol = query.getProtocol();
        Boolean errorOnly = query.getErrorOnly();
        Long startTime = query.getStartTime();
        Long endTime = query.getEndTime();
        String traceId = query.getTraceId();
        Long durationMin = query.getDurationMin();
        Long durationMax = query.getDurationMax();
        String operation = query.getOperation();
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
            if (traceId != null && !traceId.isBlank()
                    && !traceId.equals(e.getTraceId())) {
                continue;
            }
            long duration = e.getDuration();
            if (durationMin != null && duration < durationMin) {
                continue;
            }
            if (durationMax != null && duration > durationMax) {
                continue;
            }
            if (operation != null && !operation.isBlank()
                    && !containsIgnoreCase(e.getOperation(), operation)) {
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
     *
     * @param query 过滤条件参数对象（分页字段忽略，全量导出）
     * @return CSV 文本
     */
    public String exportLeaksCsv(LeakQuery query) {
        String resourceType = query.getResourceType();
        Long minAgeMs = query.getMinAgeMs();
        Long startTime = query.getStartTime();
        Long endTime = query.getEndTime();
        String resourceName = query.getResourceName();
        String thread = query.getThread();
        Boolean activeOnly = query.getActiveOnly();
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
            if (Boolean.TRUE.equals(activeOnly) && !l.isActive()) {
                continue;
            }
            if (resourceName != null && !resourceName.isBlank()
                    && !containsIgnoreCase(l.getName(), resourceName)) {
                continue;
            }
            if (thread != null && !thread.isBlank()
                    && !containsIgnoreCase(l.getThread(), thread)) {
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
     *
     * @param query 过滤条件参数对象（分页字段忽略，全量导出）
     * @return CSV 文本
     */
    public String exportLogsCsv(LogQuery query) {
        String level = query.getLevel();
        String keyword = query.getKeyword();
        String traceId = query.getTraceId();
        Long startTime = query.getStartTime();
        Long endTime = query.getEndTime();
        Query q = new Query().setLimit(EXPORT_CAP);
        if (startTime != null) {
            q.setStartTime(startTime);
        }
        if (endTime != null) {
            q.setEndTime(endTime);
        }
        if (traceId != null && !traceId.isBlank()) {
            q.setTraceId(traceId);
        }
        List<LogRecord> all = queryAll(q, storage -> storage.queryLogs(q));
        String[] header = {"id", "timestamp", "level", "logger", "className", "methodName", "message", "traceId"};
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LogRecord r : all) {
            if (level != null && !level.isBlank() && !level.equalsIgnoreCase(r.getLevel())) {
                continue;
            }
            if (keyword != null && !keyword.isBlank()
                    && !containsIgnoreCase(r.getMessage(), keyword)
                    && !containsIgnoreCase(r.getLogger(), keyword)) {
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
     * @param header 请求头，不允许为 null
     * @param rows 方法入参 rows
     * @return 结果字符串
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

    // ==================================================================
    // SkyWalking 风格增强：服务聚合 / 端点分析 / 瀑布 / 健康 / 链路树 / 拓扑
    // ==================================================================

    /**
     * 服务聚合缓存（stats 窗口内复用）。
    */
    private volatile List<Map<String, Object>> serviceCache = new ArrayList<>();

    /**
     * 端点聚合缓存。
    */
    private volatile List<Map<String, Object>> endpointCache = new ArrayList<>();

    /**
     * 缓存时间戳。
    */
    private volatile long aggregateCacheAt;

    /**
     * 聚合缓存有效期（毫秒）。
    */
    private static final long AGGREGATE_CACHE_TTL_MS = 5000L;

    /**
     * 按 traceId 构建瀑布数据（SkyWalking trace waterfall）。
     *
     * <p>每条 span 计算 offsetMs（相对 trace 起点）、depth（父链深度）、
     * percentWidth（占 trace 总时长比例）、spanKind（按方向推断）。</p>
     *
     * @param traceId 链路 ID
     * @return 瀑布结构 { traceId, spans, totalMs, spanCount }
     */
    public Map<String, Object> traceWaterfall(String traceId) {
        List<Map<String, Object>> detail = traceDetail(traceId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("traceId", traceId);
        result.put("spanCount", detail.size());
        if (detail.isEmpty()) {
            result.put("totalMs", 0L);
            result.put("spans", new ArrayList<>());
            return result;
        }
        long startMin = Long.MAX_VALUE;
        long endMax = Long.MIN_VALUE;
        for (Map<String, Object> s : detail) {
            long start = parseEpochMillis(s.get("startTime"));
            long end = parseEpochMillis(s.get("endTime"));
            if (start > 0 && start < startMin) {
                startMin = start;
            }
            if (end > 0 && end > endMax) {
                endMax = end;
            }
        }
        long totalMs = Math.max(1L, endMax - startMin);
        Map<String, Integer> spanDepth = new ConcurrentHashMap<>();
        Map<String, String> spanParent = new LinkedHashMap<>();
        for (Map<String, Object> s : detail) {
            spanParent.put(String.valueOf(s.get("spanId")),
                    String.valueOf(s.get("parentSpanId")));
        }
        List<Map<String, Object>> spans = new ArrayList<>();
        for (Map<String, Object> s : detail) {
            String spanId = String.valueOf(s.get("spanId"));
            int depth = computeDepth(spanId, spanParent, spanDepth, 0);
            long startEpoch = parseEpochMillis(s.get("startTime"));
            long endEpoch = parseEpochMillis(s.get("endTime"));
            long duration = Math.max(0L, endEpoch - startEpoch);
            Map<String, Object> w = new LinkedHashMap<>(s);
            w.put("offsetMs", Math.max(0L, startEpoch - startMin));
            w.put("durationMs", duration);
            w.put("depth", depth);
            w.put("percentWidth", Math.max(1.0, Math.round(10000.0 * duration / totalMs) / 100.0));
            w.put("spanKind", resolveSpanKind(w));
            w.put("attributes", s.get("attributes") instanceof Map<?, ?> am
                    ? toStringMap(am) : new LinkedHashMap<String, String>());
            spans.add(w);
        }
        spans.sort(Comparator.comparingLong(w -> ((Number) w.get("offsetMs")).longValue()));
        result.put("totalMs", totalMs);
        result.put("spans", spans);
        return result;
    }

    /**
     * 计算 span 深度（父链长度，上限 32 防环）。
    */
    private int computeDepth(String spanId, Map<String, String> parentOf,
                             Map<String, Integer> cache, int guard) {
        if (guard > 32 || spanId == null || "null".equals(spanId) || spanId.isBlank()) {
            return 0;
        }
        Integer cached = cache.get(spanId);
        if (cached != null) {
            return cached;
        }
        String parent = parentOf.get(spanId);
        int depth;
        if (parent == null || "null".equals(parent) || parent.isBlank()) {
            depth = 0;
        } else {
            depth = 1 + computeDepth(parent, parentOf, cache, guard + 1);
        }
        cache.put(spanId, depth);
        return depth;
    }

    /**
     * 推断 span 类型（客户端/服务端/生产者/消费者/内部）。
     *
     * @param s span DTO
     * @return spanKind 字符串
     */
    private static String resolveSpanKind(Map<String, Object> s) {
        String protocol = String.valueOf(s.get("protocol"));
        String operation = String.valueOf(s.get("operation"));
        String target = String.valueOf(s.get("target"));
        if (protocol.contains("KAFKA") || operation.contains("publish") || operation.contains("send")) {
            return "PRODUCER";
        }
        if (protocol.contains("KAFKA") && (operation.contains("consume") || operation.contains("receive"))) {
            return "CONSUMER";
        }
        if (operation.startsWith("GET") || operation.startsWith("POST")
                || operation.startsWith("PUT") || operation.startsWith("DELETE")
                || operation.startsWith("PATCH") || target.contains(":8080")
                || target.contains(":443")) {
            return "SERVER";
        }
        if (protocol.contains("HTTP") || protocol.contains("DB") || protocol.contains("REDIS")) {
            return "CLIENT";
        }
        return "INTERNAL";
    }

    /**
     * 服务聚合（SkyWalking service 层：从传输记录提取 software@host 服务名）。
     *
     * @return 服务列表 { service, endpointCount, callCount, errorCount }
     */
    public List<Map<String, Object>> services() {
        if (System.currentTimeMillis() - aggregateCacheAt < AGGREGATE_CACHE_TTL_MS) {
            return serviceCache;
        }
        Query q = new Query().setLimit(100000);
        List<TransmissionEvent> all = queryAll(q, storage -> storage.queryTransmissions(q));
        Map<String, Map<String, Object>> svc = new LinkedHashMap<>();
        for (TransmissionEvent e : all) {
            String serviceName = serviceLabel(e.getTargetSoftware(), e.getTargetHost());
            if (serviceName == null) {
                continue;
            }
            Map<String, Object> m = svc.computeIfAbsent(serviceName, k -> {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("service", k);
                x.put("endpointCount", 0L);
                x.put("callCount", 0L);
                x.put("errorCount", 0L);
                return x;
            });
            m.put("callCount", (Long) m.get("callCount") + 1L);
            m.put("endpointCount", (Long) m.get("endpointCount") + 1L);
            if (e.getStatus() == StatusCode.ERROR) {
                m.put("errorCount", (Long) m.get("errorCount") + 1L);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>(svc.values());
        out.sort(Comparator.comparingLong(x -> -((Long) x.get("callCount"))));
        serviceCache = out;
        endpointCache = new ArrayList<>();
        aggregateCacheAt = System.currentTimeMillis();
        return out;
    }

    /**
     * 服务名 = software@host（无 host 时仅 software）。
     * @param software 方法入参 software
     * @param host 主机，不允许为 null
     * @return 结果字符串
     */
    private static String serviceLabel(String software, String host) {
        if ((software == null || software.isBlank()) && (host == null || host.isBlank())) {
            return null;
        }
        if (software != null && !software.isBlank() && host != null && !host.isBlank()) {
            return software + "@" + host;
        }
        return software != null ? software : host;
    }

    /**
     * 端点聚合 + 分位数（SkyWalking endpoint 层）。
     *
     * @param limit   返回上限
     * @param keyword 端点关键字过滤（可空，匹配 protocol+operation）
     * @return 端点列表 { endpoint, callCount, errorCount, errorRate, avgMs, p95Ms, p99Ms, maxMs }
     */
    public List<Map<String, Object>> endpoints(int limit, String keyword) {
        boolean filtered = keyword != null && !keyword.isBlank();
        if (!filtered && System.currentTimeMillis() - aggregateCacheAt < AGGREGATE_CACHE_TTL_MS
                && !endpointCache.isEmpty()) {
            return endpointCache;
        }
        Query q = new Query().setLimit(100000);
        List<TransmissionEvent> all = queryAll(q, storage -> storage.queryTransmissions(q));
        Map<String, Map<String, Object>> ep = new LinkedHashMap<>();
        for (TransmissionEvent e : all) {
            String op = e.getOperation() != null ? e.getOperation() : "?";
            String key = (e.getProtocol() != null ? e.getProtocol() : "?") + " " + op;
            if (filtered && !containsIgnoreCase(key, keyword)) {
                continue;
            }
            Map<String, Object> m = ep.computeIfAbsent(key, k -> {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("endpoint", k);
                x.put("durations", new ArrayList<Long>());
                x.put("callCount", 0L);
                x.put("errorCount", 0L);
                return x;
            });
            m.put("callCount", (Long) m.get("callCount") + 1L);
            if (e.getStatus() == StatusCode.ERROR) {
                m.put("errorCount", (Long) m.get("errorCount") + 1L);
            }
            ((List<Long>) m.get("durations")).add(e.getDuration());
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> m : ep.values()) {
            @SuppressWarnings("unchecked")
            List<Long> durations = (List<Long>) m.get("durations");
            long callCount = (Long) m.get("callCount");
            long errorCount = (Long) m.get("errorCount");
            durations.sort(Comparator.naturalOrder());
            long sum = durations.stream().mapToLong(Long::longValue).sum();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("endpoint", m.get("endpoint"));
            row.put("callCount", callCount);
            row.put("errorCount", errorCount);
            row.put("errorRate", callCount > 0
                    ? Math.round(10000.0 * errorCount / callCount) / 100.0 : 0.0);
            row.put("avgMs", callCount > 0 ? sum / callCount : 0L);
            row.put("p95Ms", percentile(durations, 0.95));
            row.put("p99Ms", percentile(durations, 0.99));
            row.put("maxMs", durations.isEmpty() ? 0L : durations.get(durations.size() - 1));
            out.add(row);
        }
        out.sort(Comparator.comparingLong(x -> -((Number) x.get("callCount")).longValue()));
        if (out.size() > limit) {
            out = out.subList(0, limit);
        }
        if (!filtered) {
            endpointCache = out;
            aggregateCacheAt = System.currentTimeMillis();
            if (serviceCache.isEmpty()) {
                serviceCache = services();
            }
        }
        return out;
    }

    /**
     * 排序列表取第 p 分位（线性内插近似，空列表返回 0）。
     * @param sorted 方法入参 sorted
     * @param p 方法入参 p
     * @return 结果数值
     */
    private static long percentile(List<Long> sorted, double p) {
        if (sorted.isEmpty()) {
            return 0L;
        }
        int idx = (int) Math.ceil(p * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }

    /**
     * 依赖边聚合（按 edgeId 归并，修复每事件 callCount=1 的存储侧问题）。
     *
     * @param size 返回上限
     * @return 聚合依赖边列表
     */
    public List<Map<String, Object>> dependenciesAggregated(int size) {
        List<Map<String, Object>> raw = dependencies(size * 50);
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        Map<String, List<Long>> durations = new LinkedHashMap<>();
        for (Map<String, Object> e : raw) {
            String key = String.valueOf(e.get("edgeId"));
            if ("null".equals(key)) {
                key = e.get("source") + "→" + e.get("target");
            }
            Map<String, Object> m = merged.get(key);
            if (m == null) {
                m = new LinkedHashMap<>(e);
                m.put("callCount", 0L);
                m.put("errorCount", 0L);
                m.put("p95Ms", 0L);
                merged.put(key, m);
                durations.put(key, new ArrayList<>());
            }
            long count = ((Number) e.getOrDefault("callCount", 0)).longValue();
            m.put("callCount", (Long) m.get("callCount") + count);
            m.put("errorCount", (Long) m.get("errorCount")
                    + ((Number) e.getOrDefault("errorCount", 0)).longValue());
            durations.get(key).add(((Number) e.getOrDefault("avgDurationMs", 0)).longValue());
        }
        for (Map.Entry<String, Map<String, Object>> entry : merged.entrySet()) {
            Map<String, Object> m = entry.getValue();
            List<Long> ds = durations.get(entry.getKey());
            ds.sort(Comparator.naturalOrder());
            long callCount = (Long) m.get("callCount");
            m.put("avgDurationMs", ds.stream().mapToLong(Long::longValue).sum()
                    / Math.max(1L, ds.size()));
            m.put("p95Ms", percentile(ds, 0.95));
            m.put("errorRate", callCount > 0
                    ? Math.round(10000.0 * (Long) m.get("errorCount") / callCount) / 100.0 : 0.0);
        }
        List<Map<String, Object>> out = new ArrayList<>(merged.values());
        out.sort(Comparator.comparingLong(x -> -((Number) x.get("callCount")).longValue()));
        return out.size() > size ? new ArrayList<>(out.subList(0, size)) : out;
    }

    /**
     * 系统健康状态（SkyWalking 饱和度派生指标）。
     *
     * <p>窗口内错误率 &gt; 10% 或堆 &gt; 90% → SATURATED；
     * 错误率 &gt; 2% 或有活跃泄漏 → DEGRADED；否则 HEALTHY。</p>
     *
     * @return 健康 { health, qps, errorRate, activeLeaks, windowSeconds }
     */
    public Map<String, Object> health() {
        long windowSeconds = 60L;
        long cutoff = System.currentTimeMillis() - windowSeconds * 1000L;
        Query q = new Query().setLimit(100000).setStartTime(cutoff);
        List<TransmissionEvent> recent = queryAll(q, storage -> storage.queryTransmissions(q));
        long total = recent.size();
        long errors = recent.stream()
                .filter(e -> e.getStatus() == StatusCode.ERROR).count();
        double errorRate = total > 0
                ? Math.round(10000.0 * errors / total) / 100.0 : 0.0;
        double qps = total / (double) windowSeconds;
        int activeLeaks = countActiveLeaks();
        double heapPercent = lastJvmMemoryPercent();

        String health;
        if (heapPercent > 90.0 || errorRate > 10.0) {
            health = "SATURATED";
        } else if (heapPercent > 75.0 || errorRate > 2.0 || activeLeaks > 0) {
            health = "DEGRADED";
        } else {
            health = "HEALTHY";
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("health", health);
        result.put("qps", Math.round(qps * 100.0) / 100.0);
        result.put("errorRate", errorRate);
        result.put("activeLeaks", activeLeaks);
        result.put("windowSeconds", windowSeconds);
        return result;
    }

    /**
     * 活跃泄漏计数（APM 存储兜底 0）。
     * @return 结果数值
     */
    private int countActiveLeaks() {
        try {
            ApmStorage storage = currentStorage();
            if (storage == null) {
                return 0;
            }
            List<LeakRecord> leaks = storage.queryLeaks(new Query().setLimit(100000));
            int count = 0;
            for (LeakRecord l : leaks != null ? leaks : List.<LeakRecord>of()) {
                if (l.isActive()) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            log.debug("[Hotsport] 活跃泄漏统计失败: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 当前堆使用百分比（PlatformMXBean 直读，失败返回 0）。
     * @return 结果数值
     */
    private static double lastJvmMemoryPercent() {
        try {
            java.lang.management.MemoryUsage heap =
                    java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            return heap.getMax() > 0 ? 100.0 * heap.getUsed() / heap.getMax() : 0.0;
        } catch (Exception e) {
            log.debug("[Hotsport] 堆使用率读取失败: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Agent 链路树（trace → span 两级 ReTree 数据源）。
     *
     * @param limit trace 上限
     * @return trace 节点列表（children = span 节点）
     */
    public List<Map<String, Object>> agentTracesTree(int limit) {
        Query q = new Query().setLimit(100000);
        List<TransmissionEvent> all = queryAll(q, storage -> storage.queryTransmissions(q));
        Map<String, List<TransmissionEvent>> byTrace = new LinkedHashMap<>();
        for (int i = all.size() - 1; i >= 0; i--) {
            TransmissionEvent e = all.get(i);
            String traceId = e.getTraceId() != null ? e.getTraceId() : "root";
            byTrace.computeIfAbsent(traceId, k -> new ArrayList<>()).add(e);
            if (byTrace.size() >= limit) {
                break;
            }
        }
        List<Map<String, Object>> tree = new ArrayList<>();
        int built = 0;
        for (Map.Entry<String, List<TransmissionEvent>> entry : byTrace.entrySet()) {
            if (built >= limit) {
                break;
            }
            built++;
            List<TransmissionEvent> spans = entry.getValue();
            long startMin = spans.stream().mapToLong(TransmissionEvent::getStartTime)
                    .filter(t -> t > 0).min().orElse(0L);
            long endMax = spans.stream().mapToLong(TransmissionEvent::getEndTime)
                    .filter(t -> t > 0).max().orElse(0L);
            long errors = spans.stream()
                    .filter(e -> e.getStatus() == StatusCode.ERROR).count();
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", entry.getKey());
            node.put("traceId", entry.getKey());
            node.put("label", "trace-" + entry.getKey().substring(0, Math.min(8, entry.getKey().length())));
            node.put("spanCount", spans.size());
            node.put("totalMs", Math.max(0L, endMax - startMin));
            node.put("startTime", toIso(startMin));
            node.put("status", errors > 0 ? "ERROR" : "OK");
            node.put("error", errors > 0);
            List<Map<String, Object>> children = new ArrayList<>();
            for (TransmissionEvent e : spans) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("id", e.getSpanId());
                c.put("spanId", e.getSpanId());
                c.put("traceId", e.getTraceId());
                c.put("label", e.getOperation() != null ? e.getOperation() : e.getProtocol());
                c.put("operation", e.getOperation());
                c.put("durationMs", e.getDuration());
                c.put("status", e.getStatus() != null ? e.getStatus().name() : "OK");
                c.put("startTime", toIso(e.getStartTime()));
                c.put("error", e.getStatus() == StatusCode.ERROR);
                children.add(c);
            }
            node.put("children", children);
            tree.add(node);
        }
        return tree;
    }

    // ------------------------------------------------------------------
    // SkyWalking 风格增强：慢调用 / 错误排名 / SLA / 数据清理
    // ------------------------------------------------------------------

    /**
     * 慢调用排行 — 按耗时降序取前 N 条传输记录。
     *
     * @param limit 返回上限
     * @return 慢调用 DTO 列表
     */
    public List<Map<String, Object>> slowCalls(int limit) {
        try {
            Query q = new Query().setLimit(100000);
            List<TransmissionEvent> all = queryAll(q, storage -> storage.queryTransmissions(q));
            all.sort(Comparator.comparingLong(TransmissionEvent::getDuration).reversed());
            List<Map<String, Object>> result = new ArrayList<>();
            for (int i = 0; i < Math.min(limit, all.size()); i++) {
                result.add(transmissionDto(all.get(i)));
            }
            return result;
        } catch (Exception e) {
            log.debug("[Hotsport] slowCalls 查询失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 慢 SQL 分析 — 聚合 SQL 类协议传输记录（MYSQL/POSTGRE/ORACLE/SQLSERVER/H2 等）。
     *
     * @param limit         返回上限
     * @param minDurationMs 最小耗时阈值
     * @return 慢 SQL 列表（按总耗时降序）
     */
    public List<Map<String, Object>> slowSql(int limit, long minDurationMs) {
        try {
            Query q = new Query().setLimit(100000);
            List<TransmissionEvent> all = queryAll(q, storage -> storage.queryTransmissions(q));
            java.util.Set<String> sqlProtocols = java.util.Set.of(
                    "MYSQL", "POSTGRE", "POSTGRESQL", "ORACLE", "SQLSERVER", "H2", "DB2", "CLICKHOUSE");
            List<TransmissionEvent> sql = new ArrayList<>();
            for (TransmissionEvent e : all) {
                String protocol = e.getProtocol() != null ? e.getProtocol().toUpperCase() : "";
                if (sqlProtocols.stream().anyMatch(protocol::contains)
                        && e.getDuration() >= minDurationMs) {
                    sql.add(e);
                }
            }
            sql.sort(Comparator.comparingLong(TransmissionEvent::getDuration).reversed());
            List<Map<String, Object>> result = new ArrayList<>();
            for (int i = 0; i < Math.min(limit, sql.size()); i++) {
                TransmissionEvent e = sql.get(i);
                Map<String, Object> m = transmissionDto(e);
                m.put("sql", e.getOperation());
                m.put("db", e.getTargetHost() + ":" + e.getTargetPort());
                result.add(m);
            }
            return result;
        } catch (Exception e) {
            log.debug("[Hotsport] slowSql 查询失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 错误排行 — 按 errorType + errorMessage 分组聚合，取前 N 条。
     *
     * @param limit 返回上限
     * @return 错误排行列表
     */
    public List<Map<String, Object>> errorRanking(int limit) {
        try {
            Query q = new Query().setLimit(100000);
            List<TransmissionEvent> all = queryAll(q, storage -> storage.queryTransmissions(q));
            java.util.Map<String, java.util.Map<String, Object>> groups = new java.util.LinkedHashMap<>();
            java.util.Map<String, java.util.List<Long>> durations = new java.util.LinkedHashMap<>();
            java.util.Map<String, java.util.List<Long>> timestamps = new java.util.LinkedHashMap<>();
            for (TransmissionEvent e : all) {
                String errType = e.getErrorType() != null ? e.getErrorType() : "";
                String errMsg = e.getErrorMessage() != null ? e.getErrorMessage() : "";
                String key = errType + "\0" + errMsg;
                if (key.equals("\0")) {
                    continue;
                }
                groups.computeIfAbsent(key, k -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("errorType", errType);
                    m.put("errorMessage", errMsg);
                    m.put("callCount", 0L);
                    m.put("avgDurationMs", 0L);
                    m.put("lastOccurrence", null);
                    return m;
                });
                java.util.Map<String, Object> m = groups.get(key);
                m.put("callCount", (Long) m.get("callCount") + 1L);
                durations.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(e.getDuration());
                timestamps.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(e.getStartTime());
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (java.util.Map.Entry<String, java.util.Map<String, Object>> entry : groups.entrySet()) {
                String key = entry.getKey();
                java.util.Map<String, Object> m = entry.getValue();
                java.util.List<Long> ds = durations.get(key);
                long sum = ds.stream().mapToLong(Long::longValue).sum();
                m.put("avgDurationMs", ds.isEmpty() ? 0L : sum / ds.size());
                java.util.List<Long> ts = timestamps.get(key);
                long maxTs = ts.stream().mapToLong(Long::longValue).max().orElse(0L);
                m.put("lastOccurrence", toIso(maxTs));
                result.add(m);
            }
            result.sort((a, b) -> Long.compare((Long) b.get("callCount"), (Long) a.get("callCount")));
            if (result.size() > limit) {
                result = new ArrayList<>(result.subList(0, limit));
            }
            return result;
        } catch (Exception e) {
            log.debug("[Hotsport] errorRanking 查询失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * SLA 统计 — 全量传输记录汇总。
     *
     * @return 包含 totalCount/errorCount/successCount/slaPercent/uptimeMs/qps
     */
    public Map<String, Object> slaStats() {
        try {
            Query q = new Query().setLimit(100000);
            List<TransmissionEvent> all = queryAll(q, storage -> storage.queryTransmissions(q));
            long totalCount = all.size();
            long errorCount = all.stream()
                    .filter(e -> e.getStatus() == com.chua.runtime.protocol.StatusCode.ERROR)
                    .count();
            long successCount = totalCount - errorCount;
            double slaPercent = totalCount > 0 ? Math.round(10000.0 * successCount / totalCount) / 100.0 : 100.0;
            long firstStart = all.stream()
                    .mapToLong(TransmissionEvent::getStartTime)
                    .filter(t -> t > 0).min().orElse(System.currentTimeMillis());
            long uptimeMs = System.currentTimeMillis() - firstStart;
            long oneMinCutoff = System.currentTimeMillis() - 60_000L;
            long recentCount = all.stream()
                    .filter(e -> e.getStartTime() >= oneMinCutoff).count();
            double qps = recentCount / 60.0;
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("totalCount", totalCount);
            result.put("errorCount", errorCount);
            result.put("successCount", successCount);
            result.put("slaPercent", Math.round(slaPercent * 100.0) / 100.0);
            result.put("uptimeMs", uptimeMs);
            result.put("qps", Math.round(qps * 100.0) / 100.0);
            return result;
        } catch (Exception e) {
            log.debug("[Hotsport] slaStats 查询失败: {}", e.getMessage());
            return new java.util.LinkedHashMap<>();
        }
    }

    /**
     * 数据清理 — 按保留时长删除过期记录。
     *
     * @param retentionMs 保留毫秒数
     * @return 清理统计
     */
    public Map<String, Object> cleanup(long retentionMs) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        try {
            ApmStorage storage = currentStorage();
            if (storage != null) {
                long cleaned = storage.cleanup(retentionMs);
                result.put("cleanupMs", retentionMs);
                result.put("cleanedCount", cleaned);
            } else {
                result.put("cleanupMs", retentionMs);
                result.put("cleanedCount", 0L);
            }
            Map<String, Object> statsMap = new java.util.LinkedHashMap<>();
            try {
                if (storage != null) {
                    Map<String, Long> raw = storage.stats();
                    statsMap.putAll(raw);
                }
            } catch (Exception ex) {
                log.debug("[Hotsport] cleanup stats 查询失败: {}", ex.getMessage());
            }
            result.put("stats", statsMap);
        } catch (Exception e) {
            log.debug("[Hotsport] cleanup 失败: {}", e.getMessage());
            result.put("cleanupMs", retentionMs);
            result.put("cleanedCount", 0L);
        }
        return result;
    }

    /**
     * 系统拓扑（真实运行值：主机 / PID / 监听端口 / 网络接口 / 核心模块）。
     *
     * @return 拓扑 { center, jvmPid, modules, ports, connections, nodes, edges }
     */
    public Map<String, Object> systemTopology() {
        Map<String, Object> t = new LinkedHashMap<>();
        String hostname = "unknown";
        try {
            hostname = java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            // 保留 unknown
        }
        t.put("center", hostname);
        t.put("jvmPid", java.lang.management.ManagementFactory.getRuntimeMXBean().getPid());
        List<Map<String, Object>> modules = new ArrayList<>();
        String[] moduleNames = {"spring-boot", "spring-support-common-starter",
                "utils-support-runtime-apm", "utils-support-runtime-protocol",
                "utils-support-runtime-agent", "spring-support-hotsport-starter",
                "spring-support-agent-starter", "common-starter"};
        for (String mn : moduleNames) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", mn);
            m.put("label", mn);
            m.put("kind", "module");
            modules.add(m);
        }
        t.put("modules", modules);
        List<Map<String, Object>> ports = new ArrayList<>();
        addPort(ports, "HTTP", "Tomcat/Spring Boot", 8890);
        addPort(ports, "AGENT", "Hotsport Shell", agentAttachService.getShellPort());
        t.put("ports", ports);
        List<Map<String, Object>> connections = new ArrayList<>();
        try {
            java.util.Enumeration<java.net.NetworkInterface> en =
                    java.net.NetworkInterface.getNetworkInterfaces();
            while (en.hasMoreElements()) {
                java.net.NetworkInterface ni = en.nextElement();
                if (ni.isLoopback() || !ni.isUp()) {
                    continue;
                }
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("iface", ni.getName());
                c.put("local", ni.getHardwareAddress() != null
                        ? bytesToHex(ni.getHardwareAddress()) : "?");
                c.put("mac", ni.getHardwareAddress() != null
                        ? bytesToHex(ni.getHardwareAddress()) : "?");
                connections.add(c);
            }
        } catch (Exception e) {
            log.debug("[Hotsport] 网络接口枚举失败: {}", e.getMessage());
        }
        t.put("connections", connections);
        List<Map<String, Object>> nodes = new ArrayList<>();
        Map<String, Object> centerNode = new LinkedHashMap<>();
        centerNode.put("id", "center");
        centerNode.put("label", hostname);
        centerNode.put("symbolSize", 56);
        nodes.add(centerNode);
        List<Map<String, Object>> edges = new ArrayList<>();
        for (Map<String, Object> p : ports) {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("id", "port-" + p.get("port"));
            n.put("label", String.valueOf(p.get("port")));
            n.put("symbolSize", 30);
            nodes.add(n);
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("source", "center");
            e.put("target", "port-" + p.get("port"));
            edges.add(e);
        }
        for (Map<String, Object> m : modules) {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("id", m.get("id"));
            n.put("label", String.valueOf(m.get("label")));
            n.put("symbolSize", 20);
            nodes.add(n);
        }
        t.put("nodes", nodes);
        t.put("edges", edges);
        return t;
    }

    private static void addPort(List<Map<String, Object>> ports, String protocol,
                                String software, int port) {
        if (port <= 0) {
            return;
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("port", port);
        p.put("label", software);
        p.put("protocol", protocol);
        p.put("software", software);
        ports.add(p);
    }

    /**
     * 字节数组转为Hex。
     *
     * @param bytes 字节数组，不允许为 null
     * @return 结果字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(String.format("%02x", bytes[i]));
        }
        return sb.toString();
    }

    /**
     * 通用 long 解析（Number / 数字字符串 / null 兜底 0）。
     * @param v 方法入参 v
     * @return 结果数值
     */
    private static long parseLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v != null) {
            try {
                return Long.parseLong(String.valueOf(v));
            } catch (NumberFormatException ignored) {
                // 保持 0
            }
        }
        return 0L;
    }

    /**
     * 从 toIso() 生成的 "yyyy-MM-dd HH:mm:ss" 字符串还原 epoch 毫秒（失败返回 0）。
     * @param v 方法入参 v
     * @return 结果数值
     */
    private static long parseEpochMillis(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v == null) {
            return 0L;
        }
        String s = String.valueOf(v);
        if (s.isEmpty()) {
            return 0L;
        }
        try {
            java.time.format.DateTimeFormatter fmt =
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(s, fmt);
            java.time.Instant instant = ldt.atZone(java.time.ZoneId.systemDefault()).toInstant();
            return instant.toEpochMilli();
        } catch (Exception e2) {
            return parseLong(v);
        }
    }

    /**
     * 将任意 Map 值转换为 String 键值 Map（缺省空 Map）。
     * @param raw 方法入参 raw
     * @return 结果映射，无数据时为空映射
     */
    private static Map<String, String> toStringMap(Map<?, ?> raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw != null) {
            raw.forEach((k, v) -> out.put(String.valueOf(k), v == null ? "" : String.valueOf(v)));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /**
     * 当前 APM 存储（兜底 Noop，永不为 null）。
     * @return ApmStorage 对象
     */
    private ApmStorage currentStorage() {
        return HotsportProperties.storage();
    }

    /**
     * 取存储并执行查询；存储不可用返回空列表。
     * @param q 方法入参 q
     * @param fn 函数，不允许为 null
     * @return 结果列表，无数据时为空列表
     */
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

    /**
     * containsIgnoreCase。
     *
     * @param haystack 方法入参 haystack
     * @param needle 方法入参 needle
     * @return 是否成功（true 表示成功）
     */
    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && needle != null
                && haystack.toLowerCase().contains(needle.toLowerCase());
    }

    /**
     * endpoint标签。
     *
     * @param edge 方法入参 edge
     * @return 结果字符串
     */
    private static String endpointLabel(DependencyEdge edge) {
        return edge.getSource() != null ? edge.getSource().displayLabel() : "?";
    }

    /**
     * endpoint标签。
     *
     * @param edge 方法入参 edge
     * @param target 目标，不允许为 null
     * @return 结果字符串
     */
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

    /**
     * 转为Iso。
     *
     * @param epochMillis epoch毫秒数，不允许为 null
     * @return 结果字符串
     */
    private static String toIso(long epochMillis) {
        if (epochMillis <= 0) {
            return "";
        }
        return ISO_FMT.format(Instant.ofEpochMilli(epochMillis));
    }
}
