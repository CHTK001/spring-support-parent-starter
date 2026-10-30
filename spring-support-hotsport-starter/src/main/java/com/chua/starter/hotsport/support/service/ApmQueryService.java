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
import org.springframework.context.ApplicationContext;
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
     * Spring 容器：用于读取本进程真实的 WebServer 监听端口与实现名。
     *
     * <p>端口必须来自实际绑定的 WebServer，不能写死常量——{@code --server.port}
     * 与随机端口都以实际绑定值为准。</p>
     */
    private final ApplicationContext applicationContext;

    /**
     * 本进程 APM 引擎（启动后保留引用，供运行时热更新存储配置使用）。
     *
     * <p>只有「本进程启动的引擎」才会被这里持有；agent 在别的 JVM 里跑的副本身份不归本进程管。</p>
     */
    private volatile com.chua.runtime.apm.ApmBootstrap localApm;

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
     *
     * <p><b>为什么必须异步</b>：自注入需要扫描 classpath 定位 agent jar，
     * 依赖越多越慢（实测上百个 jar 时可耗数十秒）。若在 {@code @PostConstruct}
     * 同步执行，会把整个 Web 容器的启动一起拖住 —— 端口迟迟不开放，
     * 看起来就是「应用起不来」。观测组件不该有这种能力，故一律放到虚拟线程。</p>
     */
    @PostConstruct
    public void attachAgent() {
        Thread.ofVirtual()
                .name("hotsport-apm")
                .start(() -> {
                    try {
                        // 字节码注入统一由 AgentAttachService 完成（含幂等、失败降级、运行时重注入）
                        agentAttachService.attach();
                    } catch (Throwable t) {
                        log.warn("[Hotsport] Agent 自注入失败（不影响业务进程）: {}", t.getMessage());
                    }
                    startLocalApmEngine();
                });
    }

    /**
     * 实际自注入 + 本进程 APM 启动（虚拟线程执行）。
    */
    private void startLocalApmEngine() {
        // ② 本进程 APM 引擎（与 stats() 同 classloader，数据同源）
        try {
            // 自定义存储实现必须先注册：StorageManager.resolve 只在注册表里找非内置类型
            com.chua.starter.hotsport.support.storage.JdbcStorage.register();
            com.chua.runtime.apm.ApmBootstrap apm =
                    new com.chua.runtime.apm.ApmBootstrap(java.nio.file.Paths.get(System.getProperty("java.io.tmpdir")));
            // 把 plugin.hotsport.apm.storage.* 交给存储层：类型/保留期/容量/落盘目录/写入模式。
            // 注册的是「活来源」而不是快照：以后配置对象被修改并触发热更新时能读到最新值。
            apm.storageConfigProvider(this::currentStorageConfig);
            // 另存一份启动时快照，作为 provider 不可用时的兜底
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
            this.localApm = apm;
            log.info("[Hotsport] 本进程 APM 引擎已启动（存储: {}，与查询同源）",
                    com.chua.runtime.apm.storage.StorageManager.get().name());
        } catch (Throwable t) {
            log.warn("[Hotsport] 本进程 APM 启动失败: {}", t.getMessage());
        }
    }

    /**
     * 当前应生效的存储配置（活来源，热更新时重新读取）。
     *
     * <p>用 {@code includeDefaults=true}：运行中把某项改回默认（如清空落盘路径）时，
     * 必须让下游看到「键存在但值为空」，否则会被判定为配置未变化而不生效。</p>
     *
     * <p><b>MySQL 连接三键为什么走系统属性</b>：{@code ApmBootstrap.resolveStorageConfig()}
     * 只把固定键（type/retention/capacity/path/async/...）与「系统属性里以
     * {@code apm.storage.} 开头的键」合并进 {@code StorageConfig}；provider 返回的其它键
     * 只会以「键集合」形式参与，值不会被带入。若这里返回 url 等键，反而会让
     * {@code resolveStorageConfig} 判定「系统属性已有该键」而跳过合并。因此连接三键
     * 这里剔除并写入系统属性，让解析逻辑能真正读到它们。</p>
     *
     * @return 存储配置键值对
     */
    private Map<String, String> currentStorageConfig() {
        Map<String, String> raw;
        if (hotsportProperties == null || hotsportProperties.getApm() == null) {
            raw = java.util.Collections.emptyMap();
        } else {
            raw = hotsportProperties.getApm().toStorageConfig(true);
        }
        Map<String, String> config = new java.util.LinkedHashMap<>(raw);
        // JDBC 连接信息：从 provider 移除，改为系统属性（ApmBootstrap 只合并系统属性里的 apm.storage.*）
        String url = config.remove("apm.storage.url");
        String username = config.remove("apm.storage.username");
        String password = config.remove("apm.storage.password");
        if (url != null) {
            System.setProperty("apm.storage.url", url);
        }
        if (username != null) {
            System.setProperty("apm.storage.username", username);
        }
        if (password != null) {
            System.setProperty("apm.storage.password", password);
        }
        return config;
    }

    /**
     * 运行时热更新存储配置（{@code plugin.hotsport.apm.storage.*}）。
     *
     * <p>典型场景：内存存储跑久了想改成落盘；或落盘文件挪位置。改完配置后调本方法，
     * 无需重启进程。新存储先完整启动成功才会替换旧存储，失败则旧存储继续工作。</p>
     *
     * @return 结果：成功/无需变更 {@code success=true}；失败时 {@code success=false} 并带原因
     */
    public Map<String, Object> reloadStorage() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        com.chua.runtime.apm.ApmBootstrap apm = this.localApm;
        if (apm == null) {
            // 本进程引擎未起来（启动失败或仍在初始化），直接改存储层也能生效，但不保证同源
            result.put("success", false);
            result.put("message", "本进程 APM 引擎尚未启动，无法热更新");
            result.put("before", com.chua.runtime.apm.storage.StorageManager.describe());
            return result;
        }
        Map<String, Object> before = new java.util.LinkedHashMap<>(apm.storageStatus());
        boolean ok;
        try {
            // StorageManager.shutdown 会清空注册表，热更新前补注册，保证 mysql 类型可解析
            com.chua.starter.hotsport.support.storage.JdbcStorage.register();
            ok = apm.reloadStorageConfig();
        } catch (Throwable e) {
            log.warn("[Hotsport] 存储热更新异常: {}", e.getMessage(), e);
            ok = false;
            result.put("error", e.getMessage());
        }
        Map<String, Object> after = new java.util.LinkedHashMap<>(apm.storageStatus());
        result.put("success", ok);
        result.put("message", ok ? "存储配置已生效" : "新配置不可用，已保留原存储");
        result.put("before", before);
        result.put("after", after);
        result.put("config", currentStorageConfig());
        result.put("updatedAt", System.currentTimeMillis());
        log.info("[Hotsport] 存储热更新: success={}, {} -> {}",
                ok, before.get("type"), after.get("type"));
        return result;
    }

    /**
     * 当前存储的运行时状态（运维自检：确认配置写了什么、实际生效什么）。
     *
     * @return 状态映射
     */
    public Map<String, Object> storageStatus() {
        Map<String, Object> status;
        com.chua.runtime.apm.ApmBootstrap apm = this.localApm;
        if (apm != null) {
            try {
                status = new java.util.LinkedHashMap<>(apm.storageStatus());
            } catch (Throwable e) {
                status = new java.util.LinkedHashMap<>(com.chua.runtime.apm.storage.StorageManager.describe());
            }
        } else {
            status = new java.util.LinkedHashMap<>(com.chua.runtime.apm.storage.StorageManager.describe());
        }
        status.put("configured", currentStorageConfig());
        status.put("engineActive", apm != null);
        return status;
    }

    /**
     * APM 自身线程画像 — 调试用。
     *
     * <p>APM 是观测组件，自己最不该泄漏。这里把几个关键内部线程按名前缀统计
     * 当前存活数，正常情况都应是 0 或 1；数量随时间上涨就说明反复热更新 /
     * 反复注入在累积线程，需要立刻处理。</p>
     *
     * @return 线程画像（前缀 → 存活数）
     */
    public Map<String, Object> ownThreads() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        int maintenance = 0;
        int attach = 0;
        int engine = 0;
        int total = 0;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            String name = t.getName();
            total++;
            if (name.startsWith("apm-") && name.contains("maintenance")) {
                // sqlite / memory / jdbc 三种实现的自维护线程统一识别
                maintenance++;
            }
            if (name.startsWith("agent-attach")) {
                attach++;
            }
            if (name.startsWith("hotsport-apm")) {
                engine++;
            }
        }
        result.put("maintenance", maintenance);
        result.put("agentAttach", attach);
        result.put("engine", engine);
        result.put("jvmTotalThreads", total);
        return result;
    }

    /**
     * 从 Spring Boot fat jar 的 BOOT-INF/lib/ 中定位 agent jar 并解压到临时目录。
     * @return agent jar 物理路径；找不到返回 null
     * @throws java.io.IOException 异常
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
        // 依赖边计数：storage 的 dependencies 计数器只在字节码级依赖采集生效时才累加，
        // 纯 Spring Boot 应用恒为 0。此时回落到「从真实传输记录派生的边」条数，
        // 避免概览页显示「依赖 0 条」而依赖拓扑页却有真实边的自相矛盾。
        long dependencyCount = ((Number) result.getOrDefault("dependencyCount", 0L)).longValue();
        if (dependencyCount <= 0L) {
            try {
                int derived = dependencies(Integer.MAX_VALUE).size();
                if (derived > 0) {
                    dependencyCount = derived;
                }
            } catch (Exception e) {
                log.debug("[Hotsport] 依赖边派生失败: {}", e.getMessage());
            }
        }
        // 两个键同源写回。result.putAll(raw) 会把 storage 的复数原始键 dependencies
        // 原样带出，而它只在字节码级依赖采集生效时才累加 —— 纯 Spring Boot 应用恒为 0。
        // 若只补 *Count，同一份 stats 里就会同时出现 dependencies=0 与
        // dependencyCount=N，读哪一个都对、拼在一起却自相矛盾。
        result.put("dependencyCount", dependencyCount);
        result.put("dependencies", dependencyCount);
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
        // 真实毫秒时间戳：ISO 串只有秒精度，时间轴/甘特条必须用这个算偏移
        m.put("startEpochMs", e.getStartTime());
        m.put("endEpochMs", e.getEndTime());
        m.put("durationMs", e.getDuration());
        m.put("bytesOut", e.getBytesOut());
        m.put("bytesIn", e.getBytesIn());
        m.put("errorType", e.getErrorType());
        m.put("errorMessage", e.getErrorMessage());
        m.put("attributes", e.getAttributes() != null ? e.getAttributes() : new LinkedHashMap<String, String>());
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
        // 依赖图存储为空时（字节码依赖采集未覆盖当前应用），从真实传输记录派生边。
        // 派生的每一条边都来自真实请求的 sourceHost → targetHost，不做任何伪造。
        if (items.isEmpty()) {
            items = deriveDependenciesFromTransmissions(size);
        }
        return items;
    }

    /**
     * 从真实传输记录派生依赖边。
     *
     * <p>字节码级依赖采集（{@code DependencyGraphHandler}）只对特定第三方库生效；
     * 一个纯 Spring Boot 应用没有这些库时依赖图会为空。此方法把真实的
     * {@code sourceHost → targetHost:targetPort} 调用关系聚合为边，保证拓扑图
     * 展示的始终是真实发生过的调用，而不是空图或模拟数据。</p>
     *
     * @param size 返回上限
     * @return 依赖边列表（按调用量降序）
     */
    private List<Map<String, Object>> deriveDependenciesFromTransmissions(int size) {
        Query q = new Query().setLimit(100000);
        List<TransmissionEvent> all = queryAll(q, storage -> storage.queryTransmissions(q));
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        Map<String, List<Long>> durations = new LinkedHashMap<>();
        for (TransmissionEvent e : all) {
            String source = e.getSourceHost() != null && !e.getSourceHost().isBlank()
                    ? e.getSourceHost() : "unknown";
            String targetHost = e.getTargetHost() != null && !e.getTargetHost().isBlank()
                    ? e.getTargetHost() : "unknown";
            int targetPort = e.getTargetPort();
            String target = targetPort > 0 ? targetHost + ":" + targetPort : targetHost;
            String protocol = e.getProtocol() != null ? e.getProtocol() : e.getSourceProtocol();
            String key = source + "→" + target + "#" + protocol;
            Map<String, Object> m = merged.get(key);
            if (m == null) {
                m = new LinkedHashMap<>();
                m.put("source", source);
                m.put("target", target);
                m.put("protocol", protocol);
                m.put("software", null);
                m.put("callCount", 0L);
                m.put("errorCount", 0L);
                m.put("lastError", null);
                m.put("lastCallAt", null);
                m.put("edgeId", key);
                merged.put(key, m);
                durations.put(key, new ArrayList<>());
            }
            m.put("callCount", (Long) m.get("callCount") + 1L);
            if (e.getStatus() == StatusCode.ERROR) {
                m.put("errorCount", (Long) m.get("errorCount") + 1L);
                m.put("lastError", e.getErrorMessage());
            }
            m.put("lastCallAt", toIso(e.getEndTime()));
            durations.get(key).add(e.getDuration());
        }
        List<Map<String, Object>> out = new ArrayList<>();
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
            out.add(m);
        }
        out.sort(Comparator.comparingLong(x -> -((Number) x.get("callCount")).longValue()));
        return out.size() > size ? new ArrayList<>(out.subList(0, size)) : out;
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
            // traceId 由本层精确过滤：部分存储实现不透传 Query.traceId，
            // 若只依赖存储过滤会导致「按 traceId 下钻」失效
            if (traceId != null && !traceId.isBlank()
                    && !traceId.equals(r.getTraceId())) {
                continue;
            }
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
            // 捕获的是带 ANSI 颜色码的原始控制台输出，展示前统一清洗
            m.put("line", stripAnsi(e.getLine()));
            items.add(m);
        }
        return items;
    }

    /**
     * 统一 Span 视图 — 优先取 Agent {@code TraceHandler} 的方法级 Span；
     * 当 Agent 未产生任何方法级 Span（例如纯 Spring Boot 应用未接入字节码方法采集）时，
     * 回落到**真实传输记录**派生，字段全部取自真实请求，不做任何填充或模拟。
     *
     * @return 归一化 Span 视图列表（空列表表示确实没有任何真实链路数据）
     */
    private List<Map<String, Object>> spanViews() {
        List<Map<String, Object>> views = new ArrayList<>();
        com.chua.runtime.apm.handler.TraceHandler handler =
                com.chua.runtime.apm.ApmBootstrap.getGlobalHandler(
                        com.chua.runtime.apm.handler.TraceHandler.class);
        if (handler != null) {
            try {
                for (com.chua.runtime.apm.handler.TraceHandler.Span span : handler.getSpans()) {
                    if (span == null || span.getDuration() < 0) {
                        continue;
                    }
                    Map<String, Object> v = new LinkedHashMap<>();
                    v.put("className", span.getClassName());
                    v.put("methodName", span.getMethodName());
                    v.put("phase", span.getPhase() == null ? "method" : span.getPhase());
                    v.put("durationMs", span.getDuration());
                    v.put("selfTimeMs", span.getSelfTime());
                    v.put("error", "ERROR".equals(span.getStatus()));
                    v.put("traceId", span.getTraceId());
                    v.put("spanId", span.getSpanId());
                    v.put("parentSpanId", span.getParentSpanId());
                    v.put("startTime", span.getStartTime());
                    v.put("endTime", span.getEndTime());
                    v.put("operation", (span.getClassName() == null ? "" : span.getClassName())
                            + "." + (span.getMethodName() == null ? "" : span.getMethodName()));
                    v.put("displayName", (span.getClassName() == null ? "" : span.getClassName())
                            + "." + (span.getMethodName() == null ? "" : span.getMethodName()));
                    views.add(v);
                }
            } catch (Exception e) {
                log.debug("[Hotsport] 读取 Agent Span 失败: {}", e.getMessage());
            }
        }
        if (!views.isEmpty()) {
            return views;
        }
        // 真实回落：一次真实 HTTP 请求就是一条真实 Span（无子 Span，selfTime = 耗时）
        List<TransmissionEvent> all = queryAll(new Query().setLimit(100000),
                storage -> storage.queryTransmissions(new Query().setLimit(100000)));
        for (TransmissionEvent e : all) {
            if (e == null || e.getDuration() < 0) {
                continue;
            }
            String op = e.getOperation() != null ? e.getOperation() : "?";
            String cls = attr(e, "class.name", null);
            String mtd = attr(e, "method.name", null);
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("className", cls);
            v.put("methodName", mtd);
            v.put("phase", e.getProtocol() != null ? e.getProtocol().toLowerCase() : "http");
            v.put("durationMs", e.getDuration());
            v.put("selfTimeMs", e.getDuration());
            v.put("error", e.getStatus() == StatusCode.ERROR);
            v.put("traceId", e.getTraceId());
            v.put("spanId", e.getSpanId());
            v.put("parentSpanId", e.getParentSpanId());
            v.put("startTime", e.getStartTime());
            v.put("endTime", e.getEndTime());
            v.put("operation", op);
            // HTTP 请求记录没有类/方法语义，展示名直接用真实操作串（如 GET /api/apm/stats）
            v.put("displayName", op);
            views.add(v);
        }
        return views;
    }

    /**
     * 方法热点排行 — 按类.方法聚合 Span 的耗时/错误/阶段。
     *
     * @param limit 返回上限
     * @return 热点方法列表（按总耗时降序）
     */
    public List<Map<String, Object>> traceHotspots(int limit) {
        Map<String, Map<String, Object>> agg = new LinkedHashMap<>();
        for (Map<String, Object> span : spanViews()) {
            String className = (String) span.get("className");
            String methodName = (String) span.get("methodName");
            String phase = (String) span.get("phase");
            long duration = ((Number) span.get("durationMs")).longValue();
            String key = (className == null ? "" : className)
                    + "." + (methodName == null ? "" : methodName)
                    + "|" + (phase == null ? "method" : phase);
            Map<String, Object> row = agg.computeIfAbsent(key, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("className", className);
                m.put("methodName", methodName);
                m.put("displayName", span.get("displayName"));
                m.put("phase", phase);
                m.put("count", 0L);
                m.put("totalMs", 0L);
                m.put("maxMs", 0L);
                m.put("errorCount", 0L);
                m.put("selfTimeMs", 0L);
                return m;
            });
            row.put("count", ((Number) row.get("count")).longValue() + 1);
            row.put("totalMs", ((Number) row.get("totalMs")).longValue() + duration);
            row.put("maxMs", Math.max(((Number) row.get("maxMs")).longValue(), duration));
            row.put("errorCount", Boolean.TRUE.equals(span.get("error"))
                    ? ((Number) row.get("errorCount")).longValue() + 1
                    : row.get("errorCount"));
            row.put("selfTimeMs", ((Number) row.get("selfTimeMs")).longValue()
                    + ((Number) span.get("selfTimeMs")).longValue());
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
        Map<String, Map<String, Object>> agg = new LinkedHashMap<>();
        for (Map<String, Object> span : spanViews()) {
            String phase = (String) span.get("phase");
            if (phase == null) {
                phase = "method";
            }
            long duration = ((Number) span.get("durationMs")).longValue();
            Map<String, Object> row = agg.computeIfAbsent(phase, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("phase", k);
                m.put("count", 0L);
                m.put("totalMs", 0L);
                m.put("maxMs", 0L);
                m.put("selfTimeMs", 0L);
                return m;
            });
            row.put("count", ((Number) row.get("count")).longValue() + 1);
            row.put("totalMs", ((Number) row.get("totalMs")).longValue() + duration);
            row.put("maxMs", Math.max(((Number) row.get("maxMs")).longValue(), duration));
            row.put("selfTimeMs", ((Number) row.get("selfTimeMs")).longValue()
                    + ((Number) span.get("selfTimeMs")).longValue());
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
        Map<String, List<Map<String, Object>>> segments = new LinkedHashMap<>();
        for (Map<String, Object> span : spanViews()) {
            String traceId = span.get("traceId") == null ? "" : (String) span.get("traceId");
            List<Map<String, Object>> spans = segments.computeIfAbsent(traceId, k -> new ArrayList<>());
            Map<String, Object> sw = new LinkedHashMap<>();
            sw.put("spanId", span.get("spanId"));
            sw.put("parentSpanId", span.get("parentSpanId"));
            sw.put("operationName", span.get("operation"));
            sw.put("spanType", span.get("parentSpanId") == null ? "Entry" : "Local");
            sw.put("spanLayer", span.get("phase") == null ? "Unknown" : span.get("phase"));
            sw.put("startTime", span.get("startTime"));
            sw.put("endTime", span.get("endTime"));
            sw.put("isError", Boolean.TRUE.equals(span.get("error")));
            sw.put("logs", Boolean.TRUE.equals(span.get("error"))
                    ? String.valueOf(span.get("operation")) + " 失败" : null);
            sw.put("peer", span.get("className"));
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
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("traceId", traceId);
        result.put("found", false);
        if (traceId == null || traceId.isBlank()) {
            return result;
        }

        // 收集该 trace 的 Span：优先 Agent 方法级 Span，缺失时按 traceId 精确回落到真实传输记录
        List<Map<String, Object>> spans = new ArrayList<>();
        com.chua.runtime.apm.handler.TraceHandler handler =
                com.chua.runtime.apm.ApmBootstrap.getGlobalHandler(
                        com.chua.runtime.apm.handler.TraceHandler.class);
        if (handler != null) {
            try {
                for (com.chua.runtime.apm.handler.TraceHandler.Span span : handler.getSpans()) {
                    if (span == null || !traceId.equals(span.getTraceId()) || span.getDuration() < 0) {
                        continue;
                    }
                    Map<String, Object> v = new LinkedHashMap<>();
                    v.put("spanId", span.getSpanId());
                    v.put("parentSpanId", span.getParentSpanId());
                    v.put("operation", (span.getClassName() == null ? "" : span.getClassName())
                            + "." + (span.getMethodName() == null ? "" : span.getMethodName()));
                    v.put("phase", span.getPhase());
                    v.put("durationMs", span.getDuration());
                    v.put("selfTimeMs", span.getSelfTime());
                    v.put("status", span.getStatus());
                    v.put("startTime", span.getStartTime());
                    spans.add(v);
                }
            } catch (Exception e) {
                log.debug("[Hotsport] 读取 Agent Span 失败: {}", e.getMessage());
            }
        }
        if (spans.isEmpty()) {
            // 真实回落：该 traceId 下的真实请求记录本身就是一次 Span
            Query q = new Query().setLimit(100000).setTraceId(traceId);
            for (TransmissionEvent e : queryAll(q, storage -> storage.queryTransmissions(q))) {
                if (e == null || e.getDuration() < 0) {
                    continue;
                }
                String op = e.getOperation() != null ? e.getOperation() : "?";
                Map<String, Object> v = new LinkedHashMap<>();
                v.put("spanId", e.getSpanId());
                v.put("parentSpanId", e.getParentSpanId());
                v.put("operation", op);
                v.put("phase", e.getProtocol() != null ? e.getProtocol().toLowerCase() : "http");
                v.put("durationMs", e.getDuration());
                v.put("selfTimeMs", e.getDuration());
                v.put("status", e.getStatus() != null ? e.getStatus().name() : "OK");
                v.put("startTime", e.getStartTime());
                spans.add(v);
            }
        }
        if (spans.isEmpty()) {
            return result;
        }

        Map<String, Map<String, Object>> spanById = new LinkedHashMap<>();
        for (Map<String, Object> s : spans) {
            spanById.put((String) s.get("spanId"), s);
        }

        // 找根 Span：parentSpanId 不在本 trace（null 或外部）
        Map<String, Object> root = null;
        for (Map<String, Object> s : spans) {
            String parent = (String) s.get("parentSpanId");
            if (parent == null || !spanById.containsKey(parent)) {
                root = s;
                break;
            }
        }
        if (root == null) {
            // 退化为最早开始的 Span
            root = spans.stream()
                    .min(Comparator.comparingLong(
                            x -> ((Number) x.get("startTime")).longValue()))
                    .orElse(spans.get(0));
        }

        // 沿最慢子 Span 向下
        List<Map<String, Object>> path = new ArrayList<>();
        Map<String, Object> cursor = root;
        while (cursor != null) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("spanId", cursor.get("spanId"));
            node.put("operation", cursor.get("operation"));
            node.put("phase", cursor.get("phase"));
            node.put("durationMs", cursor.get("durationMs"));
            node.put("selfTimeMs", cursor.get("selfTimeMs"));
            node.put("status", cursor.get("status"));
            node.put("startTime", cursor.get("startTime"));
            path.add(node);

            // 选最慢的直接子 Span
            Map<String, Object> slowestChild = null;
            for (Map<String, Object> s : spans) {
                if (cursor.get("spanId") != null && cursor.get("spanId").equals(s.get("parentSpanId"))) {
                    if (slowestChild == null
                            || ((Number) s.get("durationMs")).longValue()
                            > ((Number) slowestChild.get("durationMs")).longValue()) {
                        slowestChild = s;
                    }
                }
            }
            cursor = slowestChild;
        }

        result.put("found", true);
        result.put("spanCount", spans.size());
        result.put("rootSpan", root.get("spanId"));
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
            Map<String, Object> m = transmissionDto(e);
            m.put("isError", e.getStatus() == StatusCode.ERROR);
            m.put("shortError", shortError(e));
            items.add(m);
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
        return agentTraces(limit, false);
    }

    /**
     * Agent 实时 Trace Span 列表，可选只返回错误链路。
     *
     * <p>每个 span 增加 {@code isError}/{@code shortError} 字段（前端可直接高亮），
     * 不再依赖前端去猜状态字段。</p>
     *
     * @param limit     返回上限
     * @param errorOnly true 时只保留错误状态的 span
     * @return span DTO 列表
     */
    public List<Map<String, Object>> agentTraces(int limit, boolean errorOnly) {
        Query q = new Query().setLimit(Math.max(1, limit * 20));
        List<TransmissionEvent> all = queryAll(q, storage -> storage.queryTransmissions(q));
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0 && items.size() < limit; i--) {
            TransmissionEvent e = all.get(i);
            if (errorOnly && e.getStatus() != StatusCode.ERROR) {
                continue;
            }
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
            m.put("isError", e.getStatus() == StatusCode.ERROR);
            m.put("shortError", shortError(e));
            m.put("startTime", toIso(e.getStartTime()));
            items.add(m);
        }
        return items;
    }

    /**
     * 截断的错误信息（前端列表高亮用，避免整条堆栈撑爆表格）。
     *
     * @param e 事件
     * @return 前 160 字符的错误描述；无错误返回空串
     */
    private static String shortError(TransmissionEvent e) {
        if (e.getStatus() != StatusCode.ERROR) {
            return "";
        }
        String msg = attr(e, "error.message", "");
        if (msg.isEmpty() && e.getErrorMessage() != null) {
            msg = e.getErrorMessage();
        }
        if (msg == null || msg.isEmpty()) {
            return "ERROR";
        }
        String oneLine = msg.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() > 160 ? oneLine.substring(0, 160) : oneLine;
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
            if (traceId != null && !traceId.isBlank() && !traceId.equals(r.getTraceId())) {
                continue;
            }
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
    private volatile long serviceCacheAt;

    /**
     * 端点聚合缓存时间戳。
     */
    private volatile long endpointCacheAt;

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
        // 把「本次请求真实命中的控制器方法」展开成子 span：
        // 首层 HTTP → 子层 class.method，瀑布与链路树看到的是同一份真实数据
        List<Map<String, Object>> expanded = new ArrayList<>();
        for (Map<String, Object> s : detail) {
            expanded.add(s);
            Map<String, Object> handler = handlerSpanOf(s);
            if (handler != null) {
                expanded.add(handler);
            }
        }
        detail = expanded;
        long startMin = Long.MAX_VALUE;
        long endMax = Long.MIN_VALUE;
        for (Map<String, Object> s : detail) {
            long start = epochOf(s, "startEpochMs", "startTime");
            long end = epochOf(s, "endEpochMs", "endTime");
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
            long startEpoch = epochOf(s, "startEpochMs", "startTime");
            long duration = Math.max(0L, asLong(s.get("durationMs")));
            if (duration == 0L) {
                long endEpoch = epochOf(s, "endEpochMs", "endTime");
                duration = Math.max(0L, endEpoch - startEpoch);
            }
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
        result.put("spanCount", spans.size());
        result.put("totalMs", totalMs);
        result.put("spans", spans);
        return result;
    }

    /**
     * 由传输记录 attributes 还原控制器方法 span（真实测量值，缺一不产出）。
     *
     * @param s 传输记录 DTO
     * @return 方法 span；数据不全时返回 {@code null}
     */
    private Map<String, Object> handlerSpanOf(Map<String, Object> s) {
        Object attrsObj = s.get("attributes");
        if (!(attrsObj instanceof Map<?, ?> attrs)) {
            return null;
        }
        String cls = asText(attrs.get("class.name"));
        String mtd = asText(attrs.get("method.name"));
        String durText = asText(attrs.get("method.duration"));
        if (cls == null || mtd == null || durText == null) {
            return null;
        }
        long duration;
        try {
            duration = Long.parseLong(durText.trim());
        } catch (NumberFormatException e) {
            return null;
        }
        if (duration < 0) {
            return null;
        }
        long offset = 0L;
        String offText = asText(attrs.get("method.startOffset"));
        if (offText != null) {
            try {
                offset = Math.max(0L, Long.parseLong(offText.trim()));
            } catch (NumberFormatException ignored) {
                offset = 0L;
            }
        }
        long base = epochOf(s, "startEpochMs", "startTime");
        long start = base > 0 ? base + offset : 0L;
        Map<String, Object> h = new LinkedHashMap<>();
        String parentSpanId = String.valueOf(s.get("spanId"));
        h.put("spanId", parentSpanId + "-handler");
        h.put("parentSpanId", parentSpanId);
        h.put("traceId", s.get("traceId"));
        h.put("operation", cls + "." + mtd);
        h.put("className", cls);
        h.put("methodName", mtd);
        h.put("source", s.get("source"));
        h.put("target", s.get("target"));
        h.put("protocol", "METHOD");
        h.put("software", "METHOD");
        h.put("status", s.get("status"));
        h.put("statusCode", s.get("statusCode"));
        h.put("startTime", toIso(start));
        h.put("endTime", toIso(start + duration));
        h.put("startEpochMs", start);
        h.put("endEpochMs", start + duration);
        h.put("durationMs", duration);
        h.put("errorType", s.get("errorType"));
        h.put("errorMessage", s.get("errorMessage"));
        h.put("bytesOut", 0L);
        h.put("bytesIn", 0L);
        return h;
    }

    /**
     * 取毫秒时间戳：优先真实 epoch 字段，缺失时回落到 ISO 串解析。
     *
     * @param s       记录
     * @param epochKey epoch 字段名
     * @param isoKey   ISO 字段名
     * @return epoch 毫秒（失败 0）
     */
    private static long epochOf(Map<String, Object> s, String epochKey, String isoKey) {
        long epoch = asLong(s.get(epochKey));
        if (epoch > 0) {
            return epoch;
        }
        return parseEpochMillis(s.get(isoKey));
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
        if (System.currentTimeMillis() - serviceCacheAt < AGGREGATE_CACHE_TTL_MS) {
            return serviceCache;
        }
        Query q = new Query().setLimit(100000);
        List<TransmissionEvent> all = queryAll(q, storage -> storage.queryTransmissions(q));
        Map<String, Map<String, Object>> svc = new LinkedHashMap<>();
        Map<String, List<Long>> durations = new LinkedHashMap<>();
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
                x.put("totalMs", 0L);
                return x;
            });
            m.put("callCount", (Long) m.get("callCount") + 1L);
            m.put("endpointCount", (Long) m.get("endpointCount") + 1L);
            long dur = Math.max(0L, e.getDuration());
            m.put("totalMs", (Long) m.get("totalMs") + dur);
            durations.computeIfAbsent(serviceName, k -> new ArrayList<>()).add(dur);
            if (e.getStatus() == StatusCode.ERROR) {
                m.put("errorCount", (Long) m.get("errorCount") + 1L);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>(svc.values());
        for (Map<String, Object> m : out) {
            String name = String.valueOf(m.get("service"));
            long callCount = (Long) m.get("callCount");
            List<Long> ds = durations.getOrDefault(name, new ArrayList<>());
            ds.sort(Comparator.naturalOrder());
            m.put("avgMs", callCount > 0 ? Math.round((Long) m.get("totalMs") * 10.0 / callCount) / 10.0 : 0);
            m.put("p95Ms", percentile(ds, 0.95));
            m.put("p99Ms", percentile(ds, 0.99));
            m.put("maxMs", ds.isEmpty() ? 0L : ds.get(ds.size() - 1));
            m.put("errorRate", callCount > 0
                    ? Math.round(10000.0 * (Long) m.get("errorCount") / callCount) / 100.0 : 0.0);
        }
        out.sort(Comparator.comparingLong(x -> -((Long) x.get("callCount"))));
        serviceCache = out;
        serviceCacheAt = System.currentTimeMillis();
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
        if (!filtered && System.currentTimeMillis() - endpointCacheAt < AGGREGATE_CACHE_TTL_MS
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
                // 客户端归因：同一端点的访问来源（真实 sourceHost）去重计数
                x.put("clientCallCounts", new LinkedHashMap<String, Long>());
                return x;
            });
            m.put("callCount", (Long) m.get("callCount") + 1L);
            if (e.getStatus() == StatusCode.ERROR) {
                m.put("errorCount", (Long) m.get("errorCount") + 1L);
            }
            ((List<Long>) m.get("durations")).add(e.getDuration());
            // 记录真实客户端来源（无 sourceHost 时记 unknown，不伪造）
            String client = e.getSourceHost() != null && !e.getSourceHost().isBlank()
                    ? e.getSourceHost() : "unknown";
            @SuppressWarnings("unchecked")
            Map<String, Long> clientCounts = (Map<String, Long>) m.get("clientCallCounts");
            clientCounts.merge(client, 1L, Long::sum);
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
            // 客户端信息：来源数 + 按调用量排序的来源列表（供前端展示「访问客户端」列）
            @SuppressWarnings("unchecked")
            Map<String, Long> clientCounts = (Map<String, Long>) m.get("clientCallCounts");
            List<String> sortedClients = new ArrayList<>(clientCounts.keySet());
            sortedClients.sort((a, b) -> Long.compare(clientCounts.get(b), clientCounts.get(a)));
            row.put("clientCount", (long) clientCounts.size());
            row.put("clients", sortedClients);
            out.add(row);
        }
        out.sort(Comparator.comparingLong(x -> -((Number) x.get("callCount")).longValue()));
        if (out.size() > limit) {
            out = out.subList(0, limit);
        }
        if (!filtered) {
            endpointCache = out;
            endpointCacheAt = System.currentTimeMillis();
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
     * Agent 链路树（Xrebel 风格：首层 HTTP 请求，子层按 {@code parentSpanId} 递归嵌套）。
     *
     * <p><b>层级完全由真实数据决定，不做任何构造</b>：</p>
     * <ul>
     *   <li>首层节点 = 真实传输记录，{@code label} 就是真实请求行（如 {@code GET /api/apm/stats}），
     *       {@code kind=http}；</li>
     *   <li>子层节点 = 真实方法级记录，{@code label} 是真实 {@code class.method}，{@code kind=method}。
     *       来源有两处，都取实测值：传输记录 attributes 里由
     *       {@code ApmHandlerTimingInterceptor} 实测写入的 {@code class.name}/{@code method.name}/
     *       {@code method.duration}，以及 Agent {@code TraceHandler} 采集的方法 Span；</li>
     *   <li>父子关系 = 记录自带的 {@code spanId}/{@code parentSpanId}。只有当方法级记录的父引用
     *       缺失或指向 trace 外的 span 时，才把它挂到同 trace 的根请求下——方法确实执行于该请求内，
     *       traceId 是真实归属，不是编造的层级。</li>
     * </ul>
     *
     * <p>没有方法级数据的链路就只有一个根节点（不补任何假子节点）。</p>
     *
     * @param limit trace 上限
     * @return 根节点列表（每棵树的根是一个真实 HTTP 请求）
     */
    public List<Map<String, Object>> agentTracesTree(int limit) {
        int max = Math.max(1, limit);
        Query q = new Query().setLimit(100000);
        List<TransmissionEvent> all = queryAll(q, storage -> storage.queryTransmissions(q));

        // 1) 传输记录按 traceId 分组（新 → 旧，保持「最近链路在前」）
        Map<String, List<TransmissionEvent>> txByTrace = new LinkedHashMap<>();
        for (int i = all.size() - 1; i >= 0; i--) {
            TransmissionEvent e = all.get(i);
            if (e == null) {
                continue;
            }
            String traceId = e.getTraceId() != null ? e.getTraceId() : "root";
            txByTrace.computeIfAbsent(traceId, k -> new ArrayList<>()).add(e);
            if (txByTrace.size() >= max) {
                break;
            }
        }

        // 2) Agent 方法级 Span 按 traceId 分组（未接入方法采集时为空，不填充任何数据）
        Map<String, List<Map<String, Object>>> methodByTrace = methodSpanNodes();

        // 3) 每个 trace 组装一棵树
        List<Map<String, Object>> roots = new ArrayList<>();
        for (Map.Entry<String, List<TransmissionEvent>> entry : txByTrace.entrySet()) {
            List<Map<String, Object>> flat = new ArrayList<>();
            for (TransmissionEvent e : entry.getValue()) {
                flat.add(transmissionNode(e));
                Map<String, Object> handler = handlerNodeOf(e, flat);
                if (handler != null) {
                    flat.add(handler);
                }
            }
            List<Map<String, Object>> methods = methodByTrace.get(entry.getKey());
            if (methods != null) {
                flat.addAll(methods);
            }
            roots.addAll(nestTraceNodes(flat));
        }
        return roots;
    }

    /**
     * 传输记录 → 树节点（首层 HTTP 请求）。
     *
     * @param e 真实传输记录
     * @return 树节点
     */
    private Map<String, Object> transmissionNode(TransmissionEvent e) {
        String op = e.getOperation() != null ? e.getOperation()
                : (e.getProtocol() != null ? e.getProtocol() : "?");
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", e.getSpanId() != null ? e.getSpanId() : "tx-" + e.getId());
        n.put("spanId", e.getSpanId());
        n.put("parentSpanId", e.getParentSpanId());
        n.put("traceId", e.getTraceId());
        n.put("kind", "http");
        n.put("label", op);
        n.put("operation", op);
        n.put("className", attr(e, "class.name", null));
        n.put("methodName", attr(e, "method.name", null));
        n.put("protocol", e.getProtocol());
        n.put("software", e.getSoftware());
        n.put("source", endpointLabel(e.getSourceProtocol(), e.getSourceSoftware(),
                e.getSourceHost(), e.getSourcePort(), e.getSourcePath()));
        n.put("target", endpointLabel(e.getTargetProtocol(), e.getTargetSoftware(),
                e.getTargetHost(), e.getTargetPort(), e.getTargetPath()));
        n.put("status", e.getStatus() != null ? e.getStatus().name() : "OK");
        n.put("statusCode", e.getStatusCode());
        n.put("durationMs", e.getDuration());
        n.put("startMs", e.getStartTime());
        n.put("endMs", e.getEndTime());
        n.put("startTime", toIso(e.getStartTime()));
        n.put("endTime", toIso(e.getEndTime()));
        n.put("error", e.getStatus() == StatusCode.ERROR);
        n.put("errorType", e.getErrorType());
        n.put("errorMessage", e.getErrorMessage());
        n.put("bytesOut", e.getBytesOut());
        n.put("bytesIn", e.getBytesIn());
        return n;
    }

    /**
     * 由传输记录 attributes 还原「本次请求真实命中的控制器方法」子节点。
     *
     * <p>attributes 由 {@code ApmHandlerTimingInterceptor} 实测写入；三个值缺一不可，
     * 缺任何一个都不产出节点——宁可没有子层，也不拿估算值凑数。</p>
     *
     * @param e    真实传输记录
     * @param flat 当前 trace 已收集的节点（用于去重）
     * @return 方法节点；数据不全或已有同名节点时返回 {@code null}
     */
    private Map<String, Object> handlerNodeOf(TransmissionEvent e, List<Map<String, Object>> flat) {
        String cls = attr(e, "class.name", null);
        String mtd = attr(e, "method.name", null);
        String durText = attr(e, "method.duration", null);
        if (cls == null || mtd == null || durText == null) {
            return null;
        }
        long duration;
        try {
            duration = Long.parseLong(durText.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
        if (duration < 0) {
            return null;
        }
        String label = cls + "." + mtd;
        // 同一个 trace 内已由 Agent 方法 Span 覆盖同一 class.method 时不重复插入
        for (Map<String, Object> n : flat) {
            if ("method".equals(n.get("kind")) && label.equals(n.get("label"))) {
                return null;
            }
        }
        long offset = 0L;
        String offText = attr(e, "method.startOffset", null);
        if (offText != null) {
            try {
                offset = Math.max(0L, Long.parseLong(offText.trim()));
            } catch (NumberFormatException ignored) {
                offset = 0L;
            }
        }
        long start = e.getStartTime() > 0 ? e.getStartTime() + offset : 0L;
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", (e.getSpanId() != null ? e.getSpanId() : "tx-" + e.getId()) + "-handler");
        n.put("spanId", (e.getSpanId() != null ? e.getSpanId() : "tx-" + e.getId()) + "-handler");
        n.put("parentSpanId", e.getSpanId());
        n.put("traceId", e.getTraceId());
        n.put("kind", "method");
        n.put("label", label);
        n.put("operation", label);
        n.put("className", cls);
        n.put("methodName", mtd);
        n.put("protocol", "METHOD");
        n.put("durationMs", duration);
        n.put("startMs", start);
        n.put("endMs", start + duration);
        n.put("startTime", toIso(start));
        n.put("endTime", toIso(start + duration));
        n.put("status", e.getStatus() != null ? e.getStatus().name() : "OK");
        n.put("error", e.getStatus() == StatusCode.ERROR);
        n.put("errorType", e.getErrorType());
        n.put("errorMessage", e.getErrorMessage());
        return n;
    }

    /**
     * Agent {@code TraceHandler} 采集的真实方法级 Span，按 traceId 分组。
     *
     * @return traceId → 方法节点列表（无采集时为空映射）
     */
    private Map<String, List<Map<String, Object>>> methodSpanNodes() {
        Map<String, List<Map<String, Object>>> byTrace = new LinkedHashMap<>();
        com.chua.runtime.apm.handler.TraceHandler handler =
                com.chua.runtime.apm.ApmBootstrap.getGlobalHandler(
                        com.chua.runtime.apm.handler.TraceHandler.class);
        if (handler == null) {
            return byTrace;
        }
        try {
            for (com.chua.runtime.apm.handler.TraceHandler.Span span : handler.getSpans()) {
                if (span == null || span.getDuration() < 0 || span.getTraceId() == null) {
                    continue;
                }
                String cls = span.getClassName();
                String mtd = span.getMethodName();
                String label = (cls == null ? "" : cls) + "." + (mtd == null ? "" : mtd);
                String spanId = span.getSpanId() != null ? span.getSpanId()
                        : "ms-" + label + "-" + span.getStartTime();
                Map<String, Object> n = new LinkedHashMap<>();
                n.put("id", spanId);
                n.put("spanId", spanId);
                n.put("parentSpanId", span.getParentSpanId());
                n.put("traceId", span.getTraceId());
                n.put("kind", "method");
                n.put("label", label);
                n.put("operation", label);
                n.put("className", cls);
                n.put("methodName", mtd);
                n.put("protocol", span.getPhase() == null ? "METHOD" : span.getPhase());
                n.put("durationMs", span.getDuration());
                n.put("selfTimeMs", span.getSelfTime());
                n.put("startMs", span.getStartTime());
                n.put("endMs", span.getEndTime());
                n.put("startTime", toIso(span.getStartTime()));
                n.put("endTime", toIso(span.getEndTime()));
                n.put("status", "ERROR".equals(span.getStatus()) ? "ERROR" : "OK");
                n.put("error", "ERROR".equals(span.getStatus()));
                n.put("errorMessage", span.getErrorMessage());
                byTrace.computeIfAbsent(span.getTraceId(), k -> new ArrayList<>()).add(n);
            }
        } catch (Exception ex) {
            log.debug("[Hotsport] 读取 Agent 方法 Span 失败: {}", ex.getMessage());
        }
        return byTrace;
    }

    /**
     * 把一个 trace 内的扁平节点按 {@code parentSpanId} 递归嵌套成树。
     *
     * <p>同时补齐甘特条所需的真实时间轴比例：{@code offsetMs}/{@code offsetPercent}/
     * {@code percentWidth} 全部相对本 trace 的真实起止时间换算，{@code traceTotalMs}
     * 是该 trace 的真实总耗时。</p>
     *
     * @param flat 同一 trace 的扁平节点
     * @return 根节点列表（按开始时间升序）
     */
    private List<Map<String, Object>> nestTraceNodes(List<Map<String, Object>> flat) {
        if (flat.isEmpty()) {
            return new ArrayList<>();
        }
        long startMin = Long.MAX_VALUE;
        long endMax = Long.MIN_VALUE;
        for (Map<String, Object> n : flat) {
            long st = asLong(n.get("startMs"));
            long dur = Math.max(0L, asLong(n.get("durationMs")));
            if (st > 0) {
                startMin = Math.min(startMin, st);
                endMax = Math.max(endMax, st + dur);
            }
        }
        if (startMin == Long.MAX_VALUE) {
            startMin = 0L;
            endMax = 0L;
        }
        long totalMs = Math.max(0L, endMax - startMin);

        // spanId → 节点（同 trace 内 spanId 唯一，重复时保留先到的）
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> n : flat) {
            String id = asText(n.get("spanId"));
            if (id != null && !byId.containsKey(id)) {
                byId.put(id, n);
            }
        }

        // 根请求：本 trace 内最早开始的真实传输记录
        Map<String, Object> rootReq = null;
        for (Map<String, Object> n : flat) {
            if (!"http".equals(n.get("kind"))) {
                continue;
            }
            if (rootReq == null || asLong(n.get("startMs")) < asLong(rootReq.get("startMs"))) {
                rootReq = n;
            }
        }
        String rootSpanId = rootReq == null ? null : asText(rootReq.get("spanId"));

        // 方法级记录的父引用缺失/越界时，回挂到根请求（真实归属，不是编造层级）
        if (rootSpanId != null) {
            for (Map<String, Object> n : flat) {
                if (!"method".equals(n.get("kind"))) {
                    continue;
                }
                String parent = asText(n.get("parentSpanId"));
                String self = asText(n.get("spanId"));
                if (parent == null || !byId.containsKey(parent) || parent.equals(self)) {
                    n.put("parentSpanId", rootSpanId);
                }
            }
        }

        // 时间轴比例（甘特条直接用百分比定位）
        for (Map<String, Object> n : flat) {
            long st = asLong(n.get("startMs"));
            long dur = Math.max(0L, asLong(n.get("durationMs")));
            long off = st > 0 && startMin > 0 ? Math.max(0L, st - startMin) : 0L;
            n.put("offsetMs", off);
            n.put("traceTotalMs", totalMs);
            n.put("offsetPercent", totalMs > 0 ? Math.min(100.0, 100.0 * off / totalMs) : 0.0);
            n.put("percentWidth", totalMs > 0
                    ? Math.max(0.6, Math.min(100.0, 100.0 * dur / totalMs)) : 100.0);
            n.remove("startMs");
            n.remove("endMs");
        }

        // 递归嵌套
        List<Map<String, Object>> roots = new ArrayList<>();
        for (Map<String, Object> n : flat) {
            String parent = asText(n.get("parentSpanId"));
            Map<String, Object> p = parent == null ? null : byId.get(parent);
            if (p == null || p == n || hasParentCycle(n, byId)) {
                roots.add(n);
                continue;
            }
            Object kids = p.get("children");
            if (!(kids instanceof List<?>)) {
                kids = new ArrayList<Map<String, Object>>();
                p.put("children", kids);
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> childList = (List<Map<String, Object>>) kids;
            childList.add(n);
        }

        // 子节点按时间轴顺序排列，与甘特条从上到下的视觉顺序一致
        for (Map<String, Object> n : flat) {
            Object kids = n.get("children");
            if (kids instanceof List<?> && ((List<?>) kids).size() > 1) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> childList = (List<Map<String, Object>>) kids;
                childList.sort((a, b) -> Long.compare(asLong(a.get("offsetMs")), asLong(b.get("offsetMs"))));
            }
        }

        roots.sort((a, b) -> Long.compare(asLong(a.get("offsetMs")), asLong(b.get("offsetMs"))));
        for (Map<String, Object> r : roots) {
            r.put("spanCount", countTreeSpans(r));
        }
        return roots;
    }

    /**
     * 统计一棵树里的节点总数（含自身）。
     *
     * @param node 根节点
     * @return 节点数
     */
    private static int countTreeSpans(Map<String, Object> node) {
        int count = 1;
        Object kids = node.get("children");
        if (kids instanceof List<?> childList) {
            for (Object child : childList) {
                if (child instanceof Map<?, ?> m) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> c = (Map<String, Object>) m;
                    count += countTreeSpans(c);
                }
            }
        }
        return count;
    }

    /**
     * 判断节点的父引用链是否成环（存储异常时防死循环）。
     *
     * @param node 待挂载节点
     * @param byId spanId → 节点
     * @return 成环返回 true
     */
    private static boolean hasParentCycle(Map<String, Object> node,
                                          Map<String, Map<String, Object>> byId) {
        String self = asText(node.get("spanId"));
        if (self == null) {
            return false;
        }
        String cursor = asText(node.get("parentSpanId"));
        int guard = 0;
        while (cursor != null && guard++ < 64) {
            if (cursor.equals(self)) {
                return true;
            }
            Map<String, Object> p = byId.get(cursor);
            if (p == null) {
                return false;
            }
            cursor = asText(p.get("parentSpanId"));
        }
        return guard >= 64;
    }

    /**
     * 取字符串值，空白视为缺失。
     *
     * @param value 原始值
     * @return 非空白字符串或 {@code null}
     */
    private static String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    /**
     * 取 long 值，非法值按 0 处理（观测组件不因脏数据抛错）。
     *
     * @param value 原始值
     * @return long 值
     */
    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 链路概览 — 按 traceId 聚合的扁平列表（Trace 列表页数据源）。
     *
     * <p>相比 {@link #agentTracesTree(int)} 的树结构，这里是「每 trace 一行」的
     * 紧凑视图，前端可直接做表格/筛选：每个 trace 一行包含
     * span 数、总耗时、平均耗时、最大耗时、错误 span 数、是否含错误、最新时间。</p>
     *
     * @param limit     返回上限
     * @param errorOnly true 只保留含错误 span 的链路
     * @return 链路概览列表（按最新时间降序）
     */
    public List<Map<String, Object>> traceSummaries(int limit, boolean errorOnly) {
        Query q = new Query().setLimit(100000);
        List<TransmissionEvent> all = queryAll(q, storage -> storage.queryTransmissions(q));
        Map<String, Map<String, Object>> byTrace = new LinkedHashMap<>();
        for (int i = all.size() - 1; i >= 0; i--) {
            TransmissionEvent e = all.get(i);
            String traceId = e.getTraceId() != null ? e.getTraceId() : "root";
            Map<String, Object> m = byTrace.computeIfAbsent(traceId, k -> {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("traceId", k);
                x.put("spanCount", 0L);
                x.put("errorSpanCount", 0L);
                x.put("totalMs", 0L);
                x.put("startTime", toIso(e.getStartTime()));
                x.put("operation", e.getOperation() != null ? e.getOperation() : e.getProtocol());
                return x;
            });
            m.put("spanCount", (Long) m.get("spanCount") + 1L);
            m.put("totalMs", (Long) m.get("totalMs") + Math.max(0L, e.getDuration()));
            if (e.getStatus() == StatusCode.ERROR) {
                m.put("errorSpanCount", (Long) m.get("errorSpanCount") + 1L);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> m : byTrace.values()) {
            long spanCount = (Long) m.get("spanCount");
            long errorSpanCount = (Long) m.get("errorSpanCount");
            m.put("avgMs", spanCount > 0 ? Math.round((Long) m.get("totalMs") * 10.0 / spanCount) / 10.0 : 0);
            m.put("hasError", errorSpanCount > 0);
            if (errorOnly && errorSpanCount == 0) {
                continue;
            }
            out.add(m);
        }
        out.sort((a, b) -> String.valueOf(b.get("startTime")).compareTo(String.valueOf(a.get("startTime"))));
        if (out.size() > limit) {
            return new ArrayList<>(out.subList(0, limit));
        }
        return out;
    }

    /**
     * 句柄增长趋势（判断泄漏）。
     *
     * @return 增长统计 { windowMs, perKind: { kind: { opened, closed, alive, leakDelta } } }
     */
    public Map<String, Object> handleGrowth() {
        com.chua.runtime.apm.handler.HandleInventoryHandler handler =
                com.chua.runtime.apm.ApmBootstrap.getGlobalHandler(
                        com.chua.runtime.apm.handler.HandleInventoryHandler.class);
        if (handler == null) {
            Map<String, Object> empty = new java.util.LinkedHashMap<>();
            empty.put("windowMs", 0L);
            empty.put("perKind", Map.of());
            return empty;
        }
        return handler.growth();
    }

    /**
     * 实时日志增量拉取（SSE 游标续读）。
     *
     * <p>返回序号大于 {@code afterSeq} 的系统输出增量，以及当前最大序号
     * （供客户端作为下一次续读游标）。句柄与日志两条实时流共用这一套机制。</p>
     *
     * @param afterSeq 游标序号
     * @return { items, lastSeq }
     */
    public Map<String, Object> systemOutAfter(long afterSeq) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        com.chua.runtime.apm.handler.SystemOutCaptureHandler handler =
                com.chua.runtime.apm.ApmBootstrap.getGlobalHandler(
                        com.chua.runtime.apm.handler.SystemOutCaptureHandler.class);
        if (handler == null) {
            result.put("items", new ArrayList<>());
            result.put("lastSeq", 0L);
            return result;
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (com.chua.runtime.apm.handler.SystemOutCaptureHandler.SystemOutEntry e : handler.tailAfter(afterSeq)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("seq", e.getSeq());
            m.put("timestamp", toIso(e.getTimestamp()));
            m.put("source", e.getSource());
            m.put("level", e.getLevel());
            // 控制台捕获到的是带 ANSI 颜色码的原始输出，前端要按纯文本展示，
            // 在这里统一清洗，避免终端转义序列漏到页面上
            m.put("line", stripAnsi(e.getLine()));
            items.add(m);
        }
        result.put("items", items);
        result.put("lastSeq", handler.lastSeq());
        return result;
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
        // 真实 classpath：java.class.path 是 JVM 展开后的真实 jar 列表（部署实例实测
        // 是 127 个 jar 的完整路径串，而非命令行字面量 lib/*）。逐项取文件名、去掉
        // 版本号作为展示名。绝不写死模块数组（那属于伪造数据）。
        String cp = System.getProperty("java.class.path", "");
        if (!cp.isBlank()) {
            for (String entry : cp.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator), -1)) {
                if (entry == null || entry.isBlank()) {
                    continue;
                }
                String fileName = entry;
                int slash = Math.max(entry.lastIndexOf('/'), entry.lastIndexOf('\\'));
                if (slash >= 0) {
                    fileName = entry.substring(slash + 1);
                }
                String jarName = fileName;
                String label = fileName;
                if (label.endsWith(".jar")) {
                    label = label.substring(0, label.length() - 4);
                }
                // 去掉版本号（如 -4.0.0.42 / -1.18.48），同组件聚合展示
                String shown = label.replaceFirst("-[0-9].*$", "");
                if (shown.isBlank()) {
                    shown = label;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", jarName);
                m.put("label", shown);
                m.put("jar", jarName);
                m.put("kind", "module");
                modules.add(m);
            }
        }
        if (modules.isEmpty()) {
            // 极端兜底：classpath 不可读时给一个真实感空态，绝不回退到写死模块名
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("id", "unknown-classpath");
            fallback.put("label", "classpath 不可读");
            fallback.put("jar", "");
            fallback.put("kind", "unknown");
            modules.add(fallback);
        }
        t.put("modules", modules);
        List<Map<String, Object>> ports = new ArrayList<>();
        // 端口取 WebServer 实际绑定值：原来这里写死 8890，与实际监听端口无关，属于伪造数据
        int httpPort = localServerPort();
        if (httpPort > 0) {
            addPort(ports, "HTTP", localWebServerName(), httpPort);
        }
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

        // 真实外部依赖卫星：来自依赖图 / 真实传输的聚合边（source → target）。
        // 中心进程是 target（被调用方）时，把 source 端作为卫星节点挂到中心下方，
        // 让系统拓扑真正展示「本进程实际与谁通信」，而不是孤立的中心节点。
        List<Map<String, Object>> deps = new ArrayList<>();
        try {
            deps = dependenciesAggregated(20);
        } catch (Exception e) {
            log.debug("[Hotsport] 拓扑外部依赖聚合失败: {}", e.getMessage());
        }
        // 本进程自身端点（source/target 都可能是它自己：source=本机 target=本机端口）
        String selfEndpoints = hostname + ":" + localServerPort();
        List<Map<String, Object>> peerNodes = new ArrayList<>();
        java.util.Set<String> peerSeen = new java.util.HashSet<>();
        for (Map<String, Object> dep : deps) {
            String source = String.valueOf(dep.get("source"));
            String target = String.valueOf(dep.get("target"));
            // 卫星取 source 端（本进程被谁调用 / 谁调进来）；target 通常是本机端口
            String peer = source != null && !"null".equals(source) && !source.isBlank()
                    ? source : null;
            if (peer == null || "unknown".equals(peer)
                    || peer.startsWith("127.0.0.1") || peer.startsWith("localhost")
                    || selfEndpoints.startsWith(peer + ":")) {
                continue;
            }
            if (peerSeen.add(peer)) {
                Map<String, Object> pn = new LinkedHashMap<>();
                pn.put("id", "peer-" + peer);
                pn.put("label", peer);
                pn.put("symbolSize", 26);
                pn.put("category", "peer");
                peerNodes.add(pn);
            }
        }
        t.put("deps", deps);
        t.put("peerCount", peerNodes.size());

        List<Map<String, Object>> nodes = new ArrayList<>();
        Map<String, Object> centerNode = new LinkedHashMap<>();
        centerNode.put("id", "center");
        centerNode.put("label", hostname);
        centerNode.put("symbolSize", 56);
        centerNode.put("category", "center");
        nodes.add(centerNode);
        List<Map<String, Object>> edges = new ArrayList<>();
        for (Map<String, Object> p : ports) {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("id", "port-" + p.get("port"));
            n.put("label", String.valueOf(p.get("port")));
            n.put("symbolSize", 30);
            n.put("category", "port");
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
            n.put("category", "module");
            nodes.add(n);
        }
        // 外部依赖卫星节点与中心连线（真实调用方 → 本进程）
        for (Map<String, Object> pn : peerNodes) {
            nodes.add(pn);
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("source", "center");
            e.put("target", String.valueOf(pn.get("id")));
            e.put("category", "peer");
            edges.add(e);
        }
        t.put("nodes", nodes);
        t.put("edges", edges);
        return t;
    }

    /**
     * 本进程自身标识（依赖拓扑标「当前主机」用，全部取真实值）。
     *
     * @return { hostName, address, port, software, display }
     */
    public Map<String, Object> selfIdentity() {
        String hostName = "unknown";
        String address = "";
        try {
            java.net.InetAddress local = java.net.InetAddress.getLocalHost();
            hostName = local.getHostName();
            address = local.getHostAddress();
        } catch (Exception e) {
            log.debug("[Hotsport] 读取本机地址失败: {}", e.getMessage());
        }
        int port = localServerPort();
        Map<String, Object> self = new LinkedHashMap<>();
        self.put("hostName", hostName);
        self.put("address", address);
        self.put("port", port);
        self.put("software", localWebServerName());
        self.put("display", port > 0 ? hostName + ":" + port : hostName);
        return self;
    }

    /**
     * 本进程真实 HTTP 监听端口。
     *
     * <p>取 WebServer 实际绑定的端口（{@code --server.port} 与随机端口都以实际值为准）；
     * 拿不到就返回 -1 —— 宁可不画这个节点，也不写死一个数字。</p>
     *
     * @return 监听端口，未知返回 -1
     */
    private int localServerPort() {
        try {
            if (applicationContext
                    instanceof org.springframework.boot.web.server.context.WebServerApplicationContext ctx
                    && ctx.getWebServer() != null) {
                return ctx.getWebServer().getPort();
            }
        } catch (Exception e) {
            log.debug("[Hotsport] 读取 WebServer 端口失败: {}", e.getMessage());
        }
        return -1;
    }

    /**
     * 本进程真实 WebServer 实现名（如 Tomcat / Jetty）。
     *
     * @return 软件名，未知返回 {@code HTTP}
     */
    private String localWebServerName() {
        try {
            if (applicationContext
                    instanceof org.springframework.boot.web.server.context.WebServerApplicationContext ctx
                    && ctx.getWebServer() != null) {
                String simple = ctx.getWebServer().getClass().getSimpleName();
                return simple.endsWith("WebServer")
                        ? simple.substring(0, simple.length() - "WebServer".length()) : simple;
            }
        } catch (Exception e) {
            log.debug("[Hotsport] 读取 WebServer 实现失败: {}", e.getMessage());
        }
        return "HTTP";
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
     * 终端 ANSI 转义序列（颜色 / 光标控制）。
     */
    private static final java.util.regex.Pattern ANSI_PATTERN =
            java.util.regex.Pattern.compile("\u001B\\[[0-9;?]*[ -/]*[@-~]");

    /**
     * 清洗 ANSI 转义序列，得到可直接展示的纯文本。
     *
     * @param raw 原始文本
     * @return 清洗后文本（null 原样返回）
     */
    private static String stripAnsi(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        return ANSI_PATTERN.matcher(raw).replaceAll("");
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

    // ------------------------------------------------------------------
    // 看板聚合：趋势 / 日志等级 / 端点时序 / 协议 / 线程池 / 连接池
    // ------------------------------------------------------------------

    /**
     * 调用量与耗时趋势（按时间桶聚合）。
     *
     * <p>前端 {@code getTrend(minutes, bucketSeconds)} 用于 Dashboard 的折线图，
     * 需要「每个时间桶一行」的连续序列，因此这里按
     * {@code startTime - startTime % bucketMs} 归桶，只返回有数据的桶
     * （前端以 bucketStart 为 x 轴，空桶本就画不出来）。</p>
     *
     * @param minutes       统计窗口（分钟），最小 1
     * @param bucketSeconds 桶宽（秒），最小 1
     * @return 趋势点列表，按桶升序
     */
    public List<Map<String, Object>> trend(int minutes, int bucketSeconds) {
        int window = Math.max(1, minutes);
        long bucketMs = Math.max(1, bucketSeconds) * 1000L;
        long from = System.currentTimeMillis() - window * 60_000L;

        Query q = new Query().setLimit(100000);
        List<TransmissionEvent> all = queryAll(q, storage -> storage.queryTransmissions(q));

        Map<Long, long[]> buckets = new java.util.TreeMap<>();
        for (TransmissionEvent e : all) {
            long ts = e.getStartTime() > 0 ? e.getStartTime() : e.getEndTime();
            if (ts <= 0 || ts < from) {
                continue;
            }
            long[] acc = buckets.computeIfAbsent(ts - ts % bucketMs, k -> new long[3]);
            acc[0]++;
            acc[2] += Math.max(0L, e.getDuration());
            if (e.getStatus() == StatusCode.ERROR) {
                acc[1]++;
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<Long, long[]> en : buckets.entrySet()) {
            long[] acc = en.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bucketStart", en.getKey());
            m.put("callCount", acc[0]);
            m.put("errorCount", acc[1]);
            m.put("errorRatePercent", acc[0] > 0 ? round2(100.0 * acc[1] / acc[0]) : 0.0);
            m.put("avgMs", acc[0] > 0 ? round2((double) acc[2] / acc[0]) : 0.0);
            out.add(m);
        }
        return out;
    }

    /**
     * 日志等级分布。
     *
     * @return 每行 {@code {level,count,percent}}，按数量降序
     */
    public List<Map<String, Object>> logStats() {
        Query q = new Query().setLimit(100000);
        List<LogRecord> all = queryAll(q, storage -> storage.queryLogs(q));

        Map<String, Long> counts = new LinkedHashMap<>();
        long total = 0L;
        for (LogRecord r : all) {
            String level = r.getLevel() == null ? "UNKNOWN" : r.getLevel().toUpperCase();
            counts.merge(level, 1L, Long::sum);
            total++;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, Long> en : counts.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("level", en.getKey());
            m.put("count", en.getValue());
            m.put("percent", total > 0 ? round2(100.0 * en.getValue() / total) : 0.0);
            out.add(m);
        }
        out.sort(Comparator.comparingLong(x -> -((Long) x.get("count"))));
        return out;
    }

    /**
     * 端点级时序统计（按「时间桶 × 端点」聚合）。
     *
     * <p>端点标签与 {@link #endpoints(int, String)} 保持一致
     * （{@code 协议 操作}），这样端点列表页与趋势图能对上同一批 key。</p>
     *
     * @param service       服务名过滤（空表示全部）
     * @param minutes       统计窗口（分钟）
     * @param bucketSeconds 桶宽（秒）
     * @return 每行 {@code {ts,endpoint,callCount,errorCount,avgMs}}
     */
    public List<Map<String, Object>> endpointStats(String service, int minutes, int bucketSeconds) {
        int window = Math.max(1, minutes);
        long bucketMs = Math.max(1, bucketSeconds) * 1000L;
        long from = System.currentTimeMillis() - window * 60_000L;
        String serviceKey = service == null ? "" : service.trim();

        Query q = new Query().setLimit(100000);
        List<TransmissionEvent> all = queryAll(q, storage -> storage.queryTransmissions(q));

        Map<Long, Map<String, long[]>> buckets = new LinkedHashMap<>();
        for (TransmissionEvent e : all) {
            long ts = e.getStartTime() > 0 ? e.getStartTime() : e.getEndTime();
            if (ts <= 0 || ts < from) {
                continue;
            }
            if (!serviceKey.isEmpty()) {
                String svc = serviceLabel(e.getTargetSoftware(), e.getTargetHost());
                if (svc == null || !svc.equalsIgnoreCase(serviceKey)) {
                    continue;
                }
            }
            String op = e.getOperation() != null ? e.getOperation() : "?";
            String endpoint = (e.getProtocol() != null ? e.getProtocol() : "?") + " " + op;
            long bucketStart = ts - ts % bucketMs;
            long[] acc = buckets.computeIfAbsent(bucketStart, k -> new LinkedHashMap<>())
                    .computeIfAbsent(endpoint, k -> new long[3]);
            acc[0]++;
            acc[2] += Math.max(0L, e.getDuration());
            if (e.getStatus() == StatusCode.ERROR) {
                acc[1]++;
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<Long, Map<String, long[]>> bucket : buckets.entrySet()) {
            for (Map.Entry<String, long[]> en : bucket.getValue().entrySet()) {
                long[] acc = en.getValue();
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("ts", bucket.getKey());
                m.put("endpoint", en.getKey());
                m.put("callCount", acc[0]);
                m.put("errorCount", acc[1]);
                m.put("avgMs", acc[0] > 0 ? round2((double) acc[2] / acc[0]) : 0.0);
                out.add(m);
            }
        }
        out.sort(Comparator.comparingLong(x -> (Long) x.get("ts")));
        return out;
    }

    /**
     * 协议分布统计。
     *
     * @return 每行 {@code {protocol,callCount,errorCount,errorRatePercent,avgMs}}，按调用量降序
     */
    public List<Map<String, Object>> protocolStats() {
        Query q = new Query().setLimit(100000);
        List<TransmissionEvent> all = queryAll(q, storage -> storage.queryTransmissions(q));

        Map<String, long[]> agg = new LinkedHashMap<>();
        for (TransmissionEvent e : all) {
            String protocol = e.getProtocol() == null || e.getProtocol().isBlank()
                    ? "UNKNOWN" : e.getProtocol().toUpperCase();
            long[] acc = agg.computeIfAbsent(protocol, k -> new long[3]);
            acc[0]++;
            acc[2] += Math.max(0L, e.getDuration());
            if (e.getStatus() == StatusCode.ERROR) {
                acc[1]++;
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, long[]> en : agg.entrySet()) {
            long[] acc = en.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("protocol", en.getKey());
            m.put("callCount", acc[0]);
            m.put("errorCount", acc[1]);
            m.put("errorRatePercent", acc[0] > 0 ? round2(100.0 * acc[1] / acc[0]) : 0.0);
            m.put("avgMs", acc[0] > 0 ? round2((double) acc[2] / acc[0]) : 0.0);
            out.add(m);
        }
        out.sort(Comparator.comparingLong(x -> -((Long) x.get("callCount"))));
        return out;
    }

    /**
     * 线程池视图：把 JVM 实时线程按「池名」分组。
     *
     * <p>数据来自 {@link java.lang.management.ThreadMXBean#dumpAllThreads}，
     * 不是历史记录 —— 线程池页面关心的是「此刻谁在忙」。</p>
     *
     * @return 每行 {@code {name,count,daemonCount,knownPool,sampleStates[]}}
     */
    public List<Map<String, Object>> threadPools() {
        java.lang.management.ThreadMXBean bean = java.lang.management.ManagementFactory.getThreadMXBean();
        java.lang.management.ThreadInfo[] infos = bean.dumpAllThreads(false, false);

        Map<String, List<java.lang.management.ThreadInfo>> pools = new LinkedHashMap<>();
        for (java.lang.management.ThreadInfo info : infos) {
            pools.computeIfAbsent(poolName(info.getThreadName()), k -> new ArrayList<>()).add(info);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, List<java.lang.management.ThreadInfo>> en : pools.entrySet()) {
            List<java.lang.management.ThreadInfo> threads = en.getValue();
            long daemonCount = threads.stream().filter(java.lang.management.ThreadInfo::isDaemon).count();
            Map<String, Long> states = new LinkedHashMap<>();
            for (java.lang.management.ThreadInfo info : threads) {
                String state = info.getThreadState() == null ? "UNKNOWN" : info.getThreadState().name();
                states.merge(state, 1L, Long::sum);
            }
            List<Map<String, Object>> samples = new ArrayList<>();
            for (Map.Entry<String, Long> state : states.entrySet()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("state", state.getKey());
                item.put("count", state.getValue());
                samples.add(item);
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", en.getKey());
            m.put("count", (long) threads.size());
            m.put("daemonCount", daemonCount);
            m.put("knownPool", isKnownPool(en.getKey()));
            m.put("sampleStates", samples);
            out.add(m);
        }
        out.sort(Comparator.comparingLong(x -> -((Long) x.get("count"))));
        return out;
    }

    /**
     * 连接池近似视图：按「目标 host:port」聚合调用情况。
     *
     * <p>APM 只能看到外部目标，看不到对方进程里的池对象，所以这里用
     * 「目标地址维度的调用量 / 平均耗时 / 最后调用时间」近似描述连接压力，
     * 并附带本进程当前打开的 FD 数（Linux 下读 {@code /proc/self/fd}）。</p>
     *
     * <p>{@code activeLeakCount} 恒为 {@code null}：句柄泄漏的记录只有「类型名 /
     * 持有线程 / 创建栈」，没有目标地址——{@code InterceptContext} 不携带方法入参，
     * 连 {@code Socket.connect} 的 {@code SocketAddress} 都取不到，所以这个数字在当前
     * 插桩能力下无法按端点归因。这里返回 {@code null}（不输出）而非 {@code 0}：
     * 0 会被读成「这个端点确实没有活跃泄漏」，是本观测功能不该给出的假数据；
     * 前端把 null 渲染成空白即表示「无法归因」，进程级活跃泄漏数见 {@code /stats}
     * 的 {@code leakCount} 与 {@code /leaks}。</p>
     *
     * @return 每行 {@code {endpoint,callCount,avgMs,lastCallAt,activeLeakCount,openFileDescriptors}}，
     *         其中 {@code activeLeakCount} 恒为 {@code null}
     */
    public List<Map<String, Object>> connectionPools() {
        Query q = new Query().setLimit(100000);
        List<TransmissionEvent> all = queryAll(q, storage -> storage.queryTransmissions(q));

        Map<String, long[]> agg = new LinkedHashMap<>();
        for (TransmissionEvent e : all) {
            String host = e.getTargetHost();
            if (host == null || host.isBlank()) {
                continue;
            }
            String endpoint = host + (e.getTargetPort() > 0 ? ":" + e.getTargetPort() : "");
            long[] acc = agg.computeIfAbsent(endpoint, k -> new long[3]);
            acc[0]++;
            acc[1] += Math.max(0L, e.getDuration());
            acc[2] = Math.max(acc[2], e.getStartTime());
        }
        Integer fds = openFileDescriptors();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, long[]> en : agg.entrySet()) {
            long[] acc = en.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("endpoint", en.getKey());
            m.put("callCount", acc[0]);
            m.put("avgMs", acc[0] > 0 ? round2((double) acc[1] / acc[0]) : 0.0);
            m.put("lastCallAt", toIso(acc[2]));
            // 无法按端点归因句柄泄漏（插桩拿不到目标地址），输出 null 而不是假 0：
            // 前端渲染空白 = 未知；进程级活跃泄漏数见 /stats 的 leakCount。
            m.put("activeLeakCount", null);
            m.put("openFileDescriptors", fds);
            out.add(m);
        }
        out.sort(Comparator.comparingLong(x -> -((Long) x.get("callCount"))));
        return out;
    }

    /**
     * 保留两位小数。
     *
     * @param value 原始值
     * @return 四舍五入到两位小数的值
     */
    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * 从线程名推断线程池名：去掉末尾的数字序号。
     *
     * <p>例：{@code http-nio-18170-exec-3 → http-nio-18170-exec}、
     * {@code MvcAsync12 → MvcAsync}。</p>
     *
     * @param threadName 线程名
     * @return 池名
     */
    private static String poolName(String threadName) {
        if (threadName == null || threadName.isBlank()) {
            return "unknown";
        }
        String base = threadName.trim().replaceAll("[-_#]?\\d+$", "");
        return base.isBlank() ? threadName.trim() : base;
    }

    /**
     * 是否属于已知的框架线程池（前端用来区分「框架池」与「业务自建池」）。
     *
     * @param poolName 池名
     * @return 命中已知前缀返回 true
     */
    private static boolean isKnownPool(String poolName) {
        if (poolName == null) {
            return false;
        }
        String name = poolName.toLowerCase();
        String[] known = {"http-nio", "mvcasync", "tomcat", "hikari", "lettuce", "netty",
                "hotsport", "sse", "spring", "pool", "scheduler", "forkjoin", "common-cleaner",
                "reference handler", "finalizer", "signal dispatcher", "notification thread"};
        for (String prefix : known) {
            if (name.startsWith(prefix) || name.contains(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 读取本进程当前打开的 FD 数（非 Linux 返回 {@code null}）。
     *
     * @return FD 数，取不到时返回 {@code null}
     */
    private static Integer openFileDescriptors() {
        try {
            String[] fds = new java.io.File("/proc/self/fd").list();
            return fds == null ? null : fds.length;
        } catch (Exception e) {
            return null;
        }
    }
}
