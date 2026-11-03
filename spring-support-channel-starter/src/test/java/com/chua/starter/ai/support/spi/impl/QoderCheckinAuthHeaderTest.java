package com.chua.starter.ai.support.spi.impl;

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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link QoderAccountChannelAuthManager#checkin(Map)} 的集成测试。
 *
 * <p>核心回归点：sash 端点（campaigns / claim）必须携带
 * {@code Authorization: Bearer <access_token>} —— 缺头时上游直接 401
 * （{@code {TOKEN_INVALID, missing authorization token}}），签到必然失败。
 * 测试断言 mock 收到的请求头里带 Bearer，且响应能被正确解析为
 * 「已领取 N 项活动，积分 M」。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
class QoderCheckinAuthHeaderTest {

    /** mock 上一轮收到的未带鉴权的请求头（用于断言）。 */
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>("");

    private HttpServer server;

    /** 覆写 openApiBase() 指向本地 mock（authBase 不参与签到，无需覆写）。 */
    private final QoderAccountChannelAuthManager manager = new QoderAccountChannelAuthManager() {
        @Override
        protected String openApiBase() {
            return base;
        }
    };

    private String base;

    @BeforeEach
    void startMock() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/", this::handle);
        server.start();
        lastAuthorization.set("");
    }

    @AfterEach
    void stopMock() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization") == null
                ? "" : exchange.getRequestHeaders().getFirst("Authorization"));
        String path = exchange.getRequestURI().getPath();
        String response;
        if (path.endsWith("/sash/api/v1/me/campaigns")) {
            // 活动列表：一项可领。
            response = "{\"campaigns\":[{\"campaignId\":\"c-1\",\"actionType\":\"CLAIM_BENEFIT\","
                    + "\"claimStatus\":\"CLAIMABLE\",\"benefit\":{\"kind\":\"CREDITS\",\"amount\":100}}]}";
        } else if (path.endsWith("/c-1/claim")) {
            response = "{\"status\":\"CLAIMED\",\"replayed\":false,\"amount\":100}";
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
    @DisplayName("checkin：campaigns/claim 请求头必须带 Bearer，成功解析为领取结果")
    void checkinSendsBearerAndParsesClaim() {
        Map<String, Object> credential = Map.of(
                "access_token", "dt-test-token",
                "machine_id", "m-1");

        Map<String, Object> result = manager.checkin(credential);

        assertNotNull(result);
        // 关键回归：请求必须带 Bearer（此前漏了头，上游 401）。
        assertTrue(lastAuthorization.get().startsWith("Bearer dt-test-token"),
                "campaigns 请求必须带 Bearer，实际: " + lastAuthorization.get());
        assertTrue(String.valueOf(result.get("claimResult")).contains("已领取"),
                "应解析出已领取结果，实际: " + result.get("claimResult"));
        assertEquals(100, result.get("claimedCredits"));
    }

    @Test
    @DisplayName("checkin：凭据缺 access_token 时不发上游请求")
    void checkinWithoutTokenSkipsUpstream() {
        Map<String, Object> credential = Map.of("machine_id", "m-1");
        Map<String, Object> result = manager.checkin(credential);
        assertEquals("", lastAuthorization.get(), "缺 token 不应发上游请求");
        assertTrue(String.valueOf(result.get("claimResult")).contains("缺少 access_token"));
    }
}