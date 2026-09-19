package com.chua.starter.server.support.push;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.ssh.support.client.SshClient;
import com.chua.starter.server.support.connection.ConnectionManager;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.entity.MsServerProject;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.chua.starter.server.support.mapper.MsServerProjectMapper;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.util.SshClients;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 项目任务编排服务
 * <p>
 * 将项目操作（启动/停止/重启/升级/备份/回滚/软件安装）转为<strong>异步流式</strong>任务：
 * HTTP 立即返回 taskId，后台线程按步骤执行，经 {@link ServerPushService} 实时推送
 * 步骤、进度、逐行日志与最终结果，前端用 ReProcessTask 展示。
 * </p>
 *
 * <h3>任务事件协议（module=project, event=task, dataId=taskId）</h3>
 * <pre>
 * data = {
 *   taskId, projectId, operation,
 *   phase,        // START | STEP | LOG | PROGRESS | COMPLETE | ERROR
 *   currentStep,  // 当前步骤名
 *   progress,     // 0~100
 *   line?,        // phase=LOG 时的日志行
 *   message?,     // 结果/错误信息
 *   startTime, endTime?
 * }
 * </pre>
 *
 * <h3>断点续传</h3>
 * 每个任务在内存保留状态与最近 {@link #MAX_LOG_LINES} 行日志，前端刷新或重新订阅时
 * 可通过 {@link #getSnapshot} 拉取当前快照，续上进度与日志。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectTaskService {

    // ==================== 阶段常量 ====================
    public static final String PHASE_START = "START";
    public static final String PHASE_STEP = "STEP";
    public static final String PHASE_LOG = "LOG";
    public static final String PHASE_PROGRESS = "PROGRESS";
    public static final String PHASE_COMPLETE = "COMPLETE";
    public static final String PHASE_ERROR = "ERROR";

    /**
     * 任务保留的日志行数上限（断点续传回放用）
     */
    private static final int MAX_LOG_LINES = 500;

    /**
     * 实时日志初始行数
     */
    private static final int TAIL_LINES = 100;

    private final MsServerHostMapper hostMapper;
    private final MsServerProjectMapper projectMapper;
    private final ServerHostService serverHostService;
    private final ConnectionManager connectionManager;
    private final ServerPushService pushService;

    /**
     * 任务线程池（守护线程，含可能长时间阻塞的 follow 会话）
     */
    private final ExecutorService taskPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "project-task");
        t.setDaemon(true);
        return t;
    });

    /**
     * 任务注册表 taskId -> state
     */
    private final Map<String, TaskState> tasks = new ConcurrentHashMap<>();

    /**
     * 实时日志会话 sessionId -> session
     */
    private final Map<String, LogSession> logSessions = new ConcurrentHashMap<>();

    /**
     * 任务序号（保证同毫秒任务 id 唯一）
     */
    private final AtomicInteger seq = new AtomicInteger(0);

    /**
     * 步骤定义
     *
     * @param name     步骤名
     * @param command  远端命令
     * @param weight   步骤权重（用于按权重计算总进度）
     */
    public record ProjectStep(String name, String command, int weight) {
    }

    /**
     * 任务运行状态
     */
    public static class TaskState {
        private final String taskId;
        private final Long projectId;
        private final String operation;
        private final long startTime;
        volatile String phase = PHASE_START;
        volatile String currentStep = "";
        volatile int progress;
        volatile boolean finished;
        volatile boolean success;
        volatile String message;
        volatile long endTime;
        final List<String> logs = Collections.synchronizedList(new ArrayList<>());

        TaskState(String taskId, Long projectId, String operation) {
            this.taskId = taskId;
            this.projectId = projectId;
            this.operation = operation;
            this.startTime = System.currentTimeMillis();
        }
    }

    /**
     * 实时日志会话
     */
    private static class LogSession {
        private final SshClients.Handle handle;
        private final SshClient.StreamHandle streamHandle;
        private final Long hostId;

        LogSession(SshClients.Handle handle, SshClient.StreamHandle streamHandle, Long hostId) {
            this.handle = handle;
            this.streamHandle = streamHandle;
            this.hostId = hostId;
        }
    }

    // ==================== 任务提交与执行 ====================

    /**
     * 提交异步任务
     *
     * @param hostId       目标主机标识
     * @param projectId    项目标识（软件安装等无项目场景可为空）
     * @param operation    操作类型（start/stop/.../install）
     * @param steps        步骤列表
     * @param finishStatus 成功后写入项目的状态（running/stopped，null 表示不改状态）
     * @return taskId
     */
    public String startTask(Long hostId, Long projectId, String operation,
                            List<ProjectStep> steps, String finishStatus) {
        String taskId = operation.toLowerCase() + "-" + (projectId == null ? "host" : projectId)
                + "-" + System.currentTimeMillis() + "-" + seq.incrementAndGet();
        TaskState state = new TaskState(taskId, projectId, operation);
        tasks.put(taskId, state);
        taskPool.execute(() -> run(hostId, steps, finishStatus, state));
        return taskId;
    }

    /**
     * 后台执行任务
     */
    private void run(Long hostId, List<ProjectStep> steps, String finishStatus, TaskState state) {
        SshClients.Handle handle = null;
        try {
            emit(state, PHASE_START, null);
            MsServerHost host = hostMapper.selectById(hostId);
            if (host == null) {
                throw new IllegalStateException("主机不存在: " + hostId);
            }
            ReturnResult<SshCredential> rr = serverHostService.resolveCredential(hostId, null);
            if (!rr.isOk() || rr.getData() == null) {
                throw new IllegalStateException(rr.getMsg() == null ? "凭据解析失败" : rr.getMsg());
            }
            handle = connectionManager.borrow(host, rr.getData());

            int totalWeight = Math.max(1, steps.stream().mapToInt(ProjectStep::weight).sum());
            int accWeight = 0;
            for (ProjectStep step : steps) {
                state.currentStep = step.name();
                emit(state, PHASE_STEP, Map.of("step", step.name(),
                        "progress", percent(accWeight, totalWeight)));

                SshClient.ExecResult result = handle.client().exec().command(step.command())
                        .stream(line -> {
                            appendLog(state, line);
                            emit(state, PHASE_LOG, Map.of("line", line == null ? "" : line));
                        });

                if (result.exitCode() != 0) {
                    String err = result.stderr();
                    throw new IllegalStateException(err == null || err.isBlank()
                            ? "步骤「" + step.name() + "」失败，退出码 " + result.exitCode()
                            : err.trim());
                }
                accWeight += step.weight();
                state.progress = percent(accWeight, totalWeight);
                emit(state, PHASE_PROGRESS, null);
            }

            state.success = true;
            state.finished = true;
            state.progress = 100;
            state.currentStep = "完成";
            state.endTime = System.currentTimeMillis();
            if (finishStatus != null && state.projectId != null) {
                updateProjectStatus(state.projectId, finishStatus);
            }
            emit(state, PHASE_COMPLETE, Map.of("message", "操作成功"));
        } catch (Throwable e) {
            // 捕获 Throwable（含 NoClassDefFoundError 等 Error），避免线程死亡后任务静默卡在 START
            state.success = false;
            state.finished = true;
            state.message = e.getMessage();
            state.endTime = System.currentTimeMillis();
            log.warn("[task] {} 失败: {}", state.taskId, e.getMessage());
            try {
                emit(state, PHASE_ERROR, Map.of("message", e.getMessage() == null ? "执行失败" : e.getMessage()));
            } catch (Throwable ignore) {
                /* 错误阶段推送失败不再向上抛，保证线程能正常结束 */
            }
        } finally {
            if (handle != null) {
                connectionManager.release(hostId);
            }
        }
    }

    // ==================== 实时日志 ====================

    /**
     * 开启实时日志跟随（异步建立连接）
     *
     * @param hostId    主机标识
     * @param projectId 项目标识
     * @param logPath   日志文件路径
     * @return sessionId
     */
    public String startLogTail(Long hostId, Long projectId, String logPath) {
        String sessionId = "log-" + projectId + "-" + System.currentTimeMillis() + "-" + seq.incrementAndGet();
        taskPool.execute(() -> {
            SshClients.Handle handle = null;
            try {
                MsServerHost host = hostMapper.selectById(hostId);
                if (host == null) {
                    throw new IllegalStateException("主机不存在: " + hostId);
                }
                ReturnResult<SshCredential> rr = serverHostService.resolveCredential(hostId, null);
                if (!rr.isOk() || rr.getData() == null) {
                    throw new IllegalStateException(rr.getMsg() == null ? "凭据解析失败" : rr.getMsg());
                }
                handle = connectionManager.borrow(host, rr.getData());
                String command = buildTailCommand(host.getServerHostOsType(), logPath);
                SshClient.StreamHandle streamHandle = handle.client().exec().command(command)
                        .follow(line -> pushLogLine(sessionId, projectId, line));
                logSessions.put(sessionId, new LogSession(handle, streamHandle, hostId));
                pushLogControl(sessionId, projectId, "STARTED", "实时日志已连接");
            } catch (Exception e) {
                if (handle != null) {
                    connectionManager.release(hostId);
                }
                pushLogControl(sessionId, projectId, "ERROR", e.getMessage() == null ? "日志连接失败" : e.getMessage());
            }
        });
        return sessionId;
    }

    /**
     * 停止实时日志跟随
     *
     * @param sessionId 会话标识
     */
    public void stopLogTail(String sessionId) {
        LogSession session = logSessions.remove(sessionId);
        if (session == null) {
            return;
        }
        try {
            session.streamHandle.close();
        } catch (Exception ignored) {
        } finally {
            connectionManager.release(session.hostId);
        }
    }

    /**
     * 推送一行实时日志
     */
    private void pushLogLine(String sessionId, Long projectId, String line) {
        Map<String, Object> data = new LinkedHashMap<>(5);
        data.put("sessionId", sessionId);
        data.put("projectId", projectId);
        data.put("status", "LINE");
        data.put("line", line == null ? "" : line);
        pushService.pushLog(data, sessionId);
    }

    /**
     * 推送日志会话控制消息（连接/错误）
     */
    private void pushLogControl(String sessionId, Long projectId, String status, String message) {
        Map<String, Object> data = new LinkedHashMap<>(5);
        data.put("sessionId", sessionId);
        data.put("projectId", projectId);
        data.put("status", status);
        data.put("message", message);
        pushService.pushLog(data, sessionId);
    }

    /**
     * 构造实时日志命令
     */
    private String buildTailCommand(String osType, String logPath) {
        String path = logPath == null ? "" : logPath.trim();
        if ("WINDOWS".equalsIgnoreCase(osType)) {
            return "powershell -NoProfile -Command \"Get-Content -Path '" + path + "' -Tail "
                    + TAIL_LINES + " -Wait\"";
        }
        return "tail -n " + TAIL_LINES + " -F " + path;
    }

    // ==================== 断点续传 ====================

    /**
     * 获取任务快照（断点续传）
     *
     * @param taskId 任务标识
     * @return 快照（含当前阶段、步骤、进度与缓冲日志）
     */
    public ReturnResult<Map<String, Object>> getSnapshot(String taskId) {
        TaskState state = tasks.get(taskId);
        if (state == null) {
            return ReturnResult.error("任务不存在或已过期: " + taskId);
        }
        Map<String, Object> snap = new LinkedHashMap<>(12);
        snap.put("taskId", state.taskId);
        snap.put("projectId", state.projectId);
        snap.put("operation", state.operation);
        snap.put("phase", state.phase);
        snap.put("currentStep", state.currentStep);
        snap.put("progress", state.progress);
        snap.put("finished", state.finished);
        snap.put("success", state.success);
        snap.put("message", state.message);
        snap.put("startTime", state.startTime);
        snap.put("endTime", state.endTime);
        synchronized (state.logs) {
            snap.put("logs", new ArrayList<>(state.logs));
        }
        return ReturnResult.ok(snap);
    }

    // ==================== 辅助 ====================

    /**
     * 组装并推送一帧任务消息
     *
     * @param state 任务状态
     * @param phase 当前阶段
     * @param extra 额外字段（step/line/progress/message），可为空
     */
    private void emit(TaskState state, String phase, Map<String, Object> extra) {
        state.phase = phase;
        Map<String, Object> data = new LinkedHashMap<>(10);
        data.put("taskId", state.taskId);
        data.put("projectId", state.projectId);
        data.put("operation", state.operation);
        data.put("phase", phase);
        data.put("currentStep", state.currentStep);
        data.put("progress", state.progress);
        data.put("startTime", state.startTime);
        if (extra != null) {
            data.putAll(extra);
            Object p = extra.get("progress");
            if (p instanceof Integer integer) {
                state.progress = integer;
            }
        }
        if (state.finished) {
            data.put("endTime", state.endTime);
            data.put("success", state.success);
            data.put("message", state.message);
        }
        pushService.pushTask(data, state.taskId);
    }

    /**
     * 追加日志到缓冲（限制上限）
     */
    private void appendLog(TaskState state, String line) {
        synchronized (state.logs) {
            state.logs.add(line == null ? "" : line);
            while (state.logs.size() > MAX_LOG_LINES) {
                state.logs.remove(0);
            }
        }
    }

    /**
     * 按权重计算进度百分比
     */
    private int percent(int accWeight, int totalWeight) {
        return Math.max(0, Math.min(100, (int) Math.round(accWeight * 100.0 / totalWeight)));
    }

    /**
     * 更新项目状态
     */
    private void updateProjectStatus(Long projectId, String status) {
        MsServerProject project = projectMapper.selectById(projectId);
        if (project == null) {
            return;
        }
        project.setStatus(status);
        project.setUpdateTime(LocalDateTime.now());
        projectMapper.updateById(project);
    }

    /**
     * 销毁线程池
     */
    @PreDestroy
    public void destroy() {
        logSessions.values().forEach(s -> {
            try {
                s.streamHandle.close();
            } catch (Exception ignored) {
            }
        });
        taskPool.shutdownNow();
    }
}
