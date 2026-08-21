package com.chua.starter.ai.support.nomifun.api;

import com.chua.starter.ai.support.nomifun.common.ApiErrorCode;
import com.chua.starter.ai.support.nomifun.common.ApiException;
import com.chua.starter.ai.support.nomifun.common.ApiResult;
import com.chua.starter.ai.support.nomifun.store.ConversationStore;
import com.chua.starter.ai.support.nomifun.ApiWebSocketPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSH 主机簿控制器，替代 nomicore 的 {@code /api/ssh-hosts} 契约。
 *
 * <p>维护可复用的远程主机连接档案（{@code ssh_host_id} 主键），密钥字段写回时脱敏为
 * {@code "***"}；链接实时状态通过 {@code ssh.status} 事件推送。当前为进程内内存实现。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/api/ssh-hosts")
@RequiredArgsConstructor
@Slf4j
public class ApiSshController {

    /**
     * 主键生成与基础存储工具
     */
    private final ConversationStore conversationStore;

    /**
     * WS 事件推送服务
     */
    private final ApiWebSocketPushService pushService;

    /**
     * 主机档案：ssh_host_id → 主机
     */
    private final Map<String, Map<String, Object>> hosts = new ConcurrentHashMap<>();

    @GetMapping
    public ApiResult<List<Map<String, Object>>> list() {
        return ApiResult.ok(new ArrayList<>(hosts.values()));
    }

    @GetMapping("/{sshHostId}")
    public ApiResult<Map<String, Object>> get(@PathVariable String sshHostId) {
        return ApiResult.ok(requireHost(sshHostId));
    }

    @PostMapping
    public ApiResult<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        String hostId = conversationStore.newId();
        Map<String, Object> host = new LinkedHashMap<>();
        host.put("ssh_host_id", hostId);
        host.put("name", body.getOrDefault("name", "未命名主机"));
        host.put("host", body.get("host"));
        host.put("port", body.getOrDefault("port", 22));
        host.put("username", body.get("username"));
        host.put("auth_type", body.getOrDefault("auth_type", "password"));
        host.put("password", mask((String) body.get("password")));
        host.put("private_key", mask((String) body.get("private_key")));
        host.put("passphrase", mask((String) body.get("passphrase")));
        host.put("certificate", mask((String) body.get("certificate")));
        host.put("sudo_password", mask((String) body.get("sudo_password")));
        host.put("host_fingerprint", body.get("host_fingerprint"));
        host.put("status", "unknown");
        long now = System.currentTimeMillis();
        host.put("last_connected_at", null);
        host.put("created_at", now);
        host.put("updated_at", now);
        hosts.put(hostId, host);
        return ApiResult.ok(host);
    }

    @PutMapping("/{sshHostId}")
    public ApiResult<Map<String, Object>> update(@PathVariable String sshHostId,
                                                  @RequestBody Map<String, Object> body) {
        Map<String, Object> host = requireHost(sshHostId);
        putIfPresent(host, body, "name");
        putIfPresent(host, body, "host");
        putIfPresent(host, body, "port");
        putIfPresent(host, body, "username");
        putIfPresent(host, body, "auth_type");
        putIfPresent(host, body, "host_fingerprint");
        if (body.containsKey("password")) {
            host.put("password", mask((String) body.get("password")));
        }
        if (body.containsKey("private_key")) {
            host.put("private_key", mask((String) body.get("private_key")));
        }
        if (body.containsKey("passphrase")) {
            host.put("passphrase", mask((String) body.get("passphrase")));
        }
        if (body.containsKey("certificate")) {
            host.put("certificate", mask((String) body.get("certificate")));
        }
        if (body.containsKey("sudo_password")) {
            host.put("sudo_password", mask((String) body.get("sudo_password")));
        }
        host.put("updated_at", System.currentTimeMillis());
        return ApiResult.ok(host);
    }

    @DeleteMapping("/{sshHostId}")
    public ApiResult<Void> delete(@PathVariable String sshHostId) {
        requireHost(sshHostId);
        hosts.remove(sshHostId);
        return ApiResult.ok();
    }

    @PostMapping("/{sshHostId}/test-connection")
    public ApiResult<Map<String, Object>> testConnection(@PathVariable String sshHostId) {
        Map<String, Object> host = requireHost(sshHostId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", false);
        result.put("message", "内存后端未接入真实 SSH，仅返回占位连接测试结果");
        host.put("status", "error");
        host.put("last_connected_at", System.currentTimeMillis());
        pushStatus(host, "closed", false, false, false);
        return ApiResult.ok(result);
    }

    @GetMapping("/statuses")
    public ApiResult<List<Map<String, Object>>> statuses() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> host : hosts.values()) {
            list.add(buildStatus(host, "idle", null, null, false));
        }
        return ApiResult.ok(list);
    }

    @GetMapping("/import-candidates")
    public ApiResult<Map<String, Object>> importCandidates() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("candidates", new ArrayList<Map<String, Object>>());
        return ApiResult.ok(result);
    }

    @PostMapping("/import")
    public ApiResult<Map<String, Object>> importHosts(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", new ArrayList<Map<String, Object>>());
        return ApiResult.ok(result);
    }

    private void pushStatus(Map<String, Object> host, String state, Boolean reaped, Boolean retryable, boolean detail) {
        Map<String, Object> status = buildStatus(host, state, reaped, retryable, detail);
        pushService.push("ssh.status", status);
    }

    private Map<String, Object> buildStatus(Map<String, Object> host, String state, Boolean reaped,
                                            Boolean retryable, boolean withDetail) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("ssh_host_id", host.get("ssh_host_id"));
        status.put("conversation_id", "");
        status.put("state", state);
        status.put("attempt", 0);
        status.put("next_retry_in_ms", null);
        status.put("host_fingerprint", host.get("host_fingerprint"));
        status.put("detail", withDetail ? "内存后端占位状态" : null);
        status.put("reaped", reaped);
        status.put("retryable", retryable);
        status.put("changed_at", System.currentTimeMillis());
        return status;
    }

    private String mask(String value) {
        return (value == null || value.isEmpty()) ? null : "***";
    }

    private void putIfPresent(Map<String, Object> host, Map<String, Object> body, String key) {
        if (body.containsKey(key)) {
            host.put(key, body.get(key));
        }
    }

    private Map<String, Object> requireHost(String sshHostId) {
        Map<String, Object> host = hosts.get(sshHostId);
        if (host == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "SSH 主机不存在: " + sshHostId);
        }
        return host;
    }
}
