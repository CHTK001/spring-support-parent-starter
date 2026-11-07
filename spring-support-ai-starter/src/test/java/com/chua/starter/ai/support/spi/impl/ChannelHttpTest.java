// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.spi.impl;

import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.common.support.http.ChannelHttpException;
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ChannelHttp} 下沉后的契约测试。
 *
 * <p>ChannelHttp 的实现已经搬到 common 的
 * {@code com.chua.starter.common.support.http.ChannelHttp}，AI 侧保留的是适配壳。
 * 这层壳最容易在迁移中悄悄改变行为，所以这里锁死三件事：</p>
 * <ul>
 *   <li>2xx 原样返回响应体；</li>
 *   <li>非 2xx 仍然抛 {@link ApiException}（而不是 common 的异常），
 *       错误码、文案、根因都保持不变；</li>
 *   <li>429 仍能被 {@link ChannelHttp#isRateLimited(Throwable)} 识别，
 *       登录轮询才能继续等而不是判失败。</li>
 * </ul>
 *
 * @since 4.0.0.51
 * @author CH
 */
class ChannelHttpTest {

    /**
     * 测试用上游。
     */
    private HttpServer server;

    /**
     * 每个路径的处理逻辑。
     */
    private final Map<String, Handler> routes = new HashMap<>();

    /**
     * 上游地址前缀。
     */
    private String baseUrl;

    /**
     * 启动本地上游。
     *
     * @throws IOException 端口绑定失败
     */
    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /**
     * 关闭本地上游。
     */
    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    @DisplayName("2xx 原样返回响应体")
    void okReturnsBody() {
        routes.put("/ok", exchange -> reply(exchange, 200, "{\"ok\":true}", Map.of()));

        assertEquals("{\"ok\":true}", ChannelHttp.getJson(baseUrl + "/ok", null));
    }

    @Test
    @DisplayName("非 2xx 仍抛本模块 ApiException，并保留上游状态与文案")
    void non2xxStillThrowsApiException() {
        routes.put("/fail", exchange -> reply(exchange, 502, "upstream exploded", Map.of()));

        ApiException error = assertThrows(ApiException.class,
                () -> ChannelHttp.getJson(baseUrl + "/fail", null));

        assertEquals(ApiErrorCode.UPSTREAM_ERROR, error.getErrorCode());
        assertTrue(error.getMessage().contains("渠道上游返回非 2xx"), error.getMessage());
        assertTrue(error.getMessage().contains("502"), error.getMessage());
        assertTrue(error.getMessage().contains("upstream exploded"), error.getMessage());

        assertNotNull(error.getCause(), "必须保留 common 异常作为根因");
        assertTrue(error.getCause() instanceof ChannelHttpException);
        assertEquals(502, ((ChannelHttpException) error.getCause()).getHttpStatus());
        assertEquals(com.chua.starter.common.support.http.ChannelHttp.CODE_UPSTREAM_ERROR,
                ((ChannelHttpException) error.getCause()).getCode());
    }

    @Test
    @DisplayName("429 退避重试后仍限流，且 isRateLimited 认得出")
    void rateLimitedIsRecognised() {
        AtomicInteger attempts = new AtomicInteger();
        routes.put("/limited", exchange -> {
            attempts.incrementAndGet();
            reply(exchange, 429, "slow down", Map.of("Retry-After", "1"));
        });

        ApiException error = assertThrows(ApiException.class,
                () -> ChannelHttp.getJson(baseUrl + "/limited", null));

        assertEquals(3, attempts.get(), "首次 + 2 次退避重试");
        assertEquals(ApiErrorCode.UPSTREAM_ERROR, error.getErrorCode());
        assertTrue(error.getMessage().startsWith(ChannelHttp.RATE_LIMITED_MARKER), error.getMessage());
        assertTrue(ChannelHttp.isRateLimited(error), "登录轮询要靠它继续等");
    }

    @Test
    @DisplayName("普通失败不冒充限流")
    void plainFailureIsNotRateLimited() {
        routes.put("/fail2", exchange -> reply(exchange, 500, "boom", Map.of()));

        ApiException error = assertThrows(ApiException.class,
                () -> ChannelHttp.getJson(baseUrl + "/fail2", null));

        assertFalse(ChannelHttp.isRateLimited(error));
    }

    @Test
    @DisplayName("getRaw 不跟随重定向、非 2xx 也不抛错")
    void getRawKeepsRedirect() {
        routes.put("/raw", exchange -> reply(exchange, 303, "",
                Map.of("Location", "/next", "Set-Cookie", "admin_session=key")));

        java.net.http.HttpResponse<String> response = ChannelHttp.getRaw(baseUrl + "/raw", null);

        assertEquals(303, response.statusCode());
        assertTrue(response.headers().firstValue("Set-Cookie").isPresent(),
                "换 Cookie 的握手依赖这条头，跟随重定向就会丢");
    }

    @Test
    @DisplayName("truncate 压平空白并截断超长响应")
    void truncateCollapsesAndCaps() {
        assertEquals("", ChannelHttp.truncate(null));
        // 只压平空白，不去首尾空格（原实现的语义，改它会动到日志文案）
        assertEquals(" a b c ", ChannelHttp.truncate(" a\n b\t c "));

        String longBody = "x".repeat(400);
        String truncated = ChannelHttp.truncate(longBody);
        assertEquals(303, truncated.length(), "300 字符 + 省略号");
        assertTrue(truncated.endsWith("..."));
    }

    /**
     * 分发到已注册的路径。
     *
     * @param exchange 请求
     * @throws IOException 写响应失败
     */
    private void handle(HttpExchange exchange) throws IOException {
        Handler handler = routes.get(exchange.getRequestURI().getPath());
        if (handler == null) {
            reply(exchange, 404, "no route", Map.of());
            return;
        }
        handler.handle(exchange);
    }

    /**
     * 写一个固定响应。
     *
     * @param exchange 请求
     * @param status   HTTP 状态码
     * @param body     响应体
     * @param headers  响应头
     * @throws IOException 写响应失败
     */
    private static void reply(HttpExchange exchange, int status, String body, Map<String, String> headers)
            throws IOException {
        headers.forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /**
     * 路径处理逻辑。
     */
    @FunctionalInterface
    private interface Handler {

        /**
         * 处理一次请求。
         *
         * @param exchange 请求
         * @throws IOException 写响应失败
         */
        void handle(HttpExchange exchange) throws IOException;
    }
}
