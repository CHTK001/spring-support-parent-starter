package com.chua.starter.ai.support.workspace.terminal;

import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.starter.ai.support.workspace.WorkspacePathResolver;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 工作区终端 WebSocket 处理器（端点 {@code /ws/terminal}）。
 *
 * <p>协议与 {@code @repo/core SocketTemplate} 保持一致：客户端发送
 * {@code {event,data}}，服务端按事件回包。</p>
 *
 * <p>支持的事件：</p>
 * <ul>
 *   <li>{@code terminal.start}：{@code data:{sessionId}}，建立终端并返回工作目录；</li>
 *   <li>{@code terminal.input}：{@code data:{command}}，执行一条命令并流式返回输出；</li>
 *   <li>{@code terminal.stop}：终止当前命令；</li>
 *   <li>{@code ping}：心跳。</li>
 * </ul>
 *
 * <p>服务端事件：{@code terminal.ready}、{@code terminal.command}、{@code terminal.data}、
 * {@code terminal.exit}、{@code terminal.error}、{@code pong}。</p>
 *
 * <p>说明：当前实现为“行式命令终端”，每条命令独立进程执行并跟踪 {@code cd} 目录，
 * 因此命令状态（环境变量、交互式程序）不会跨命令保留；这是无 PTY 依赖下更稳妥的工程实现。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    /**
     * 单条命令最大输出字节数
     */
    private static final long MAX_OUTPUT_BYTES = 5L * 1024L * 1024L;

    /**
     * 单条命令超时时间（秒）
     */
    private static final long COMMAND_TIMEOUT_SECONDS = 600L;

    /**
     * 读取线程等待时间（秒）
     */
    private static final long READER_JOIN_SECONDS = 10L;

    /**
     * 命令最大长度
     */
    private static final int MAX_COMMAND_LENGTH = 4000;

    /**
     * 标准输出流名称
     */
    private static final String STREAM_STDOUT = "stdout";

    /**
     * 标准错误流名称
     */
    private static final String STREAM_STDERR = "stderr";

    /**
     * 工作目录解析器
     */
    private final WorkspacePathResolver pathResolver;

    /**
     * JSON 工具
     */
    private final ObjectMapper objectMapper;

    /**
     * WS 会话 id -> 终端连接
     */
    private final Map<String, TerminalConnection> connections = new ConcurrentHashMap<>();

    /**
     * 命令执行线程池（虚拟线程，避免阻塞平台线程）
     */
    private final ThreadPoolExecutor commandExecutor = new ThreadPoolExecutor(
            0,
            64,
            60L,
            TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            Thread.ofVirtual().name("ai-terminal-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy());

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("[terminal-ws] 连接已建立 id={} remote={}", session.getId(), session.getRemoteAddress());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode root = objectMapper.readTree(message.getPayload());
            String event = root.path("event").asText("");
            JsonNode data = root.path("data");
            switch (event) {
                case "ping" -> send(session, "pong", new LinkedHashMap<>());
                case "terminal.start" -> handleStart(session, data);
                case "terminal.input" -> handleInput(session, data);
                case "terminal.stop" -> handleStop(session);
                default -> log.debug("[terminal-ws] 未识别事件: {}", event);
            }
        } catch (Exception e) {
            log.warn("[terminal-ws] 处理消息异常: {}", e.getMessage());
            sendError(session, null, "终端消息处理失败：" + messageOf(e));
        }
    }

    /**
     * 建立终端会话。
     *
     * @param session WS 会话
     * @param data    请求数据
     */
    private void handleStart(WebSocketSession session, JsonNode data) {
        String sessionId = data.path("sessionId").asText("");
        if (sessionId.isBlank()) {
            sendError(session, null, "sessionId 不能为空");
            return;
        }
        UserResume resume = (UserResume) session.getAttributes().get(AiUserContext.RESUME_ATTRIBUTE);
        AtomicReference<Path> workspaceRef = new AtomicReference<>();
        AtomicReference<Path> sessionRootRef = new AtomicReference<>();
        AtomicReference<String> errorRef = new AtomicReference<>();
        AiUserContext.runWith(resume, () -> {
            try {
                workspaceRef.set(pathResolver.agentWorkspace());
                sessionRootRef.set(pathResolver.sessionWorkspace(sessionId));
            } catch (Exception e) {
                errorRef.set(messageOf(e));
            }
        });
        if (errorRef.get() != null || workspaceRef.get() == null || sessionRootRef.get() == null) {
            sendError(session, null, errorRef.get() == null ? "无法解析工作目录" : errorRef.get());
            return;
        }
        TerminalConnection previous = connections.remove(session.getId());
        if (previous != null) {
            previous.stop();
        }
        TerminalConnection connection = new TerminalConnection(
                sessionId,
                workspaceRef.get(),
                sessionRootRef.get()
        );
        connections.put(session.getId(), connection);
        Map<String, Object> ready = new LinkedHashMap<>();
        ready.put("sessionId", sessionId);
        ready.put("cwd", connection.cwd().toString());
        ready.put("workspace", connection.workspaceRoot().toString());
        ready.put("shell", shellName());
        send(session, "terminal.ready", ready);
        log.info("[terminal-ws] 终端就绪 id={} session={} cwd={}",
                session.getId(), sessionId, connection.cwd());
    }

    /**
     * 执行命令。
     *
     * @param session WS 会话
     * @param data    请求数据
     */
    private void handleInput(WebSocketSession session, JsonNode data) {
        TerminalConnection connection = connections.get(session.getId());
        if (connection == null) {
            sendError(session, null, "终端尚未初始化，请先发送 terminal.start");
            return;
        }
        String command = data.path("command").asText("");
        String normalized = command.trim();
        if (normalized.isEmpty()) {
            return;
        }
        if (normalized.length() > MAX_COMMAND_LENGTH) {
            sendError(session, null, "命令过长，最多 4000 字符");
            return;
        }
        if (!connection.running().compareAndSet(false, true)) {
            sendError(session, null, "上一条命令仍在执行，请先停止或等待完成");
            return;
        }
        long sequence = connection.nextSequence();
        try {
            if (isChangeDirectory(normalized)) {
                handleChangeDirectory(session, connection, normalized, sequence);
                return;
            }
            sendCommand(session, connection, normalized, sequence);
            commandExecutor.execute(() -> execute(session, connection, normalized, sequence));
        } catch (Exception e) {
            connection.running().set(false);
            sendError(session, sequence, "启动命令失败：" + messageOf(e));
        }
    }

    /**
     * 停止当前命令。
     *
     * @param session WS 会话
     */
    private void handleStop(WebSocketSession session) {
        TerminalConnection connection = connections.get(session.getId());
        if (connection == null) {
            return;
        }
        connection.stop();
        send(session, "terminal.stopped", new LinkedHashMap<>());
    }

    /**
     * 执行一条外部命令并流式输出。
     *
     * @param session    WS 会话
     * @param connection 终端连接
     * @param command    命令
     * @param sequence   序号
     */
    private void execute(WebSocketSession session, TerminalConnection connection, String command, long sequence) {
        long startedAt = System.currentTimeMillis();
        Process process = null;
        CompletableFuture<Void> stdoutPump = null;
        CompletableFuture<Void> stderrPump = null;
        try {
            ProcessBuilder builder = buildProcess(command);
            builder.directory(connection.cwd().toFile());
            builder.environment().put("TERM", "dumb");
            builder.environment().put("NO_COLOR", "1");
            builder.environment().put("CLICOLOR", "0");
            builder.environment().put("PYTHONUNBUFFERED", "1");
            process = builder.start();
            connection.attach(process);
            InputStream stdout = process.getInputStream();
            InputStream stderr = process.getErrorStream();
            stdoutPump = CompletableFuture.runAsync(
                    () -> pump(session, connection, sequence, STREAM_STDOUT, stdout), commandExecutor);
            stderrPump = CompletableFuture.runAsync(
                    () -> pump(session, connection, sequence, STREAM_STDERR, stderr), commandExecutor);
            boolean finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                killProcess(process);
                sendError(session, sequence, "命令执行超时（600 秒），已终止");
            }
            awaitPump(stdoutPump);
            awaitPump(stderrPump);
            int exitCode = -1;
            try {
                if (process.isAlive()) {
                    killProcess(process);
                }
                exitCode = process.exitValue();
            } catch (Exception ignored) {
                // 进程被强制终止时以 -1 表示
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("seq", sequence);
            result.put("code", exitCode);
            result.put("cwd", connection.cwd().toString());
            result.put("durationMs", System.currentTimeMillis() - startedAt);
            send(session, "terminal.exit", result);
        } catch (Exception e) {
            log.warn("[terminal-ws] 命令执行失败 session={}: {}", connection.sessionId(), e.getMessage());
            sendError(session, sequence, "命令执行失败：" + messageOf(e));
        } finally {
            if (stdoutPump != null) {
                stdoutPump.cancel(true);
            }
            if (stderrPump != null) {
                stderrPump.cancel(true);
            }
            if (process != null) {
                killProcess(process);
            }
            connection.running().set(false);
        }
    }

    /**
     * 处理目录切换。
     *
     * @param session    WS 会话
     * @param connection 终端连接
     * @param command    命令
     * @param sequence   序号
     */
    private void handleChangeDirectory(WebSocketSession session,
                                       TerminalConnection connection,
                                       String command,
                                       long sequence) {
        try {
            String target = command.replaceFirst("(?i)^(cd|sl|set-location)(\\s+|$)", "").trim();
            if ((target.startsWith("\"") && target.endsWith("\""))
                    || (target.startsWith("'") && target.endsWith("'"))) {
                target = target.substring(1, target.length() - 1);
            }
            Path next;
            if (target.isEmpty() || "~".equals(target)) {
                next = connection.workspaceRoot();
            } else if (target.startsWith("~/")) {
                next = connection.workspaceRoot().resolve(target.substring(2)).normalize();
            } else {
                next = connection.cwd().resolve(target).normalize();
            }
            if (!next.startsWith(connection.workspaceRoot())) {
                throw new IllegalArgumentException("目录不能超出工作区");
            }
            if (!Files.isDirectory(next)) {
                throw new IllegalArgumentException("目录不存在：" + target);
            }
            connection.changeCwd(next);
            sendCommand(session, connection, command, sequence);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("seq", sequence);
            result.put("code", 0);
            result.put("cwd", next.toString());
            result.put("durationMs", 0L);
            send(session, "terminal.exit", result);
        } catch (Exception e) {
            sendError(session, sequence, messageOf(e));
        } finally {
            connection.running().set(false);
        }
    }

    /**
     * 构造平台命令。
     *
     * @param command 用户命令
     * @return 进程构造器
     */
    private ProcessBuilder buildProcess(String command) {
        if (isWindows()) {
            String script = "$OutputEncoding=[Console]::OutputEncoding=[Text.Encoding]::UTF8; " + command;
            return new ProcessBuilder(
                    "powershell.exe",
                    "-NoLogo",
                    "-NoProfile",
                    "-NonInteractive",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-Command",
                    script);
        }
        return new ProcessBuilder("/bin/bash", "-lc", command);
    }

    /**
     * 读取进程输出并逐块下发。
     *
     * @param session    WS 会话
     * @param connection 终端连接
     * @param sequence   序号
     * @param streamName 流名称
     * @param stream     输入流
     */
    private void pump(WebSocketSession session,
                      TerminalConnection connection,
                      long sequence,
                      String streamName,
                      InputStream stream) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8), 8192)) {
            char[] buffer = new char[2048];
            int length;
            while ((length = reader.read(buffer)) != -1) {
                if (connection.outputBytes().addAndGet(length) > MAX_OUTPUT_BYTES) {
                    sendError(session, sequence, "输出超过 5MB，已终止命令");
                    connection.stop();
                    break;
                }
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("seq", sequence);
                data.put("stream", streamName);
                data.put("data", new String(buffer, 0, length));
                send(session, "terminal.data", data);
            }
        } catch (Exception e) {
            if (session.isOpen()) {
                log.debug("[terminal-ws] 读取输出结束 session={}: {}", connection.sessionId(), e.getMessage());
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        TerminalConnection connection = connections.remove(session.getId());
        if (connection != null) {
            connection.stop();
        }
        log.info("[terminal-ws] 连接关闭 id={} status={}", session.getId(), status);
    }

    /**
     * 关闭命令线程池。
     */
    @PreDestroy
    public void shutdown() {
        connections.values().forEach(TerminalConnection::stop);
        connections.clear();
        commandExecutor.shutdownNow();
    }

    /**
     * 发送命令开始事件。
     *
     * @param session    WS 会话
     * @param connection 终端连接
     * @param command    命令
     * @param sequence   序号
     */
    private void sendCommand(WebSocketSession session,
                             TerminalConnection connection,
                             String command,
                             long sequence) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("seq", sequence);
        data.put("command", command);
        data.put("cwd", connection.cwd().toString());
        send(session, "terminal.command", data);
    }

    /**
     * 发送错误事件。
     *
     * @param session WS 会话
     * @param seq     序号
     * @param message 错误信息
     */
    private void sendError(WebSocketSession session, Long seq, String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (seq != null) {
            data.put("seq", seq);
        }
        data.put("message", message);
        send(session, "terminal.error", data);
    }

    /**
     * 发送一条 {event,data} 消息。
     *
     * @param session WS 会话
     * @param event   事件
     * @param data    数据
     */
    private void send(WebSocketSession session, String event, Object data) {
        if (!session.isOpen()) {
            return;
        }
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("event", event);
            envelope.put("data", data);
            TextMessage message = new TextMessage(objectMapper.writeValueAsString(envelope));
            synchronized (session) {
                session.sendMessage(message);
            }
        } catch (Exception e) {
            log.debug("[terminal-ws] 发送失败 event={}: {}", event, e.getMessage());
        }
    }

    /**
     * 等待读取线程结束。
     *
     * @param future 读取任务
     */
    private void awaitPump(CompletableFuture<Void> future) {
        try {
            future.get(READER_JOIN_SECONDS, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // 读取超时或中断时由 finally 统一回收进程
        }
    }

    /**
     * 强制终止进程及其子进程。
     *
     * @param process 进程
     */
    private void killProcess(Process process) {
        if (process == null || !process.isAlive()) {
            return;
        }
        try {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
        } catch (Exception ignored) {
            // 子进程回收失败时继续终止主进程
        }
        process.destroyForcibly();
    }

    /**
     * 判断是否为目录切换命令。
     *
     * @param command 命令
     * @return 是否 cd 类命令
     */
    private boolean isChangeDirectory(String command) {
        return command.matches("(?i)^(cd|sl|set-location)(\\s+.*)?$");
    }

    /**
     * 判断当前是否为 Windows。
     *
     * @return 是否 Windows
     */
    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /**
     * 返回终端 Shell 名称。
     *
     * @return Shell 名称
     */
    private String shellName() {
        return isWindows() ? "PowerShell" : "bash";
    }

    /**
     * 提取异常提示。
     *
     * @param e 异常
     * @return 提示文本
     */
    private String messageOf(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    /**
     * 单个 WebSocket 连接的终端状态。
     *
     * @author CH
     * @since 4.0.0.51
     */
    private static final class TerminalConnection {

        /**
         * 聊天会话标识
         */
        private final String sessionId;

        /**
         * 账号工作目录
         */
        private final Path workspaceRoot;

        /**
         * 当前执行目录
         */
        private volatile Path cwd;

        /**
         * 当前命令进程
         */
        private final AtomicReference<Process> process = new AtomicReference<>();

        /**
         * 是否有命令正在执行
         */
        private final AtomicBoolean running = new AtomicBoolean(false);

        /**
         * 命令序号
         */
        private final AtomicLong sequence = new AtomicLong(0L);

        /**
         * 当前命令累计输出字节数
         */
        private final AtomicLong outputBytes = new AtomicLong(0L);

        /**
         * 构造终端连接。
         *
         * @param sessionId     聊天会话标识
         * @param workspaceRoot 账号工作目录
         * @param cwd           当前执行目录
         */
        private TerminalConnection(String sessionId,
                                   Path workspaceRoot,
                                   Path cwd) {
            this.sessionId = sessionId;
            this.workspaceRoot = workspaceRoot;
            this.cwd = cwd;
        }

        /**
         * 获取聊天会话标识。
         *
         * @return 聊天会话标识
         */
        private String sessionId() {
            return sessionId;
        }

        /**
         * 获取账号工作目录。
         *
         * @return 账号工作目录
         */
        private Path workspaceRoot() {
            return workspaceRoot;
        }

        /**
         * 获取当前目录。
         *
         * @return 当前目录
         */
        private Path cwd() {
            return cwd;
        }

        /**
         * 切换当前目录。
         *
         * @param cwd 新目录
         */
        private void changeCwd(Path cwd) {
            this.cwd = cwd;
        }

        /**
         * 获取执行状态。
         *
         * @return 执行状态
         */
        private AtomicBoolean running() {
            return running;
        }

        /**
         * 获取输出字节计数。
         *
         * @return 输出字节计数
         */
        private AtomicLong outputBytes() {
            return outputBytes;
        }

        /**
         * 分配下一个命令序号。
         *
         * @return 命令序号
         */
        private long nextSequence() {
            outputBytes.set(0L);
            return sequence.incrementAndGet();
        }

        /**
         * 绑定当前进程。
         *
         * @param process 进程
         */
        private void attach(Process process) {
            this.process.set(process);
        }

        /**
         * 终止当前命令。
         */
        private void stop() {
            Process current = process.getAndSet(null);
            if (current == null || !current.isAlive()) {
                return;
            }
            try {
                current.descendants().forEach(ProcessHandle::destroyForcibly);
            } catch (Exception ignored) {
                // 子进程回收失败时继续终止主进程
            }
            current.destroyForcibly();
        }
    }
}
