package com.chua.starter.datasourcehub.support.middleware;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 中间件通道「按操作指定超时」的行为测试。
 *
 * <p><b>为什么需要</b>：共享通道的默认超时是 10s，但 ES 的建/删索引与
 * {@code _cluster/settings?include_defaults=true} 这类重操作会冲高，实测在真机上分别出现过
 * 建索引 20.6s、集群设置读取 {@code request timed out}（日志 6 次）。调用方必须能按操作放宽，
 * 且放宽必须真的生效——否则只是把常量抄了一遍。</p>
 *
 * <p>用 JDK 自带 {@link HttpServer} 造一个「慢端点」：短超时必须抛错、长超时必须拿到结果，
 * 从而证明超时参数确实传到了请求上。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class MiddlewareHttpTimeoutTest {

    /** 慢端点固定延迟：要明显大于短超时、小于长超时 */
    private static final long SLOW_DELAY_MS = 1500L;

    /** 短超时：小于延迟，必然触发超时 */
    private static final Duration SHORT = Duration.ofMillis(200);

    /** 长超时：大于延迟，必然成功 */
    private static final Duration LONG = Duration.ofSeconds(10);

    /** 可抛 IOException 的动作（避免把受检异常包成 RuntimeException） */
    @FunctionalInterface
    private interface IoAction {
        void run() throws IOException;
    }

    private HttpServer server;

    private String base;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(SLOW_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange);
        });
        server.createContext("/fast", MiddlewareHttpTimeoutTest::respond);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    /** 断言调用在短超时下以 IOException 失败 */
    private static void assertTimesOut(IoAction action, String what) {
        IOException error = null;
        try {
            action.run();
        } catch (IOException e) {
            error = e;
        }
        assertNotNull(error, what + " 应在短超时下抛 IOException");
    }

    @Test
    @DisplayName("GET：短超时对慢端点报错，放宽后能读到结果")
    void getHonoursTimeout() throws IOException {
        assertTimesOut(() -> MiddlewareHttp.get(base + "/slow", Map.of(), Map.of(), SHORT), "GET(短超时)");
        assertEquals("ok", MiddlewareHttp.get(base + "/slow", Map.of(), Map.of(), LONG),
                "放宽超时后同一条慢端点必须成功，证明超时参数真的生效");
    }

    @Test
    @DisplayName("PUT JSON：指定超时同样生效（ES 建索引走这条）")
    void putJsonHonoursTimeout() throws IOException {
        assertTimesOut(() -> MiddlewareHttp.putJson(base + "/slow", Map.of(), "{}", Map.of(), SHORT), "PUT(短超时)");
        assertEquals("ok", MiddlewareHttp.putJson(base + "/slow", Map.of(), "{}", Map.of(), LONG));
    }

    @Test
    @DisplayName("DELETE：指定超时同样生效（ES 删索引走这条）")
    void deleteHonoursTimeout() throws IOException {
        assertTimesOut(() -> MiddlewareHttp.delete(base + "/slow", Map.of(), Map.of(), SHORT), "DELETE(短超时)");
        assertEquals("ok", MiddlewareHttp.delete(base + "/slow", Map.of(), Map.of(), LONG));
    }

    @Test
    @DisplayName("不带超时参数的重载仍走默认超时，快端点不受影响")
    void defaultOverloadStillWorks() throws IOException {
        long start = System.currentTimeMillis();
        assertEquals("ok", MiddlewareHttp.get(base + "/fast", Map.of(), Map.of()));
        assertTrue(System.currentTimeMillis() - start < 5000, "快端点不应触发任何超时");
    }
}
