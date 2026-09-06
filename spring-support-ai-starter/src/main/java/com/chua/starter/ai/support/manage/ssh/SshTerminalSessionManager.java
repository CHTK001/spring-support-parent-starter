package com.chua.starter.ai.support.manage.ssh;

import com.chua.starter.ai.support.manage.ApiWebSocketPushService;
import com.chua.starter.ai.support.manage.store.SysAiSshHost;
import com.chua.ssh.support.client.SshClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSH 交互式终端会话管理。
 *
 * <p>基于 {@link SshClient#terminal()}（MINA SSHD PTY 实时终端）维护一组
 * 活跃的远程主机交互终端：创建时连接远端 shell，把输出通过
 * {@code terminal.output} WebSocket 事件推给前端 XtermView，输入/尺寸经
 * REST 端点转发到远端 PTY。scrollback 按 base64 缓冲，供断线重放。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SshTerminalSessionManager {

    /**
     * WS 事件推送服务
     */
    private final ApiWebSocketPushService pushService;

    /**
     * 活跃 SSH 终端会话：terminal_id → 会话包装
     */
    private final Map<String, SshTerminalSession> sessions = new ConcurrentHashMap<>();

    /**
     * scrollback 缓冲上限（字符），超出后丢弃旧输出，避免内存膨胀。
     */
    private static final int MAX_SCROLLBACK_CHARS = 64 * 1024;

    /**
     * 创建一个连接指定主机的交互式 SSH 终端。
     *
     * @param host     主机档案（含真实凭据）
     * @param terminalId 终端 ID（由调用方生成，如 UUIDv7）
     * @param cols     初始列数
     * @param rows     初始行数
     * @return 会话包装；连接失败抛异常
     */
    public SshTerminalSession open(SysAiSshHost host, String terminalId, int cols, int rows) throws Exception {
        SshClient client = buildConnectedClient(host);
        StringBuilder scrollback = new StringBuilder();
        SshTerminalSession session = new SshTerminalSession();
        session.terminalId = terminalId;
        session.sshHostId = host.getSshHostId();
        session.name = host.getName();
        session.client = client;
        session.scrollback = scrollback;

        SshClient.TerminalOperation op = client.terminal()
                .pty(true)
                .width(Math.max(cols, 20))
                .height(Math.max(rows, 5))
                .onOutput(data -> {
                    // 输出：缓冲 + WS 推送（base64）
                    synchronized (scrollback) {
                        scrollback.append(data);
                        int excess = scrollback.length() - MAX_SCROLLBACK_CHARS;
                        if (excess > 0) {
                            scrollback.delete(0, excess);
                        }
                    }
                    pushService.push("terminal.output", Map.of(
                            "terminal_id", terminalId,
                            "data_b64", Base64.getEncoder().encodeToString(data.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    ));
                })
                .onClose(() -> {
                    session.lastStatus = "exited";
                    pushService.push("terminal.exit", Map.of(
                            "terminal_id", terminalId,
                            "exit_code", 0
                    ));
                    sessions.remove(terminalId);
                })
                .connect();

        session.operation = op;
        session.lastStatus = "running";
        session.updatedAt = System.currentTimeMillis();
        sessions.put(terminalId, session);
        log.info("[SshTerminal] 已开启交互终端: {} @ {}:{} ({}x{})", host.getName(), host.getHost(), host.getPort(), cols, rows);
        return session;
    }

    /**
     * 发送原始输入到远端 PTY（按键/粘贴，UTF-8 base64 解码后透传）。
     *
     * @param terminalId 终端 ID
     * @param dataB64    base64 编码的输入字节
     * @return 是否成功
     */
    public boolean input(String terminalId, String dataB64) {
        SshTerminalSession session = sessions.get(terminalId);
        if (session == null || session.operation == null || !session.operation.isConnected()) {
            return false;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(dataB64);
            session.operation.sendRaw(new String(raw, java.nio.charset.StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) {
            log.warn("[SshTerminal] 输入转发失败: {} - {}", terminalId, e.getMessage());
            return false;
        }
    }

    /**
     * 调整远端 PTY 尺寸（通过反射调用 ChannelShell 的 window-change）。
     *
     * @param terminalId 终端 ID
     * @param cols       列数
     * @param rows       行数
     * @return 是否成功
     */
    public boolean resize(String terminalId, int cols, int rows) {
        SshTerminalSession session = sessions.get(terminalId);
        if (session == null || session.operation == null) {
            return false;
        }
        try {
            // TerminalOperation 内部 channel 是私有的，反射取出来调 window-change
            Field channelField = session.operation.getClass().getDeclaredField("channel");
            channelField.setAccessible(true);
            Object channel = channelField.get(session.operation);
            if (channel instanceof org.apache.sshd.client.channel.PtyCapableChannelSession pty) {
                pty.sendWindowChange(Math.max(cols, 20), Math.max(rows, 5));
            }
            return true;
        } catch (Exception e) {
            log.warn("[SshTerminal] 尺寸调整失败: {} - {}", terminalId, e.getMessage());
            return false;
        }
    }

    /**
     * 关闭 SSH 终端会话（断开远端连接并清理）。
     *
     * @param terminalId 终端 ID
     */
    public void close(String terminalId) {
        SshTerminalSession session = sessions.remove(terminalId);
        if (session == null) {
            return;
        }
        try {
            if (session.operation != null) {
                session.operation.close();
            }
            if (session.client != null) {
                session.client.close();
            }
        } catch (Exception e) {
            log.debug("[SshTerminal] 关闭异常: {}", e.getMessage());
        }
        session.lastStatus = "exited";
        pushService.push("terminal.exit", Map.of(
                "terminal_id", terminalId,
                "exit_code", 0
        ));
    }

    /**
     * 查询会话（用于 scrollback 重放）。
     *
     * @param terminalId 终端 ID
     * @return 会话包装，不存在返回 null
     */
    public SshTerminalSession get(String terminalId) {
        return sessions.get(terminalId);
    }

    /**
     * 会话是否存在且运行中。
     */
    public boolean isRunning(String terminalId) {
        SshTerminalSession session = sessions.get(terminalId);
        return session != null && session.operation != null && session.operation.isConnected();
    }

    // ==================== 会话载体 ====================

    /**
     * 单个 SSH 终端会话的运行时载体。
     */
    public static class SshTerminalSession {
        /**
         * 终端 ID
         */
        public String terminalId;
        /**
         * 关联主机 ID
         */
        public String sshHostId;
        /**
         * 显示名称
         */
        public String name;
        /**
         * 底层 SSH 客户端
         */
        public SshClient client;
        /**
         * PTY 终端操作
         */
        public SshClient.TerminalOperation operation;
        /**
         * scrollback 缓冲（同步访问）
         */
        public StringBuilder scrollback;
        /**
         * 当前状态
         */
        public String lastStatus = "running";
        /**
         * 最后更新时间
         */
        public long updatedAt = System.currentTimeMillis();

        /**
         * 以 base64 返回 scrollback 快照。
         */
        public String scrollbackB64() {
            synchronized (scrollback) {
                if (scrollback.length() == 0) {
                    return null;
                }
                return Base64.getEncoder().encodeToString(scrollback.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 装配并建立真实 SSH 连接（与 SshConnectionManager 一致：password / key / certificate）。
     */
    private SshClient buildConnectedClient(SysAiSshHost host) throws Exception {
        String authType = host.getAuthType() == null ? "password" : host.getAuthType();
        String address = host.getHost();
        int port = host.getPort() == null || host.getPort() <= 0 ? 22 : host.getPort();
        String username = host.getUsername();

        Path tempKey = null;
        boolean hasKey = "key".equals(authType) || "certificate".equals(authType);
        try {
            var builder = SshClient.builder()
                    .host(address)
                    .port(port)
                    .username(username)
                    .connectTimeout(8)
                    .sessionTimeout(8);
            if (hasKey && host.getPrivateKey() != null && !host.getPrivateKey().isBlank()) {
                tempKey = Files.createTempFile("ai-ssh-key-", ".pem");
                Files.writeString(tempKey, host.getPrivateKey());
                builder.privateKey(tempKey.toString());
                if (host.getPassphrase() != null && !host.getPassphrase().isBlank()) {
                    builder.password(host.getPassphrase());
                }
            } else {
                builder.password(host.getPassword() == null ? "" : host.getPassword());
            }
            SshClient client = builder.build();
            client.connect();
            return client;
        } catch (Exception e) {
            if (tempKey != null) {
                try {
                    Files.deleteIfExists(tempKey);
                } catch (IOException ignored) {
                    // ignore
                }
            }
            throw e;
        }
    }
}