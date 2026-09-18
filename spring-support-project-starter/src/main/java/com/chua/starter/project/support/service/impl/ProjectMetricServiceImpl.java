package com.chua.starter.project.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.project.support.config.ProjectProperties;
import com.chua.starter.project.support.constants.ProjectConstants;
import com.chua.starter.project.support.entity.MsProject;
import com.chua.starter.project.support.mapper.MsProjectMapper;
import com.chua.starter.project.support.service.ProjectAlarmHook;
import com.chua.starter.project.support.service.ProjectMetricService;
import com.chua.starter.project.support.service.ProjectPushService;
import com.chua.starter.server.support.connection.ConnectionManager;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.util.SshClients;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 项目指标采集服务实现（SSH 轮询，零 Agent）
 * <p>
 * 指标名与告警引擎约定一致（{@link ProjectAlarmHook}）：
 * {@code project.process.mem.mb} / {@code project.process.cpu} /
 * {@code project.port.alive} / {@code project.http.code}；
 * 时间序列写宿主指标表（JDBC 直写，表由 {@code project.support.metric-jdbc-url}
 * 指定，未配置时静默跳过持久化），实时值经 socket 推送，
 * 同时喂给告警引擎评估规则。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectMetricServiceImpl implements ProjectMetricService {

    /** 项目 映射器 */
    private final MsProjectMapper projectMapper;

    /** 服务器主机 映射器（来自 spring-支持-服务端-启动） */
    private final MsServerHostMapper serverHostMapper;

    /** 服务器主机服务（凭据解析） */
    private final ServerHostService serverHostService;

    /** SSH 连接池 */
    private final ConnectionManager connectionManager;

    /** 实时推送 */
    private final ProjectPushService pushService;

    /** 告警出口（反射桥接 monitor 告警引擎，可选） */
    private final ProjectAlarmHook alarmHook;

    /** 模块配置 */
    private final ProjectProperties projectProperties;

    /** JDBC 模板（写主数据源 ms_project_metric 时间序列表） */
    private final JdbcTemplate jdbcTemplate;

    /** Jackson */
    private final ObjectMapper objectMapper;

    /** 单主机指标采集超时（秒） */
    private static final int COLLECT_TIMEOUT_SECONDS = 20;

    /** 指标采集专用线程池（多项目并行采集，4 线程） */
    private final ExecutorService collectExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "project-metric-collect");
        t.setDaemon(true);
        return t;
    });

    @Override
    public void collectOne(MsProject project) {
        Long projectId = project.getId();
        List<Long> hostIds = parseMetricHostIds(project);
        if (hostIds.isEmpty()) {
            return;
        }
        for (Long hostId : hostIds) {
            MsServerHost host = serverHostMapper.selectById(hostId);
            if (host == null) {
                continue;
            }
            try {
                collectFromHost(project, host, hostId);
            } catch (Exception e) {
                log.warn("[project-metric] 项目 {} 主机 {} 采集失败: {}", projectId, host.getServerHostName(), e.getMessage());
                pushService.pushProgress(projectId, hostId, "METRIC", "COLLECT", "FAIL",
                        "指标采集失败: " + e.getMessage(), project.getMsProjectCurrentVersion());
            }
        }
    }

    /**
    * 单主机指标采集：进程内存/CPU + 端口存活 + HTTP 健康码
    *
    * @param project 项目
    * @param host    主机
    * @param hostId  主机 标识
    */
    private void collectFromHost(MsProject project, MsServerHost host, Long hostId) {
        ReturnResult<SshCredential> resolved = serverHostService.resolveCredential(hostId, null);
        if (!resolved.isOk()) {
            pushService.pushProgress(project.getId(), hostId, "METRIC", "COLLECT", "FAIL",
                    "凭据解析失败: " + resolved.getMsg(), project.getMsProjectCurrentVersion());
            boolean wasFailed = Boolean.TRUE.equals(collectFailState.get(collectFailKey(project, host)));
            if (!wasFailed) {
                collectFailState.put(collectFailKey(project, host), Boolean.TRUE);
                persistOne(project, host, ProjectAlarmHook.METRIC_COLLECT_FAIL, 1);
            } else {
                pushService.pushProjectMetric(project.getId(), hostId, ProjectAlarmHook.METRIC_COLLECT_FAIL, 1, java.time.LocalDateTime.now());
                alarmHook.evaluateProjectMetric(project, ProjectAlarmHook.METRIC_COLLECT_FAIL, 1.0);
            }
            return;
        }
        boolean linux = isLinux(host);
        Map<String, Double> metrics = new LinkedHashMap<>();
        String target = project.getMsProjectHealthTarget();
        // 单轮采集 SSH 失败短路：连接失败后不再逐条命令重连（每条命令都有连接超时开销）
        java.util.concurrent.atomic.AtomicBoolean sshBroken = new java.util.concurrent.atomic.AtomicBoolean(false);
        if (isNotBlank(target)) {
            // 进程内存（RSS MB）：按关键字精确匹配，避免整机误采
            String key = target.trim();
            String memOut = execQuiet(host, resolved.getData(), buildProcessMemCommand(key, linux), sshBroken);
            if (memOut != null) {
                metrics.put(ProjectAlarmHook.METRIC_PROCESS_MEM_MB, parseDouble(memOut, -1));
            }
            // 进程 CPU（%）
            String cpuOut = execQuiet(host, resolved.getData(), buildProcessCpuCommand(key, linux), sshBroken);
            if (cpuOut != null) {
                metrics.put(ProjectAlarmHook.METRIC_PROCESS_CPU, parseDouble(cpuOut, -1));
            }
        }
        String healthType = project.getMsProjectHealthType();
        if (ProjectConstants.HEALTH_TYPE_PORT.equals(healthType) && isNotBlank(target) && !sshBroken.get()) {
            String port = portOf(target);
            if (port != null) {
                String aliveCmd = linux
                        ? "ss -tln 2>/dev/null | grep -q ':" + port + "' && echo 1 || echo 0"
                        : "netstat -ano 2>nul | findstr :" + port + " >nul 2>&1 && echo 1 || echo 0";
                String aliveOut = execQuiet(host, resolved.getData(), aliveCmd, sshBroken);
                if (aliveOut != null) {
                    metrics.put(ProjectAlarmHook.METRIC_PORT_ALIVE, parseDouble(aliveOut, -1));
                }
            }
        }
        if (ProjectConstants.HEALTH_TYPE_HTTP.equals(healthType) && isNotBlank(target) && !sshBroken.get()) {
            String codeCmd = linux
                    ? "curl -s -o /dev/null -w '%{http_code}' --max-time 8 http://" + target + " 2>/dev/null || echo 000"
                    : "powershell -NoProfile -Command \"try { (Invoke-WebRequest -Uri 'http://"
                    + target + "' -UseBasicParsing -TimeoutSec 8).StatusCode } catch { 0 }\"";
            String codeOut = execQuiet(host, resolved.getData(), codeCmd, sshBroken);
            if (codeOut != null) {
                metrics.put("project.http.code", parseDouble(codeOut, -1));
            }
        }
        if (metrics.isEmpty()) {
            // 全部命令执行失败（SSH 不可达/认证失败/命令异常）：
            // DB 只在状态翻转时落点（去刷屏）；但 alarm 引擎每周期都喂值（让 consecutiveCount 正常累计）
            boolean wasFailed = Boolean.TRUE.equals(collectFailState.get(collectFailKey(project, host)));
            if (!wasFailed) {
                // 翻转：首次失败 → 落 DB + 推 socket + 喂告警
                collectFailState.put(collectFailKey(project, host), Boolean.TRUE);
                persistOne(project, host, ProjectAlarmHook.METRIC_COLLECT_FAIL, 1);
            } else {
                // 仍在失败：DB 不重复写，但每周期喂告警引擎（让连续触发计数器递增）
                pushService.pushProjectMetric(project.getId(), hostId, ProjectAlarmHook.METRIC_COLLECT_FAIL, 1, java.time.LocalDateTime.now());
                alarmHook.evaluateProjectMetric(project, ProjectAlarmHook.METRIC_COLLECT_FAIL, 1.0);
            }
            return;
        }
        // 采集恢复正常：清除失败标记
        boolean wasFailedRecovery = Boolean.TRUE.equals(collectFailState.remove(collectFailKey(project, host)));
        if (wasFailedRecovery) {
            // 翻转恢复：落一条 collect.fail=0 表明恢复，推给 alarm 引擎归零连续计数
            persistOne(project, host, ProjectAlarmHook.METRIC_COLLECT_FAIL, 0);
        }
        persistMetrics(project, host, metrics);
        java.time.LocalDateTime collectTime = java.time.LocalDateTime.now();
        for (Map.Entry<String, Double> entry : metrics.entrySet()) {
            pushService.pushProjectMetric(project.getId(), hostId, entry.getKey(), entry.getValue(), collectTime);
            alarmHook.evaluateProjectMetric(project, entry.getKey(), entry.getValue());
        }
    }

    /** 采集失败状态（项目:主机 → 是否已落点），用于去刷屏 */
    private final java.util.concurrent.ConcurrentHashMap<String, Boolean> collectFailState =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
    * 采集失败状态键
    *
    * @param project 项目
    * @param host    主机
    * @return 键
    */
    private String collectFailKey(MsProject project, MsServerHost host) {
        return project.getId() + ":" + host.getId();
    }

    /**
    * 持久化单个指标点（采集失败占位等场景）
    *
    * @param project    项目
    * @param host       主机
    * @param metricName 指标名
    * @param value      值
    */
    private void persistOne(MsProject project, MsServerHost host, String metricName, double value) {
        Map<String, Double> metrics = new LinkedHashMap<>(1);
        metrics.put(metricName, value);
        persistMetrics(project, host, metrics);
        java.time.LocalDateTime collectTime = java.time.LocalDateTime.now();
        pushService.pushProjectMetric(project.getId(), host.getId(), metricName, value, collectTime);
        alarmHook.evaluateProjectMetric(project, metricName, value);
    }

    /**
    * 持久化指标点到 ms_project_metric（JdbcTemplate 写主数据源）
    *
    * @param project 项目
    * @param host    主机
    * @param metrics 指标点
    */
    private void persistMetrics(MsProject project, MsServerHost host, Map<String, Double> metrics) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        for (Map.Entry<String, Double> entry : metrics.entrySet()) {
            if (entry.getValue() == null || entry.getValue() < 0) {
                continue;
            }
            try {
                jdbcTemplate.update(
                        "INSERT INTO ms_project_metric (project_id, host_id, metric_name, metric_value, collect_time) "
                                + "VALUES (?, ?, ?, ?, ?)",
                        project.getId(), host.getId(), entry.getKey(), entry.getValue(), now);
            } catch (Exception e) {
                log.debug("[project-metric] 指标持久化失败（不影响采集主流程）: {}", e.getMessage());
            }
        }
    }

    /**
    * 确保指标表存在（幂等建表）
    *
    * @param conn 连接
    * @throws java.sql.SQLException SQL 异常
    */
    @SuppressWarnings("unused")
    private void ensureTable(java.sql.Connection conn) throws java.sql.SQLException {
        try (var stmt = conn.createStatement()) {
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS ms_project_metric ("
                            + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                            + "project_id BIGINT NOT NULL,"
                            + "host_id BIGINT,"
                            + "metric_name VARCHAR(64) NOT NULL,"
                            + "metric_value DOUBLE,"
                            + "collect_time DATETIME,"
                            + "KEY idx_pm_project (project_id),"
                            + "KEY idx_pm_metric (metric_name)"
                            + ")");
        } catch (java.sql.SQLException e) {
            // H2 内存库不支持 inline KEY 语法时降级建表（仅主键 + 普通列）
            if (e.getMessage() != null && e.getMessage().contains("KEY")) {
                try (var stmt2 = conn.createStatement()) {
                    stmt2.executeUpdate(
                            "CREATE TABLE IF NOT EXISTS ms_project_metric ("
                                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                                    + "project_id BIGINT NOT NULL,"
                                    + "host_id BIGINT,"
                                    + "metric_name VARCHAR(64) NOT NULL,"
                                    + "metric_value DOUBLE,"
                                    + "collect_time DATETIME)");
                }
                return;
            }
            throw e;
        }
    }

    @Override
    public List<Map<String, Object>> queryHistory(Long projectId, String metricName, int limit) {
        if (projectId == null) {
            return List.of();
        }
        int max = limit <= 0 ? 200 : Math.min(limit, 1000);
        String sql = "SELECT metric_name AS metricName, metric_value AS metricValue, host_id AS hostId, "
                + "collect_time AS collectTime FROM ms_project_metric "
                + "WHERE project_id = ?"
                + (metricName == null || metricName.isBlank() ? "" : " AND metric_name = ?")
                + " ORDER BY collect_time DESC LIMIT " + max;
        List<Object> args = new ArrayList<>();
        args.add(projectId);
        if (metricName != null && !metricName.isBlank()) {
            args.add(metricName);
        }
        try {
            return jdbcTemplate.query(sql, args.toArray(), (rs, rownum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("metricName", rs.getString("metricName"));
                row.put("metricValue", rs.getDouble("metricValue"));
                long hostIdVal = rs.getLong("hostId");
                row.put("hostId", rs.wasNull() ? null : hostIdVal);
                java.sql.Timestamp ts = rs.getTimestamp("collectTime");
                row.put("collectTime", ts == null ? null : ts.toLocalDateTime().toString());
                return row;
            });
        } catch (Exception e) {
            log.debug("[project-metric] 历史查询失败: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public int cleanupExpired() {
        int retentionDays = projectProperties.getMetricRetentionDays();
        if (retentionDays <= 0) {
            return 0;
        }
        try {
            int deleted = jdbcTemplate.update(
                    "DELETE FROM ms_project_metric WHERE collect_time < ?",
                    java.time.LocalDateTime.now().minusDays(retentionDays));
            if (deleted > 0) {
                log.info("[project-metric] 清理超期指标 {} 行（保留 {} 天）", deleted, retentionDays);
            }
            return deleted;
        } catch (Exception e) {
            log.debug("[project-metric] 指标清理失败: {}", e.getMessage());
            return 0;
        }
    }

    /**
    * 静默执行 SSH 命令（采集专用，失败返回 空 不抛异常）
    *
    * @param host       主机
    * @param credential 凭据
    * @param command    命令
    * @param sshBroken  短路标记：连接失败置 true，后续命令跳过
    * @return 标准输出（trim 后），失败返回 空
    */
    private String execQuiet(MsServerHost host, SshCredential credential, String command,
                             java.util.concurrent.atomic.AtomicBoolean sshBroken) {
        if (sshBroken != null && sshBroken.get()) {
            return null;
        }
        SshClients.Handle handle = null;
        try {
            handle = connectionManager.borrow(host, credential);
            final SshClients.Handle borrowed = handle;
            Future<String> future = collectExecutor.submit(() -> {
                var result = borrowed.client().exec().command(command).execute();
                return result.stdout() == null ? "" : result.stdout().trim();
            });
            return future.get(COLLECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("[project-metric] 命令执行失败 host={}: {}", host.getServerHostName(), e.getMessage());
            if (sshBroken != null) {
                sshBroken.set(true);
            }
            return null;
        } finally {
            if (handle != null) {
                connectionManager.release(host.getId());
            }
        }
    }

    /**
    * 构建进程内存采集命令（RSS MB，按关键字精确匹配 PID）
    *
    * @param key   进程 关键字
    * @param linux 是否 Linux
    * @return 命令
    */
    private String buildProcessMemCommand(String key, boolean linux) {
        String safe = key.replace("'", "'\\''");
        return linux
                ? "pids=$(pgrep -f " + "'" + safe + "'" + " 2>/dev/null); "
                + "if [ -n \"$pids\" ]; then ps -o rss= $pids | awk '{s+=$1} END {print int(s/1024)+0}'; else echo -1; fi"
                : "powershell -NoProfile -Command \"Get-CimInstance Win32_Process | Where-Object { $_.Name -match "
                + "'" + safe + "' } | ForEach-Object { [int]($_.WorkingSetSize/1MB) } | Measure-Object -Sum | ForEach-Object { $_.Sum }\"";
    }

    /**
    * 构建进程 CPU 采集命令（按关键字匹配 PID 的 pcpu 合计，%）
    *
    * @param key   进程 关键字
    * @param linux 是否 Linux
    * @return 命令
    */
    private String buildProcessCpuCommand(String key, boolean linux) {
        String safe = key.replace("'", "'\\''");
        return linux
                ? "pids=$(pgrep -f " + "'" + safe + "'" + " 2>/dev/null); "
                + "if [ -n \"$pids\" ]; then ps -o pcpu= $pids | awk '{s+=$1} END {printf \"%.1f\", s}'; else echo -1; fi"
                : "powershell -NoProfile -Command \"Get-Process | Where-Object { $_.ProcessName -match "
                + "'" + safe + "' } | Measure-Object -Property CPU -Average | ForEach-Object { [math]::Round($_.Average*100,1) }\"";
    }

    /**
    * 解析端口号（健康目标为 host:port 或 host:port/path 时取端口）
    *
    * @param target 健康目标
    * @return 端口，无返回 空
    */
    private String portOf(String target) {
        if (target == null || target.isBlank()) {
            return null;
        }
        String t = target.trim();
        if (t.contains(":")) {
            String rest = t.substring(t.lastIndexOf(':') + 1);
            int slash = rest.indexOf('/');
            return slash >= 0 ? rest.substring(0, slash) : rest;
        }
        return t;
    }

    /**
    * 解析双精度
    *
    * @param text 文本
    * @param dflt 默认值
    * @return 数值
    */
    private double parseDouble(String text, double dflt) {
        if (text == null || text.isBlank()) {
            return dflt;
        }
        try {
            return Double.parseDouble(text.trim().split("\\s+")[0]);
        } catch (Exception e) {
            return dflt;
        }
    }

    @Override
    public List<MsProject> enabledProjects() {
        LambdaQueryWrapper<MsProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MsProject::getMsProjectMetricEnabled, 1)
                .eq(MsProject::getMsProjectEnabled, 1);
        return projectMapper.selectList(wrapper);
    }

    @Override
    public MsProject reloadEnabled(Long projectId) {
        if (projectId == null) {
            return null;
        }
        MsProject project = projectMapper.selectById(projectId);
        if (project == null
                || project.getMsProjectMetricEnabled() == null
                || project.getMsProjectMetricEnabled() != 1
                || project.getMsProjectEnabled() == null
                || project.getMsProjectEnabled() != 1) {
            return null;
        }
        return project;
    }

    @Override
    public boolean isEnabled(Long projectId) {
        MsProject project = projectId == null ? null : projectMapper.selectById(projectId);
        return project != null
                && project.getMsProjectMetricEnabled() != null
                && project.getMsProjectMetricEnabled() == 1
                && project.getMsProjectEnabled() != null
                && project.getMsProjectEnabled() == 1;
    }

    @Override
    public int metricIntervalOf(Long projectId) {
        MsProject project = projectId == null ? null : projectMapper.selectById(projectId);
        if (project == null || project.getMsProjectMetricInterval() == null
                || project.getMsProjectMetricInterval() < 5) {
            return 60;
        }
        return project.getMsProjectMetricInterval();
    }

    /**
    * 解析指标采集目标主机（优先 msProjectMetricHostIds，为空时回退全部绑定主机）
    *
    * @param project 项目
    * @return ID 列表
    */
    private List<Long> parseMetricHostIds(MsProject project) {
        List<Long> metricHosts = parseHostIds(project.getMsProjectMetricHostIds());
        if (!metricHosts.isEmpty()) {
            return metricHosts;
        }
        return parseHostIds(project.getMsProjectHostIds());
    }

    /**
    * 解析 主机标识 JSON 数组
    *
    * @param json JSON 字符串
    * @return ID 列表，解析失败返回空列表
    */
    private List<Long> parseHostIds(String json) {
        if (!isNotBlank(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
        } catch (Exception e) {
            log.warn("[project-metric] hostIds 解析失败: {}", json);
            return List.of();
        }
    }

    /**
    * 是否 Linux 主机
    *
    * @param host 主机
    * @return 是否 Linux
    */
    private boolean isLinux(MsServerHost host) {
        return host != null && host.getServerHostOsType() != null
                && host.getServerHostOsType().trim().toUpperCase(java.util.Locale.ROOT).startsWith("LINUX");
    }

    /**
    * 非空判断
    *
    * @param value 字符串
    * @return 是否非空
    */
    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
