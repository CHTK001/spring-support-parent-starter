// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.ai.chat.route.RouteTarget;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.spi.AccountChannelAuthManager;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WorkBuddyAccountChannelAuthManager} 的集成测试。
 *
 * <p>用 JDK 内置 {@link HttpServer} 起一个 mock 面板，模拟 {@code workbuddy2api-panel}
 * 的两段式登录协议：</p>
 * <ul>
 *   <li>{@code POST /panel/api/login/start}：{@code {"realm":"..."}} →
 *       {@code {ok,url,state,realm}}；</li>
 *   <li>{@code GET /panel/api/login/poll?state=...}：未完成 {@code {done:false}}，
 *       完成后 {@code {done:true,uid,nickname,realm,credits,credits_total,...}}。</li>
 * </ul>
 *
 * <p>另外覆盖 uid 路径穿越校验、auth 文件落盘形态、路由目标与探活分支。不依赖外网。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
class WorkBuddyAccountChannelAuthManagerTest {

    /**
     * 轮询登录模拟状态：0=未完成 1=完成
     */
    private volatile int loginStage = 0;

    /**
     * 完成时返回的 uid（可改造成非法值以验证路径穿越拦截）
     */
    private volatile String loginUid = "8f4a1c2e-uid_ok";

    /**
     * 完成时返回的 realm
     */
    private volatile String loginRealm = "cn";

    /**
     * 探活返回的状态码（404 模拟 access_token 失效）
     */
    private volatile int probeStatus = 200;

    /**
     * 登录发起次数
     */
    private final AtomicInteger startCalls = new AtomicInteger(0);

    /**
     * 登录轮询次数
     */
    private final AtomicInteger pollCalls = new AtomicInteger(0);

    private HttpServer server;

    /**
     * mock 服务地址
     */
    private String origin;

    /**
     * 临时 auth 目录
     */
    private Path authDir;

    /**
     * 覆写三方地址指向 mock 的实例（其余行为与线上一致）
     */
    private WorkBuddyAccountChannelAuthManager manager;

    @BeforeEach
    void startMockServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        origin = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/", this::handle);
        server.start();
        authDir = Files.createTempDirectory("workbuddy-auth-test");
        loginStage = 0;
        loginUid = "8f4a1c2e-uid_ok";
        loginRealm = "cn";
        probeStatus = 200;
        startCalls.set(0);
        pollCalls.set(0);
        manager = new WorkBuddyAccountChannelAuthManager() {
            @Override
            protected String panelOrigin() {
                return origin;
            }

            @Override
            protected String upstreamOrigin(String realm) {
                return origin;
            }

            @Override
            protected String billingOrigin(String realm) {
                return origin;
            }

            @Override
            protected String webOrigin(String realm) {
                return origin;
            }

            @Override
            protected Path authDir() {
                return authDir;
            }
        };
    }

    @AfterEach
    void stopMockServer() throws IOException {
        server.stop(0);
        if (authDir != null && Files.isDirectory(authDir)) {
            try (var walk = Files.walk(authDir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // 临时目录清理失败不影响断言
                    }
                });
            }
        }
    }

    /**
     * mock 路由：按路径分派，模拟 panel 两段式登录 + 探活。
     *
     * @param exchange 请求
     * @throws IOException 写出失败
     */
    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String response;

        if ("POST".equals(method) && path.endsWith("/panel/api/login/start")) {
            startCalls.incrementAndGet();
            assertTrue(body.contains("\"realm\""), "发起登录必须携带 realm");
            response = "{\"ok\":true,\"url\":\"" + origin + "/device?state=st-mock\","
                    + "\"state\":\"st-mock\",\"realm\":\"cn\"}";
        } else if ("GET".equals(method) && path.endsWith("/panel/api/login/poll")) {
            pollCalls.incrementAndGet();
            if (loginStage == 0) {
                response = "{\"done\":false,\"message\":\"login ing\"}";
            } else {
                response = "{\"done\":true,\"access_token\":\"at-mock\",\"uid\":\"" + loginUid + "\","
                        + "\"nickname\":\"测试号\",\"realm\":\"" + loginRealm + "\","
                        + "\"credits\":880,\"credits_total\":1000,\"checkin_message\":\"\"}";
            }
        } else if ("GET".equals(method) && path.endsWith("/v3/config")) {
            if (probeStatus != 200) {
                exchange.sendResponseHeaders(probeStatus, -1);
                exchange.close();
                return;
            }
            response = "{\"code\":0,\"data\":{\"models\":["
                    + "{\"id\":\"deepseek-v4.1-flash\",\"maxInputTokens\":1000000,"
                    + "\"maxOutputTokens\":384000,\"descriptionZh\":\"深度求索\"},"
                    + "{\"id\":\"auto\"}]}}";
        } else if ("POST".equals(method) && path.endsWith("/v2/billing/meter/daily-checkin")) {
            response = "{\"code\":0,\"msg\":\"success\"}";
        } else if ("GET".equals(method) && path.endsWith("/v2/activity/growth/tasks")) {
            response = "{\"code\":0,\"data\":{\"tasks\":["
                    + "{\"task_code\":\"daily_chat\",\"accept_status\":\"not_accepted\"},"
                    + "{\"task_code\":\"first_buddy\",\"accept_status\":\"accepted\","
                    + "\"progress\":{\"current\":1,\"target\":1}}]}}";
        } else if ("POST".equals(method) && path.endsWith("/v2/billing/meter/get-user-resource")) {
            response = "{\"code\":0,\"Response\":{\"Data\":{\"Accounts\":["
                    + "{\"CycleCapacityRemain\":800,\"CycleCapacitySize\":1000}]}}}";
        } else if ("POST".equals(method) && path.endsWith("/v2/activity/growth/tasks/accept")) {
            response = "{\"code\":0}";
        } else if ("POST".equals(method) && path.contains("/activity/growth/tasks/")
                && path.endsWith("/claim")) {
            response = "{\"code\":0,\"data\":{\"already_claimed\":false,\"credit\":100,\"energy\":5}}";
        } else if ("GET".equals(method) && path.endsWith("/console/enterprises/personal/models")) {
            response = "{\"code\":0,\"data\":{\"models\":[],\"agents\":[]}}";
        } else {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }

        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Test
    @DisplayName("startLogin：POST panel login/start，返回 state/verifyUrl/realm")
    void startLoginReturnsPanelState() {
        Map<String, Object> handle = manager.startLogin();

        assertEquals("st-mock", handle.get("handle"), "handle 应为 panel 返回的 state");
        assertEquals(origin + "/device?state=st-mock", handle.get("verifyUrl"), "应返回授权地址");
        assertEquals("cn", handle.get(WorkBuddyAccountChannelAuthManager.HANDLE_REALM));
        assertEquals(1, startCalls.get(), "应调用一次 panel login/start");
    }

    @Test
    @DisplayName("pollLogin：done=false 返回 null，done=true 返回凭据并落盘 auth 文件")
    void pollLoginWritesAuthFile() throws Exception {
        Map<String, Object> handle = manager.startLogin();
        handle.put(WorkBuddyAccountChannelAuthManager.HANDLE_POLL_INTERVAL_MS, 1L);

        loginStage = 0;
        handle.remove(WorkBuddyAccountChannelAuthManager.HANDLE_LAST_POLL_AT);
        assertNull(manager.pollLogin(handle), "未完成时返回 null 继续等");

        loginStage = 1;
        handle.remove(WorkBuddyAccountChannelAuthManager.HANDLE_LAST_POLL_AT);
        Map<String, Object> credential = manager.pollLogin(handle);
        assertNotNull(credential, "完成后应返回凭据");
        assertEquals("at-mock", credential.get("access_token"));
        assertEquals("8f4a1c2e-uid_ok", credential.get("uid"));
        assertEquals("测试号", credential.get("nickname"));
        assertEquals("cn", credential.get("realm"));
        assertEquals(origin, credential.get("base_url"), "cn 域 base_url 指向 chat 上游");
        assertTrue(pollCalls.get() >= 2, "应至少轮询两次");

        Path file = authDir.resolve("workbuddy-8f4a1c2e-uid_ok.json");
        assertTrue(Files.exists(file), "登录完成必须落盘 auth 文件: " + file);
        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(content.contains("\"accessToken\":\"at-mock\""), "auth 文件应为嵌套形: " + content);
        assertTrue(content.contains("\"uid\":\"8f4a1c2e-uid_ok\""), "auth 文件应含账号信息");
        assertTrue(content.contains("\"realm\":\"cn\""), "auth 文件应显式写入 realm");
    }

    @Test
    @DisplayName("pollLogin：uid 含路径字符时拒绝落盘（防路径穿越）")
    void pollLoginRejectsPathTraversalUid() {
        Map<String, Object> handle = manager.startLogin();
        handle.put(WorkBuddyAccountChannelAuthManager.HANDLE_POLL_INTERVAL_MS, 1L);
        loginStage = 1;
        loginUid = "../../evil";

        ApiException error = assertThrows(ApiException.class, () -> manager.pollLogin(handle));
        assertEquals("UPSTREAM_ERROR", error.getErrorCode().name());
        assertFalse(Files.exists(authDir.resolve("evil.json")), "不得写出目录外的文件");
    }

    @Test
    @DisplayName("validUID：只放行 [A-Za-z0-9_-] 且长度 ≤ 64")
    void validUIDRules() {
        assertTrue(WorkBuddyAccountChannelAuthManager.validUID("a1B2-c3_D4"));
        assertFalse(WorkBuddyAccountChannelAuthManager.validUID(""));
        assertFalse(WorkBuddyAccountChannelAuthManager.validUID(null));
        assertFalse(WorkBuddyAccountChannelAuthManager.validUID("a/b"));
        assertFalse(WorkBuddyAccountChannelAuthManager.validUID("../x"));
        assertFalse(WorkBuddyAccountChannelAuthManager.validUID("a.b"));
        assertFalse(WorkBuddyAccountChannelAuthManager.validUID("x".repeat(65)));
        assertTrue(WorkBuddyAccountChannelAuthManager.validUID("x".repeat(64)));
    }

    @Test
    @DisplayName("pollLogin：句柄缺失 state 时抛 INVALID_PARAM")
    void pollLoginWithoutStateThrows() {
        ApiException error = assertThrows(ApiException.class, () -> manager.pollLogin(Map.of()));
        assertEquals("INVALID_PARAM", error.getErrorCode().name());
    }

    @Test
    @DisplayName("status：探活成功 valid，401 判 invalid，缺 token 判 invalid")
    void statusBranches() {
        Map<String, Object> credential = Map.of(
                "access_token", "at-mock",
                "uid", "u-1",
                "realm", "cn");

        Map<String, Object> valid = manager.status(credential);
        assertEquals("valid", valid.get("state"));

        probeStatus = 401;
        Map<String, Object> invalid = manager.status(credential);
        assertEquals("invalid", invalid.get("state"));

        Map<String, Object> missing = manager.status(Map.of());
        assertEquals("invalid", missing.get("state"));
    }

    @Test
    @DisplayName("status：本地到期优先于探活")
    void statusExpiredLocally() {
        Map<String, Object> credential = Map.of(
                "access_token", "at-mock",
                "expire_time", System.currentTimeMillis() - 1000L);
        assertEquals("expired", manager.status(credential).get("state"));
    }

    @Test
    @DisplayName("toRouteTarget：provider=workbuddy，baseUrl 与账号域一致")
    void toRouteTargetUsesRealm() {
        // 用未覆写上游地址的原型实例校验 realm → 上游域的映射（不产生网络请求）
        WorkBuddyAccountChannelAuthManager plain = new WorkBuddyAccountChannelAuthManager();

        RouteTarget cn = plain.toRouteTarget(Map.of("access_token", "at-mock", "realm", "cn"));
        assertNotNull(cn);
        assertEquals("workbuddy", cn.getProvider());
        assertEquals("https://copilot.tencent.com", cn.getBaseUrl());
        assertEquals("at-mock", cn.getApiKey());
        assertEquals("bearer", cn.getAuthScheme());

        RouteTarget global = plain.toRouteTarget(Map.of(
                "access_token", "at-mock", "realm", "global", "uid", "u-1"));
        assertNotNull(global);
        assertEquals("https://www.workbuddy.ai", global.getBaseUrl());
        assertEquals("www.workbuddy.ai", global.getHeaders().get("X-Domain"));
        assertEquals("u-1", global.getHeaders().get("X-User-Id"));

        assertNull(plain.toRouteTarget(Map.of()), "缺 access_token 时不产生路由目标");
    }

    @Test
    @DisplayName("checkin：CN 走 daily-checkin 并返回消息")
    void checkinCn() {
        Map<String, Object> credential = new java.util.LinkedHashMap<>();
        credential.put("access_token", "at-mock");
        credential.put("uid", "u-1");
        credential.put("realm", "cn");

        Map<String, Object> updated = manager.checkin(credential);
        assertNotNull(updated);
        String message = String.valueOf(updated.get("checkin_message"));
        assertTrue(message.contains("每日签到成功"), "签到消息应包含成功提示: " + message);
        assertTrue(message.contains("已接受 1 个成长任务"), "应接受未报名的任务: " + message);
        assertTrue(message.contains("已领取 1 个任务奖励"), "应领取达标任务奖励: " + message);
        assertEquals(800L, updated.get("credits"), "应回写余额");
        assertEquals(1000L, updated.get("credits_total"));
        assertNotNull(updated.get("checkin_at"));
    }

    @Test
    @DisplayName("listModels：/v3/config 解析模型目录")
    void listModelsFromV3Config() {
        List<Map<String, Object>> models = manager.listModels(Map.of(
                "access_token", "at-mock", "realm", "cn"));
        assertEquals(2, models.size(), "应解析出 2 个模型: " + models);
        assertEquals("deepseek-v4.1-flash", models.get(0).get("id"));
        assertEquals(1000000L, models.get(0).get("contextWindow"));
        assertEquals(384000L, models.get(0).get("maxOutputTokens"));
        assertEquals("auto", models.get(1).get("id"));
    }

    @Test
    @DisplayName("channel/displayName/loginMethod：SPI 标识稳定")
    void spiMetadata() {
        assertEquals("workbuddy", manager.channel());
        assertEquals("WorkBuddy（腾讯）", manager.displayName());
        assertEquals("browser-device", manager.loginMethod());
        assertTrue(manager.supportsCheckin());
    }

    @Test
    @DisplayName("SPI 注册：AccountChannelAuthManager 扩展按 workbuddy 可取到本实现")
    void registeredInSpi() {
        AccountChannelAuthManager resolved = com.chua.common.support.spi.ServiceProvider
                .of(AccountChannelAuthManager.class)
                .getExtension("workbuddy");
        assertNotNull(resolved, "META-INF/extensions 未注册 workbuddy");
        assertTrue(resolved instanceof WorkBuddyAccountChannelAuthManager,
                "workbuddy 应解析到 WorkBuddyAccountChannelAuthManager，实际: " + resolved.getClass());
    }
}
