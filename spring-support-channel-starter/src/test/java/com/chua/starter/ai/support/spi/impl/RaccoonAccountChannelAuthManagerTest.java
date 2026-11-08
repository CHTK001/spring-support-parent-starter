// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.spi.impl;

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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RaccoonAccountChannelAuthManager} 的集成测试。
 *
 * <p>用 JDK 内置 {@link HttpServer} 起一个 mock 服务器，覆写 {@link RaccoonAccountChannelAuthManager#siteOrigin()}
 * 指向 mock，模拟旧版扫码协议：{@code POST /api/web/auth/v1/login_with_qrcode_code}（轮询登录状态：
 * pending → logging → success，success 带回 access_token / refresh_token / expired_at）、
 * {@code GET /api/web/auth/v1/user_info}（探活）与 {@code POST /api/web/auth/v1/refresh}（续期）。</p>
 *
 * <p>不依赖外网。覆盖断言：创建句柄字段与二维码 URL、轮询等待/完成两态、
 * 凭据结构、探活与续期的分支处理。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
class RaccoonAccountChannelAuthManagerTest {

    /** 登录轮询模拟状态：0=pending(未扫码) 1=logging(已扫码待确认) 2=success(完成)。 */
    private volatile int loginStage = 0;
    /** /user_info 探活次数。 */
    private final AtomicInteger userInfoCalls = new AtomicInteger(0);
    /** /refresh 续期次数。 */
    private final AtomicInteger refreshCalls = new AtomicInteger(0);

    private HttpServer server;
    private String origin;

    /** 覆写 siteOrigin 指向本地 mock 的实例（其余行为与线上一致）。 */
    private final RaccoonAccountChannelAuthManager manager = new RaccoonAccountChannelAuthManager() {
        @Override
        protected String siteOrigin() {
            return origin;
        }
    };

    @BeforeEach
    void startMockServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        origin = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/", this::handle);
        server.start();
        loginStage = 0;
        userInfoCalls.set(0);
        refreshCalls.set(0);
    }

    @AfterEach
    void stopMockServer() {
        server.stop(0);
    }

    /** mock 路由：按路径分派，模拟旧版扫码协议 + 探活/续期。 */
    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String response;

        if ("POST".equals(method) && path.endsWith("/api/web/auth/v1/login_with_qrcode_code")) {
            // 轮询登录状态：三个阶段。
            assertTrue(body.contains("\"qrcode_code\""),
                    "轮询请求体应带 qrcode_code");
            switch (loginStage) {
                case 0 -> response = "{\"code\":0,\"data\":{\"status\":\"pending\",\"expired_at\":299}}";
                case 1 -> response = "{\"code\":0,\"data\":{\"status\":\"logging\",\"expired_at\":298}}";
                default -> {
                    response = "{\"code\":0,\"data\":{\"status\":\"success\","
                            + "\"access_token\":\"jwt-at-mock\",\"refresh_token\":\"rt-mock\","
                            + "\"expired_at\":3600}}";
                }
            }
        } else if ("GET".equals(method) && path.endsWith("/api/web/auth/v1/user_info")) {
            userInfoCalls.incrementAndGet();
            response = "{\"code\":0,\"data\":{\"id\":\"u-1\",\"name\":\"Raccoon\",\"phone\":\"138****0000\"}}";
        } else if ("POST".equals(method) && path.endsWith("/api/web/auth/v1/refresh")) {
            refreshCalls.incrementAndGet();
            assertTrue(body.contains("\"refresh_token\":\"rt-mock\""),
                    "续期请求体应带 refresh_token");
            response = "{\"code\":0,\"data\":{\"access_token\":\"jwt-at-refreshed\","
                    + "\"refresh_token\":\"rt-refreshed\"}}";
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
    @DisplayName("startLogin：本地生成扫码 code，返回二维码 URL 与轮询间隔")
    void startLoginReturnsLocalCode() {
        Map<String, Object> handle = manager.startLogin();

        assertNotNull(handle.get("handle"), "handle 应为本地生成的扫码 code");
        assertEquals(32, String.valueOf(handle.get("handle")).length(),
                "扫码 code 应为 16 字节 hex（32 字符）");
        assertNotNull(handle.get("qrUrl"), "应返回二维码 URL");
        String verifyUrl = String.valueOf(handle.get("verifyUrl"));
        assertTrue(verifyUrl.startsWith(origin + "/login/mp?code="),
                "落地页 URL 应带 code: " + verifyUrl);
        assertTrue(verifyUrl.contains("&appname="), "落地页 URL 应带 appname: " + verifyUrl);
        assertEquals(2_000L, handle.get(RaccoonAccountChannelAuthManager.HANDLE_POLL_INTERVAL_MS),
                "轮询间隔应为默认 2s");
    }

    @Test
    @DisplayName("pollLogin：pending/logging 返回 null，success 返回凭据")
    void pollLoginTransitions() throws InterruptedException {
        Map<String, Object> handle = manager.startLogin();
        // 测试内连续轮询：把轮询间隔改小，避免被节流挡在门外。
        handle.put(RaccoonAccountChannelAuthManager.HANDLE_POLL_INTERVAL_MS, 1L);

        // 阶段 0：pending（未扫码）。
        loginStage = 0;
        handle.remove(RaccoonAccountChannelAuthManager.HANDLE_LAST_POLL_AT);
        assertNull(manager.pollLogin(handle), "未扫码(pending)应返回 null 继续等");

        // 阶段 1：logging（已扫码，待手机确认）。
        loginStage = 1;
        handle.remove(RaccoonAccountChannelAuthManager.HANDLE_LAST_POLL_AT);
        Thread.sleep(2);
        assertNull(manager.pollLogin(handle), "已扫码待确认(logging)应返回 null 继续等");

        // 阶段 2：success（完成）。
        loginStage = 2;
        handle.remove(RaccoonAccountChannelAuthManager.HANDLE_LAST_POLL_AT);
        Thread.sleep(2);
        Map<String, Object> credential = manager.pollLogin(handle);
        assertNotNull(credential, "success 应返回凭据");
        assertEquals("jwt-at-mock", credential.get("access_token"));
        assertEquals("rt-mock", credential.get("refresh_token"));
        assertEquals(3_600_000L, credential.get("expire_time"),
                "expired_at=3600s 应转为毫秒 3600000");
        assertEquals("Raccoon", credential.get("nickname"));
    }

    @Test
    @DisplayName("refresh/status：探活与续期分支正确")
    void refreshAndStatus() {
        Map<String, Object> credential = Map.of(
                "access_token", "jwt-at-mock",
                "refresh_token", "rt-mock",
                "expire_time", System.currentTimeMillis() + 3_600_000L);

        Map<String, Object> status = manager.status(credential);
        assertEquals("valid", status.get("state"));
        assertTrue(userInfoCalls.get() >= 1, "探活应调用 /user_info");

        Map<String, Object> refreshed = manager.refresh(credential);
        assertEquals("jwt-at-refreshed", refreshed.get("access_token"));
        assertEquals("rt-refreshed", refreshed.get("refresh_token"));
        assertTrue(refreshCalls.get() >= 1, "续期应调用 /refresh");
    }

    @Test
    @DisplayName("handle 缺失时 pollLogin 抛 INVALID_PARAM")
    void pollLoginWithoutHandleThrows() {
        try {
            manager.pollLogin(Map.of());
            throw new AssertionError("应抛 INVALID_PARAM");
        } catch (ApiException e) {
            assertEquals("INVALID_PARAM", e.getErrorCode().name());
        }
    }
}