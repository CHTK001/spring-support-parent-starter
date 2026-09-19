package com.chua.starter.server.support.terminal.handler;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.entity.MsTerminalSession;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.service.TerminalSessionService;
import com.chua.starter.server.support.util.SshClients;
import com.chua.ssh.support.client.SshClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSH 直连终端 webSocket 处理器
 * <p>
 * 与 {@link TerminalWebSocketHandler} 的区别是不经过 guacd：
 * 由 {@link SshClients} 直接以 SSH 协议连到目标主机的 PTY，
 * 浏览器侧只依赖一个标准终端仿真组件（xterm）。
 * </p>
 * <p>
 * 帧约定：文本帧仅承载 JSON 控制指令（尺寸调整），键盘输入与远端输出
 * 一律走二进制帧（UTF-8），避免与 shell 中合法的 {'"'"'{'} 字符冲突。
 * </p>
 * 端点：{@code /ws/ms/server/terminal/ssh?hostId=&identityId=&cols=&rows=&user=}
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SshDirectWebSocketHandler extends AbstractWebSocketHandler {

    /**
     * 单帧文本上限（控制指令很短，留余量即可）
    */
    private static final int MAX_TEXT_BUFFER = 64 * 1024;

    /**
     * 初始终端尺寸兜底
    */
    private static final int DEFAULT_COLS = 120;
    private static final int DEFAULT_ROWS = 30;

    /**
     * 主机服务（地址/端口/凭据解析）
    */
    private final ServerHostService serverHostService;

    /**
     * 会话审计服务
    */
    private final TerminalSessionService terminalSessionService;

    /**
     * 活跃直连会话：WS 会话 标识 -> 桥接上下文
    */
    private final Map<String, Bridge> bridges = new ConcurrentHashMap<>();

    /**
     * 连接建立：解析参数、SSH 握手并启动输出回推
     *
     * @param wsSession 浏览器 WS 会话
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession wsSession) throws Exception {
        wsSession.setTextMessageSizeLimit(MAX_TEXT_BUFFER);
        Map<String, String> params = parseQuery(wsSession);
        Long hostId = parseLong(params.get("hostId"));
        if (hostId == null) {
            reject(wsSession, "缺少 hostId 参数");
            return;
        }
        Long identityId = parseLong(params.get("identityId"));
        int cols = parsePositiveInt(params.get("cols"), DEFAULT_COLS);
        int rows = parsePositiveInt(params.get("rows"), DEFAULT_ROWS);
        String userName = params.getOrDefault("user", "anonymous");

        ReturnResult<MsServerHost> hostResult = serverHostService.get(hostId);
        if (!hostResult.isOk() || hostResult.getData() == null) {
            reject(wsSession, hostResult.getMsg());
            return;
        }
        MsServerHost host = hostResult.getData();
        ReturnResult<SshCredential> resolved = serverHostService.resolveCredential(hostId, identityId);
        if (!resolved.isOk()) {
            reject(wsSession, resolved.getMsg());
            return;
        }
        int port = host.getServerHostRemotePort() != null && host.getServerHostRemotePort() > 0
                ? host.getServerHostRemotePort()
                : (host.getServerHostPort() == null ? ServerConstants.DEFAULT_SSH_PORT : host.getServerHostPort());

        SshClients.Handle handle;
        try {
            handle = SshClients.open(host.getServerHostAddress(), port, resolved.getData());
            handle.client().connect();
        } catch (Exception e) {
            log.warn("[terminal-ssh] SSH 连接失败 hostId={} {}:{}: {}", hostId, host.getServerHostAddress(), port, e.getMessage());
            reject(wsSession, "SSH 连接失败: " + e.getMessage());
            return;
        }

        Bridge bridge = new Bridge(handle, wsSession);
        MsTerminalSession record = terminalSessionService.openRecord(
                hostId, host.getServerHostName(), "SSH", userName, resolveClientIp(wsSession));
        bridge.recordId = record == null ? null : record.getId();
        bridges.put(wsSession.getId(), bridge);
        try {
            SshClient.TerminalOperation operation = handle.client().terminal()
                    .pty(true)
                    .width(cols)
                    .height(rows)
                    .onOutput(bridge::writeToBrowser)
                    .onClose(() -> closeBridge(bridge, "远端会话结束"))
                    .connect();
            bridge.operation = operation;
            log.info("[terminal-ssh] 会话建立 sessionId={} hostId={} {}:{} {}x{}",
                    wsSession.getId(), hostId, host.getServerHostAddress(), port, cols, rows);
        } catch (Exception e) {
            bridges.remove(wsSession.getId());
            closeHandle(handle);
            terminalSessionService.closeRecord(bridge.recordId, "PTY 打开失败");
            log.warn("[terminal-ssh] PTY 打开失败 hostId={}: {}", hostId, e.getMessage());
            reject(wsSession, "PTY 打开失败: " + e.getMessage());
        }
    }

    /**
     * 文本帧：仅接受 JSON 控制指令
     *
     * @param wsSession WS 会话
     * @param message   控制指令
     */
    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) {
        Bridge bridge = bridges.get(wsSession.getId());
        if (bridge == null) {
            return;
        }
        String payload = message.getPayload();
        if (payload == null || !payload.startsWith("{")) {
            return;
        }
        if (payload.contains("\"resize\"")) {
            int nextCols = readInt(payload, "cols", bridge.cols);
            int nextRows = readInt(payload, "rows", bridge.rows);
            if (nextCols <= 0 || nextRows <= 0) {
                return;
            }
            bridge.cols = nextCols;
            bridge.rows = nextRows;
            bridge.resize(nextCols, nextRows);
        }
    }

    /**
     * 二进制帧：浏览器键盘输入原样写入远端 伪终端
     *
     * @param wsSession WS 会话
     * @param message   输入字节
     */
    @Override
    protected void handleBinaryMessage(WebSocketSession wsSession, BinaryMessage message) {
        Bridge bridge = bridges.get(wsSession.getId());
        if (bridge == null || bridge.operation == null) {
            return;
        }
        ByteBuffer buffer = message.getPayload();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        try {
            bridge.operation.sendRaw(new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("[terminal-ssh] 输入写入失败 sessionId={}: {}", wsSession.getId(), e.getMessage());
            closeBridge(bridge, "输入写入失败");
        }
    }

    /**
     * 连接关闭：断开 SSH 并闭环审计
     *
     * @param wsSession  WS 会话
     * @param status     关闭状态
     */
    @Override
    public void afterConnectionClosed(WebSocketSession wsSession, CloseStatus status) {
        Bridge bridge = bridges.get(wsSession.getId());
        if (bridge != null) {
            closeBridge(bridge, "浏览器断开: " + status);
        }
    }

    /**
     * 传输异常：统一走关闭流程
     *
     * @param wsSession WS 会话
     * @param exception 异常
     */
    @Override
    public void handleTransportError(WebSocketSession wsSession, Throwable exception) {
        log.warn("[terminal-ssh] WS 传输异常 sessionId={} {}", wsSession.getId(), exception.toString());
        Bridge bridge = bridges.get(wsSession.getId());
        if (bridge != null) {
            closeBridge(bridge, "传输异常");
        }
    }

    /**
     * 关闭桥接：幂等清理 SSH 句柄、审计记录与注册表
     *
     * @param bridge 桥接上下文
     * @param reason 原因
     */
    private void closeBridge(Bridge bridge, String reason) {
        if (bridge.closed) {
            return;
        }
        bridge.closed = true;
        bridges.remove(bridge.wsSession.getId());
        if (bridge.operation != null) {
            try {
                bridge.operation.close();
            } catch (Exception e) {
                log.debug("[terminal-ssh] 关闭 PTY 异常: {}", e.getMessage());
            }
        }
        closeHandle(bridge.handle);
        terminalSessionService.closeRecord(bridge.recordId, reason);
        try {
            bridge.wsSession.close(CloseStatus.NORMAL);
        } catch (IOException e) {
            log.debug("[terminal-ssh] 关闭 WS 失败 sessionId={}", bridge.wsSession.getId());
        }
        log.info("[terminal-ssh] 会话结束 sessionId={} reason={}", bridge.wsSession.getId(), reason);
    }

    /**
     * 关闭 SSH 句柄（同时清理临时私钥文件）
     *
     * @param handle SSH 句柄
     */
    private void closeHandle(SshClients.Handle handle) {
        try {
            handle.close();
        } catch (Exception e) {
            log.debug("[terminal-ssh] 关闭 SSH 句柄异常: {}", e.getMessage());
        }
    }

    /**
     * 拒绝连接并发送原因后关闭 WS
     *
     * @param wsSession WS 会话
     * @param reason    原因
     */
    private void reject(WebSocketSession wsSession, String reason) {
        log.warn("[terminal-ssh] 拒绝直连 sessionId={} reason={}", wsSession.getId(), reason);
        try {
            wsSession.sendMessage(new TextMessage("{\"c\":\"error\",\"msg\":\"" + escape(reason) + "\"}"));
        } catch (IOException ignored) {
            // 发送失败不影响关闭动作
        }
        try {
            wsSession.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException e) {
            log.debug("[terminal-ssh] 关闭被拒连接失败 sessionId={}", wsSession.getId());
        }
    }

    /**
     * 解析 WS 握手 URL 的查询参数
     *
     * @param wsSession WS 会话
     * @return 参数表
     */
    private Map<String, String> parseQuery(WebSocketSession wsSession) {
        URI uri = wsSession.getUri();
        Map<String, String> params = new java.util.HashMap<>();
        if (uri == null || uri.getRawQuery() == null) {
            return params;
        }
        for (String pair : uri.getRawQuery().split("&")) {
            int idx = pair.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            params.put(java.net.URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8),
                    java.net.URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8));
        }
        return params;
    }

    /**
     * 提取客户端 IP
     *
     * @param wsSession WS 会话
     * @return IP 字符串
     */
    private String resolveClientIp(WebSocketSession wsSession) {
        var remoteAddress = wsSession.getRemoteAddress();
        return remoteAddress == null ? "unknown" : remoteAddress.getAddress().getHostAddress();
    }

    /**
     * 安全解析长整型参数
     *
     * @param value 原始字符串
     * @return 长整型或 空
     */
    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 安全解析正整型参数
     *
     * @param value      原始字符串
     * @param defaultVal 兜底值
     * @return 正整型
     */
    private int parsePositiveInt(String value, int defaultVal) {
        if (value == null || value.isBlank()) {
            return defaultVal;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : defaultVal;
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    /**
     * 从控制指令 JSON 中取整数字段（仅尺寸两个字段，无需引入 JSON 解析器）
     *
     * @param json 原始 JSON
     * @param key  字段名
     * @param def  缺省值
     * @return 字段值
     */
    private int readInt(String json, String key, int def) {
        int idx = json.indexOf("\"" + key + "\"");
        if (idx < 0) {
            return def;
        }
        int colon = json.indexOf(':', idx);
        if (colon < 0) {
            return def;
        }
        int start = colon + 1;
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\t')) {
            start++;
        }
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        if (end == start) {
            return def;
        }
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * 转义错误信息中的 JSON 特殊字符
     *
     * @param value 原始文本
     * @return 转义后文本
     */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }

    /**
     * 单条直连会话的桥接上下文
     */
    private static final class Bridge {

        /**
         * SSH 句柄
        */
        private final SshClients.Handle handle;

        /**
         * 浏览器 WS 会话
        */
        private final WebSocketSession wsSession;

        /**
         * 远端 PTY 操作，可空（握手未完成时）
        */
        private volatile SshClient.TerminalOperation operation;

        /**
         * 审计记录 标识，可空
        */
        private volatile Long recordId;

        /**
         * 当前列数
        */
        private volatile int cols = DEFAULT_COLS;

        /**
         * 当前行数
        */
        private volatile int rows = DEFAULT_ROWS;

        /**
         * 是否已关闭（幂等标记）
        */
        private volatile boolean closed;

        /**
         * 构造桥接上下文
         *
         * @param handle    SSH 句柄
         * @param wsSession 浏览器 WS 会话
         */
        private Bridge(SshClients.Handle handle, WebSocketSession wsSession) {
            this.handle = handle;
            this.wsSession = wsSession;
        }

        /**
         * 远端输出回推浏览器（二进制帧，UTF-8 编码）
         *
         * @param data 远端输出
         */
        private void writeToBrowser(String data) {
            if (closed || !wsSession.isOpen()) {
                return;
            }
            try {
                synchronized (wsSession) {
                    wsSession.sendMessage(new BinaryMessage(data.getBytes(StandardCharsets.UTF_8)));
                }
            } catch (IOException | IllegalStateException e) {
                log.debug("[terminal-ssh] 输出回推失败 sessionId={}: {}", wsSession.getId(), e.getMessage());
            }
        }

        /**
         * 调整远端 伪终端 尺寸
         *
         * @param nextCols 列数
         * @param nextRows 行数
         */
        private void resize(int nextCols, int nextRows) {
            SshClient.TerminalOperation current = operation;
            if (current == null || closed) {
                return;
            }
            try {
                org.apache.sshd.client.channel.ChannelShell channel = current.getChannel();
                if (channel != null && channel.isOpen()) {
                    channel.sendWindowChange(nextCols, nextRows);
                }
            } catch (Exception e) {
                log.debug("[terminal-ssh] 尺寸调整失败 sessionId={}: {}", wsSession.getId(), e.getMessage());
            }
        }
    }
}
