package com.chua.starter.ai.support.workspace.terminal;

import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.starter.ai.support.workspace.WorkspacePathResolver;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作区终端 WebSocket 处理器（端点 {@code /ws/terminal}）。
 *
 * <p>基于 pty4j 的真实伪终端：Windows 使用 ConPTY、Unix 使用原生 libpty，
 * 因此 {@code vim}、{@code top}、{@code git rebase -i} 等交互式程序可正常使用，
 * 环境变量与进程状态在同一终端会话内持续保留。</p>
 *
 * <p>协议与 {@code @repo/core SocketTemplate} 保持一致：客户端发送 {@code {event,data}}，
 * 服务端按事件回包。</p>
 *
 * <p>客户端事件：</p>
 * <ul>
 *   <li>{@code terminal.start}：{@code data:{sessionId,cols,rows}}，启动或重启终端；</li>
 *   <li>{@code terminal.input}：{@code data:{data}}，写入原始按键/粘贴文本；</li>
 *   <li>{@code terminal.resize}：{@code data:{cols,rows}}，同步终端尺寸；</li>
 *   <li>{@code terminal.stop}：销毁当前终端进程；</li>
 *   <li>{@code ping}：心跳。</li>
 * </ul>
 *
 * <p>服务端事件：{@code terminal.ready}、{@code terminal.output}、{@code terminal.exit}、
 * {@code terminal.stopped}、{@code terminal.error}、{@code pong}。输出为原始终端字节流
 * （含 ANSI 控制序列），由前端 xterm.js 直接渲染。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    /**
     * 默认终端列数
     */
    private static final int DEFAULT_COLUMNS = 120;

    /**
     * 默认终端行数
     */
    private static final int DEFAULT_ROWS = 32;

    /**
     * 终端列数下限
     */
    private static final int MIN_COLUMNS = 20;

    /**
     * 终端列数上限
     */
    private static final int MAX_COLUMNS = 500;

    /**
     * 终端行数下限
     */
    private static final int MIN_ROWS = 5;

    /**
     * 终端行数上限
     */
    private static final int MAX_ROWS = 200;

    /**
     * PTY 输出读取缓冲字符数
     */
    private static final int READ_BUFFER_SIZE = 8192;

    /**
     * 单次写入 PTY 的最大字符数（防止单帧粘贴过大拖垮服务端）
     */
    private static final int MAX_INPUT_CHARS = 64 * 1024;

    /**
     * 等待读取线程结束的秒数
     */
    private static final long READER_JOIN_SECONDS = 5L;

    /**
     * 终端类型环境变量值
     */
    private static final String TERM_VALUE = "xterm-256color";

    /**
     * 颜色终端环境变量值
     */
    private static final String COLORTERM_VALUE = "truecolor";

    /**
     * Windows PowerShell 7 可执行文件。
     *
     * <p>存在时优先使用：实测 PowerShell 5.1 在部分 Windows 版本（如 10.0.18363）
     * 于 ConPTY 下启动会卡住不输出提示符，而 PowerShell 7 正常。</p>
     */
    private static final String PWSH_EXECUTABLE = "pwsh.exe";

    /**
     * Windows PowerShell 可执行文件
     */
    private static final String POWERSHELL_EXECUTABLE = "powershell.exe";

    /**
     * Windows 命令提示符可执行文件
     */
    private static final String CMD_EXECUTABLE = "cmd.exe";

    /**
     * Unix bash 路径
     */
    private static final String BASH_EXECUTABLE = "/bin/bash";

    /**
     * Unix 兜底 shell 路径
     */
    private static final String SH_EXECUTABLE = "/bin/sh";

    /**
     * PowerShell 启动时的 UTF-8 初始化脚本
     */
    private static final String POWERSHELL_UTF8_SCRIPT =
            "[Console]::OutputEncoding=[System.Text.Encoding]::UTF8;"
                    + "$OutputEncoding=[System.Text.Encoding]::UTF8";

    /**
     * 事件名：终端启动
     */
    private static final String EVENT_START = "terminal.start";

    /**
     * 事件名：终端输入
     */
    private static final String EVENT_INPUT = "terminal.input";

    /**
     * 事件名：终端尺寸变化
     */
    private static final String EVENT_RESIZE = "terminal.resize";

    /**
     * 事件名：终端停止
     */
    private static final String EVENT_STOP = "terminal.stop";

    /**
     * 事件名：心跳
     */
    private static final String EVENT_PING = "ping";

    /**
     * 工作目录解析器
     */
    private final WorkspacePathResolver pathResolver;

    /**
     * JSON 工具
     */
    private final ObjectMapper objectMapper;

    /**
     * WS 会话 id -> PTY 会话
     */
    private final Map<String, PtySession> sessions = new ConcurrentHashMap<>();

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
                case EVENT_PING -> send(session, "pong", new LinkedHashMap<>());
                case EVENT_START -> handleStart(session, data);
                case EVENT_INPUT -> handleInput(session, data);
                case EVENT_RESIZE -> handleResize(session, data);
                case EVENT_STOP -> handleStop(session);
                default -> log.debug("[terminal-ws] 未识别事件: {}", event);
            }
        } catch (Exception e) {
            log.warn("[terminal-ws] 处理消息异常: {}", messageOf(e));
            sendError(session, "终端消息处理失败：" + messageOf(e));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        PtySession ptySession = sessions.remove(session.getId());
        if (ptySession != null) {
            ptySession.close();
        }
        log.info("[terminal-ws] 连接已关闭 id={} status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("[terminal-ws] 传输异常 id={} error={}", session.getId(), messageOf(exception));
        PtySession ptySession = sessions.remove(session.getId());
        if (ptySession != null) {
            ptySession.close();
        }
    }

    /**
     * 启动或重启 PTY 终端。
     *
     * @param session WS 会话
     * @param data    请求数据
     */
    private void handleStart(WebSocketSession session, JsonNode data) {
        String sessionId = data.path("sessionId").asText("");
        if (sessionId.isBlank()) {
            sendError(session, "sessionId 不能为空");
            return;
        }
        int columns = clamp(data.path("cols").asInt(DEFAULT_COLUMNS), MIN_COLUMNS, MAX_COLUMNS);
        int rows = clamp(data.path("rows").asInt(DEFAULT_ROWS), MIN_ROWS, MAX_ROWS);

        UserResume resume = (UserResume) session.getAttributes().get(AiUserContext.RESUME_ATTRIBUTE);
        Path[] directories = new Path[2];
        String[] error = new String[1];
        AiUserContext.runWith(resume, () -> {
            try {
                directories[0] = pathResolver.agentWorkspace();
                directories[1] = pathResolver.sessionWorkspace(sessionId);
            } catch (Exception e) {
                error[0] = messageOf(e);
            }
        });
        if (error[0] != null) {
            sendError(session, error[0]);
            return;
        }

        PtySession previous = sessions.remove(session.getId());
        if (previous != null) {
            previous.close();
        }

        PtySession ptySession = new PtySession(session, sessionId, directories[0], directories[1], columns, rows);
        try {
            ptySession.start();
        } catch (IOException e) {
            log.warn("[terminal-ws] PTY 启动失败 id={} error={}", session.getId(), messageOf(e));
            sendError(session, "终端启动失败：" + messageOf(e));
            return;
        }
        sessions.put(session.getId(), ptySession);

        Map<String, Object> ready = new LinkedHashMap<>();
        ready.put("sessionId", sessionId);
        ready.put("cwd", ptySession.cwd().toString());
        ready.put("workspace", ptySession.workspaceRoot().toString());
        ready.put("shell", ptySession.shellName());
        ready.put("cols", columns);
        ready.put("rows", rows);
        send(session, "terminal.ready", ready);
        log.info("[terminal-ws] 终端就绪 id={} session={} cwd={} shell={}",
                session.getId(), sessionId, ptySession.cwd(), ptySession.shellName());
    }

    /**
     * 向 PTY 写入原始按键。
     *
     * @param session WS 会话
     * @param data    请求数据
     */
    private void handleInput(WebSocketSession session, JsonNode data) {
        PtySession ptySession = sessions.get(session.getId());
        if (ptySession == null) {
            sendError(session, "终端尚未初始化，请先发送 terminal.start");
            return;
        }
        String text = data.path("data").asText("");
        if (text.isEmpty()) {
            return;
        }
        if (text.length() > MAX_INPUT_CHARS) {
            sendError(session, "单次输入过长，请分批粘贴");
            return;
        }
        ptySession.write(text);
    }

    /**
     * 同步终端尺寸。
     *
     * @param session WS 会话
     * @param data    请求数据
     */
    private void handleResize(WebSocketSession session, JsonNode data) {
        PtySession ptySession = sessions.get(session.getId());
        if (ptySession == null) {
            return;
        }
        int columns = clamp(data.path("cols").asInt(DEFAULT_COLUMNS), MIN_COLUMNS, MAX_COLUMNS);
        int rows = clamp(data.path("rows").asInt(DEFAULT_ROWS), MIN_ROWS, MAX_ROWS);
        ptySession.resize(columns, rows);
    }

    /**
     * 终止当前 PTY 进程。
     *
     * @param session WS 会话
     */
    private void handleStop(WebSocketSession session) {
        PtySession ptySession = sessions.get(session.getId());
        if (ptySession == null) {
            sendError(session, "终端尚未初始化");
            return;
        }
        ptySession.stop();
    }

    /**
     * 关闭全部终端（应用停止时调用）。
     */
    @PreDestroy
    public void shutdown() {
        sessions.values().forEach(ptySession -> ptySession.close());
        sessions.clear();
        log.info("[terminal-ws] 全部终端已关闭");
    }

    /**
     * 数值裁剪。
     *
     * @param value 原始值
     * @param min   下限
     * @param max   上限
     * @return 裁剪后的值
     */
    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 发送服务端事件。
     *
     * @param session WS 会话
     * @param event   事件名
     * @param data    事件数据
     */
    private void send(WebSocketSession session, String event, Map<String, Object> data) {
        if (!session.isOpen()) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event", event);
            payload.put("data", data);
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            }
        } catch (Exception e) {
            log.debug("[terminal-ws] 发送事件失败 event={} error={}", event, messageOf(e));
        }
    }

    /**
     * 发送终端错误事件。
     *
     * @param session WS 会话
     * @param message 错误信息
     */
    private void sendError(WebSocketSession session, String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", message);
        send(session, "terminal.error", data);
    }

    /**
     * 异常信息提取。
     *
     * @param throwable 异常
     * @return 异常信息
     */
    private String messageOf(Throwable throwable) {
        if (throwable == null) {
            return "未知错误";
        }
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    /**
     * 构建当前平台的交互式 shell 命令。
     *
     * <p>Windows 优先 PowerShell 7（{@code pwsh.exe}），无可执行文件时退回
     * PowerShell 5.1，两者都不可用时由 {@link PtySession#start()} 回退 {@code cmd.exe}。</p>
     *
     * @return 命令数组
     */
    private String[] buildShellCommand() {
        if (isWindows()) {
            if (isExecutableOnPath(PWSH_EXECUTABLE)) {
                return new String[]{PWSH_EXECUTABLE, "-NoLogo", "-NoProfile", "-NoExit"};
            }
            // PowerShell 5.1 默认按控制台代码页输出，先切 UTF-8 再进入交互
            return new String[]{
                    POWERSHELL_EXECUTABLE,
                    "-NoLogo",
                    "-NoProfile",
                    "-NoExit",
                    "-Command",
                    POWERSHELL_UTF8_SCRIPT
            };
        }
        if (Files.isExecutable(Paths.get(BASH_EXECUTABLE))) {
            return new String[]{BASH_EXECUTABLE, "-l"};
        }
        return new String[]{SH_EXECUTABLE, "-l"};
    }

    /**
     * 构建 Windows 下的回退命令。
     *
     * @return 回退命令数组
     */
    private String[] buildWindowsFallbackCommand() {
        if (isExecutableOnPath(PWSH_EXECUTABLE)) {
            return new String[]{
                    POWERSHELL_EXECUTABLE,
                    "-NoLogo",
                    "-NoProfile",
                    "-NoExit",
                    "-Command",
                    POWERSHELL_UTF8_SCRIPT
            };
        }
        return new String[]{CMD_EXECUTABLE};
    }

    /**
     * 判断可执行文件是否存在于 PATH 中。
     *
     * @param executable 可执行文件名（如 {@code pwsh.exe}）
     * @return 是否存在
     */
    private boolean isExecutableOnPath(String executable) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return false;
        }
        for (String entry : path.split(java.io.File.pathSeparator)) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            try {
                if (Files.isRegularFile(Paths.get(entry.trim(), executable))) {
                    return true;
                }
            } catch (Exception ignored) {
                // 单个 PATH 条目非法不影响整体判断
            }
        }
        return false;
    }

    /**
     * 当前平台是否为 Windows。
     *
     * @return 是否 Windows
     */
    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /**
     * 构建 PTY 环境变量。
     *
     * <p>继承服务进程环境以保证 shell 可用，但剔除密钥类变量：终端对普通账号开放，
     * 不能把服务端凭据（数据库密码、模型 API Key 等）暴露给任意登录用户。</p>
     *
     * @return 环境变量
     */
    private Map<String, String> buildEnvironment() {
        Map<String, String> environment = new HashMap<>();
        System.getenv().forEach((key, value) -> {
            if (!isSensitiveEnvironment(key)) {
                environment.put(key, value);
            }
        });
        environment.put("TERM", TERM_VALUE);
        environment.put("COLORTERM", COLORTERM_VALUE);
        // 去掉会关闭颜色的变量，保证 git / ls 等命令输出可读
        environment.remove("NO_COLOR");
        environment.remove("CLICOLOR");
        environment.put("CLICOLOR_FORCE", "1");
        return environment;
    }

    /**
     * 判断环境变量名是否属于凭据类（不向终端会话透传）。
     *
     * @param name 环境变量名
     * @return 是否凭据类
     */
    private boolean isSensitiveEnvironment(String name) {
        if (name == null) {
            return true;
        }
        String upper = name.toUpperCase(Locale.ROOT);
        return upper.contains("PASSWORD")
                || upper.contains("PASSWD")
                || upper.contains("SECRET")
                || upper.contains("TOKEN")
                || upper.contains("CREDENTIAL")
                || upper.contains("API_KEY")
                || upper.contains("APIKEY")
                || upper.contains("ACCESS_KEY")
                || upper.contains("PRIVATE_KEY");
    }

    /**
     * 单个 WS 连接对应的 PTY 终端会话。
     *
     * @author CH
     * @since 4.0.0.51
     */
    private final class PtySession {

        /**
         * WS 会话
         */
        private final WebSocketSession webSocketSession;

        /**
         * 聊天会话标识
         */
        private final String chatSessionId;

        /**
         * 账号工作目录
         */
        private final Path workspaceRoot;

        /**
         * 终端初始工作目录
         */
        private final Path cwd;

        /**
         * 初始列数
         */
        private int columns;

        /**
         * 初始行数
         */
        private int rows;

        /**
         * PTY 进程
         */
        private volatile PtyProcess process;

        /**
         * 写入 PTY 的输入流
         */
        private volatile OutputStream stdin;

        /**
         * 是否正在主动停止
         */
        private volatile boolean stopping;

        /**
         * 是否已结束
         */
        private volatile boolean finished;

        /**
         * 是否静默关闭（重启或连接断开时不向前端广播事件）
         */
        private volatile boolean silent;

        /**
         * 读取 PTY 输出的线程
         */
        private volatile Thread readerThread;

        /**
         * 构造 PTY 会话。
         *
         * @param webSocketSession WS 会话
         * @param chatSessionId    聊天会话标识
         * @param workspaceRoot    账号工作目录
         * @param cwd              终端初始工作目录
         * @param columns          初始列数
         * @param rows             初始行数
         */
        private PtySession(WebSocketSession webSocketSession,
                           String chatSessionId,
                           Path workspaceRoot,
                           Path cwd,
                           int columns,
                           int rows) {
            this.webSocketSession = webSocketSession;
            this.chatSessionId = chatSessionId;
            this.workspaceRoot = workspaceRoot;
            this.cwd = cwd;
            this.columns = columns;
            this.rows = rows;
        }

        /**
         * 启动 PTY 进程与读取线程。
         *
         * @throws IOException 启动失败
         */
        private void start() throws IOException {
            String[] command = buildShellCommand();
            PtyProcessBuilder builder = new PtyProcessBuilder(command)
                    .setEnvironment(buildEnvironment())
                    .setDirectory(cwd.toString())
                    .setInitialColumns(columns)
                    .setInitialRows(rows)
                    .setRedirectErrorStream(true)
                    .setWindowsAnsiColorEnabled(true);
            if (isWindows()) {
                builder.setUseWinConPty(true);
            }
            PtyProcess ptyProcess;
            try {
                ptyProcess = builder.start();
            } catch (IOException firstError) {
                // PowerShell 缺失或 ConPTY 不可用时回退到 cmd，保证终端仍可用
                if (!isWindows()) {
                    throw firstError;
                }
                log.warn("[terminal-ws] PowerShell 启动失败，回退 cmd: {}", messageOf(firstError));
                ptyProcess = new PtyProcessBuilder(new String[]{CMD_EXECUTABLE})
                        .setEnvironment(buildEnvironment())
                        .setDirectory(cwd.toString())
                        .setInitialColumns(columns)
                        .setInitialRows(rows)
                        .setRedirectErrorStream(true)
                        .start();
            }
            this.process = ptyProcess;
            this.stdin = ptyProcess.getOutputStream();
            this.readerThread = Thread.ofVirtual()
                    .name("ai-terminal-io-" + chatSessionId + "-")
                    .start(this::readLoop);
        }

        /**
         * 读取 PTY 输出并推送给前端。
         */
        private void readLoop() {
            try (Reader reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
                char[] buffer = new char[READ_BUFFER_SIZE];
                int length;
                while ((length = reader.read(buffer)) != -1) {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("data", new String(buffer, 0, length));
                    send(webSocketSession, "terminal.output", data);
                }
            } catch (IOException e) {
                if (!stopping) {
                    log.debug("[terminal-ws] 读取 PTY 输出结束 session={} error={}", chatSessionId, messageOf(e));
                }
            } finally {
                finish();
            }
        }

        /**
         * 进程结束后的收尾：广播退出事件并清理引用。
         */
        private void finish() {
            if (finished) {
                return;
            }
            finished = true;
            PtyProcess current = process;
            int exitCode = -1;
            if (current != null) {
                try {
                    exitCode = current.waitFor();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (silent) {
                closeStdin();
                return;
            }
            if (stopping) {
                send(webSocketSession, "terminal.stopped", new LinkedHashMap<>());
            } else {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("code", exitCode);
                send(webSocketSession, "terminal.exit", data);
            }
            closeStdin();
        }

        /**
         * 写入原始按键。
         *
         * @param text 按键文本
         */
        private void write(String text) {
            OutputStream outputStream = stdin;
            if (outputStream == null || finished) {
                sendError(webSocketSession, "终端进程已结束，请重新启动终端");
                return;
            }
            try {
                outputStream.write(text.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            } catch (IOException e) {
                sendError(webSocketSession, "终端写入失败：" + messageOf(e));
            }
        }

        /**
         * 同步终端尺寸。
         *
         * @param newColumns 新列数
         * @param newRows    新行数
         */
        private void resize(int newColumns, int newRows) {
            if (this.columns == newColumns && this.rows == newRows) {
                return;
            }
            this.columns = newColumns;
            this.rows = newRows;
            PtyProcess current = process;
            if (current == null || finished) {
                return;
            }
            try {
                current.setWinSize(new WinSize(newColumns, newRows));
            } catch (Exception e) {
                log.debug("[terminal-ws] 调整终端尺寸失败 error={}", messageOf(e));
            }
        }

        /**
         * 主动停止终端进程。
         */
        private void stop() {
            if (stopping || finished) {
                return;
            }
            stopping = true;
            PtyProcess current = process;
            if (current != null && current.isAlive()) {
                current.destroy();
            }
        }

        /**
         * 关闭终端并释放资源（重启、连接断开、应用停止时使用，不向前端广播事件）。
         */
        private void close() {
            silent = true;
            stopping = true;
            PtyProcess current = process;
            if (current != null && current.isAlive()) {
                current.destroy();
            }
            Thread thread = readerThread;
            if (thread != null) {
                try {
                    thread.join(READER_JOIN_SECONDS * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            closeStdin();
        }

        /**
         * 关闭输入流。
         */
        private void closeStdin() {
            OutputStream outputStream = stdin;
            stdin = null;
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException ignored) {
                    // 关闭失败不影响终端销毁
                }
            }
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
         * 获取终端初始工作目录。
         *
         * @return 终端初始工作目录
         */
        private Path cwd() {
            return cwd;
        }

        /**
         * 获取 shell 名称。
         *
         * @return shell 名称
         */
        private String shellName() {
            return isWindows() ? POWERSHELL_EXECUTABLE : BASH_EXECUTABLE;
        }
    }
}

