package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.configuration.rag.RagClientManager;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分级健康探针与运行指标。
 *
 * <p><b>为什么要分级</b>：一个探针既当存活又当就绪，会把「依赖暂时不可用」误判成
 * 「进程该被杀」。上层编排（k8s / nginx / 监控）需要两条独立信号：</p>
 * <ul>
 *   <li>{@code /v2/health/live}：<b>存活</b>。只要进程还能响应就 200，
 *       <b>不查任何依赖</b> —— 依赖挂了不该导致进程被重启（重启也修不好下游）。</li>
 *   <li>{@code /v2/health/ready}：<b>就绪</b>。查关键依赖（库 / Redis），
 *       任一不可用返回 <b>503</b>，让上游把流量切走而不是打进来超时。</li>
 *   <li>{@code /v2/health/deep}：<b>深度自检</b>。在就绪基础上再查 RAG 客户端与
 *       embedding 配置，供人工排查与定时巡检（较重，不要挂到高频探针上）。</li>
 * </ul>
 *
 * <p>{@code /v2/health/metrics} 输出 JVM 与数据库连接池指标（无 actuator 依赖时的轻量替代）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/v2/health")
@RequiredArgsConstructor
public class HealthController {

    /** 应用上下文：用于按类型惰性取依赖，避免构造期强绑定多数据源等歧义 Bean */
    private final ApplicationContext applicationContext;

    /** RAG 客户端管理器：深度自检用 */
    private final RagClientManager ragClientManager;

    /**
     * 存活探针：进程能响应即 200，不查依赖。
     *
     * @return 存活状态
     */
    @GetMapping("/live")
    public ResponseEntity<ReturnResult<Map<String, Object>>> live() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("alive", true);
        body.put("time", LocalDateTime.now().toString());
        return ResponseEntity.ok(ReturnResult.success(body));
    }

    /**
     * 就绪探针：关键依赖（数据库 / Redis）全部可用才 200，否则 503。
     *
     * <p>返回 503 而不是「200 + 业务码」，是因为上游编排只认 HTTP 状态码，
     * 带着 200 的失败体对它们等于成功。</p>
     *
     * @return 就绪状态（含逐项检查明细）
     */
    @GetMapping("/ready")
    public ResponseEntity<ReturnResult<Map<String, Object>>> ready() {
        Map<String, Object> db = checkDatabase();
        Map<String, Object> redis = checkRedis();
        boolean ready = isUp(db) && isUp(redis);
        List<Map<String, Object>> checks = new ArrayList<>(2);
        checks.add(db);
        checks.add(redis);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ready", ready);
        body.put("checks", checks);
        body.put("time", LocalDateTime.now().toString());
        if (ready) {
            return ResponseEntity.ok(ReturnResult.success(body));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ReturnResult.error("NOT_READY", "关键依赖未就绪", body));
    }

    /**
     * 深度自检：就绪项 + RAG 客户端 + embedding 配置。
     *
     * @return 逐项自检结果
     */
    @GetMapping("/deep")
    public ResponseEntity<ReturnResult<Map<String, Object>>> deep() {
        Map<String, Object> db = checkDatabase();
        Map<String, Object> redis = checkRedis();
        Map<String, Object> rag = checkRag();
        Map<String, Object> embedding = checkEmbedding();

        boolean ready = isUp(db) && isUp(redis) && isUp(rag) && isUp(embedding);
        List<Map<String, Object>> checks = new ArrayList<>(4);
        checks.add(db);
        checks.add(redis);
        checks.add(rag);
        checks.add(embedding);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ready", ready);
        body.put("checks", checks);
        body.put("time", LocalDateTime.now().toString());
        if (ready) {
            return ResponseEntity.ok(ReturnResult.success(body));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ReturnResult.error("NOT_READY", "深度自检未通过", body));
    }

    /**
     * 运行指标：JVM 内存 / 线程 / 运行时长 + 数据库连接池。
     *
     * @return 指标快照
     */
    @GetMapping("/metrics")
    public ResponseEntity<ReturnResult<Map<String, Object>>> metrics() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jvm", jvmMetrics());
        Map<String, Object> pool = hikariMetrics();
        if (pool != null) {
            body.put("dbPool", pool);
        }
        body.put("time", LocalDateTime.now().toString());
        return ResponseEntity.ok(ReturnResult.success(body));
    }

    /**
     * JVM 指标快照。
     *
     * @return 堆 / 非堆 / 线程 / 运行时长
     */
    private Map<String, Object> jvmMetrics() {
        // [P3C A20 豁免]
        long mb = 1024L * 1024L;
        Runtime rt = Runtime.getRuntime();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();

        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("heapUsedMb", (rt.totalMemory() - rt.freeMemory()) / mb);
        jvm.put("heapTotalMb", rt.totalMemory() / mb);
        jvm.put("heapMaxMb", rt.maxMemory() / mb);
        jvm.put("nonHeapUsedMb", memory.getNonHeapMemoryUsage().getUsed() / mb);
        jvm.put("threadCount", threads.getThreadCount());
        jvm.put("peakThreadCount", threads.getPeakThreadCount());
        // [P3C A20 豁免]
        jvm.put("uptimeSeconds", runtime.getUptime() / 1000L);
        jvm.put("startTimeMillis", runtime.getStartTime());
        jvm.put("pid", ProcessHandle.current().pid());
        return jvm;
    }

    /**
     * Hikari 连接池指标（取第一个 HikariDataSource；没有则返回 null）。
     *
     * @return 连接池快照，或 null
     */
    private Map<String, Object> hikariMetrics() {
        try {
            Map<String, DataSource> all = applicationContext.getBeansOfType(DataSource.class);
            for (DataSource ds : all.values()) {
                if (!(ds instanceof HikariDataSource hikari)) {
                    continue;
                }
                HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
                if (pool == null) {
                    continue;
                }
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("active", pool.getActiveConnections());
                out.put("idle", pool.getIdleConnections());
                out.put("total", pool.getTotalConnections());
                out.put("waiting", pool.getThreadsAwaitingConnection());
                return out;
            }
        } catch (Throwable e) {
            log.debug("[Health] 读取连接池指标失败: {}", e.toString());
        }
        return null;
    }

    /**
     * 数据库连通检查：取第一个 DataSource，跑 {@code isValid}。
     *
     * @return 检查项（name / status / detail）
     */
    private Map<String, Object> checkDatabase() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", "database");
        try {
            Map<String, DataSource> all = applicationContext.getBeansOfType(DataSource.class);
            if (all.isEmpty()) {
                item.put("status", "down");
                item.put("detail", "未找到 DataSource");
                return item;
            }
            DataSource ds = all.values().iterator().next();
            try (Connection connection = ds.getConnection()) {
                boolean valid = connection.isValid(2);
                item.put("status", valid ? "up" : "down");
                item.put("detail", valid ? "connection valid" : "connection invalid");
            }
        } catch (Throwable e) {
            item.put("status", "down");
            item.put("detail", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return item;
    }

    /**
     * Redis 连通检查：PING。
     *
     * @return 检查项（name / status / detail）
     */
    private Map<String, Object> checkRedis() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", "redis");
        try {
            RedisConnectionFactory factory = applicationContext
                    .getBeanProvider(RedisConnectionFactory.class)
                    .getIfAvailable();
            if (factory == null) {
                item.put("status", "down");
                item.put("detail", "未找到 RedisConnectionFactory");
                return item;
            }
            try (RedisConnection connection = factory.getConnection()) {
                String pong = connection.ping();
                boolean up = "PONG".equalsIgnoreCase(pong);
                item.put("status", up ? "up" : "down");
                item.put("detail", "ping=" + pong);
            }
        } catch (Throwable e) {
            item.put("status", "down");
            item.put("detail", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return item;
    }

    /**
     * RAG 客户端检查：能否构建出客户端。
     *
     * @return 检查项（name / status / detail）
     */
    private Map<String, Object> checkRag() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", "ragClient");
        try {
            boolean available = ragClientManager.current() != null;
            item.put("status", available ? "up" : "down");
            item.put("detail", available ? "客户端可用" : "客户端未构建（缺 LLM / 向量存储配置）");
        } catch (Throwable e) {
            item.put("status", "down");
            item.put("detail", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return item;
    }

    /**
     * embedding 配置检查：引擎 / 模型 id / 凭证是否完整（复用 RagClientManager 的自检）。
     *
     * @return 检查项（name / status / detail）
     */
    private Map<String, Object> checkEmbedding() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", "embedding");
        try {
            String issue = ragClientManager.embeddingIssue();
            item.put("status", issue == null ? "up" : "down");
            item.put("detail", issue == null ? "配置完整" : issue);
        } catch (Throwable e) {
            item.put("status", "down");
            item.put("detail", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return item;
    }

    /**
     * 判断检查项是否可用。
     *
     * @param item 检查项
     * @return 是否 up
     */
    private boolean isUp(Map<String, Object> item) {
        return item != null && "up".equals(item.get("status"));
    }
}