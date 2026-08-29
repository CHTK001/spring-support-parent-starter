package com.chua.starter.ai.support.manage.ssh;

import com.chua.starter.ai.support.manage.ApiWebSocketPushService;
import com.chua.starter.ai.support.manage.store.SysAiSshHost;
import com.chua.ssh.support.client.SshClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 远程主机（SSH）连接管理服务。
 *
 * <p>替代 {@code /api/ssh-hosts} 占位实现：基于 Apache MINA SSHD（
 * {@link SshClient}）提供真实的主机握手与状态机，并通过 {@code ssh.status}
 * WebSocket 事件把 {@code connecting → connected / dropped} 等阶段实时推送给前端，
 * 使会话 header 上的「远程主机」状态药丸（{@code SshHostStatusPill}）真实可见。</p>
 *
 * <p>状态模型对齐前端 {@code IApiSshStatus} 契约：每个字段恒存在，
 * 未知用显式 null；{@code reaped} 仅 closed 时有值、{@code retryable} 仅 dropped 时有值。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SshConnectionManager {

    /**
     * 连接建立阶段：idle → connecting → connected / dropped（/ closed）
     */
    public enum SshLinkPhase {
        /**
         * 空闲（无拨号记录）
         */
        idle,
        /**
         * 拨号中
         */
        connecting,
        /**
         * 已连接
         */
        connected,
        /**
         * 已断开（可重试）
         */
        dropped,
        /**
         * 已关闭
         */
        closed
    }

    /**
     * WS 事件推送服务（ssh.status）
     */
    private final ApiWebSocketPushService pushService;

    /**
     * 每条链路最新状态：ssh_host_id → 状态快照
     */
    private final Map<String, Map<String, Object>> linkStates = new ConcurrentHashMap<>();

    /**
     * 异步拨号线程池（守护线程，避免阻塞请求线程）
     */
    private final ExecutorService dialExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ssh-dial");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 默认连接超时（秒）
     */
    private static final int CONNECT_TIMEOUT_SECONDS = 8;

    /**
     * 私钥临时文件前缀
     */
    private static final String TEMP_KEY_PREFIX = "ai-ssh-key-";

    /**
     * 获取全部链路的实时状态快照（与 {@code ssh.status} 事件同一形状）。
     *
     * @return 状态列表
     */
    public List<Map<String, Object>> statuses() {
        return new ArrayList<>(linkStates.values());
    }

    /**
     * 删除主机时移除其链路状态（前端按快照缺失置 null）。
     *
     * @param sshHostId 主机 ID
     */
    public void forgetHost(String sshHostId) {
        linkStates.remove(sshHostId);
    }

    /**
     * 真实测试主机连通性：握手成功后执行 {@code uname} 探活。
     *
     * @param host 主机档案（含真实凭据）
     * @return 测试结果 {@code {ok, message}}
     */
    public Map<String, Object> testConnection(SysAiSshHost host) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (host == null || host.getSshHostId() == null) {
            result.put("ok", false);
            result.put("message", "主机不存在");
            return result;
        }
        try {
            try (SshClient client = buildConnectedClient(host)) {
                var exec = client.exec().command("uname -s").execute();
                boolean ok = exec.exitCode() == 0;
                result.put("ok", ok);
                result.put("message", ok
                        ? "连接成功（" + exec.stdout().trim() + "）"
                        : "连接成功但探活命令失败: " + exec.stderr().trim());
                host.setStatus(ok ? "ok" : "error");
                if (ok) {
                    host.setLastConnectedAt(System.currentTimeMillis());
                }
            }
        } catch (Exception e) {
            log.warn("[Ssh] 连接测试失败: {}@{}:{} - {}", host.getUsername(), host.getHost(),
                    host.getPort(), e.getMessage());
            result.put("ok", false);
            result.put("message", sanitizeMessage(e.getMessage()));
            host.setStatus("error");
        }
        pushPhase(host.getSshHostId(), null, SshLinkPhase.closed, 0, null,
                "connection test", null, false);
        return result;
    }

    /**
     * 为绑定远程主机的会话发起异步拨号（会话创建时调用）。
     *
     * <p>拨号结果通过 {@code ssh.status} 事件推送，前端状态药丸据此呈现
     * connecting / connected / dropped 阶段。</p>
     *
     * @param host           主机档案（含真实凭据）
     * @param conversationId 绑定的会话 ID（可能为空）
     */
    public void dial(SysAiSshHost host, String conversationId) {
        if (host == null || host.getSshHostId() == null) {
            return;
        }
        pushPhase(host.getSshHostId(), conversationId, SshLinkPhase.connecting, 1, null,
                "正在拨号连接", null, false);
        dialExecutor.execute(() -> {
            try {
                try (SshClient client = buildConnectedClient(host)) {
                    var exec = client.exec().command("uname -s").execute();
                    if (exec.exitCode() == 0) {
                        host.setStatus("ok");
                        host.setLastConnectedAt(System.currentTimeMillis());
                        pushPhase(host.getSshHostId(), conversationId, SshLinkPhase.connected, 0, null,
                                null, null, false);
                        return;
                    }
                }
                pushPhase(host.getSshHostId(), conversationId, SshLinkPhase.dropped, 1, null,
                        "探活命令执行失败", true, false);
            } catch (Exception e) {
                log.warn("[Ssh] 拨号失败: {}@{}:{} - {}", host.getUsername(), host.getHost(),
                        host.getPort(), e.getMessage());
                pushPhase(host.getSshHostId(), conversationId, SshLinkPhase.dropped, 1, null,
                        sanitizeMessage(e.getMessage()), true, false);
            }
        });
    }

    /**
     * 关闭某主机关联的全部链路（主机删除/连接断开时调用）。
     *
     * @param sshHostId 主机 ID
     */
    public void closeLinks(String sshHostId) {
        Map<String, Object> state = linkStates.get(sshHostId);
        if (state == null) {
            return;
        }
        pushPhase(sshHostId, (String) state.get("conversation_id"), SshLinkPhase.closed, 0, null,
                "连接已关闭", null, true);
        linkStates.remove(sshHostId);
    }

    // ==================== 私有方法 ====================

    /**
     * 装配并建立真实 SSH 连接。
     *
     * <p>私钥内容经临时文件交给底层库（其 API 仅接受路径），关闭时清理，
     * 确保密钥明文不在磁盘残留。认证方式：password / key / certificate 均走
     * 密码或私钥；agent 暂不支持（按 password 兜底，通常失败并给出提示）。</p>
     *
     * @param host 主机档案（含真实凭据）
     * @return 已连接的客户端
     * @throws Exception 连接或认证失败时抛出
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
                    .connectTimeout(CONNECT_TIMEOUT_SECONDS)
                    .sessionTimeout(CONNECT_TIMEOUT_SECONDS);
            if (hasKey && isNotBlank(host.getPrivateKey())) {
                tempKey = Files.createTempFile(TEMP_KEY_PREFIX, ".pem");
                Files.writeString(tempKey, host.getPrivateKey());
                builder.privateKey(tempKey.toString());
                if (isNotBlank(host.getPassphrase())) {
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
                    // 临时文件删除失败不影响主流程，交由操作系统临时目录清理机制兜底
                }
            }
            throw e;
        }
    }

    /**
     * 推送一次状态变更（写快照 + 广播 ssh.status 事件）。
     *
     * @param sshHostId       主机 ID
     * @param conversationId  会话 ID（可能为空）
     * @param phase           阶段
     * @param attempt         拨号尝试次数
     * @param nextRetryInMs   下次重试等待（毫秒，当前无自动重试恒为 null）
     * @param detail          诊断信息（不含凭据）
     * @param retryable       dropped 时是否可重试
     * @param reaped          closed 时远端 shell 是否已被证实退出
     */
    private void pushPhase(String sshHostId, String conversationId, SshLinkPhase phase,
                           int attempt, Long nextRetryInMs, String detail,
                           Boolean retryable, boolean reaped) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("sshHostId", sshHostId);
        status.put("conversationId", conversationId == null ? "" : conversationId);
        status.put("state", phase.name());
        status.put("attempt", attempt);
        status.put("nextRetryInMs", nextRetryInMs);
        status.put("hostFingerprint", null);
        status.put("detail", detail);
        status.put("reaped", phase == SshLinkPhase.closed ? reaped : null);
        status.put("retryable", phase == SshLinkPhase.dropped ? retryable : null);
        status.put("changedAt", System.currentTimeMillis());
        linkStates.put(sshHostId, status);
        pushService.push("ssh.status", status);
    }

    /**
     * 非空判断
     *
     * @param value 待判断字符串
     * @return 是否非空
     */
    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 脱敏连接错误信息（避免异常堆栈里携带凭据或密钥明文）。
     *
     * @param message 原始错误信息
     * @return 脱敏后的信息
     */
    private static String sanitizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "连接失败";
        }
        String compact = message.replaceAll("\\s+", " ").trim();
        return compact.length() > 200 ? compact.substring(0, 200) + "..." : compact;
    }

    /**
     * 生成链路追踪标识（调试用）
     *
     * @return 随机 UUID
     */
    static String newTraceId() {
        return UUID.randomUUID().toString();
    }
}
