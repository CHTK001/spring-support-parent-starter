// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.spi.impl;

import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WorkBuddyAccountChannelAuthManager} 的集成测试。
 *
 * <p>用 JDK 内置 {@link HttpServer} 起一个 <b>mock wb2api 网关</b>，模拟
 * {@code /admin/login}（管理登录→csrf+会话 cookie）、{@code /admin/oauth}
 * （发起授权→flow id + auth_url）、{@code /admin/oauth/{flowId}}（轮询状态）、
 * {@code /admin/access}（取 API Key）、{@code /admin/tasks} + {@code .../runs}
 * （签到任务）、{@code /v1/models}（模型目录）等端点，通过
 * {@code chua.workbuddy.gateway-url} / {@code chua.workbuddy.admin-key} 系统属性
 * 指向 mock，真实验证 SPI 全链路。浏览器扫码步骤无法自动化，故用可控的
 * {@code oauthStatus} 模拟「用户已扫码完成」。</p>
 *
 * <p>覆盖断言：句柄字段、poll 三态（complete→凭据 / waiting→null / failed→异常）、
 * 凭据结构、模型解析（cn:/global: 前缀）、状态探活、签到触发、缺管理密钥时报错。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
class WorkBuddyAccountChannelAuthManagerTest {

    private static final String FLOW_ID = "flow-1";
    private static final String API_KEY = "test-api-key-123456";
    private static final String MOCK_AUTH_URL =
            "https://auth.codebuddy.example/login?channel=wb2api&invite&token=mock";

    private HttpServer server;
    private String gateway;

    /** mock 网关当前授权流状态（测试中用 setOAuthStatus 控制流转）。 */
    private volatile String oauthStatus = "waiting";
    /** /admin/access 被调用次数。 */
    private volatile int accessCalls = 0;
    /** /admin/tasks/{id}/runs 被调用次数。 */
    private volatile int taskRunCalls = 0;

    private final WorkBuddyAccountChannelAuthManager manager =
            new WorkBuddyAccountChannelAuthManager();

    @BeforeEach
    void startMockGateway() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        gateway = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/", this::handle);
        server.start();
        System.setProperty("chua.workbuddy.gateway-url", gateway);
        System.setProperty("chua.workbuddy.admin-key", "test-admin-key-32bytes!");
    }

    @AfterEach
    void stopMockGateway() {
        server.stop(0);
        System.clearProperty("chua.workbuddy.gateway-url");
        System.clearProperty("chua.workbuddy.admin-key");
        System.clearProperty("chua.workbuddy.realm");
    }

    private void setOAuthStatus(String status) {
        this.oauthStatus = status;
    }

    // ==================== 全链路 ====================

    @Test
    @DisplayName("startLogin：登录网关 + 发起授权，返回带 auth_url 的句柄")
    void startLoginReturnsHandle() throws Exception {
        Map<String, Object> handle = manager.startLogin();
        assertNotNull(handle);
        assertEquals("workbuddy-oauth-" + FLOW_ID, handle.get("handle"));
        assertEquals(FLOW_ID, handle.get(WorkBuddyAccountChannelAuthManager.HANDLE_FLOW_ID));
        assertEquals(MOCK_AUTH_URL, handle.get("verifyUrl"));
        assertEquals(MOCK_AUTH_URL, handle.get("loginUrl"));
        assertNotNull(handle.get(WorkBuddyAccountChannelAuthManager.HANDLE_COOKIE));
        assertEquals("test-csrf", handle.get(WorkBuddyAccountChannelAuthManager.HANDLE_CSRF));
        assertEquals("cn", handle.get(WorkBuddyAccountChannelAuthManager.HANDLE_REALM));
    }

    @Test
    @DisplayName("pollLogin complete：取 API Key 组装凭据（access_token/gateway/base_url）")
    void pollCompleteBuildsCredential() throws Exception {
        setOAuthStatus("complete");
        Map<String, Object> handle = manager.startLogin();
        Map<String, Object> credentials = manager.pollLogin(handle);
        assertNotNull(credentials);
        assertEquals(API_KEY, credentials.get("access_token"));
        assertEquals(gateway, credentials.get("gateway_url"));
        assertEquals("cn", credentials.get("realm"));
        assertEquals(gateway + "/v1", credentials.get("base_url"));
        // complete 只会在收尾调一次 access
        assertEquals(1, accessCalls);
    }

    @Test
    @DisplayName("pollLogin waiting：返回 null（继续等），不产生凭据")
    void pollWaitingReturnsNull() throws Exception {
        Map<String, Object> handle = manager.startLogin();
        // 默认 oauthStatus = waiting
        assertNull(manager.pollLogin(handle));
        assertEquals(0, accessCalls);
    }

    @Test
    @DisplayName("pollLogin failed：抛 UPSTREAM_ERROR")
    void pollFailedThrows() throws Exception {
        setOAuthStatus("failed");
        Map<String, Object> handle = manager.startLogin();
        ApiException e = assertThrows(ApiException.class, () -> manager.pollLogin(handle));
        assertEquals(ApiErrorCode.UPSTREAM_ERROR, e.getErrorCode());
        assertTrue(e.getMessage().contains("WorkBuddy 授权失败"));
    }

    @Test
    @DisplayName("pollLogin：凭证内 base_url 供对话链路直连网关 /v1")
    void credentialBaseUrlPointsAtGatewayV1() throws Exception {
        setOAuthStatus("complete");
        Map<String, Object> credentials = manager.pollLogin(manager.startLogin());
        String base = String.valueOf(credentials.get("base_url"));
        assertEquals(gateway + "/v1", base);
    }

    // ==================== 模型 / 状态 / 签到 ====================

    @Test
    @DisplayName("listModels：解析 OpenAI 兼容 data[]，保留 cn:/global: 前缀与上下文窗口")
    void listModelsParsesOpenAiFormat() throws Exception {
        setOAuthStatus("complete");
        Map<String, Object> credentials = manager.pollLogin(manager.startLogin());
        List<Map<String, Object>> models = manager.listModels(credentials);
        assertEquals(2, models.size());
        assertEquals("cn:glm-5.2", models.get(0).get("id"));
        assertEquals("global:gpt-5.6", models.get(1).get("id"));
        assertEquals(131072, models.get(0).get("contextWindow"));
    }

    @Test
    @DisplayName("status：网关可达时凭据视为 valid")
    void statusValidWhenGatewayReachable() throws Exception {
        setOAuthStatus("complete");
        Map<String, Object> credentials = manager.pollLogin(manager.startLogin());
        Map<String, Object> st = manager.status(credentials);
        assertEquals("valid", st.get("status"));
    }

    @Test
    @DisplayName("supportsCheckin = true，checkin 触发网关签到任务")
    void checkinTriggersGatewayTask() throws Exception {
        setOAuthStatus("complete");
        Map<String, Object> credentials = manager.pollLogin(manager.startLogin());
        assertTrue(manager.supportsCheckin());
        Map<String, Object> result = manager.checkin(credentials);
        assertEquals(Boolean.TRUE, result.get("ok"));
        assertTrue(String.valueOf(result.get("message")).contains("签到"));
        assertEquals(1, taskRunCalls);
    }

    @Test
    @DisplayName("refresh：无续期语义，返回原凭据副本")
    void refreshReturnsCopy() {
        Map<String, Object> creds = Map.of("access_token", API_KEY, "gateway_url", gateway);
        Map<String, Object> refreshed = manager.refresh(creds);
        assertNotNull(refreshed);
        assertEquals(API_KEY, refreshed.get("access_token"));
    }

    // ==================== 配置缺失 ====================

    @Test
    @DisplayName("未配置管理密钥：startLogin 直接报错引导，不产生半成品流")
    void missingAdminKeyThrows() {
        System.clearProperty("chua.workbuddy.admin-key");
        ApiException e = assertThrows(ApiException.class, () -> manager.startLogin());
        assertEquals(ApiErrorCode.INVALID_PARAM, e.getErrorCode());
        assertTrue(e.getMessage().contains("admin-key"));
    }

    // ==================== mock 网关分发 ====================

    /** mock wb2api 网关：按 path 分发到各管理/公开端点。 */
    private void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String body = readAll(ex);
            String respBody;
            int status = 200;
            switch (path) {
                case "/admin/login" -> {
                    // 管理登录：返回 csrf + 会话 cookie
                    ex.getResponseHeaders().add("Set-Cookie",
                            "wb2a_admin=session-id; Path=/admin; HttpOnly; SameSite=Strict");
                    respBody = "{\"csrf\":\"test-csrf\"}";
                }
                case "/admin/oauth" -> {
                    // 发起授权：返回 flow id + auth_url
                    respBody = "{\"id\":\"" + FLOW_ID + "\",\"status\":\"waiting\","
                            + "\"message\":\"请在打开的上游页面完成登录\","
                            + "\"auth_url\":\"" + MOCK_AUTH_URL + "\"}";
                }
                case "/admin/oauth/" + FLOW_ID + "/poll" -> {
                    // 轮询：按测试控制的 oauthStatus 返回（console 对外路由：POST .../poll）
                    String msg = switch (oauthStatus) {
                        case "complete" -> "授权成功";
                        case "failed" -> "上游登录失败";
                        default -> "继续等待";
                    };
                    respBody = "{\"id\":\"" + FLOW_ID + "\",\"status\":\"" + oauthStatus
                            + "\",\"message\":\"" + msg + "\",\"auth_url\":\"" + MOCK_AUTH_URL + "\"}";
                }
                case "/admin/access" -> {
                    accessCalls++;
                    respBody = "{\"api_key\":\"" + API_KEY + "\"}";
                }
                case "/admin/tasks" -> {
                    respBody = "{\"items\":[{\"id\":\"task-checkin-1\",\"name\":\"签到 checkin\"},"
                            + "{\"id\":\"task-travel\",\"name\":\"猫猫旅行\"}]}";
                }
                case "/admin/tasks/task-checkin-1/runs" -> {
                    taskRunCalls++;
                    respBody = "{\"ok\":true,\"run_id\":\"run-1\"}";
                }
                case "/v1/models" -> {
                    respBody = "{\"object\":\"list\",\"data\":["
                            + "{\"id\":\"cn:glm-5.2\",\"object\":\"model\",\"context_length\":131072,\"owned_by\":\"workbuddy\"},"
                            + "{\"id\":\"global:gpt-5.6\",\"object\":\"model\",\"context_length\":200000,\"owned_by\":\"workbuddy\"}"
                            + "]}";
                }
                default -> {
                    status = 404;
                    respBody = "{\"error\":\"not found\"}";
                }
            }
            byte[] bytes = respBody.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        } finally {
            ex.close();
        }
    }

    private static String readAll(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }}