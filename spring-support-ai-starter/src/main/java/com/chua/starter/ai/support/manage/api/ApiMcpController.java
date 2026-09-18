package com.chua.starter.ai.support.manage.api;

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
* MCP �������������� nomicore �� {@code /api/mcp/*} ��Լ��
*
* <p>ά�� MCP ������������{@code mcp_server_id} ���������������롢��ͣ�л������Ӳ�����
* o认证 ����·�ɡ������־û��� {@code sys_ai_mcp_server} ����MySQL����</p>
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
    * MCP �г����ÿ���飺MCP �г�����״̬�洢�ڴ˷����档
    */
    private static final String MCP_MARKET_GROUP = "mcp";

    /**
    * MCP �г������ֶΣ�true=������false=�رա�
    */
    private static final String MCP_MARKET_ENABLED_FIELD = "market_enabled";

    /**
    * ��������������洢����
    */
    private final SysAiConversationStore conversationStore;

    /**
    * WS �¼����ͷ���
    */
    private final ApiWebSocketPushService pushService;

    /**
    * MCP ��ʵ���ӷ���SDK ���� + �����о٣�
    */
    private final McpConnectionService mcpConnectionService;

    /**
    * MCP �������������ݿ�洢
    */
    private final McpServerStore mcpServerStore;

    /**
    * ȫ���������񣨶�ȡ/д�� MCP �г����ؿ���
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
        return ApiResult.ok(mcpConnectionService.testConnection(name, transport));
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
