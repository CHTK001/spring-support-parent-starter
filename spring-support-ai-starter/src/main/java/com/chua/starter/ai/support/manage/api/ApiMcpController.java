package com.chua.starter.ai.support.manage.api;

import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.manage.store.SysAiConversationStore;
import com.chua.starter.ai.support.manage.ApiWebSocketPushService;
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
 * MCP 服务控制器，替代 nomicore 的 {@code /api/mcp/*} 契约。
 *
 * <p>维护 MCP 服务器档案（{@code mcp_server_id} 主键）、批量导入、启停切换、连接测试与
 * OAuth 握手路由。当前为进程内内存实现。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
@Slf4j
public class ApiMcpController {

    /**
     * 主键生成与基础存储工具
     */
    private final SysAiConversationStore conversationStore;

    /**
     * WS 事件推送服务
     */
    private final ApiWebSocketPushService pushService;

    /**
     * MCP 真实连接服务（SDK 连接 + 工具列举）
     */
    private final McpConnectionService mcpConnectionService;

    /**
     * MCP 服务器档案：mcp_server_id → 服务器
     */
    private final Map<String, Map<String, Object>> servers = new ConcurrentHashMap<>();

    @GetMapping("/servers")
    public ApiResult<List<Map<String, Object>>> listServers() {
        return ApiResult.ok(new ArrayList<>(servers.values()));
    }

    @PostMapping("/servers")
    public ApiResult<Map<String, Object>> createServer(@RequestBody Map<String, Object> body) {
        String serverId = conversationStore.newId();
        Map<String, Object> server = newLinked(body);
        server.put("mcp_server_id", serverId);
        server.putIfAbsent("name", "未命名 MCP");
        server.putIfAbsent("transport", "stdio");
        server.putIfAbsent("enabled", true);
        server.putIfAbsent("original_json", "");
        server.putIfAbsent("builtin", false);
        server.putIfAbsent("last_test_status", "disconnected");
        long now = System.currentTimeMillis();
        server.put("created_at", now);
        server.put("updated_at", now);
        servers.put(serverId, server);
        return ApiResult.ok(server);
    }

    @PostMapping("/servers/import")
    public ApiResult<List<Map<String, Object>>> importServers(@RequestBody Map<String, Object> body) {
        List<Map<String, Object>> created = new ArrayList<>();
        Object raw = body.get("servers");
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                if (item instanceof Map) {
                    Map<String, Object> result = createServer((Map<String, Object>) item).getData();
                    created.add(result);
                }
            }
        }
        return ApiResult.ok(created);
    }

    @PutMapping("/servers/{serverId}")
    public ApiResult<Map<String, Object>> updateServer(@PathVariable String serverId,
                                                        @RequestBody Map<String, Object> body) {
        Map<String, Object> server = requireServer(serverId);
        body.forEach((k, v) -> {
            if (!"mcp_server_id".equals(k) && !"created_at".equals(k)) {
                server.put(k, v);
            }
        });
        server.put("updated_at", System.currentTimeMillis());
        return ApiResult.ok(server);
    }

    @DeleteMapping("/servers/{serverId}")
    public ApiResult<Void> deleteServer(@PathVariable String serverId) {
        requireServer(serverId);
        servers.remove(serverId);
        return ApiResult.ok();
    }

    @PostMapping("/servers/{serverId}/toggle")
    public ApiResult<Map<String, Object>> toggleServer(@PathVariable String serverId) {
        Map<String, Object> server = requireServer(serverId);
        Object enabled = server.get("enabled");
        boolean next = !(enabled instanceof Boolean && (Boolean) enabled);
        server.put("enabled", next);
        server.put("updated_at", System.currentTimeMillis());
        return ApiResult.ok(server);
    }

    @GetMapping("/agent-configs")
    public ApiResult<List<Map<String, Object>>> agentConfigs() {
        return ApiResult.ok(new ArrayList<>());
    }

    @PostMapping("/test-connection")
    public ApiResult<Map<String, Object>> testConnection(@RequestBody Map<String, Object> body) {
        String name = body.get("name") == null ? "" : String.valueOf(body.get("name"));
        @SuppressWarnings("unchecked")
        Map<String, Object> transport =
                body.get("transport") instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
        return ApiResult.ok(mcpConnectionService.testConnection(name, transport));
    }

    @PostMapping("/oauth/check-status")
    public ApiResult<Map<String, Object>> oauthCheckStatus(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("authenticated", false);
        return ApiResult.ok(result);
    }

    @PostMapping("/oauth/login")
    public ApiResult<Map<String, Object>> oauthLogin(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("error", "内存后端未实现 MCP OAuth 登录");
        return ApiResult.ok(result);
    }

    @PostMapping("/oauth/logout")
    public ApiResult<Void> oauthLogout(@RequestBody Map<String, Object> body) {
        return ApiResult.ok();
    }

    @GetMapping("/oauth/authenticated")
    public ApiResult<List<String>> oauthAuthenticated() {
        return ApiResult.ok(new ArrayList<>());
    }

    private Map<String, Object> newLinked(Map<String, Object> source) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (source != null) {
            map.putAll(source);
        }
        return map;
    }

    private Map<String, Object> requireServer(String serverId) {
        Map<String, Object> server = servers.get(serverId);
        if (server == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "MCP 服务器不存在: " + serverId);
        }
        return server;
    }
}
