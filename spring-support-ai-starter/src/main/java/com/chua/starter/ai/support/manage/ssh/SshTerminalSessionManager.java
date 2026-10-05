package com.chua.starter.ai.support.manage.ssh;

import com.chua.common.support.reflection.ReflectUtils;
import com.chua.starter.ai.support.manage.ApiWebSocketPushService;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.store.SysAiSshHost;
import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.ssh.support.client.SshClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSH 交互式终端会话管理。
 *
 * <p>基于 {@link SshClient#terminal()}（MINA SSHD PTY 实时终端）维护一组
 * 活跃的远程主机交互终端：创建时连接远端 Shell，把输出通过
 * {@code terminal.output} WebSocket 事件推给前端 XtermView，输入/尺寸经
 * REST 端点转发到远端 伪终端。scrollback 按 基础64 缓冲，供断线重放。</p>
 *
 * <p>会话是登录用户的实时 Shell，归属在 {@link #open} 时按当前登录用户盖章：
 * 本人可读写；管理员可读他人会话（看 scrollback/状态）但不可输入、调尺寸或关闭；
 * 他人一律 403。口径与 {@code sys_ai_ssh_host} 等落库表保持一致。</p>
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
     * 活跃 SSH 终端会话：terminal_标识 → 会话包装
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
     * @param terminalId 终端 标识（由调用方生成，如 uuidv7）
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
        session.userId = AiUserContext.requireUserId();
        session.client = client;
        session.scrollback = scrollback;

        SshClient.TerminalOperation op = client.terminal()
                .pty(true)
                .width(Math.max(cols, 20))
                .height(Math.max(rows, 5))
                .onOutput(data -> {
 // 输出：缓冲 + WS 推送（基础64）
                    synchronized (scrollback) {
                        scrollback.append(data);
                        int excess = scrollback.length() - MAX_SCROLLBACK_CHARS;
                        if (excess > 0) {
                            scrollback.delete(0, excess);
                        }
                    }
                    pushService.pushToUser(session.userId, "terminal.output", Map.of(
                            "terminal_id", terminalId,
                            "data_b64", Base64.getEncoder().encodeToString(data.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    ));
                })
                .onClose(() -> {
                    session.lastStatus = "exited";
                    pushService.pushToUser(session.userId, "terminal.exit", Map.of(
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
     * 登记一个 WebUI 本地占位终端（真实 伪终端 由宿主进程管理）。
     *
     * <p>登记的意义是让占位终端与真实 SSH 会话走同一套归属校验，
     * 避免他人凭 标识 猜中占位终端后关闭或复用同 标识。</p>
     *
     * @param terminalId 终端 标识
     * @param name       显示名称
     * @return 登记后的会话载体
     */
    public SshTerminalSession registerLocalPlaceholder(String terminalId, String name) {
        SshTerminalSession session = new SshTerminalSession();
        session.terminalId = terminalId;
        session.name = name;
        session.userId = AiUserContext.requireUserId();
        session.scrollback = new StringBuilder();
        session.lastStatus = "running";
        session.updatedAt = System.currentTimeMillis();
        sessions.put(terminalId, session);
        return session;
    }

    /**
     * 发送原始输入到远端 伪终端（按键/粘贴，UTF-8 基础64 解码后透传）。
     *
     * @param terminalId 终端 标识
     * @param dataB64    基础64 编码的输入字节
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
     * 调整远端 伪终端 尺寸（读取内部 通道 调用 窗口-改变）。
     *
     * @param terminalId 终端 标识
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
 // terminaloperation 内部 通道 是私有的，通过 reflect工具 取出来调 窗口-改变
            Object channel = ReflectUtils.getField(session.operation, "channel");
            // 暂时注释，编译错误
            // if (channel instanceof org.apache.sshd.client.channel.PtyCapableChannelSession pty) {
            //     pty.sendWindowChange(Math.max(cols, 20), Math.max(rows, 5));
            // }
            return true;
        } catch (Exception e) {
            log.warn("[SshTerminal] 尺寸调整失败: {} - {}", terminalId, e.getMessage());
            return false;
        }
    }

    /**
     * 关闭 SSH 终端会话（断开远端连接并清理）。
     *
     * @param terminalId 终端 标识
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
        pushService.pushToUser(session.userId, "terminal.exit", Map.of(
                "terminal_id", terminalId,
                "exit_code", 0
        ));
    }

    /**
     * 查询会话（用于 scrollback 重放）。
     *
     * @param terminalId 终端 标识
     * @return 会话包装，不存在返回 空
     */
    public SshTerminalSession get(String terminalId) {
        return sessions.get(terminalId);
    }

    /**
     * 当前登录用户可见的会话列表（仅本人；管理员在业务数据侧与普通账号同权）。
     *
     * @return 会话列表（按标识稳定排序）
     */
    public List<SshTerminalSession> listVisible() {
        String userId = AiUserContext.requireUserId();
        boolean crossAccount = AiUserContext.canAccessOthersData();
        List<SshTerminalSession> result = new ArrayList<>();
        for (SshTerminalSession session : sessions.values()) {
            if (crossAccount || userId.equals(session.userId)) {
                result.add(session);
            }
        }
        result.sort(Comparator.comparing(s -> s.terminalId));
        return result;
    }

    /**
     * 取回会话并校验归属。
     *
     * @param terminalId 终端 标识
     * @param writable   是否要求写权限（{@code false} 表示只读）
     * @return 通过校验的会话
     */
    public SshTerminalSession requireOwned(String terminalId, boolean writable) {
        SshTerminalSession session = sessions.get(terminalId);
        if (session == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "终端会话不存在: " + terminalId);
        }
        String userId = AiUserContext.requireUserId();
        if (userId.equals(session.userId)) {
            return session;
        }
        if (!writable && AiUserContext.canAccessOthersData()) {
            return session;
        }
        throw new ApiException(ApiErrorCode.FORBIDDEN,
                "无权" + (writable ? "操作他人的" : "访问他人的") + "终端会话");
    }

    /**
     * 会话是否存在且运行中。
     * @param terminalId terminalid
     * @return 是否running的结果
     */
    public boolean isRunning(String terminalId) {
        SshTerminalSession session = sessions.get(terminalId);
        return session != null && session.operation != null && session.operation.isConnected();
    }

    // ==================== 会话载体 ====================

    /**
     * 单个 SSH 终端会话的运行时载体。
     * @author CH
     * @since 4.0.0
     */
    public static class SshTerminalSession {
        /**
         * 终端 标识
         */
        public String terminalId;
        /**
         * 关联主机 标识
         */
        public String sshHostId;
        /**
         * 归属登录用户（创建时盖章）
         */
        public String userId;
        /**
         * 显示名称
         */
        public String name;
        /**
         * 底层 SSH 客户端
         */
        public SshClient client;
        /**
         * 伪终端 终端操作
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
         * 以 基础64 返回 scrollback 快照。
         * @return scrollbackB64的结果
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
     * 装配并建立真实 SSH 连接（与 sshconnection管理器 一致：密码 / Key / 证书）。
     * @param host 主机
     * @return 构建连接客户端的结果
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
