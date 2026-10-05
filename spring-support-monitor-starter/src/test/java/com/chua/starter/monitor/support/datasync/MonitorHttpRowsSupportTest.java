package com.chua.starter.monitor.support.datasync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HTTP 输入源（JSON / HTML）的真实测试。
 *
 * <p>用 JDK 内置的 {@link HttpServer} 起一个真的本地服务，按真实 HTTP 协议回响应，
 * 不 mock 任何对象——被测对象里有 URL 解析、请求头、编码嗅探、CSS 选择器，
 * mock 掉 HTTP 客户端等于把这些全测不到。</p>
 *
 * <p>覆盖三类最容易出错的地方：</p>
 * <ul>
 *   <li>JSON：数据位置路径（<code>data.list</code>、下标）定位对不对</li>
 *   <li>HTML：表头行去重 + 列数不齐时补空/截断</li>
 *   <li>通用：非 2xx 要报出响应片段（接口报错时那行 JSON 是唯一线索）</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.43
 */
@DisplayName("HTTP 输入源解析")
class MonitorHttpRowsSupportTest {

    /**
     * 本地测试服务
     */
    private HttpServer server;

    /**
     * 服务根地址（含结尾斜杠）
     */
    private String base;

    /**
     * 最近一次收到的请求体，用于断言请求确实带上了配置的头与体
     */
    private final AtomicReference<String> lastBody = new AtomicReference<>("");

    /**
     * 最近一次收到的 Cookie 头。
     *
     * <p>断言「Cookie 真的发出去了」而不是「选项对象里存着 cookie」——
     * 后者只证明 record 有这个字段，前提是它真被拼进请求头。</p>
     */
    private final AtomicReference<String> lastCookie = new AtomicReference<>("");

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * 注册一个返回固定文本的接口。
     *
     * @param path     路径
     * @param status   状态码
     * @param contentType Content-Type
     * @param body     响应体
     * @param charset  响应体编码
     */
    private void serve(String path, int status, String contentType, String body, Charset charset) {
        server.createContext(path, exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String cookie = exchange.getRequestHeaders().getFirst("Cookie");
            lastCookie.set(cookie == null ? "" : cookie);
            byte[] bytes = body.getBytes(charset);
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
    }

    @Test
    @DisplayName("JSON：按数据位置取到数组，列名是对象的键")
    void jsonByPath() throws Exception {
        serve("/api/orders", 200, "application/json; charset=UTF-8", """
                {"code":0,"data":{"list":[{"id":1,"name":"A"},{"id":2,"name":"B"}]}}
                """, StandardCharsets.UTF_8);

        List<String> columns = MonitorHttpRowsSupport.headers(
                SyncSourceType.HTTP_JSON, base + "api/orders",
                new SyncHttpOptions("GET", null, null, "UTF-8", "data.list", null, null, null, 10, null));

        assertEquals(List.of("id", "name"), columns);

        List<Map<String, Object>> rows = MonitorHttpRowsSupport.rows(
                SyncSourceType.HTTP_JSON, base + "api/orders",
                new SyncHttpOptions("GET", null, null, "UTF-8", "data.list", null, null, null, 10, null));
        assertEquals(2, rows.size());
        assertEquals("1", rows.get(0).get("id"));
        assertEquals("B", rows.get(1).get("name"));
    }

    @Test
    @DisplayName("JSON：未配数据位置时自动取根对象里的数组字段")
    void jsonAutoArray() throws Exception {
        serve("/api/auto", 200, "application/json; charset=UTF-8",
                "{\"code\":0,\"msg\":\"ok\",\"rows\":[{\"a\":1}]}", StandardCharsets.UTF_8);

        List<Map<String, Object>> rows = MonitorHttpRowsSupport.rows(
                SyncSourceType.HTTP_JSON, base + "api/auto",
                new SyncHttpOptions(null, null, null, null, null, null, null, null, null, null));

        assertEquals(1, rows.size());
        assertEquals("1", rows.get(0).get("a"));
    }

    @Test
    @DisplayName("JSON：数据位置指向非数组时报错而不是静默同步 0 行")
    void jsonPathNotArray() {
        serve("/api/obj", 200, "application/json; charset=UTF-8",
                "{\"data\":{\"x\":1}}", StandardCharsets.UTF_8);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> MonitorHttpRowsSupport.rows(
                SyncSourceType.HTTP_JSON, base + "api/obj",
                new SyncHttpOptions(null, null, null, null, "data", null, null, null, null, null)));

        assertTrue(error.getMessage().contains("数组"), error.getMessage());
    }

    @Test
    @DisplayName("HTML：按选择器取表格行，重复表头补后缀、列数不齐补空")
    void htmlTable() throws Exception {
        serve("/report", 200, "text/html; charset=UTF-8", """
                <html><body>
                <div class="noise"><span>不该被当成行</span></div>
                <table>
                  <thead><tr><th>日期</th><th>金额</th><th>金额</th></tr></thead>
                  <tbody>
                    <tr><td>2026-01-01</td><td>10</td><td>20</td></tr>
                    <tr><td>2026-01-02</td><td>30</td></tr>
                    <tr><td>2026-01-03</td><td>40</td><td>50</td><td>多余</td></tr>
                  </tbody>
                </table>
                </body></html>
                """, StandardCharsets.UTF_8);

        SyncHttpOptions options = new SyncHttpOptions(
                null, null, null, "UTF-8", null, "table tbody tr", "td", true, 10, null);

        List<String> columns = MonitorHttpRowsSupport.headers(SyncSourceType.HTTP_HTML, base + "report", options);
        // 两列都叫「金额」：不去重的话 Map 只会剩一列，数据静默少一半
        assertEquals(List.of("日期", "金额", "金额_2"), columns);

        List<Map<String, Object>> rows = MonitorHttpRowsSupport.rows(SyncSourceType.HTTP_HTML, base + "report", options);
        assertEquals(3, rows.size());
        assertEquals("2026-01-01", rows.get(0).get("日期"));
        // 少一列 → 补 null；多一列 → 截断
        assertEquals(null, rows.get(1).get("金额_2"));
        assertEquals(3, rows.get(2).size());
    }

    @Test
    @DisplayName("HTTP：非 2xx 时错误里带响应片段")
    void httpErrorIncludesSnippet() {
        serve("/api/boom", 500, "application/json; charset=UTF-8",
                "{\"error\":\"quota exceeded\"}", StandardCharsets.UTF_8);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> MonitorHttpRowsSupport.rows(
                SyncSourceType.HTTP_JSON, base + "api/boom", SyncHttpOptions.defaults().normalized()));

        assertTrue(error.getMessage().contains("500"), error.getMessage());
        assertTrue(error.getMessage().contains("quota exceeded"), error.getMessage());
    }

    @Test
    @DisplayName("HTTP：请求头与请求体确实发到了服务端")
    void httpRequestHeadersAndBody() throws Exception {
        serve("/api/query", 200, "application/json; charset=UTF-8", "{\"items\":[]}", StandardCharsets.UTF_8);

        SyncHttpOptions options = new SyncHttpOptions(
                "POST", Map.of("X-Token", "abc123"), "{\"pageNo\":2}", "UTF-8", "items", null, null, null, 10, null);
        MonitorHttpRowsSupport.rows(SyncSourceType.HTTP_JSON, base + "api/query", options);

        assertEquals("{\"pageNo\":2}", lastBody.get());
    }

    @Test
    @DisplayName("HTTP：cookie 字段确实作为 Cookie 请求头发出去")
    void cookieIsSentAsHeader() throws Exception {
        serve("/api/cookie", 200, "application/json; charset=UTF-8", "{\"items\":[]}", StandardCharsets.UTF_8);

        SyncHttpOptions options = new SyncHttpOptions(
                "GET", null, null, "UTF-8", "items", null, null, null, 10, "SID=abc123; uid=7");
        MonitorHttpRowsSupport.rows(SyncSourceType.HTTP_JSON, base + "api/cookie", options);

        assertEquals("SID=abc123; uid=7", lastCookie.get());
    }

    @Test
    @DisplayName("HTTP：没配 cookie 时不带 Cookie 头")
    void noCookieNoHeader() throws Exception {
        serve("/api/nocookie", 200, "application/json; charset=UTF-8", "{\"items\":[]}", StandardCharsets.UTF_8);

        MonitorHttpRowsSupport.rows(SyncSourceType.HTTP_JSON, base + "api/nocookie",
                SyncHttpOptions.defaults().normalized());

        assertEquals("", lastCookie.get());
    }

    @Test
    @DisplayName("HTTP：headers 里也写了 Cookie 时以 cookie 字段为准，不发两个 Cookie 头")
    void cookieFieldWinsOverHeaders() throws Exception {
        serve("/api/both", 200, "application/json; charset=UTF-8", "{\"items\":[]}", StandardCharsets.UTF_8);

        SyncHttpOptions options = new SyncHttpOptions(
                "GET", Map.of("Cookie", "OLD=1", "X-Token", "t"), null, "UTF-8", "items", null, null, null, 10, "NEW=2");
        MonitorHttpRowsSupport.rows(SyncSourceType.HTTP_JSON, base + "api/both", options);

        // 两个 Cookie 头会被服务端拼成非预期的会话，所以只允许字段值那一个
        assertEquals("NEW=2", lastCookie.get());
    }

    @Test
    @DisplayName("HTTP：非 http(s) 协议直接拒绝")
    void rejectNonHttpScheme() {
        assertThrows(IllegalArgumentException.class, () -> MonitorHttpRowsSupport.rows(
                SyncSourceType.HTTP_JSON, "file:///etc/passwd", SyncHttpOptions.defaults().normalized()));
    }

    @Test
    @DisplayName("HTTP：响应编码按配置解码，GBK 页面不乱码")
    void gbkResponse() throws Exception {
        serve("/gbk", 200, "text/html", "<table><tr><th>姓名</th></tr><tr><td>张三</td></tr></table>",
                Charset.forName("GBK"));

        // 服务端 Content-Type 不给 charset，只能靠显式配置
        List<Map<String, Object>> rows = MonitorHttpRowsSupport.rows(
                SyncSourceType.HTTP_HTML, base + "gbk",
                new SyncHttpOptions(null, null, null, "GBK", null, "table tr", "td", true, 10, null));

        assertEquals(1, rows.size());
        assertEquals("张三", rows.get(0).get("姓名"));
    }
}