package com.chua.starter.panel.software.socket;

import com.chua.socket.support.SocketListener;
import com.chua.socket.support.annotations.OnEvent;
import com.chua.socket.support.session.SocketSession;
import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.starter.panel.service.DockerPanelService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.IOException;

import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Docker 面板终端 Socket.IO 监听器（实时 PTY 流式版本）。
 * <p>
 * 使用 docker-java SDK 的 exec API + ResultCallback 异步回调
 * 创建持久 /bin/sh TTY 会话，onNext(Frame) 有数据即推送到前端，
 * 实现类似 Xshell/SecureCRT 的实时终端体验。
 * </p>
 *
 * @author CH
 */
@Component
public class DockerPanelTerminalSocketListener implements SocketListener {

    private static final Logger log = LoggerFactory.getLogger(DockerPanelTerminalSocketListener.class);
    private static final String MODULE = "DOCKER";
    private static final String SHELL = "/bin/sh";
    private static final int STDIN_BUFFER_SIZE = 64 * 1024;

    private final ObjectMapper objectMapper;
    private final DockerPanelService dockerPanelService;
    private final ObjectProvider<SocketSessionTemplate> socketSessionTemplateProvider;
    private final ConcurrentHashMap<String, PtySession> ptySessions = new ConcurrentHashMap<>();

    public DockerPanelTerminalSocketListener(ObjectMapper objectMapper,
                                             DockerPanelService dockerPanelService,
                                             ObjectProvider<SocketSessionTemplate> socketSessionTemplateProvider) {
        this.objectMapper = objectMapper;
        this.dockerPanelService = dockerPanelService;
        this.socketSessionTemplateProvider = socketSessionTemplateProvider;
    }

    // ─── Socket 事件处理 ─────────────────────────────────────────────────

    @OnEvent("docker.terminal.connect")
    public void onConnect(SocketSession socketSession, String rawData) {
        Map<String, Object> request;
        try {
            request = parseRawData(rawData);
        } catch (Exception e) {
            publishTerminalStatus(null, "FAILED", "无效的请求数据: " + e.getMessage());
            return;
        }

        String sessionId = getString(request, "sessionId");
        String connectionId = getString(request, "connectionId");
        String containerName = getString(request, "containerName");
        Integer bindingId = getInteger(request, "bindingId");

        if (sessionId == null || connectionId == null || containerName == null) {
            publishTerminalStatus(sessionId, "FAILED", "参数不完整: 需要 sessionId, connectionId, containerName");
            return;
        }

        // 清理已存在的同名会话
        PtySession existing = ptySessions.get(sessionId);
        if (existing != null) {
            closePtySession(sessionId, existing);
        }

        try {
            DockerClient dc = dockerPanelService.getDockerClient(connectionId);
            PtySession pty = createPtySession(sessionId, dc, containerName);
            ptySessions.put(sessionId, pty);
            publishTerminalStatus(sessionId, "CONNECTED", "终端已连接: " + containerName);
        } catch (Exception e) {
            log.warn("[DockerTerminal] 创建 PTY 失败: sessionId={}, error={}", sessionId, e.getMessage());
            publishTerminalStatus(sessionId, "FAILED", "连接失败: " + e.getMessage());
        }
    }

    @OnEvent("docker.terminal.input")
    public void onInput(SocketSession socketSession, String rawData) {
        Map<String, Object> request;
        try {
            request = parseRawData(rawData);
        } catch (Exception e) {
            return;
        }

        String sessionId = getString(request, "sessionId");
        String input = request.get("input") instanceof String s ? s : null;

        if (sessionId == null) return;
        PtySession pty = ptySessions.get(sessionId);
        if (pty == null) {
            publishTerminalStatus(sessionId, "FAILED", "终端会话不存在");
            return;
        }
        if (input == null) return;

        try {
            pty.stdin().write(input.getBytes(StandardCharsets.UTF_8));
            pty.stdin().flush();
        } catch (Exception e) {
            log.warn("[DockerTerminal] 写入 PTY stdin 失败: sessionId={}", sessionId);
            closePtySession(sessionId, pty);
            publishTerminalStatus(sessionId, "DISCONNECTED", "连接已断开");
        }
    }

    @OnEvent("docker.terminal.disconnect")
    public void onDisconnect(SocketSession socketSession, String rawData) {
        String sessionId = extractSessionId(rawData);
        if (sessionId == null) return;
        PtySession pty = ptySessions.get(sessionId);
        if (pty != null) {
            closePtySession(sessionId, pty);
        }
        publishTerminalStatus(sessionId, "DISCONNECTED", "终端已断开");
    }

    @OnEvent("docker.terminal.resize")
    public void onResize(SocketSession socketSession, String rawData) {
        Map<String, Object> request;
        try {
            request = parseRawData(rawData);
        } catch (Exception e) {
            return;
        }
        String sessionId = getString(request, "sessionId");
        if (sessionId == null) return;
        PtySession pty = ptySessions.get(sessionId);
        if (pty == null) return;

        Integer cols = getInteger(request, "cols");
        Integer rows = getInteger(request, "rows");
        if (cols != null && rows != null && cols > 0 && rows > 0) {
            try {
                pty.dockerClient().resizeExecCmd(pty.execId())
                        .withSize(cols, rows)
                        .exec();
            } catch (Exception e) {
                log.debug("[DockerTerminal] resize 失败: {}", e.getMessage());
            }
        }
        publishTerminalStatus(sessionId, "RESIZED", "尺寸已同步");
    }

    // ─── PTY 会话管理 ─────────────────────────────────────────────────────

    private PtySession createPtySession(String sessionId, DockerClient dc, String containerName) {
        // 1. 创建交互式 shell exec（持久会话）
        ExecCreateCmdResponse execCreate = dc.execCreateCmd(containerName)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withAttachStdin(true)
                .withTty(true)
                .withCmd(SHELL)
                .exec();
        String execId = execCreate.getId();

        // 2. 准备 stdin pipe（大缓冲区避免背压阻塞）
        PipedOutputStream pipeOut = new PipedOutputStream();
        PipedInputStream pipeIn;
        try {
            pipeIn = new PipedInputStream(pipeOut, STDIN_BUFFER_SIZE);
        } catch (IOException e) {
            throw new RuntimeException("创建 stdin pipe 失败", e);
        }

        // 3. 启动 exec 异步回调 — onNext(Frame) 实时推送输出
        //    这是实现 "类 Xshell 实时终端" 的核心：
        //    docker-java 内部线程持续读取 TTY 输出，有数据即回调 onNext
        AtomicBoolean closed = new AtomicBoolean(false);
        ResultCallback<Frame> callback = dc.execStartCmd(execId)
                .withStdIn(pipeIn)
                .withTty(true)
                .exec(new ResultCallback<Frame>() {
                    @Override
                    public void onStart(Closeable closeable) {
                        log.debug("[DockerTerminal] PTY started: sessionId={}", sessionId);
                    }

                    @Override
                    public void onNext(Frame frame) {
                        byte[] payload = frame.getPayload();
                        if (payload != null && payload.length > 0) {
                            publishTerminalOutput(sessionId, payload);
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        if (!closed.compareAndSet(false, true)) return;
                        log.warn("[DockerTerminal] PTY error: sessionId={}", sessionId, throwable);
                        publishTerminalStatus(sessionId, "FAILED", "PTY 错误: " + throwable.getMessage());
                        ptySessions.remove(sessionId);
                    }

                    @Override
                    public void onComplete() {
                        if (!closed.compareAndSet(false, true)) return;
                        publishTerminalStatus(sessionId, "COMPLETED", "终端进程已退出");
                        ptySessions.remove(sessionId);
                    }

                    @Override
                    public void close() {
                        closed.set(true);
                    }
                });

        return new PtySession(sessionId, execId, dc, pipeOut, callback, closed);
    }

    private void closePtySession(String sessionId, PtySession pty) {
        pty.closed().set(true);
        try { pty.stdin().close(); } catch (Exception ignored) {}
        try { pty.callback().close(); } catch (Exception ignored) {}
        ptySessions.remove(sessionId, pty);
    }

    // ─── Socket.IO 消息发布 ──────────────────────────────────────────────

    private void publishTerminalStatus(String sessionId, String status, String message) {
        SocketSessionTemplate template = socketSessionTemplateProvider.getIfAvailable();
        if (template == null) return;

        Map<String, Object> data = new HashMap<>(4);
        data.put("sessionId", Objects.toString(sessionId, ""));
        data.put("status", status);
        data.put("message", message);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("module", MODULE);
        envelope.put("event", "docker.terminal.status");
        envelope.put("dataId", Objects.toString(sessionId, "anonymous"));
        envelope.put("data", data);
        envelope.put("timestamp", System.currentTimeMillis());
        template.broadcastObject("message", envelope);
    }

    private void publishTerminalOutput(String sessionId, byte[] payload) {
        SocketSessionTemplate template = socketSessionTemplateProvider.getIfAvailable();
        if (template == null) return;

        // Base64 编码后推给前端 xterm.js 解码写入
        Map<String, Object> data = new HashMap<>(3);
        data.put("sessionId", Objects.toString(sessionId, ""));
        data.put("data", Base64.getEncoder().encodeToString(payload));

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("module", MODULE);
        envelope.put("event", "docker.terminal.output");
        envelope.put("dataId", Objects.toString(sessionId, "anonymous"));
        envelope.put("data", data);
        envelope.put("timestamp", System.currentTimeMillis());
        template.broadcastObject("message", envelope);
    }

    // ─── 数据解析 ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseRawData(String rawData) throws JsonProcessingException {
        if (rawData == null || rawData.isBlank()) return Map.of();
        return objectMapper.readValue(rawData, Map.class);
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String s && !s.isBlank() ? s : null;
    }

    private Integer getInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private String extractSessionId(String rawData) {
        if (rawData == null || rawData.isBlank()) return null;
        try {
            Map<String, Object> map = objectMapper.readValue(rawData, Map.class);
            return getString(map, "sessionId");
        } catch (Exception e) {
            return null;
        }
    }

    // ─── 内部类型 ─────────────────────────────────────────────────────────

    private record PtySession(
            String sessionId,
            String execId,
            DockerClient dockerClient,
            OutputStream stdin,
            ResultCallback<Frame> callback,
            AtomicBoolean closed
    ) {}
}
