package com.chua.starter.datasource.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据源健康检查指示器
 * <p>
 * 集成 Spring Boot Actuator，在 {@code /actuator/health} 端点中
 * 展示所有已注册数据源的连接健康状态。
 * </p>
 *
 * <h3>输出示例</h3>
 * <pre>{@code
 * {
 *   "status": "UP",
 *   "components": {
 *     "dataSource": {
 *       "status": "UP",
 *       "details": {
 *         "master":    { "status": "UP",   "responseTime": 5  },
 *         "slave_1":   { "status": "UP",   "responseTime": 8  },
 *         "slave_2":   { "status": "DOWN", "error": "连接超时" }
 *       }
 *     }
 *   }
 * }
 * }</pre>
 *
 * <h3>2026-11-22 加固：请求线程不再直接做 JDBC 检查</h3>
 * <p>此前 {@link #doHealthCheck} 在请求线程里逐个数据源执行
 * {@code getConnection() + isValid()}。只要有一个数据源连接池耗尽或目标库卡顿，
 * {@code /actuator/health} 就会被拖住数十秒，而业务接口（走缓存或其它连接）
 * 仍然秒回 —— 运维守卫据此把一个完全健康的进程误判为「假死」并重启。
 * 现改为：后台守护线程按固定节奏检查并维护一份 volatile 快照，
 * 请求线程只读快照、永不阻塞在 JDBC 上；单轮检查带总预算，
 * 卡死的数据源只影响它自己那一轮，不会占死检查线程。</p>
 *
 * @since 2026/07/14
 * @author CH
 */
@Slf4j
public class DataSourceHealthIndicator extends AbstractHealthIndicator {

    /**
     * 连接验证超时（秒）
    */
    private static final int VALIDATION_TIMEOUT_SECONDS = 5;

    /**
     * 健康快照有效期（毫秒）。
     * <p>后台线程按该节奏持续刷新快照；请求线程读到的快照最多滞后一个周期。</p>
     */
    private static final long SNAPSHOT_TTL_MS = 10_000;

    /**
     * 单轮后台检查的总预算（毫秒）。
     * <p>超时后剩余数据源本轮不检查（details 里记 unverified），下一轮再试；
     * 避免一个卡死的数据源把后台线程永久占住。已验证的结果照常参与状态判定，
     * 未验证的数据源不拖累整体状态（不谎报 DOWN）。</p>
     */
    private static final long REFRESH_BUDGET_MS = 8_000;

    /**
     * 首次请求等待后台首轮结果的最长时间（毫秒）。
     * <p>服务刚启动、后台首轮检查尚未完成时的等待上限；超时后按 UNKNOWN 返回，
     * 不让请求线程长时间挂起。</p>
     */
    private static final long FIRST_RESULT_WAIT_MS = 3_000;

    /**
     * 运行时异常白名单（不标记为 DOWN，避免循环）
    */
    private static final String[] TRANSIENT_ERROR_PREFIXES = {
            "CommunicationsException", "SocketException", "InterruptedException"
    };

    /**
     * 最近一次检查结果快照（volatile，请求线程只读）。
     */
    private volatile HealthSnapshot snapshot;

    /**
     * 后台刷新线程（守护线程，懒启动；JVM 退出即随之结束）。
     */
    private final Object refresherLock = new Object();
    private volatile Thread refresher;

    /**
     * 数据源健康indicator。
     */
    public DataSourceHealthIndicator() {
        /**
         * 父。
         * @param "数据源健康检查失败" "数据源健康检查失败"
         */
        super("数据源健康检查失败");
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        startRefresherIfNeeded();
        HealthSnapshot snap = snapshot;
        if (snap == null) {
            snap = awaitFirstSnapshot();
        }
        if (snap == null) {
            builder.unknown().withDetail("message", "数据源健康检查尚未完成首次采集");
            return;
        }
        long age = System.currentTimeMillis() - snap.at;
        builder.status(snap.health.getStatus());
        for (Map.Entry<String, Object> entry : snap.health.getDetails().entrySet()) {
            builder.withDetail(entry.getKey(), entry.getValue());
        }
        if (age > SNAPSHOT_TTL_MS) {
            builder.withDetail("stale", Boolean.TRUE);
            builder.withDetail("checkAgeMs", age);
        }
    }

    /**
     * 检查所有已注册数据源并构建健康结果（只在后台线程执行）。
     * @return 健康结果
     */
    private Health checkAll() {
        Map<String, DataSource> allDataSources = DataSourceContextSupport.getAllDataSources();
        Health.Builder builder = new Health.Builder();

        if (allDataSources.isEmpty()) {
            return builder.outOfService().withDetail("message", "没有已注册的数据源").build();
        }

        Map<String, Object> dsDetails = new LinkedHashMap<>();
        boolean allUp = true;
        int upCount = 0;
        int downCount = 0;
        int verified = 0;
        long deadline = System.currentTimeMillis() + REFRESH_BUDGET_MS;

        for (Map.Entry<String, DataSource> entry : allDataSources.entrySet()) {
            String dsName = entry.getKey();
            DataSource dataSource = entry.getValue();

            if (dataSource == null) {
                dsDetails.put(dsName, Map.of("status", "UNKNOWN", "message", "数据源实例为 null"));
                allUp = false;
                downCount++;
                verified++;
                continue;
            }

            if (System.currentTimeMillis() > deadline) {
                dsDetails.put(dsName, Map.of("status", "UNKNOWN", "message", "本轮检查预算耗尽，未检查"));
                continue;
            }

            // 对每个数据源执行快速连接验证
            Map<String, Object> dsHealth = checkSingleDataSource(dsName, dataSource);
            dsDetails.put(dsName, dsHealth);
            verified++;

            if ("UP".equals(dsHealth.get("status"))) {
                upCount++;
            } else {
                allUp = false;
                downCount++;
            }
        }

        // 构建整体健康状态
        builder.withDetail("total", allDataSources.size());
        builder.withDetail("up", upCount);
        builder.withDetail("down", downCount);
        if (verified < allDataSources.size()) {
            builder.withDetail("unverified", allDataSources.size() - verified);
        }
        builder.withDetail("dataSources", dsDetails);

        if (verified == 0) {
            // 本轮一个数据源都没验证完（预算被卡死的数据源耗尽）——如实报 UNKNOWN，不谎报 DOWN
            builder.unknown();
        } else if (allUp) {
            builder.up();
        } else if (upCount == 0) {
            builder.down();
        } else {
            // 部分数据源不可用 — 使用 DEGRADED 状态
            builder.status(new Status("DEGRADED", "部分数据源不可用"));
        }
        return builder.build();
    }

    /**
     * 检查单个数据源的健康状态
     * @param name 名称
     * @param dataSource 数据源
     * @return 检查单个数据源的结果
     */
    private Map<String, Object> checkSingleDataSource(String name, DataSource dataSource) {
        Map<String, Object> result = new LinkedHashMap<>();
        long start = System.currentTimeMillis();

        /**
         * 尝试。
         * @param dataSource.getConnection( 数据源.getconnection(
         */
        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(VALIDATION_TIMEOUT_SECONDS);
            long elapsed = System.currentTimeMillis() - start;

            if (valid) {
                result.put("status", "UP");
                result.put("responseTime", elapsed);
            } else {
                result.put("status", "DOWN");
                result.put("responseTime", elapsed);
                result.put("error", "连接验证返回 false");
            }
        } catch (SQLException e) {
            long elapsed = System.currentTimeMillis() - start;
            String errorMsg = e.getMessage();

            // 暂时性错误：仍然标记为 UP 但附带警告
            if (isTransientError(errorMsg)) {
                result.put("status", "UP");
                result.put("warning", errorMsg);
                result.put("responseTime", elapsed);
                log.warn("[数据源健康] {} 暂时性连接异常: {}", name, errorMsg);
            } else {
                result.put("status", "DOWN");
                result.put("error", errorMsg != null ? errorMsg : e.getClass().getSimpleName());
                result.put("responseTime", elapsed);
                log.warn("[数据源健康] {} 连接检查失败: {}", name, errorMsg);
            }
        }

        return result;
    }

    /**
     * 判断是否为暂时性错误（网络抖动、超时等）
     * @param errorMsg 错误msg
     * @return 是否transient错误的结果
     */
    private boolean isTransientError(String errorMsg) {
        if (errorMsg == null) {
            return false;
        }
        for (String prefix : TRANSIENT_ERROR_PREFIXES) {
            if (errorMsg.contains(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 懒启动后台刷新线程（重复调用安全）。
     */
    private void startRefresherIfNeeded() {
        Thread current = refresher;
        if (current != null && current.isAlive()) {
            return;
        }
        synchronized (refresherLock) {
            if (refresher != null && refresher.isAlive()) {
                return;
            }
            Thread thread = new Thread(this::refreshLoop, "datasource-health-refresher");
            thread.setDaemon(true);
            thread.start();
            refresher = thread;
        }
    }

    /**
     * 后台刷新循环：持续检查并维护快照；单轮耗时超过预算时下一轮立即补上。
     */
    private void refreshLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            long start = System.currentTimeMillis();
            try {
                Health health = checkAll();
                snapshot = new HealthSnapshot(health, System.currentTimeMillis());
            } catch (Throwable t) {
                // 检查失败不中断刷新循环，下一轮重试
                log.warn("[数据源健康] 后台刷新失败: {}", t.toString());
            }
            long elapsed = System.currentTimeMillis() - start;
            long sleep = Math.max(1000L, SNAPSHOT_TTL_MS - elapsed);
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * 首次请求等待后台首轮结果（有限等待，超时返回当前快照或 null）。
     * @return 快照，可能为 null
     */
    private HealthSnapshot awaitFirstSnapshot() {
        long deadline = System.currentTimeMillis() + FIRST_RESULT_WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            HealthSnapshot snap = snapshot;
            if (snap != null) {
                return snap;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return snapshot;
    }

    /**
     * 不可变健康快照。
     */
    private static final class HealthSnapshot {
        private final Health health;
        private final long at;

        private HealthSnapshot(Health health, long at) {
            this.health = health;
            this.at = at;
        }
    }
}
