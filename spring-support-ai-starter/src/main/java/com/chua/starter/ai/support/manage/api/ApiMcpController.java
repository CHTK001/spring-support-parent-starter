package com.chua.starter.ai.support.manage.api;

import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;

import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.manage.store.McpServerStore;
import com.chua.starter.ai.support.manage.store.SysAiConversationStore;
import com.chua.starter.ai.support.manage.ApiWebSocketPushService;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
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

/**
 * MCP 服务端管理控制器，兼容 nomicore 的 {@code /api/mcp/*} 路径。
 *
 * <p>维护 MCP 服务端的注册、配置、启停、连通性测试和认证状态。服务端注册表没有用户维度，
 * 由 {@code sys_ai_mcp_server} 持久化并共享给所有会话。</p>
 *
 * <p>隔离口径：MCP 服务器注册表没有用户维度（{@code sys_ai_mcp_server} 全表共享），列表里的 stdio
 * 命令由本机进程执行、启停结果对所有用户的会话同时生效，因此本控制器的全部写出口与连通性测试
 * （新建/导入/更新/删除/启停/test-connection/OAuth 登录登出/市场开关）仅管理员可用；
 * 读取出口（服务器列表、市场状态、已认证列表）保持开放，供普通用户渲染可用工具。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
@Slf4j
public class ApiMcpController {

    /**
     * MCP 市场分组名称：MCP 市场配置存储在参数档案中。
     */
    private static final String MCP_MARKET_GROUP = "mcp";

    /**
     * MCP 市场启用字段：true 表示启用，false 表示关闭。
     */
    private static final String MCP_MARKET_ENABLED_FIELD = "market_enabled";

    /**
     * 会话存储服务，用于生成服务端标识。
     */
    private final SysAiConversationStore conversationStore;

    /**
     * WebSocket 事件推送服务。
     */
    private final ApiWebSocketPushService pushService;

    /**
     * MCP 实例连接服务，负责 SDK 连接和连接状态维护。
     */
    private final McpConnectionService mcpConnectionService;

    /**
     * MCP 服务端配置持久化存储。
     */
    private final McpServerStore mcpServerStore;

    /**
     * 全局设置服务，用于读取和写入 MCP 市场开关。
     */
    private final SysAiGlobalSettingService globalSettingService;

    @GetMapping("/servers")
    /**
     * 列表服务端。
     * @return 列表服务端的结果
     */
    public ApiResult<List<Map<String, Object>>> listServers() {
        return ApiResult.ok(mcpServerStore.listAll());
    }

    @Permission("admin")
    @PostMapping("/servers")
    /**
     * 创建服务端。
     * @param body 主体
     * @return 创建服务端的结果
     */
    public ApiResult<Map<String, Object>> createServer(@RequestBody Map<String, Object> body) {
        String serverId = conversationStore.newId();
        Map<String, Object> server = newLinked(body);
        server.put("mcp_server_id", serverId);
        server.putIfAbsent("name", "未命名 MCP");
        server.putIfAbsent("transport", Map.of("type", "stdio"));
        server.putIfAbsent("enabled", true);
        server.putIfAbsent("original_json", "");
        server.putIfAbsent("builtin", false);
        server.putIfAbsent("last_test_status", "disconnected");
        long now = System.currentTimeMillis();
        server.put("created_at", now);
        server.put("updated_at", now);
        return ApiResult.ok(mcpServerStore.insert(server));
    }

    @Permission("admin")
    @PostMapping("/servers/import")
    /**
     * 导入服务端。
     * @param body 主体
     * @return 导入服务端的结果
     */
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

    @Permission("admin")
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
        return ApiResult.ok(mcpServerStore.update(server));
    }

    @Permission("admin")
    @DeleteMapping("/servers/{serverId}")
    /**
     * 删除服务端。
     * @param serverId 服务端标识
     * @return 删除服务端的结果
     */
    public ApiResult<Void> deleteServer(@PathVariable String serverId) {
        /**
         * require服务端。
         * @param serverId 服务端标识
         */
        requireServer(serverId);
        mcpServerStore.delete(serverId);
        return ApiResult.ok();
    }

    @Permission("admin")
    @PostMapping("/servers/{serverId}/toggle")
    /**
     * toggle服务端。
     * @param serverId 服务端标识
     * @return toggle服务端的结果
     */
    public ApiResult<Map<String, Object>> toggleServer(@PathVariable String serverId) {
        Map<String, Object> server = requireServer(serverId);
        Object enabled = server.get("enabled");
        boolean next = !(enabled instanceof Boolean && (Boolean) enabled);
        server.put("enabled", next);
        server.put("updated_at", System.currentTimeMillis());
        return ApiResult.ok(mcpServerStore.update(server));
    }

    @GetMapping("/agent-configs")
    /**
     * Agent配置。
     * @return Agent配置的结果
     */
    public ApiResult<List<Map<String, Object>>> agentConfigs() {
        return ApiResult.ok(new ArrayList<>());
    }

    @Permission("admin")
    @PostMapping("/test-connection")
    /**
     * 测试connection。
     * @param body 主体
     * @return 测试connection的结果
     */
    public ApiResult<Map<String, Object>> testConnection(@RequestBody Map<String, Object> body) {
        String name = body.get("name") == null ? "" : String.valueOf(body.get("name"));
        @SuppressWarnings("unchecked")
        Map<String, Object> transport =
                body.get("transport") instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
        Map<String, Object> result = mcpConnectionService.testConnection(name, transport);

        // 对已存在的服务器，回写本次连接状态（成功 connected / 失败 failed）与连接时间
        Object idObj = body.get("mcp_server_id");
        if (idObj != null) {
            Map<String, Object> existing = mcpServerStore.get(String.valueOf(idObj));
            if (existing != null) {
                boolean ok = Boolean.TRUE.equals(result.get("success"));
                long now = System.currentTimeMillis();
                existing.put("last_test_status", ok ? "connected" : "failed");
                if (ok) {
                    existing.put("last_connected", now);
                }
                existing.put("updated_at", now);
                mcpServerStore.update(existing);
            }
        }
        return ApiResult.ok(result);
    }

    @PostMapping("/oauth/check-status")
    /**
     * OAuth检查状态。
     * @param body 主体
     * @return oauth检查状态的结果
     */
    public ApiResult<Map<String, Object>> oauthCheckStatus(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("authenticated", false);
        return ApiResult.ok(result);
    }

    @Permission("admin")
    @PostMapping("/oauth/login")
    /**
     * oauthlogin。
     * @param body 主体
     * @return oauthLogin的结果
     */
    public ApiResult<Map<String, Object>> oauthLogin(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("error", "内存后端未实现 MCP OAuth 登录");
        return ApiResult.ok(result);
    }

    @Permission("admin")
    @PostMapping("/oauth/logout")
    /**
     * oauthlogout。
     * @param body 主体
     * @return oauthLogout的结果
     */
    public ApiResult<Void> oauthLogout(@RequestBody Map<String, Object> body) {
        return ApiResult.ok();
    }

    @GetMapping("/oauth/authenticated")
    /**
     * oauthauthenticated。
     * @return oauthAuthenticated的结果
     */
    public ApiResult<List<String>> oauthAuthenticated() {
        return ApiResult.ok(new ArrayList<>());
    }

    /**
     * 启用 MCP 市场。
     * <p>将 MCP 市场启用状态持久化到 {@code sys_ai_global_setting}（分组 mcp，字段 market_enabled）。</p>
     *
     * @return 成功信封
     */
    @Permission("admin")
    @PostMapping("/market/enable")
    public ApiResult<Void> mcpMarketEnable() {
        globalSettingService.upsertValue(MCP_MARKET_GROUP, MCP_MARKET_ENABLED_FIELD, "true", "MCP 市场是否启用");
        log.info("[mcp] market enabled");
        return ApiResult.ok();
    }

    /**
     * 禁用 MCP 市场。
     * <p>将 MCP 市场禁用状态持久化到 {@code sys_ai_global_setting}（分组 mcp，字段 market_enabled）。</p>
     *
     * @return 成功信封
     */
    @Permission("admin")
    @PostMapping("/market/disable")
    public ApiResult<Void> mcpMarketDisable() {
        globalSettingService.upsertValue(MCP_MARKET_GROUP, MCP_MARKET_ENABLED_FIELD, "false", "MCP 市场是否启用");
        log.info("[mcp] market disabled");
        return ApiResult.ok();
    }

    /**
     * 查询 MCP 市场启用状态。
     *
     * @return 市场启用状态（enabled=true 表示市场开启；默认开启）
     */
    @GetMapping("/market/status")
    public ApiResult<Map<String, Object>> mcpMarketStatus() {
        boolean enabled = globalSettingService.getBoolean(MCP_MARKET_GROUP, MCP_MARKET_ENABLED_FIELD, true);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", enabled);
        return ApiResult.ok(result);
    }

    /**
     * 新链接。
     * @param source 源
     * @return 新链接的结果
     */
    private Map<String, Object> newLinked(Map<String, Object> source) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (source != null) {
            map.putAll(source);
        }
        return map;
    }

    /**
     * require服务端。
     * @param serverId 服务端标识
     * @return require服务端的结果
     */
    private Map<String, Object> requireServer(String serverId) {
        Map<String, Object> server = mcpServerStore.get(serverId);
        if (server == null) {
            /**
             * api异常。
             * @param ApiErrorCode.NOT_FOUND api错误编码.notfound
             * @param serverId 服务端标识
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.NOT_FOUND, "MCP 服务器不存在: " + serverId);
        }
        return server;
    }
}
