// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.channel.magpie;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 测试用的「假 magpie」。
 *
 * <p>同时扮演两个面：</p>
 * <ul>
 *   <li>管理面 {@code /api/*}：{@code ?k=} → {@code 303 + Set-Cookie} 握手，
 *       之后要求 {@code magpie_web_<端口>} Cookie；</li>
 *   <li>数据面 {@code /v1/*}：不校验 Cookie，只记录 {@code Authorization}。</li>
 * </ul>
 *
 * @since 4.0.0.53
 * @author CH
 */
final class FakeMagpieServer implements AutoCloseable {

    /**
     * 页面 Key。
     */
    static final String PAGE_KEY = "page-key-0123456789";

    /**
     * 假服务（随机端口）。
     */
    private final HttpServer server;

    /**
     * 各路径命中次数。
     */
    private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();

    /**
     * 最近一次 POST 的请求体。
     */
    private final AtomicReference<String> lastBody = new AtomicReference<>("");

    /**
     * 最近一次数据面请求的 Authorization。
     */
    private final AtomicReference<String> dataPlaneAuthorization = new AtomicReference<>("");

    /**
     * 启动假服务。
     *
     * @throws IOException 端口绑定失败
     */
    FakeMagpieServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    /**
     * 服务根地址。
     *
     * @return 形如 {@code http://127.0.0.1:12345}
     */
    String origin() {
        return "http://127.0.0.1:" + port();
    }

    /**
     * 页面 Cookie 名（含随机端口）。
     *
     * @return 形如 {@code magpie_web_12345}
     */
    String cookieName() {
        return MagpieSupport.COOKIE_PREFIX + port();
    }

    /**
     * 某路径的命中次数。
     *
     * @param path 路径
     * @return 次数
     */
    int hits(String path) {
        AtomicInteger count = hits.get(path);
        return count == null ? 0 : count.get();
    }

    /**
     * 最近一次 POST 请求体。
     *
     * @return 请求体
     */
    String lastBody() {
        return lastBody.get();
    }

    /**
     * 最近一次数据面请求的 Authorization。
     *
     * @return 请求头；没有则为空串
     */
    String dataPlaneAuthorization() {
        return dataPlaneAuthorization.get();
    }

    /**
     * 停止假服务。
     */
    @Override
    public void close() {
        server.stop(0);
    }

    /**
     * 端口。
     *
     * @return 端口
     */
    private int port() {
        return server.getAddress().getPort();
    }

    /**
     * 路由入口。
     *
     * @param exchange 请求
     * @throws IOException 写响应失败
     */
    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        hits.computeIfAbsent(path, key -> new AtomicInteger()).incrementAndGet();
        if ("/".equals(path)) {
            handshake(exchange);
            return;
        }
        if (path.startsWith("/v1/")) {
            dataPlaneAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        }
        // 管理面走页面 Cookie；数据面在 magpie 里是另一套鉴权，本机放行。
        if (path.startsWith("/api/") && !authorized(exchange)) {
            respond(exchange, 401, "{\"error\":\"unauthorized\"}");
            return;
        }
        if ("POST".equals(exchange.getRequestMethod())) {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        }
        switch (path) {
            case "/api/providers" -> respond(exchange, 200, """
                    {"vendors":["workbuddy","trae"],"providers":[
                      {"id":"codex","name":"Codex","models":[{"id":"gpt-5","name":"GPT-5"}],
                       "keyList":[{"id":"k1","key":"sk-secret"}]}
                    ]}""");
            case "/api/provider/save" -> respond(exchange, 200, "{\"providers\":[]}");
            case "/api/provider/delete" -> respond(exchange, 200, "{\"providers\":[]}");
            case "/api/provider/models" -> respond(exchange, 200,
                    "{\"count\":2,\"dropped\":0,\"provider\":{\"models\":[{\"id\":\"alpha\"},{\"id\":\"beta\"}]}}");
            case "/api/usage/workbuddy-checkin" -> respond(exchange, 200,
                    "[{\"account\":\"a1\",\"message\":\"signed\"}]");
            case "/api/usage/trae-checkin",
                 "/api/usage/minimax-checkin",
                 "/api/usage/qoder-checkin" -> respond(exchange, 200, "[]");
            case "/v1/magpie/quotas" -> respond(exchange, 200, """
                    {"data":[
                      {"provider":"codex","kind":"balance","balance":"12"},
                      {"provider":"claude","kind":"subscription","plan":"pro",
                       "windows":[{"name":"5h","remaining":30,"limit":40,"unit":"credit",
                                   "resetsAt":1762500000000}]}
                    ]}""");
            case "/v1/models" -> respond(exchange, 200,
                    "{\"data\":[{\"id\":\"codex/gpt-5\",\"owned_by\":\"codex\"}]}");
            default -> respond(exchange, 404, "{\"error\":\"not found\"}");
        }
    }

    /**
     * 页面握手：{@code ?k=} 正确时 303 + Set-Cookie，否则 401。
     *
     * @param exchange 请求
     * @throws IOException 写响应失败
     */
    private void handshake(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || !query.contains("k=" + PAGE_KEY)) {
            respond(exchange, 401, "{\"error\":\"bad page key\"}");
            return;
        }
        exchange.getResponseHeaders().add("Set-Cookie", cookieName() + "=" + PAGE_KEY + "; Path=/; HttpOnly");
        exchange.sendResponseHeaders(303, -1);
        exchange.close();
    }

    /**
     * 校验页面 Cookie。
     *
     * @param exchange 请求
     * @return true 表示通过
     */
    private boolean authorized(HttpExchange exchange) {
        String cookie = exchange.getRequestHeaders().getFirst("Cookie");
        return cookie != null && cookie.contains(cookieName() + "=" + PAGE_KEY);
    }

    /**
     * 写 JSON 响应。
     *
     * @param exchange 请求
     * @param status   状态码
     * @param body     响应体
     * @throws IOException 写响应失败
     */
    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
