package com.chua.starter.project.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.project.support.constants.ProjectConstants;
import com.chua.starter.project.support.entity.MsProject;
import com.chua.starter.project.support.entity.MsProjectDeployLog;
import com.chua.starter.project.support.mapper.MsProjectDeployLogMapper;
import com.chua.starter.project.support.mapper.MsProjectMapper;
import com.chua.starter.project.support.service.ProjectAlarmHook;
import com.chua.starter.project.support.service.ProjectPushService;
import com.chua.starter.project.support.service.WatchdogService;
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
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 守护进程服务实现（持续存活检测 + 自动拉起）
 * <p>
 * 时间轮到期回调 {@link #checkOne(MsProject)}：
 * 优先执行项目自定义 {@code msProjectCheckScript}（exit 0=健康），未配置时回退
 * 健康检查类型（PORT/HTTP/PROCESS）探测；不健康时累计连续失败计数，达到
 * {@code msProjectWatchdogMaxFail} 阈值自动执行重启脚本拉起，全程写
 * {@code ms_project_deploy_log}（action=WATCHDOG）留并经 socket 推送。
 * 连续失败计数与拉起次数存内存（进程级），后端重启清零。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WatchdogServiceImpl implements WatchdogService {

    /** 项目 映射器 */
    private final MsProjectMapper projectMapper;

    /** 部署日志 映射器 */
    private final MsProjectDeployLogMapper deployLogMapper;

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

    /** Jackson */
    private final ObjectMapper objectMapper;

    /** 单主机守护探测超时（秒） */
    private static final int PROBE_TIMEOUT_SECONDS = 30;

    /** 项目连续失败计数（内存级，重启清零） */
    private final ConcurrentHashMap<Long, AtomicInteger> consecutiveFailMap = new ConcurrentHashMap<>();

    /** 项目累计自动拉起次数（内存级） */
    private final ConcurrentHashMap<Long, AtomicInteger> restartedCountMap = new ConcurrentHashMap<>();

    /** 项目最近一次检测时间 */
    private final ConcurrentHashMap<Long, LocalDateTime> lastCheckTimeMap = new ConcurrentHashMap<>();

    /** 项目最近一次检测结果（HEALTHY/UNHEALTHY/RESTARTED） */
    private final ConcurrentHashMap<Long, String> lastResultMap = new ConcurrentHashMap<>();

    /** 守护探测专用线程池（多项目并行探测，4 线程） */
    private final ExecutorService probeExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "project-watchdog-probe");
        t.setDaemon(true);
        return t;
    });

    @Override
    public void checkOne(MsProject project) {
        Long projectId = project.getId();
        int maxFail = project.getMsProjectWatchdogMaxFail() == null ? 1 : project.getMsProjectWatchdogMaxFail();
        List<Long> hostIds = parseHostIds(project.getMsProjectHostIds());
        if (hostIds.isEmpty()) {
            return;
        }
        boolean healthy = true;
        String firstError = null;
        for (Long hostId : hostIds) {
            MsServerHost host = serverHostMapper.selectById(hostId);
            if (host == null) {
                healthy = false;
                firstError = "目标服务器不存在: " + hostId;
                continue;
            }
            try {
                ReturnResult<SshCredential> resolved = serverHostService.resolveCredential(hostId, null);
                if (!resolved.isOk()) {
                    healthy = false;
                    firstError = resolved.getMsg();
                    continue;
                }
                boolean ok = probeHost(project, host, resolved.getData());
                if (!ok) {
                    healthy = false;
                    firstError = "主机 " + host.getServerHostName() + " 探测不通过";
                }
            } catch (Exception e) {
                healthy = false;
                firstError = host.getServerHostName() + " 探测异常: " + e.getMessage();
                log.warn("[watchdog] 项目 {} 主机 {} 探测异常: {}", projectId, hostId, e.getMessage());
            }
        }
        recordResult(project, healthy, firstError, maxFail);
    }

    /**
    * 单主机探测：优先自定义检测脚本（exit 0=健康），未配置时回退健康检查类型
    *
    * @param project    项目
    * @param host       主机
    * @param credential 凭据
    * @return 是否健康
    * @throws Exception 连接/超时异常
    */
    private boolean probeHost(MsProject project, MsServerHost host, SshCredential credential) throws Exception {
        String script = project.getMsProjectCheckScript();
        String cmd;
        if (script != null && !script.isBlank()) {
            cmd = renderScript(script, project.getMsProjectDeployDir(), isLinux(host));
        } else {
            String target = project.getMsProjectHealthTarget();
            boolean linux = isLinux(host);
            cmd = switch (project.getMsProjectHealthType()) {
                case ProjectConstants.HEALTH_TYPE_PORT ->
                        linux ? "ss -tlnp 2>/dev/null | grep -q ':" + target + "'"
                                : "netstat -ano 2>nul | findstr :" + target + " >nul 2>&1";
                case ProjectConstants.HEALTH_TYPE_HTTP ->
                        linux ? "curl -sI --max-time 10 http://" + target + " >/dev/null 2>&1"
                                : "powershell -NoProfile -Command \"try { Invoke-WebRequest -Uri 'http://"
                                + target + "' -UseBasicParsing -TimeoutSec 5 | Out-Null } catch { exit 1 }\"";
                case ProjectConstants.HEALTH_TYPE_PROCESS ->
                        linux ? "pgrep -f '" + target + "'"
                                : "tasklist 2>nul | findstr /i " + target;
                default -> "true";
            };
        }
        SshClients.Handle handle = null;
        boolean connectionBroken = false;
        try {
            handle = connectionManager.borrow(host, credential);
            final SshClients.Handle borrowed = handle;
            Future<Boolean> future = probeExecutor.submit(() -> {
                var result = borrowed.client().exec().command(cmd).execute();
                return result.exitCode() == 0;
            });
            try {
                return future.get(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException te) {
                future.cancel(true);
                borrowed.client().close();
                throw new IllegalStateException("探测命令超时(>" + PROBE_TIMEOUT_SECONDS + "s)");
            }
        } catch (Exception e) {
            connectionBroken = true;
            throw e;
        } finally {
            if (handle != null) {
                connectionManager.release(host.getId());
            }
            if (connectionBroken) {
                // 探测抛错说明这条连接已经废了：作废它，别等到心跳发现才恢复
                connectionManager.invalidate(host.getId());
            }
        }
    }

    /**
    * 记录检测结果：连续失败计数、阈值触发自动拉起、状态写库与推送
    *
    * @param project    项目
    * @param healthy    是否健康
    * @param firstError 首个错误描述
    * @param maxFail    连续失败阈值
    */
    private void recordResult(MsProject project, boolean healthy, String firstError, int maxFail) {
        Long projectId = project.getId();
        lastCheckTimeMap.put(projectId, LocalDateTime.now());
        if (healthy) {
            consecutiveFailMap.put(projectId, new AtomicInteger(0));
            lastResultMap.put(projectId, ProjectConstants.RUNTIME_HEALTHY);
            return;
        }
        AtomicInteger failCounter = consecutiveFailMap.computeIfAbsent(projectId, k -> new AtomicInteger(0));
        int failCount = failCounter.incrementAndGet();
        int threshold = Math.max(1, maxFail);
        if (failCount < threshold) {
            lastResultMap.put(projectId, ProjectConstants.RUNTIME_UNHEALTHY);
            pushService.pushProgress(projectId, null, ProjectConstants.ACTION_WATCHDOG, "CHECK",
                    "FAIL", "守护检测第 " + failCount + "/" + threshold + " 次不健康: " + firstError,
                    project.getMsProjectCurrentVersion());
            insertLog(project, null, "WATCHDOG-CHECK", firstError);
            alarmHook.evaluateProjectMetric(project, ProjectAlarmHook.METRIC_WATCHDOG_FAIL, failCount);
            return;
        }
        // 达到阈值：自动执行重启脚本拉起
        lastResultMap.put(projectId, "RESTARTED");
        int restarted = restartedCountMap.computeIfAbsent(projectId, k -> new AtomicInteger(0)).incrementAndGet();
        String restartScript = project.getMsProjectRestartScript();
        if (restartScript == null || restartScript.isBlank()) {
            restartScript = project.getMsProjectStartScript();
        }
        if (restartScript == null || restartScript.isBlank()) {
            MsProject update = new MsProject();
            update.setId(projectId);
            update.setMsProjectRuntimeStatus(ProjectConstants.RUNTIME_UNHEALTHY);
            projectMapper.updateById(update);
            pushService.pushProgress(projectId, null, ProjectConstants.ACTION_WATCHDOG, "CHECK",
                    "FAIL", "守护检测连续 " + failCount + " 次不健康且未配置重启/启动脚本，需人工介入: " + firstError,
                    project.getMsProjectCurrentVersion());
            insertLog(project, null, "WATCHDOG-ALERT", firstError + "（无重启脚本，未自动拉起）");
            return;
        }
        // 多主机并发执行重启脚本
        List<Long> hostIds = parseHostIds(project.getMsProjectHostIds());
        int size = Math.max(1, Math.min(hostIds.size(), 4));
        ExecutorService pool = new java.util.concurrent.ThreadPoolExecutor(size, size, 60L,
                TimeUnit.SECONDS, new java.util.concurrent.ArrayBlockingQueue<>(16),
                r -> {
                    Thread t = new Thread(r, "project-watchdog-restart-" + projectId);
                    t.setDaemon(true);
                    return t;
                });
        boolean anyOk = false;
        try {
            String rendered = renderScript(restartScript, project.getMsProjectDeployDir(),
                    project.getMsProjectHostOs() != null
                            && project.getMsProjectHostOs().toUpperCase(java.util.Locale.ROOT).startsWith("LINUX"));
            List<Future<MsProjectDeployLog>> futures = new ArrayList<>();
            for (Long hostId : hostIds) {
                MsServerHost host = serverHostMapper.selectById(hostId);
                futures.add(pool.submit(() -> execRestart(project, host, rendered)));
            }
            List<MsProjectDeployLog> results = new ArrayList<>();
            for (Future<MsProjectDeployLog> f : futures) {
                try {
                    results.add(f.get());
                } catch (Exception e) {
                    log.warn("[watchdog] 项目 {} 重启任务异常: {}", projectId, e.getMessage());
                }
            }
            for (MsProjectDeployLog item : results) {
                deployLogMapper.insert(item);
                if (ProjectConstants.LOG_STATUS_SUCCESS.equals(item.getMsProjectDeployLogStatus())) {
                    anyOk = true;
                }
            }
        } finally {
            pool.shutdownNow();
        }
        MsProject update = new MsProject();
        update.setId(projectId);
        update.setMsProjectRuntimeStatus(anyOk ? ProjectConstants.RUNTIME_RUNNING : ProjectConstants.RUNTIME_UNHEALTHY);
        projectMapper.updateById(update);
        pushService.pushProgress(projectId, null, ProjectConstants.ACTION_WATCHDOG, "RESTART",
                anyOk ? "SUCCESS" : "FAIL",
                anyOk
                        ? "守护第 " + restarted + " 次自动拉起成功（连续 " + failCount + " 次检测不健康）"
                        : "守护第 " + restarted + " 次自动拉起失败: " + firstError,
                project.getMsProjectCurrentVersion());
        if (!anyOk) {
            alarmHook.evaluateProjectMetric(project, ProjectAlarmHook.METRIC_WATCHDOG_RESTART_FAIL, restarted);
        }
    }

    /**
    * 执行单主机重启脚本并返回日志记录
    *
    * @param project 项目
    * @param host    主机
    * @param command 命令
    * @return 部署日志
    */
    private MsProjectDeployLog execRestart(MsProject project, MsServerHost host, String command) {
        long startMs = System.currentTimeMillis();
        MsProjectDeployLog item = new MsProjectDeployLog();
        item.setMsProjectDeployLogProjectId(project.getId());
        item.setMsProjectDeployLogProjectName(project.getMsProjectName());
        if (host != null) {
            item.setMsProjectDeployLogHostId(host.getId());
            item.setMsProjectDeployLogHostName(host.getServerHostName());
        }
        item.setMsProjectDeployLogAction(ProjectConstants.ACTION_WATCHDOG);
        item.setMsProjectDeployLogVersion(project.getMsProjectCurrentVersion());
        item.setMsProjectDeployLogStartTime(LocalDateTime.now());
        if (host == null) {
            item.setMsProjectDeployLogStatus(ProjectConstants.LOG_STATUS_FAIL);
            item.setMsProjectDeployLogError("目标服务器不存在");
            item.setMsProjectDeployLogEndTime(LocalDateTime.now());
            item.setMsProjectDeployLogCostMs(System.currentTimeMillis() - startMs);
            return item;
        }
        SshClients.Handle handle = null;
        try {
            ReturnResult<SshCredential> resolved = serverHostService.resolveCredential(host.getId(), null);
            if (!resolved.isOk()) {
                item.setMsProjectDeployLogStatus(ProjectConstants.LOG_STATUS_FAIL);
                item.setMsProjectDeployLogError(resolved.getMsg());
            } else {
                handle = connectionManager.borrow(host, resolved.getData());
                var result = handle.client().exec().command(command).execute();
                item.setMsProjectDeployLogOutput(result.stdout());
                if (result.exitCode() == 0) {
                    item.setMsProjectDeployLogStatus(ProjectConstants.LOG_STATUS_SUCCESS);
                } else {
                    item.setMsProjectDeployLogStatus(ProjectConstants.LOG_STATUS_FAIL);
                    String stderr = result.stderr();
                    if (stderr != null && !stderr.isBlank()) {
                        item.setMsProjectDeployLogError(stderr);
                    } else {
                        item.setMsProjectDeployLogError(result.exitCode() == -1
                                ? ProjectConstants.EXIT_NO_STATUS_MESSAGE : "退出码非0: " + result.exitCode());
                    }
                }
            }
        } catch (Exception e) {
            item.setMsProjectDeployLogStatus(ProjectConstants.LOG_STATUS_FAIL);
            item.setMsProjectDeployLogError(e.getMessage());
        } finally {
            if (handle != null) {
                connectionManager.release(host.getId());
            }
            item.setMsProjectDeployLogEndTime(LocalDateTime.now());
            item.setMsProjectDeployLogCostMs(System.currentTimeMillis() - startMs);
        }
        return item;
    }

    /**
    * 插入守护检测留痕日志（action 固定为 WATCHDOG，阶段放 output 前缀，避免超出列宽）
    *
    * @param project 项目
    * @param hostId  主机 标识，可为空
    * @param stage   阶段标记
    * @param message 消息
    */
    private void insertLog(MsProject project, Long hostId, String stage, String message) {
        MsProjectDeployLog item = new MsProjectDeployLog();
        item.setMsProjectDeployLogProjectId(project.getId());
        item.setMsProjectDeployLogProjectName(project.getMsProjectName());
        if (hostId != null) {
            MsServerHost host = serverHostMapper.selectById(hostId);
            if (host != null) {
                item.setMsProjectDeployLogHostId(host.getId());
                item.setMsProjectDeployLogHostName(host.getServerHostName());
            }
        }
        item.setMsProjectDeployLogAction(ProjectConstants.ACTION_WATCHDOG);
        item.setMsProjectDeployLogVersion(project.getMsProjectCurrentVersion());
        item.setMsProjectDeployLogStatus(ProjectConstants.LOG_STATUS_FAIL);
        item.setMsProjectDeployLogOutput("[" + stage + "] " + message);
        item.setMsProjectDeployLogStartTime(LocalDateTime.now());
        item.setMsProjectDeployLogEndTime(LocalDateTime.now());
        item.setMsProjectDeployLogCostMs(0L);
        deployLogMapper.insert(item);
    }

    @Override
    public List<MsProject> enabledProjects() {
        LambdaQueryWrapper<MsProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MsProject::getMsProjectWatchdogEnabled, 1)
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
                || project.getMsProjectWatchdogEnabled() == null
                || project.getMsProjectWatchdogEnabled() != 1
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
                && project.getMsProjectWatchdogEnabled() != null
                && project.getMsProjectWatchdogEnabled() == 1
                && project.getMsProjectEnabled() != null
                && project.getMsProjectEnabled() == 1;
    }

    @Override
    public int watchdogIntervalOf(Long projectId) {
        MsProject project = projectId == null ? null : projectMapper.selectById(projectId);
        if (project == null || project.getMsProjectWatchdogInterval() == null
                || project.getMsProjectWatchdogInterval() < 5) {
            return 30;
        }
        return project.getMsProjectWatchdogInterval();
    }

    @Override
    public Map<String, Object> statusOf(Long projectId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("consecutiveFail", consecutiveFailMap.getOrDefault(projectId, new AtomicInteger(0)).get());
        result.put("restartedCount", restartedCountMap.getOrDefault(projectId, new AtomicInteger(0)).get());
        result.put("lastCheckTime", lastCheckTimeMap.get(projectId));
        result.put("lastResult", lastResultMap.get(projectId));
        MsProject project = projectId == null ? null : projectMapper.selectById(projectId);
        result.put("watchdogEnabled", project != null
                && project.getMsProjectWatchdogEnabled() != null
                && project.getMsProjectWatchdogEnabled() == 1);
        result.put("interval", project == null ? null : project.getMsProjectWatchdogInterval());
        result.put("maxFail", project == null ? null : project.getMsProjectWatchdogMaxFail());
        return result;
    }

    /**
    * 脚本模板渲染：{{dir}} 替换为部署目录（按目标 OS 转义）
    *
    * @param script 脚本
    * @param dir    部署目录
    * @param linux  是否 Linux
    * @return 渲染后脚本
    */
    private String renderScript(String script, String dir, boolean linux) {
        if (script == null) {
            return "";
        }
        String safeDir;
        if (dir == null || dir.isBlank()) {
            safeDir = "";
        } else if (linux) {
            safeDir = "'" + dir.replace("'", "'\\''") + "'";
        } else {
            safeDir = "\"" + dir.replace("\"", "\\\"") + "\"";
        }
        return script.replace("{{dir}}", safeDir);
    }

    /**
    * 解析 主机标识 JSON 数组
    *
    * @param json JSON 字符串
    * @return ID 列表，解析失败返回空列表
    */
    private List<Long> parseHostIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
        } catch (Exception e) {
            log.warn("[watchdog] hostIds 解析失败: {}", json);
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
}
