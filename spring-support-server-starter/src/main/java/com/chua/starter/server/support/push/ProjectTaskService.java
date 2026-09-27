package com.chua.starter.server.support.push;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.ssh.support.client.SshClient;
import com.chua.starter.server.support.connection.ConnectionManager;
import com.chua.starter.server.support.connection.WindowsSshShell;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.entity.MsServerProject;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.chua.starter.server.support.mapper.MsServerProjectMapper;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.model.vo.LogPushVO;
import com.chua.starter.server.support.model.vo.ProjectTaskEventVO;
import com.chua.starter.server.support.model.vo.ProjectTaskSnapshotVO;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.util.SshClients;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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
 * 载荷为 {@link ProjectTaskEventVO}：{@code taskId, projectId, operation, phase, currentStep,
 * progress, startTime, step?, line?, message?, endTime?, success?}，
 * 其中 {@code phase} 取 START | STEP | LOG | PROGRESS | COMPLETE | ERROR。
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
     * 操作类型 -> 中文标签（用于终端日志横幅）
     */
    private static final Map<String, String> OP_LABEL = Map.of(
            "start", "启动",
            "stop", "停止",
            "restart", "重启",
            "upgrade", "升级",
            "backup", "备份",
            "rollback", "回滚",
            "install", "安装",
            "uninstall", "卸载",
            "deploy", "部署");

    /**
     * 任务保留的日志行数上限（断点续传回放用）
     */
    private static final int MAX_LOG_LINES = 500;

    /**
     * 实时日志初始行数
     */
    private static final int TAIL_LINES = 100;

    /**
     * 单步骤执行超时（秒，语义上限；WinRM 单次 receive 超时另算）
     */
    private static final int DEFAULT_STEP_TIMEOUT_SECONDS = 600;

    private final MsServerHostMapper hostMapper;
    private final MsServerProjectMapper projectMapper;
    private final ServerHostService serverHostService;
    private final ConnectionManager connectionManager;
    private final WindowsSshShell windowsSshShell;
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
     * 项目级操作占用集合：同一项目同一时刻只允许一个操作在跑，
     * 避免并发「启动/停止/重启/升级」互相竞争、覆盖 app.pid 或产生孤儿进程。
     */
    private final java.util.Set<Long> busyProjects = ConcurrentHashMap.newKeySet();

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
        /**
         * 跟随通道（SSH StreamHandle 或 WinRM FollowHandle），关闭即停止跟随
         */
        private final AutoCloseable streamHandle;
        /**
         * SSH 连接句柄；WinRM 跟随为 null
         */
        private final SshClients.Handle sshHandle;
        private final Long hostId;

        LogSession(AutoCloseable streamHandle, SshClients.Handle sshHandle, Long hostId) {
            this.streamHandle = streamHandle;
            this.sshHandle = sshHandle;
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
     * @return taskId；项目已有操作在跑时返回 空（调用方据此提示「有操作进行中」）
     */
    public String startTask(Long hostId, Long projectId, String operation,
                            List<ProjectStep> steps, String finishStatus) {
        // 项目级串行化：同一项目同一时刻只允许一个操作
        if (projectId != null && !busyProjects.add(projectId)) {
            return null;
        }
        String taskId = operation.toLowerCase() + "-" + (projectId == null ? "host" : projectId)
                + "-" + System.currentTimeMillis() + "-" + seq.incrementAndGet();
        TaskState state = new TaskState(taskId, projectId, operation);
        tasks.put(taskId, state);
        try {
            taskPool.execute(() -> run(hostId, steps, finishStatus, state));
        } catch (RuntimeException e) {
            // 提交失败必须释放占用，否则项目被永久锁死
            if (projectId != null) {
                busyProjects.remove(projectId);
                tasks.remove(taskId);
            }
            throw e;
        }
        return taskId;
    }

    /**
     * 项目是否有操作在跑
     *
     * @param projectId 项目 标识
     * @return 是否占用中
     */
    public boolean isProjectBusy(Long projectId) {
        return projectId != null && busyProjects.contains(projectId);
    }

    /**
     * 后台执行任务
     */
    private void run(Long hostId, List<ProjectStep> steps, String finishStatus, TaskState state) {
        SshClients.Handle handle = null;
        try {
            emit(state, PHASE_START, null, null, null, null);
            MsServerHost host = hostMapper.selectById(hostId);
            if (host == null) {
                throw new IllegalStateException("主机不存在: " + hostId);
            }
            String opLabelText = OP_LABEL.getOrDefault(state.operation, state.operation);
            logBanner(state, "──── 开始「" + opLabelText + "」 · 目标 " + host.getServerHostName()
                    + " (" + host.getServerHostAddress() + ") · "
                    + java.time.LocalTime.now().withNano(0) + " ────");
            ReturnResult<SshCredential> rr = serverHostService.resolveCredential(hostId, null);
            if (!rr.isOk() || rr.getData() == null) {
                throw new IllegalStateException(rr.getMsg() == null ? "凭据解析失败" : rr.getMsg());
            }
            boolean windows = WindowsSshShell.isWindows(host);
            // 非 Windows 复用 SSH 连接缓存；Windows 每步经 OpenSSH 独立执行（内部自清理连接）
            if (!windows) {
                handle = connectionManager.borrow(host, rr.getData());
            }

            /** 逐行输出：落缓冲 + 推 LOG（SSH / WinRM 共用） */
            java.util.function.Consumer<String> lineHandler = (line) -> {
                appendLog(state, line);
                emit(state, PHASE_LOG, null, line == null ? "" : line, null, null);
            };

            int totalWeight = Math.max(1, steps.stream().mapToInt(ProjectStep::weight).sum());
            int accWeight = 0;
            int stepNo = 0;
            // 当前步骤的标准输出（最后一步为「记录PID」，其输出仅为 PID）
            java.util.concurrent.CopyOnWriteArrayList<String> stepOut =
                    new java.util.concurrent.CopyOnWriteArrayList<>();
            for (ProjectStep step : steps) {
                stepNo++;
                state.currentStep = step.name();
                emit(state, PHASE_STEP, step.name(), null, percent(accWeight, totalWeight), null);
                long stepStart = System.currentTimeMillis();
                logBanner(state, "▶ [" + stepNo + "/" + steps.size() + "] " + step.name());
                logBanner(state, "  $ " + compactCommand(step.command()));

                stepOut.clear();
                java.util.function.Consumer<String> stepLineHandler = (line) -> {
                    lineHandler.accept(line);
                    stepOut.add(line);
                };

                int exitCode;
                String stderr;
                if (windows) {
                    WindowsSshShell.Result r = windowsSshShell.execLines(host, rr.getData(), step.command(),
                            DEFAULT_STEP_TIMEOUT_SECONDS, stepLineHandler);
                    exitCode = r.exitCode();
                    stderr = r.stderr();
                } else {
                    SshClient.ExecResult result;
                    boolean retried = false;
                    while (true) {
                        try {
                            result = handle.client().exec().command(step.command())
                                    .stream(stepLineHandler);
                            break;
                        } catch (SshClient.SshClientException ce) {
                            // 复用连接在通道层立即失败（常见于长连接半死 / 会话状态异常，logs 为空）：
                            // 失效旧连接、重建后重试一次；重试仍失败则正常报错
                            if (retried) {
                                throw ce;
                            }
                            retried = true;
                            String notice = "连接通道异常，正在重建连接并重试…";
                            appendLog(state, notice);
                            emit(state, PHASE_LOG, null, notice, null, null);
                            log.warn("[task] {} 步骤「{}」SSH 通道层失败，重建连接重试: {}",
                                    state.taskId, step.name(), ce.getMessage());
                            connectionManager.invalidate(hostId);
                            handle = connectionManager.borrow(host, rr.getData());
                        }
                    }
                    exitCode = result.exitCode();
                    stderr = result.stderr();
                }

                if (exitCode != 0) {
                    logBanner(state, "✖ 步骤「" + step.name() + "」失败 · exit " + exitCode
                            + " · 耗时 " + fmtDuration(System.currentTimeMillis() - stepStart));
                    if (stderr != null && !stderr.isBlank()) {
                        logBanner(state, "  stderr: " + compactCommand(stderr));
                    }
                    throw new IllegalStateException(stderr == null || stderr.isBlank()
                            ? "步骤「" + step.name() + "」失败，退出码 " + exitCode
                            : stderr.trim());
                }
                logBanner(state, "✔ 步骤完成 · exit 0 · 耗时 "
                        + fmtDuration(System.currentTimeMillis() - stepStart));
                accWeight += step.weight();
                state.progress = percent(accWeight, totalWeight);
                emit(state, PHASE_PROGRESS, null, null, null, null);
            }

            state.success = true;
            state.finished = true;
            state.progress = 100;
            state.currentStep = "完成";
            state.endTime = System.currentTimeMillis();
            if (finishStatus != null && state.projectId != null) {
                updateProjectStatus(state.projectId, finishStatus);
                updateProjectPid(state.projectId, finishStatus, stepOut);
            }
            String pidInfo = "";
            if ("running".equals(finishStatus)) {
                for (String l : stepOut) {
                    String t = l == null ? "" : l.trim();
                    if (t.matches("\\d+")) {
                        pidInfo = " · PID " + t;
                        break;
                    }
                }
            }
            logBanner(state, "──── 「" + opLabelText + "」成功 · 总耗时 "
                    + fmtDuration(System.currentTimeMillis() - state.startTime) + pidInfo + " ────");
            emit(state, PHASE_COMPLETE, null, null, null, "操作成功");
        } catch (Throwable e) {
            // 捕获 Throwable（含 NoClassDefFoundError 等 Error），避免线程死亡后任务静默卡在 START
            state.success = false;
            state.finished = true;
            state.message = e.getMessage();
            state.endTime = System.currentTimeMillis();
            log.warn("[task] {} 失败: {}", state.taskId, e.toString(), e);
            try {
                logBanner(state, "──── 「" + OP_LABEL.getOrDefault(state.operation, state.operation)
                        + "」失败 · 总耗时 " + fmtDuration(System.currentTimeMillis() - state.startTime) + " ────");
                logBanner(state, "✖ " + (e.getMessage() == null ? "执行失败" : e.getMessage()));
            } catch (Throwable ignoreBanner) {
                /* 横幅推送失败不影响错误帧 */
            }
            try {
                emit(state, PHASE_ERROR, null, null, null, e.getMessage() == null ? "执行失败" : e.getMessage());
            } catch (Throwable ignore) {
                /* 错误阶段推送失败不再向上抛，保证线程能正常结束 */
            }
        } finally {
            if (handle != null) {
                connectionManager.release(hostId);
            }
            // 释放项目占用，允许下一个操作
            if (state.projectId != null) {
                busyProjects.remove(state.projectId);
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
            SshClients.Handle sshHandle = null;
            AutoCloseable streamHandle = null;
            try {
                MsServerHost host = hostMapper.selectById(hostId);
                if (host == null) {
                    throw new IllegalStateException("主机不存在: " + hostId);
                }
                ReturnResult<SshCredential> rr = serverHostService.resolveCredential(hostId, null);
                if (!rr.isOk() || rr.getData() == null) {
                    throw new IllegalStateException(rr.getMsg() == null ? "凭据解析失败" : rr.getMsg());
                }
                String command = buildTailCommand(host.getServerHostOsType(), logPath);
                if (WindowsSshShell.isWindows(host)) {
                    // Windows 经 OpenSSH 持续跟随（Get-Content -Wait），follow 内部包 EncodedCommand
                    streamHandle = windowsSshShell.follow(host, rr.getData(), command,
                            line -> pushLogLine(sessionId, projectId, line));
                } else {
                    sshHandle = connectionManager.borrow(host, rr.getData());
                    streamHandle = sshHandle.client().exec().command(command)
                            .follow(line -> pushLogLine(sessionId, projectId, line));
                }
                logSessions.put(sessionId, new LogSession(streamHandle, sshHandle, hostId));
                pushLogControl(sessionId, projectId, "STARTED", "实时日志已连接");
            } catch (Exception e) {
                if (streamHandle != null) {
                    try {
                        streamHandle.close();
                    } catch (Exception ignore) {
                        // 清理异常不处理
                    }
                }
                if (sshHandle != null) {
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
            // 关闭异常不处理
        }
        if (session.sshHandle != null) {
            connectionManager.release(session.hostId);
        }
    }

    /**
     * 推送一行实时日志
     */
    private void pushLogLine(String sessionId, Long projectId, String line) {
        pushService.pushLog(new LogPushVO(sessionId, projectId, "LINE", line == null ? "" : line, null), sessionId);
    }

    /**
     * 推送日志会话控制消息（连接/错误）
     */
    private void pushLogControl(String sessionId, Long projectId, String status, String message) {
        pushService.pushLog(new LogPushVO(sessionId, projectId, status, null, message), sessionId);
    }

    /**
     * 构造实时日志命令
     */
    private String buildTailCommand(String osType, String logPath) {
        String path = logPath == null ? "" : logPath.trim();
        if ("WINDOWS".equalsIgnoreCase(osType)) {
            // 纯 PowerShell 脚本；WinRM follow 内部统一包 EncodedCommand。
            // 日志文件可能尚未生成，先确保存在，否则 Get-Content -Wait 直接报错退出。
            return "if (-not (Test-Path '" + path + "')) { New-Item -ItemType File -Path '" + path
                    + "' -Force | Out-Null }\n"
                    + "Get-Content -Path '" + path + "' -Tail " + TAIL_LINES + " -Wait";
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
    public ReturnResult<ProjectTaskSnapshotVO> getSnapshot(String taskId) {
        TaskState state = tasks.get(taskId);
        if (state == null) {
            return ReturnResult.error("任务不存在或已过期: " + taskId);
        }
        List<String> logs;
        synchronized (state.logs) {
            logs = new ArrayList<>(state.logs);
        }
        return ReturnResult.ok(new ProjectTaskSnapshotVO(state.taskId, state.projectId, state.operation,
                state.phase, state.currentStep, state.progress, state.finished, state.success,
                state.message, state.startTime, state.endTime, logs));
    }

    // ==================== 辅助 ====================

    /**
     * 组装并推送一帧任务消息
     *
     * @param state   任务状态
     * @param phase   当前阶段
     * @param step    步骤名，仅 STEP 帧携带
     * @param line    日志行，仅 LOG 帧携带
     * @param progress 进度百分比，仅 STEP 帧携带
     * @param message 结束说明或错误信息，仅终态帧携带
     */
    private void emit(TaskState state, String phase, String step, String line, Integer progress, String message) {
        state.phase = phase;
        if (progress != null) {
            state.progress = progress;
        }
        if (message != null) {
            state.message = message;
        }
        pushService.pushTask(new ProjectTaskEventVO(state.taskId, state.projectId, state.operation,
                phase, state.currentStep, state.progress, state.startTime, step, line, message,
                state.finished ? state.endTime : null, state.finished ? state.success : null), state.taskId);
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
     * 输出一行「编排横幅」日志：落缓冲 + 推 LOG（不计入步骤 stdout，不参与 PID 解析）
     */
    private void logBanner(TaskState state, String line) {
        appendLog(state, line);
        emit(state, PHASE_LOG, null, line, null, null);
    }

    /**
     * 将多行命令压缩为单行预览（折叠空白、限制长度），便于终端展示正在执行的命令
     */
    private String compactCommand(String cmd) {
        String one = cmd == null ? "" : cmd.trim().replaceAll("\\s+", " ");
        if (one.length() > 200) {
            one = one.substring(0, 200) + " …";
        }
        return one;
    }

    /**
     * 时长格式化：<60s 显示 Ns；<60m 显示 MmSs；否则 HhMm
     */
    private String fmtDuration(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s < 60) {
            return s + "s";
        }
        long m = s / 60;
        s %= 60;
        if (m < 60) {
            return m + "m" + s + "s";
        }
        long h = m / 60;
        m %= 60;
        return h + "h" + m + "m";
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
     * 依据任务最后一步输出回写运行时 PID：
     * running 时从「记录PID」步骤输出中取纯数字 PID；stopped 时清空。
     *
     * @param projectId   项目ID
     * @param finishStatus 成功后状态（running/stopped）
     * @param lastStepOut 最后一步的标准输出行
     */
    private void updateProjectPid(Long projectId, String finishStatus, List<String> lastStepOut) {
        MsServerProject project = projectMapper.selectById(projectId);
        if (project == null) {
            return;
        }
        if ("stopped".equals(finishStatus)) {
            project.setPid(null);
            project.setRunningSince(null);
        } else if ("running".equals(finishStatus)) {
            String pid = null;
            for (String line : lastStepOut) {
                String t = line == null ? "" : line.trim();
                if (t.matches("\\d+")) {
                    pid = t;
                    break;
                }
            }
            project.setPid(pid);
            project.setRunningSince(LocalDateTime.now());
        } else {
            return;
        }
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
                // 关闭异常不处理
            }
            if (s.sshHandle != null) {
                try {
                    connectionManager.release(s.hostId);
                } catch (Exception ignore) {
                    // 释放异常不处理
                }
            }
        });
        taskPool.shutdownNow();
    }
}
