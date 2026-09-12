package com.chua.starter.hotsport.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.runtime.apm.storage.ApmStorage;
import com.chua.runtime.apm.storage.LeakRecord;
import com.chua.runtime.apm.storage.LogRecord;
import com.chua.runtime.apm.storage.Query;
import com.chua.runtime.apm.storage.TransmissionEvent;
import com.chua.runtime.protocol.DependencyEdge;
import com.chua.starter.hotsport.support.config.HotsportProperties;
import com.chua.runtime.protocol.Endpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
* APM 数据查询服务 — 统一门面，对 {@link StorageManager} 读操作做降级兜底。
*
* <p>设计原则：APM 数据"能 attach 就读实时，不能 attach 就返回空 + 明确标记"，
* 保证前端无论 智能体 是否成功注入，页面始终可演示、不报错。</p>
*
* @author CH
* @since 4.0.0.42
 */
@Slf4j
@Service
public class ApmQueryService {

    /** 是否已尝试自注入 智能体。 */
    private final AtomicBoolean attachAttempted = new AtomicBoolean(false);

    /** 智能体 自注入是否成功。 */
    private volatile boolean agentActive = false;

    @Value("${hotsport.agent.enabled:true}")
    private boolean agentEnabled; // 智能体已启用

    @Value("${hotsport.agent.shell-port:4567}")
    private int shellPort; // Shell端口

    @Value("${hotsport.agent.options:}")
    private String agentOptions; // 智能体期权

    /** 时间格式：轮次 毫秒 → 前端可读 ISO 字符串。 */
    private static final DateTimeFormatter ISO_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.systemDefault());

    /**
    * 启动时尝试自注入 智能体。
    * 失败仅告警，不阻断应用（前端走降级路径）。
     */
    @PostConstruct
    public void attachAgent() {
        if (!agentEnabled) {
            log.info("[Hotsport] Agent 自注入被配置禁用 (hotsport.agent.enabled=false)");
            return;
        }
        // 异步执行：agentmain 涉及字节码 retransform（大量类），阻塞主线程会拖慢 Spring 上下文启动
        Thread.ofVirtual().name("hotsport-agent-attach").start(this::doAttachAgent);
    }

    /** 实际自注入逻辑（虚拟线程执行）。 */
    private void doAttachAgent() {
        if (!attachAttempted.compareAndSet(false, true)) {
            return;
        }
        try {
            Path agentJar = resolveAgentJarFromBootInf();
            var boot = com.chua.runtime.starter.RuntimeBoot.create()
                    .withAgent(agentJar)
                    .withAgentOptions(agentOptions.isEmpty() ? "port=" + shellPort : agentOptions)
                    .attachSelf();
            agentActive = true;
            log.info("[Hotsport] Agent 自注入完成，Shell 端口 {}", shellPort);
        } catch (Throwable t) {
            agentActive = false;
            log.warn("[Hotsport] Agent 自注入失败（APM 将返回空数据）: {}", t.getMessage());
        }
    }

    /**
    * 从 Spring Boot fat jar 的 {@code BOOT-INF/lib/} 中定位 agent jar 并解压到临时目录。
    *
    * <p>{@code RuntimeBoot.resolveSelfAgentPath()} 依赖 System.getProperty("java.class.path")，
    * 但 Spring Boot fat jar 的 LaunchedClassLoader 不会回填该属性，导致探测失败。
    * 此方法直接解析外层 jar 文件作为兜底方案。</p>
    *
    * @return agent jar 物理路径；找不到返回 null
     */
    private static Path resolveAgentJarFromBootInf() {
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
            } catch (Exception e) {
                log.debug("[Hotsport] 检查 {} 失败: {}", candidate, e.getMessage());
            }
        }
        log.warn("[Hotsport] 未找到 agent jar，使用默认路径");
        return null;
    }

    /**
    * 智能体 是否已激活。
    *
    * @return 是否智能体活跃的结果
     */
    public boolean isAgentActive() {
        return agentActive;
    }

    // ------------------------------------------------------------------
    // 统计
    // ------------------------------------------------------------------

    /**
    * 概览统计（各类型总量 + 智能体 状态）。
    *
    * @return 统计 映射
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
        result.putIfAbsent("transmissionCount", 0L);
        result.putIfAbsent("leakCount", 0L);
        result.putIfAbsent("errorCount", 0L);
        result.putIfAbsent("traceCount", 0L);
        result.putIfAbsent("dependencyCount", 0L);
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
        Query q = new Query().setLimit(size).setOffset(offset);
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
 // 源/Target 在 transmission事件 里是 源主机/Target主机，做内存过滤
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
        result.put("items", items);
        return result;
    }

    /**
    * transmissiondto。
    * @param e e
    * @return transmissionDto的结果
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
    * 查询依赖边（源 → Target 聚合）。
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
        Query q = new Query().setLimit(size).setOffset(Math.max(0, (page - 1) * size));
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
        result.put("offset", Math.max(0, (page - 1) * size));
        result.put("limit", size);
        result.put("items", items);
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
        Query q = new Query().setLimit(size).setOffset(Math.max(0, (page - 1) * size));
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
        result.put("offset", Math.max(0, (page - 1) * size));
        result.put("limit", size);
        result.put("items", items);
        return result;
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /**
    * 当前 APM 存储（兜底 Noop，永不为 空）。
    *
    * @return 当前storage的结果
     */
    private ApmStorage currentStorage() {
        return HotsportProperties.storage();
    }

    /**
    * 取存储并执行查询；存储不可用返回空列表。
    *
    * @param q q
    * @param fn fn
    * @return 查询全部的结果
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
    * containsignore大小写。
    * @param haystack haystack
    * @param needle needle
    * @return containsignore大小写的结果
     */
    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && needle != null
                && haystack.toLowerCase().contains(needle.toLowerCase());
    }

    /**
    * 端点标签。
    * @param edge edge
    * @return 端点标签的结果
     */
    private static String endpointLabel(DependencyEdge edge) {
        return edge.getSource() != null ? edge.getSource().displayLabel() : "?";
    }

    /**
    * 端点标签。
    * @param edge edge
    * @param target Target
    * @return 端点标签的结果
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
    * 转为iso。
    * @param epochMillis 轮次millis
    * @return 转为iso的结果
     */
    private static String toIso(long epochMillis) {
        if (epochMillis <= 0) {
            return "";
        }
        return ISO_FMT.format(Instant.ofEpochMilli(epochMillis));
    }
}
