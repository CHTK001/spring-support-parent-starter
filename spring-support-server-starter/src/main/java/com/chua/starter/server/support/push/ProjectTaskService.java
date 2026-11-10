// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.server.support.push;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.text.json.Json;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import com.chua.starter.server.support.service.ProjectOpLogService;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.sse.ReleaseSseHub;
import com.chua.starter.server.support.util.SshClients;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            "deploy", "Git发布");

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

    /** 本机（LOCAL）单步执行超时（秒）：本机不走网络，给足启动/备份时间 */
    private static final int LOCAL_STEP_TIMEOUT_SECONDS = 600;
    /**
     * 启动脚本成功时打印的运行 PID 行
     */
    private static final Pattern RUNNING_PID = Pattern.compile("running pid=(\\d+)");

    private final MsServerHostMapper hostMapper;
    private final MsServerProjectMapper projectMapper;
    private final ServerHostService serverHostService;
    private final ConnectionManager connectionManager;
    private final WindowsSshShell windowsSshShell;
    private final ServerPushService pushService;
    /**
     * 发布管理全局 SSE 通道（任务帧主通道，替代页面级 Socket/Socket.IO 订阅）
     */
    private final ReleaseSseHub releaseSseHub;
    /**
     * 项目操作日志（任务起止都落库，日志管理面板经 SSE 即时刷新）
     */
    private final ProjectOpLogService projectOpLogService;

    /**
     * 任务线程池（守护线程，含可能长时间阻塞的 follow 会话）
     */
// [P3C C1 豁免] 场景化创建线程，需文档化
    private final ExecutorService taskPool = Executors.newCachedThreadPool(r -> {
// [P3C C1 豁免] 场景化创建线程，需文档化
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
     * 步骤动作：能在 SDK/服务层完成的动作用它表达，避免退回命令行。
     *
     * <p>发布链路里多数步骤本质是远端命令（systemctl / launchctl / schtasks / tar / nginx 等无 SDK 等价物），
     * 但文件搬运有 SFTP SDK（{@code rename/upload/...}）。此接口让这类步骤直接走 SDK，
     * 只有 SDK 不可用时才回退到平台命令（「SDK 优先、命令兜底」）。</p>
     */
    @FunctionalInterface
    public interface StepAction {
        /**
         * 执行动作。
         *
         * @return 需要回显的输出行；无输出返回 null 或空列表
         * @throws Exception 执行失败（按步骤失败处理）
         */
        List<String> run() throws Exception;
    }

    /**
     * 步骤定义
     *
     * @param name     步骤名
     * @param command  远端命令；SDK 动作步骤可为空串
     * @param weight   步骤权重（用于按权重计算总进度）
     * @param action   SDK 动作；非空时优先执行，命令不再下发
     */
    public record ProjectStep(String name, String command, int weight, StepAction action) {

        /**
         * 命令步骤（保持既有调用签名）。
         *
         * @param name    步骤名
         * @param command 远端命令
         * @param weight  步骤权重
         */
        public ProjectStep(String name, String command, int weight) {
            this(name, command, weight, null);
        }
    }

    /**
     * 任务成功后的系统服务名回写指令。
     *
     * <p>挂载/卸载的系统服务名必须与「任务是否真的成功」绑定：
     * 旧实现在提交任务时就写库，安装失败（例如守护进程未拉起）会留下
     * 「库里有服务名、机器上没有服务」的假状态；卸载时 {@code setServiceName(null)}
     * 又会被 MyBatis-Plus 的 {@code updateById} 跳过 null 字段而落不到库。
     * 因此改为成功回调显式写/清。</p>
     *
     * @param value 成功后的服务名；{@code null} 表示清空
     */
    public record ServiceNameWrite(String value) {
    }

    /**
     * 任务运行状态
     */
    public static class TaskState {
        private final String taskId;
        private final Long projectId;
        private final String operation;
        private final long startTime;
        private final ServiceNameWrite serviceNameWrite;
        volatile String phase = PHASE_START;
        volatile String currentStep = "";
        volatile int progress;
        volatile boolean finished;
        volatile boolean success;
        volatile String message;
        volatile long endTime;
        final List<String> logs = Collections.synchronizedList(new ArrayList<>());

        /**
         * Task State
         * @param operation 字符串
         * @param projectId 长整数
         * @param serviceNameWrite 参数
         * @param taskId 字符串
         * @return 返回值
        */
        TaskState(String taskId, Long projectId, String operation, ServiceNameWrite serviceNameWrite) {
            this.taskId = taskId;
            this.projectId = projectId;
            this.operation = operation;
            this.serviceNameWrite = serviceNameWrite;
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
        /**
         * 发布管理 SSE 客户端标识；为空表示回退到 Socket 推送
         */
        private final String clientId;

        /**
         * Log Session
         * @param hostId 长整数
         * @param sshHandle 参数
         * @param streamHandle 参数
         * @param clientId 发布管理 SSE 客户端标识
         * @return 返回值
        */
        LogSession(AutoCloseable streamHandle, SshClients.Handle sshHandle, Long hostId, String clientId) {
            this.streamHandle = streamHandle;
            this.sshHandle = sshHandle;
            this.hostId = hostId;
            this.clientId = clientId;
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
        return startTask(hostId, projectId, operation, steps, finishStatus, null);
    }

    /**
     * 提交任务（成功后额外回写/清空系统服务名）
     *
     * @param hostId           目标主机标识
     * @param projectId        项目标识（软件安装等无项目场景可为空）
     * @param operation        操作类型（start/stop/.../install）
     * @param steps            步骤列表
     * @param finishStatus     成功后写入项目的状态（running/stopped，null 表示不改状态）
     * @param serviceNameWrite 成功后对 serviceName 的写入指令（null 表示不改）
     * @return taskId；项目已有操作在跑时返回 空（调用方据此提示「有操作进行中」）
     */
    public String startTask(Long hostId, Long projectId, String operation,
                            List<ProjectStep> steps, String finishStatus,
                            ServiceNameWrite serviceNameWrite) {
        // 项目级串行化：同一项目同一时刻只允许一个操作
        if (projectId != null && !busyProjects.add(projectId)) {
            return null;
        }
        String taskId = operation.toLowerCase() + "-" + (projectId == null ? "host" : projectId)
                + "-" + System.currentTimeMillis() + "-" + seq.incrementAndGet();
        TaskState state = new TaskState(taskId, projectId, operation, serviceNameWrite);
        tasks.put(taskId, state);
        recordOpLogStart(state);
        try {
            taskPool.execute(() -> run(hostId, steps, finishStatus, state));
        } catch (RuntimeException e) {
            // 提交失败必须释放占用，否则项目被永久锁死
            if (projectId != null) {
                busyProjects.remove(projectId);
                tasks.remove(taskId);
            }
            finishOpLog(state, false, "任务提交失败: "
                    + (e.getMessage() == null ? e.toString() : e.getMessage()));
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
     * @param finishStatus 字符串
     * @param hostId 长整数
     * @param state 参数
     * @param steps 参数
     * @return 返回值
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
            // 本机（collect_mode=LOCAL）主机不经 SSH：跑在平台所在机器，直接本地执行。
            // 否则 start/stop/restart/backup/rollback 会经 OpenSSH 回环连本机（192.168.50.101），
            // 实测长脚本写远端 temp 时连接被拒（远程计算机拒绝网络连接），全部操作失败。
            boolean localHost = "LOCAL".equalsIgnoreCase(host.getServerHostCollectMode());
            // 非 Windows 复用 SSH 连接缓存；Windows 每步经 OpenSSH 独立执行（内部自清理连接）。
            // 本机主机不占 SSH，无需连接缓存。
            if (!windows && !localHost) {
                handle = connectionManager.borrow(host, rr.getData());
            }

            /** 逐行输出：落缓冲 + 推 LOG（SSH / WinRM 共用） */
            java.util.function.Consumer<String> lineHandler = (line) -> {
                appendLog(state, line);
                emit(state, PHASE_LOG, null, line == null ? "" : line, null, null);
            };

            int accWeight = 0;
            int stepNo = 0;
            // 当前步骤的标准输出（最后一步为「记录PID」，其输出仅为 PID）
            java.util.concurrent.CopyOnWriteArrayList<String> stepOut =
                    new java.util.concurrent.CopyOnWriteArrayList<>();
            // 用索引遍历而不是增强 for：Git 发布的步骤依赖构建产物路径，
            // 构建成功后才会把投放/重启步骤追加到同一个列表（同一线程内追加）。
            for (int i = 0; i < steps.size(); i++) {
                ProjectStep step = steps.get(i);
                stepNo++;
                // 每步开始前重算总权重，追加步骤后进度按最新的步骤集合计算
                int totalWeight = Math.max(1, steps.stream().mapToInt(ProjectStep::weight).sum());
                state.currentStep = step.name();
                emit(state, PHASE_STEP, step.name(), null, percent(accWeight, totalWeight), null);
                long stepStart = System.currentTimeMillis();
                logBanner(state, "▶ [" + stepNo + "/" + steps.size() + "] " + step.name());
                if (step.action() != null) {
                    logBanner(state, "  ~ SDK 调用（不走命令行）");
                } else {
                    logBanner(state, "  $ " + compactCommand(step.command()));
                }

                stepOut.clear();
                java.util.function.Consumer<String> stepLineHandler = (line) -> {
                    lineHandler.accept(line);
                    stepOut.add(line);
                };

                int exitCode;
                String stderr;
                if (step.action() != null) {
                    // SDK 优先：能在 SDK 完成的动作不落命令行
                    try {
                        List<String> outs = step.action().run();
                        if (outs != null) {
                            for (String l : outs) {
                                stepLineHandler.accept(l);
                            }
                        }
                        exitCode = 0;
                        stderr = "";
                    } catch (Exception e) {
                        exitCode = 1;
                        stderr = e.getMessage() == null ? e.toString() : e.getMessage();
                        stepLineHandler.accept("SDK 调用失败: " + stderr);
                    }
                } else if (localHost) {
                    LocalExecResult r = execLocalStep(step.command(), stepLineHandler);
                    exitCode = r.exitCode;
                    stderr = r.stderr;
                } else if (windows) {
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
            if (state.serviceNameWrite != null && state.projectId != null) {
                updateProjectServiceName(state.projectId, state.serviceNameWrite.value());
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
            finishOpLog(state, true, "操作成功");
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
            // 失败也可能留下「真实运行态」：升级健康检查失败后自动回滚成功时，
            // 远端实际在跑回滚后的旧版本（任务仍以 exit 1 判失败，避免谎报成功）。
            // 若不在此回写，库里会留着回滚前的旧 PID / 旧 runningSince，
            // 列表页显示的 PID 与端口属主不一致，运维据此判断会得到错误结论。
            try {
                reconcileProjectAfterFailure(state);
            } catch (Throwable ignoreReconcile) {
                log.warn("[task] {} 失败后状态回写异常", state.taskId, ignoreReconcile);
            }
            finishOpLog(state, false, state.message == null ? "执行失败" : state.message);
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

    // ==================== 本机执行 ====================

    /** 本机一步执行的结果（只需要退出码与合并输出） */
    private static final class LocalExecResult {
        private final int exitCode;
        private final String stderr;

        private LocalExecResult(int exitCode, String stderr) {
            this.exitCode = exitCode;
            this.stderr = stderr;
        }
    }

    /**
     * 本机（collect_mode=LOCAL）主机执行一步脚本。
     *
     * <p>本机主机跑在平台所在机器上，不能再经 OpenSSH 回环：实测长脚本分块写远端
     * temp 时连接被拒，启动/重启/备份/回滚全部失败。这里直接在进程内起 PowerShell，
     * 与 {@code ExecServiceImpl} 的 LOCAL 通道保持同一套处理：EncodedCommand 下发、
     * UTF-8 输出、关闭进度流、Get-Content 默认 UTF8。</p>
     *
     * @param psCommand   PowerShell 脚本
     * @param lineHandler 逐行回调（实时进任务日志）
     * @return 退出码与合并输出
     */
    private LocalExecResult execLocalStep(String psCommand,
                                          java.util.function.Consumer<String> lineHandler) {
        String script = "[Console]::OutputEncoding=[Text.Encoding]::UTF8;"
                + "$ProgressPreference='SilentlyContinue';"
                + "$PSDefaultParameterValues['Get-Content:Encoding']='UTF8';"
                + psCommand;
        String encoded = Base64.getEncoder()
                .encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        StringBuilder merged = new StringBuilder();
        try {
            ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile",
                    "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (lineHandler != null) {
                        lineHandler.accept(line);
                    }
                    merged.append(line).append('\n');
                }
            }
            if (!process.waitFor(LOCAL_STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new LocalExecResult(1, "本机命令执行超时: " + LOCAL_STEP_TIMEOUT_SECONDS + "s");
            }
            return new LocalExecResult(process.exitValue(), merged.toString().trim());
        } catch (Exception e) {
            return new LocalExecResult(1, e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    /**
     * 本机日志跟随：在进程内启动 PowerShell {@code Get-Content -Wait}，逐行回调。
     *
     * <p>本机主机不能再经 SSH 跟随：实测回环连接被拒，只收到 STARTED 收不到任何行。</p>
     *
     * @param psCommand 跟随命令（buildTailCommand 生成）
     * @param onLine    每行回调
     * @return 关闭句柄：关闭即终止进程
     */
    private AutoCloseable followLocalLog(String psCommand, java.util.function.Consumer<String> onLine) {
        String script = "[Console]::OutputEncoding=[Text.Encoding]::UTF8;"
                + "$ProgressPreference='SilentlyContinue';"
                + "$PSDefaultParameterValues['Get-Content:Encoding']='UTF8';"
                + psCommand;
        String encoded = Base64.getEncoder()
                .encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        Process process;
        try {
            process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded)
                    .redirectErrorStream(true)
                    .start();
        } catch (Exception e) {
            throw new IllegalStateException("本机日志跟随启动失败: " + e.getMessage(), e);
        }
        Thread worker = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (onLine != null) {
                        onLine.accept(line);
                    }
                }
            } catch (Exception ignore) {
                // 跟随结束（进程被关闭）属正常
            }
        }, "local-log-follow");
        worker.setDaemon(true);
        worker.start();
        return process::destroyForcibly;
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
        return startLogTail(hostId, projectId, logPath, null);
    }

    /**
     * 开启实时日志跟随（异步建立连接）
     *
     * @param hostId    主机标识
     * @param projectId 项目标识
     * @param logPath   日志文件路径
     * @param clientId  发布管理 SSE 客户端标识；为 空 时回退到 Socket 推送
     * @return sessionId
     */
    public String startLogTail(Long hostId, Long projectId, String logPath, String clientId) {
        return startLogTail(hostId, projectId, logPath, clientId, TAIL_LINES);
    }

    /**
     * 开启实时日志跟随（异步建立连接），并指定先回放的历史行数。
     *
     * @param hostId    主机标识
     * @param projectId 项目标识
     * @param logPath   日志文件路径
     * @param clientId  发布管理 SSE 客户端标识；为空时回退到 Socket 推送
     * @param lines     连接时先读取的历史行数（来自「日志读取行数」配置）
     * @return sessionId
     */
    public String startLogTail(Long hostId, Long projectId, String logPath, String clientId, int lines) {
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
                if ("LOCAL".equalsIgnoreCase(host.getServerHostCollectMode())) {
                    // 本机主机不经 SSH：直接在进程内跟随日志文件（与执行通道同一口径）
                    streamHandle = followLocalLog(command,
                            line -> pushLogLine(sessionId, projectId, clientId, line));
                } else if (WindowsSshShell.isWindows(host)) {
                    // Windows 经 OpenSSH 持续跟随（Get-Content -Wait），follow 内部包 EncodedCommand
                    streamHandle = windowsSshShell.follow(host, rr.getData(), command,
                            line -> pushLogLine(sessionId, projectId, clientId, line));
                } else {
                    sshHandle = connectionManager.borrow(host, rr.getData());
                    streamHandle = sshHandle.client().exec().command(command)
                            .follow(line -> pushLogLine(sessionId, projectId, clientId, line));
                }
                logSessions.put(sessionId, new LogSession(streamHandle, sshHandle, hostId, clientId));
                pushLogControl(sessionId, projectId, clientId, "STARTED", "实时日志已连接");
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
                pushLogControl(sessionId, projectId, clientId, "ERROR",
                        e.getMessage() == null ? "日志连接失败" : e.getMessage());
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
     * @param line 字符串
     * @param projectId 长整数
     * @param sessionId 字符串
     * @param clientId 发布管理 SSE 客户端标识；为空回退 Socket
     * @return 返回值
    */
    private void pushLogLine(String sessionId, Long projectId, String clientId, String line) {
        LogPushVO frame = new LogPushVO(sessionId, projectId, "LINE", line == null ? "" : line, null);
        if (clientId == null || clientId.isBlank()) {
            pushService.pushLog(frame, sessionId);
            return;
        }
        // 实时日志只推给发起跟随的那个客户端，多标签页互不串台
        try {
            releaseSseHub.send(clientId, ReleaseSseHub.EVENT_PROJECT_LOG, Json.toJson(frame));
        } catch (Exception e) {
            log.debug("[task] 日志帧 SSE 推送失败 sessionId={}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * 推送日志会话控制消息（连接/错误）
     * @param message 字符串
     * @param projectId 长整数
     * @param sessionId 字符串
     * @param clientId 发布管理 SSE 客户端标识；为空回退 Socket
     * @param status 字符串
     * @return 返回值
    */
    private void pushLogControl(String sessionId, Long projectId, String clientId, String status, String message) {
        LogPushVO frame = new LogPushVO(sessionId, projectId, status, null, message);
        if (clientId == null || clientId.isBlank()) {
            pushService.pushLog(frame, sessionId);
            return;
        }
        try {
            releaseSseHub.send(clientId, ReleaseSseHub.EVENT_PROJECT_LOG, Json.toJson(frame));
        } catch (Exception e) {
            log.debug("[task] 日志控制帧 SSE 推送失败 sessionId={}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * 构造实时日志命令
     * @param logPath 字符串
     * @param osType 字符串
     * @return 返回值
    */
    private String buildTailCommand(String osType, String logPath) {
        String path = logPath == null ? "" : logPath.trim();
        if ("WINDOWS".equalsIgnoreCase(osType)) {
            // 纯 PowerShell 脚本；WinRM/SSH follow 内部统一包 EncodedCommand。
            // 日志文件可能尚未生成，先确保存在，否则 Get-Content -Wait 直接报错退出；
            // 日志路径被配成目录时，跟随目录里最新的文件。
            // 编码必须显式固定：Windows PowerShell 5.1 默认按 ANSI(GBK) 读文件、
            // 也按 ANSI 写 stdout，而读取端按 UTF-8 解码 —— 不固定就整段中文乱码。
            return "$ProgressPreference='SilentlyContinue';"
                    + "[Console]::OutputEncoding=[Text.Encoding]::UTF8;"
                    + "$OutputEncoding=[Text.Encoding]::UTF8;"
                    + "$PSDefaultParameterValues['Get-Content:Encoding']='UTF8';\n"
                    + "if (Test-Path '" + path + "' -PathType Container) { "
                    + "$f = Get-ChildItem -Path '" + path + "' -File "
                    + "| Sort-Object LastWriteTime -Descending | Select-Object -First 1; "
                    + "if ($f) { $target = $f.FullName } else { $target = Join-Path '" + path + "' 'app.log' } } "
                    + "else { $target = '" + path + "' }\n"
                    + "if (-not (Test-Path $target)) { New-Item -ItemType File -Path $target -Force | Out-Null }\n"
                    + "Get-Content -Path $target -Tail " + TAIL_LINES + " -Wait";
        }
        return "p='" + path.replace("'", "'\\''") + "'; if [ -d \"$p\" ]; then "
                + "f=$(ls -tp \"$p\"/* 2>/dev/null | grep -v '/$' | head -n 1); "
                + "[ -z \"$f\" ] && f=\"$p/app.log\"; else f=\"$p\"; fi; "
                + "mkdir -p \"$(dirname \"$f\")\" 2>/dev/null; "
                + "tail -n " + TAIL_LINES + " -F \"$f\"";
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
        ProjectTaskEventVO frame = new ProjectTaskEventVO(state.taskId, state.projectId, state.operation,
                phase, state.currentStep, state.progress, state.startTime, step, line, message,
                state.finished ? state.endTime : null, state.finished ? state.success : null);
        pushService.pushTask(frame, state.taskId);
        // 发布管理只保留一条全局 SSE：任务帧主通道，页面不再各自订阅 Socket.IO
        try {
            releaseSseHub.broadcast(ReleaseSseHub.EVENT_PROJECT_TASK, Json.toJson(frame));
        } catch (Exception e) {
            log.debug("[task] {} 任务帧 SSE 推送失败: {}", state.taskId, e.getMessage());
        }
    }

    /**
     * 记录一次任务开始（失败只记日志，不影响任务本身）。
     *
     * @param state 任务状态
     */
    private void recordOpLogStart(TaskState state) {
        try {
            MsServerProject project = state.projectId == null ? null : projectMapper.selectById(state.projectId);
            projectOpLogService.recordStart(state.projectId,
                    project == null ? null : project.getProjectName(),
                    project == null ? null : project.getReleaseId(),
                    state.operation, state.taskId);
        } catch (Exception e) {
            log.warn("[task] {} 记录操作日志失败: {}", state.taskId, e.getMessage());
        }
    }

    /**
     * 回填一次任务终态（失败只记日志，不影响任务结果）。
     *
     * @param state   任务状态
     * @param success 是否成功
     * @param message 结果说明或失败原因
     */
    private void finishOpLog(TaskState state, boolean success, String message) {
        try {
            projectOpLogService.recordFinish(state.taskId, success, message);
        } catch (Exception e) {
            log.warn("[task] {} 回填操作日志失败: {}", state.taskId, e.getMessage());
        }
    }

    /**
     * 追加日志到缓冲（限制上限）
     * @param line 字符串
     * @param state 参数
     * @return 返回值
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
     * @param line 字符串
     * @param state 参数
     * @return 返回值
    */
    private void logBanner(TaskState state, String line) {
        appendLog(state, line);
        emit(state, PHASE_LOG, null, line, null, null);
    }

    /**
     * 将多行命令压缩为单行预览（折叠空白、限制长度），便于终端展示正在执行的命令
     * @param cmd 字符串
     * @return 返回值
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
     * @param ms 长整数
     * @return 返回值
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
     * @param accWeight 整数
     * @param totalWeight 整数
     * @return 返回值
    */
    private int percent(int accWeight, int totalWeight) {
        return Math.max(0, Math.min(100, (int) Math.round(accWeight * 100.0 / totalWeight)));
    }

    /**
     * 更新项目状态
     * @param projectId 长整数
     * @param status 字符串
     * @return 返回值
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
        if (!"stopped".equals(finishStatus) && !"running".equals(finishStatus)) {
            return;
        }
        // 必须走 LambdaUpdateWrapper 显式 set：MyBatis-Plus 的 updateById 会跳过 null 字段，
        // 停止时的 setPid(null)/setRunningSince(null) 根本落不到库 —— 表现为「项目已停止，
        // 列表页仍显示旧 PID 与运行时长」，且该 PID 早已不存在于目标机，误导排障。
        LambdaUpdateWrapper<MsServerProject> update = new LambdaUpdateWrapper<MsServerProject>()
                .eq(MsServerProject::getId, projectId)
                .set(MsServerProject::getUpdateTime, LocalDateTime.now());
        if ("stopped".equals(finishStatus)) {
            update.set(MsServerProject::getPid, null)
                    .set(MsServerProject::getRunningSince, null);
        } else {
            String pid = null;
            for (String line : lastStepOut) {
                String t = line == null ? "" : line.trim();
                if (t.matches("\\d+")) {
                    pid = t;
                    break;
                }
            }
            update.set(MsServerProject::getPid, pid)
                    .set(MsServerProject::getRunningSince, LocalDateTime.now());
        }
        projectMapper.update(null, update);
    }

    /**
     * 回写（或清空）项目挂载的系统服务名。
     *
     * <p>必须走 {@code LambdaUpdateWrapper} 显式 set：MyBatis-Plus 的 {@code updateById}
     * 会跳过 null 字段，卸载时 {@code setServiceName(null)} 落不到库——表现为
     * 「服务已卸载，列表与库中仍显示服务名」，UI 的挂载状态因此一直为真。</p>
     *
     * @param projectId   项目标识
     * @param serviceName 服务名；null 表示清空
     */
    private void updateProjectServiceName(Long projectId, String serviceName) {
        projectMapper.update(null, new LambdaUpdateWrapper<MsServerProject>()
                .eq(MsServerProject::getId, projectId)
                .set(MsServerProject::getUpdateTime, LocalDateTime.now())
                .set(MsServerProject::getServiceName, serviceName));
        log.info("[task] 项目 {} 服务名回写: {}", projectId, serviceName);
    }

    /**
     * 失败任务结束后的项目运行态对账。
     *
     * <p>升级健康检查失败时，中转会按 {@code autoRollback} 自动回滚并以非 0 退出——
     * 这是「操作失败但服务正常」的典型场景：远端实际在跑回滚后的旧版本，库里却可能
     * 仍留着升级前的旧 PID / runningSince（甚至过期 PID），列表页展示与端口属主对不上。
     * 本方法只处理日志能给出确定结论的失败，其余保持原状（宁可不动，也不写错）：</p>
     * <ul>
     *   <li>回滚成功（{@code rollback ok} + {@code rolled back to previous version}）：
     *       回写 running 与日志中最后一个 {@code running pid=}；</li>
     *   <li>回滚本身失败（{@code rollback failed}）：新旧版本都没起来，回写 stopped 并清空 PID；</li>
     *   <li>投放失败后的止损（{@code 已用最新备份还原}）：重新拉起成功则回写 running + PID，
     *       未拉起则回写 stopped；</li>
     *   <li>升级后状态校验失败（{@code 启动后状态校验未通过}）：进程没起来，回写 stopped 并清空 PID。</li>
     * </ul>
     *
     * @param state 任务运行状态（含日志缓冲）
     */
    private void reconcileProjectAfterFailure(TaskState state) {
        if (state.projectId == null || state.logs.isEmpty()) {
            return;
        }
        List<String> logs;
        synchronized (state.logs) {
            logs = new ArrayList<>(state.logs);
        }
        boolean rolledBack = false;
        boolean rollbackFailed = false;
        boolean restoreAttempted = false;
        boolean restoreRestarted = false;
        boolean verifyFailed = false;
        for (String line : logs) {
            String t = line == null ? "" : line;
            if (t.contains("rolled back to previous version")) {
                rolledBack = true;
            }
            if (t.contains("rollback failed")) {
                rollbackFailed = true;
            }
            if (t.contains("启动后状态校验未通过")) {
                verifyFailed = true;
            }
            if (t.contains("已用最新备份还原") || t.contains("还原备份失败")) {
                restoreAttempted = true;
            }
            if (t.contains("已重新拉起服务")) {
                restoreRestarted = true;
            }
        }
        if (!rolledBack && !rollbackFailed && !restoreAttempted && !verifyFailed) {
            return;
        }
        // 结论优先级：回滚失败 > 回滚成功 > 投放失败后的还原重启 > 状态校验失败。
        // 投放失败（H/J 等）走 restoreAndRestart：文件已还原且服务已重新拉起时，
        // 项目实际是 running，必须回写真实 PID，否则库里留着升级前的旧 PID。
        boolean running;
        if (rollbackFailed) {
            running = false;
        } else if (rolledBack) {
            running = true;
        } else if (restoreAttempted) {
            running = restoreRestarted;
        } else {
            // 升级后状态校验未通过：启动脚本退出码为 0 但进程没起来，必须回置 stopped
            running = false;
        }
        // 同样走显式 set：需要把 pid/runningSince 置空时，
        // updateById 会跳过 null 字段而清不掉。
        LambdaUpdateWrapper<MsServerProject> update = new LambdaUpdateWrapper<MsServerProject>()
                .eq(MsServerProject::getId, state.projectId)
                .set(MsServerProject::getUpdateTime, LocalDateTime.now());
        String pid;
        if (!running) {
            pid = null;
            update.set(MsServerProject::getStatus, "stopped")
                    .set(MsServerProject::getPid, null)
                    .set(MsServerProject::getRunningSince, null);
        } else {
            pid = extractLastRunningPid(logs);
            update.set(MsServerProject::getStatus, "running")
                    .set(MsServerProject::getPid, pid)
                    .set(MsServerProject::getRunningSince, LocalDateTime.now());
        }
        projectMapper.update(null, update);
        log.info("[task] {} 失败后回写项目运行态: status={} pid={}", state.taskId,
                running ? "running" : "stopped", pid);
    }

    /**
     * 从任务日志里取最后一个真实运行 PID。
     *
     * <p>启动脚本成功时打印 {@code running pid=1234}。失败实例的进程解析输出里也可能
     * 出现裸数字，但回滚发生在最后，因此只认日志中最后出现的 {@code running pid=}，
     * 它必然属于回滚后重启的旧版本进程；日志里没有该行时才回退到最后一个纯数字行。</p>
     *
     * @param logs 任务日志
     * @return PID 字符串；无法确定时返回 null
     */
    private String extractLastRunningPid(List<String> logs) {
        String pid = null;
        for (String line : logs) {
            Matcher m = RUNNING_PID.matcher(line == null ? "" : line);
            if (m.find()) {
                pid = m.group(1);
            }
        }
        if (pid != null) {
            return pid;
        }
        for (int i = logs.size() - 1; i >= 0; i--) {
            String t = (logs.get(i) == null ? "" : logs.get(i)).trim();
            if (t.matches("\\d+")) {
                return t;
            }
        }
        return null;
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
        taskPool.shutdownNow();    }}