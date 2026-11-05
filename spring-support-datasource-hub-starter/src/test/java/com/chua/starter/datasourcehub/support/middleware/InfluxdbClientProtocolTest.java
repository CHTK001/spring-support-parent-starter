// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasourcehub.support.middleware;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * InfluxDB 客户端协议契约测试（桩服务，不打真实实例）。
 *
 * <p><b>为什么必须用桩</b>：环境里只有 1.x（{@code v1.11.8}）与 3.x（{@code 3.12.0}）
 * 两个真实实例，<b>没有任何 2.x</b>。v2 的两条链路——Flux 查询、以及
 * 「2.x 服务端关掉 flux-enabled 后改走 InfluxQL」——没法靠实机覆盖；而它们的正确性
 * 恰恰体现在「发了什么请求、怎么解析响应」上，用桩把契约钉死，比等真的有 2.x 再发现
 * 参数发错便宜得多。</p>
 *
 * <p>本类覆盖：v 前缀版本识别、登记版本驱动方言、Flux 发 {@code type=flux}+org、
 * InfluxQL 发 {@code type=sql}+bucket、注解 CSV 解析、以及错误必须显式抛出。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class InfluxdbClientProtocolTest {

    /**
     * 桩服务收到的请求。
     *
     * @param path  请求路径
     * @param query 查询参数
     * @param body  请求体
     */
    private record Captured(String path, Map<String, String> query, String body) {
    }

    private HttpServer server;
    private String base;
    private int port;
    private final List<Captured> captured = new ArrayList<>();
    private volatile String queryResponse = "";
    private volatile int queryStatus = 200;
    private volatile String pingVersion = "2.7.11";
    /** 最近一次请求带的 Authorization 头（2.x 的 /query 认 Token，用来断言确实带上了） */
    private final java.util.concurrent.atomic.AtomicReference<String> authHeader =
            new java.util.concurrent.atomic.AtomicReference<>();

    @BeforeEach
    void startStub() throws IOException {
        captured.clear();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        base = "http://127.0.0.1:" + port;

        server.createContext("/ping", exchange -> {
            exchange.getResponseHeaders().add("X-Influxdb-Version", pingVersion);
            respond(exchange, "", 204);
        });
        server.createContext("/api/v2/query", exchange -> respond(exchange, queryResponse, queryStatus));
        server.createContext("/api/v2/orgs", exchange ->
                respond(exchange, "{\"orgs\":[{\"id\":\"org-id-1\",\"name\":\"my-org\"}]}", 200));
        // v1 的查询入口；v1 把语句错误放在 200 响应体里，用来验证不会被静默吞掉
        server.createContext("/query", exchange -> respond(exchange, queryResponse, queryStatus));
        server.start();
    }

    @AfterEach
    void stopStub() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * 记录请求并按给定状态码回响应。
     *
     * @param exchange 交换
     * @param body     响应体
     * @param status   状态码
     * @throws IOException 写回失败
     */
    private void respond(HttpExchange exchange, String body, int status) throws IOException {
        String bodyText;
        try (InputStream in = exchange.getRequestBody()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            in.transferTo(out);
            bodyText = out.toString(StandardCharsets.UTF_8);
        }
        Map<String, String> query = new HashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw != null) {
            for (String pair : raw.split("&")) {
                if (pair.isEmpty()) {
                    continue;
                }
                int eq = pair.indexOf('=');
                String key = eq < 0 ? pair : pair.substring(0, eq);
                String value = eq < 0 ? "" : pair.substring(eq + 1);
                query.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                        URLDecoder.decode(value, StandardCharsets.UTF_8));
            }
        }
        captured.add(new Captured(exchange.getRequestURI().getPath(), query, bodyText));
        authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/csv; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    /**
     * 取最近一次打到指定路径的请求。
     *
     * @param path 路径
     * @return 请求快照
     */
    private Captured lastRequest(String path) {
        for (int i = captured.size() - 1; i >= 0; i--) {
            if (path.equals(captured.get(i).path())) {
                return captured.get(i);
            }
        }
        throw new AssertionError("没有请求过 " + path + "，实际: " + captured.stream().map(Captured::path).toList());
    }

    /**
     * 构造一个指向桩服务的数据源。
     *
     * @param version 登记的版本（"2" / "3"），空串表示未登记
     * @return 数据源
     */
    private DataSourceVO datasource(String version) {
        return new DataSourceVO(
                null, "stub-influxdb", "influxdb", "127.0.0.1", port,
                null, "", "", null, 1, null, null, null, null,
                null, null, null, null, null, null, version);
    }

    /**
     * 空值安全的取值。
     *
     * @param row 行
     * @param key 键
     * @return 文本，取不到为空串
     */
    private static String str(JsonObject row, String key) {
        Object value = row.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    @Test
    @DisplayName("版本探测认得 v 前缀：v1.11.8 判成 1.x，且 1.x 必须钉死 SQL 方言")
    void detectsV1AndForcesSqlDialect() {
        pingVersion = "v1.11.8";
        InfluxdbClient.Target target = new InfluxdbClient(null).target(datasource("2"));

        assertEquals(InfluxdbClient.Version.V1, target.version(),
                "v1.11.8 带 v 前缀，不认就会被当成 v2，从而对不存在的 /api/v2/query 发 Flux");
        assertFalse(target.usesFlux(), "1.x 没有 Flux 引擎，即使登记了 v2/Flux 也必须走 InfluxQL");
    }

    @Test
    @DisplayName("v2 实例 + 登记 v2：Flux 查询发 type=flux、带 org，并解析注解 CSV")
    void v2FluxSendsFluxWithOrg() {
        pingVersion = "2.7.11";
        queryResponse = """
                #datatype,string,long,dateTime:RFC3339,double,string,string,string
                #group,false,false,true,false,false,true,true
                #default,_result,,,,,,
                ,result,table,_time,_value,_field,_measurement,host
                ,,0,2026-10-15T09:00:00Z,42.5,usage,acctest,h1
                ,,0,2026-10-15T08:59:00Z,43.5,usage,acctest,h1
                """;

        InfluxdbClient client = new InfluxdbClient(null);
        InfluxdbClient.Target target = client.target(datasource("2"));
        assertEquals(InfluxdbClient.Version.V2, target.version());
        assertTrue(target.usesFlux(), "登记 v2 的 2.x 实例默认用 Flux");

        List<JsonObject> rows = client.query(target, "from(bucket: \"b\") |> range(start: -1h)");

        Captured request = lastRequest("/api/v2/query");
        JsonObject body = Json.getJsonObject(request.body());
        assertEquals("flux", str(body, "type"), "Flux 方言必须发 type=flux");
        assertEquals("my-org", request.query().get("org"), "v2 查询必须带 org");

        assertEquals(2, rows.size(), "注解 CSV 的数据行都要还原（不是逐行 JSON）");
        assertEquals("acctest", str(rows.get(0), "_measurement"));
        assertEquals(42.5D, ((Number) rows.get(0).get("_value")).doubleValue(), 1e-9);
        assertEquals("h1", str(rows.get(0), "host"));
    }

    @Test
    @DisplayName("v2 Flux 多 table：每个 table 重复的表头不能被当成数据行")
    void v2FluxMultiTableSkipsRepeatedHeader() {
        pingVersion = "2.7.11";
        // 真实 2.x 的注解 CSV 在每个 table 前都会重发一段 #datatype/#group/#default + 表头。
        // 只认第一个表头时，第 2 个 table 的表头会变成一行 _time=_time 的脏数据
        // （101.200.205.64 上查 from(bucket:"e2e") 实测复现）。
        queryResponse = """
                #datatype,string,long,dateTime:RFC3339,double,string,string
                #group,false,false,false,false,true,true
                #default,_result,,,,,
                ,result,table,_time,_value,_field,_measurement
                ,,0,2026-10-26T08:00:00Z,1.5,pressure,cpu
                #datatype,string,long,dateTime:RFC3339,double,string,string
                #group,false,false,false,false,true,true
                #default,_result,,,,,
                ,result,table,_time,_value,_field,_measurement
                ,,1,2026-10-26T08:01:00Z,0.6,value,cpu
                """;

        InfluxdbClient client = new InfluxdbClient(null);
        InfluxdbClient.Target target = client.target(datasource("2"));
        List<JsonObject> rows = client.query(target, "from(bucket: \"b\") |> range(start: -1h)");

        assertEquals(2, rows.size(), "两个 table 各 1 行数据，表头行不能再算一行");
        assertEquals("pressure", str(rows.get(0), "_field"));
        assertEquals("value", str(rows.get(1), "_field"), "第二个 table 的值要落到正确的列");
        assertEquals(0.6D, ((Number) rows.get(1).get("_value")).doubleValue(), 1e-9);
        for (JsonObject row : rows) {
            assertFalse("_time".equals(str(row, "_time")), "表头行泄漏进了数据行：" + row);
        }
    }

    @Test
    @DisplayName("v2 实例 + 登记 v3：InfluxQL 必须走 v1 兼容 /query（带 db 与 Token），不走 /api/v2/query")
    void v2InfluxQlUsesV1CompatQuery() {
        pingVersion = "2.7.11";
        queryResponse = "{\"results\":[{\"statement_id\":0,\"series\":[{\"name\":\"measurements\","
                + "\"columns\":[\"name\"],\"values\":[[\"cq\"]]}]}]}";

        InfluxdbClient client = new InfluxdbClient(null);
        InfluxdbClient.Target target = client.target(datasource("3"));
        assertEquals(InfluxdbClient.Version.V2, target.version(), "服务端仍是 2.x");
        assertFalse(target.usesFlux(), "登记 v3 时 2.x 实例应走 InfluxQL 方言");

        List<JsonObject> rows = client.v1Query(target, "e2e_acc", "SHOW MEASUREMENTS");

        // 关键：不能打到 /api/v2/query（真实 2.x 上只吃 Flux，InfluxQL 一律 400）
        Captured request = lastRequest("/query");
        assertTrue(request.query().get("db").contains("e2e_acc"),
                "v1 兼容 /query 用 db 参数指定目标桶，实际=" + request.query());
        assertEquals("q=SHOW+MEASUREMENTS", request.body().replace("%20", "+"),
                "语句以表单体 q= 提交");
        assertEquals(1, rows.size(), "results[].series[] 要摊平成行");
        assertEquals("cq", str(rows.get(0), "name"));
        assertTrue(captured.stream().noneMatch(c -> c.path().equals("/api/v2/query") && c.body().contains("sql")),
                "不得把 InfluxQL 发到 /api/v2/query（真实 2.x 返回 400）");
    }

    @Test
    @DisplayName("v2 带 Token 的 InfluxQL /query 必须带 Authorization（2.x 认 Token）")
    void v2InfluxQlCarriesToken() {
        pingVersion = "2.7.11";
        queryResponse = "{\"results\":[{\"statement_id\":0,\"series\":[{\"name\":\"m\","
                + "\"columns\":[\"name\"],\"values\":[[\"x\"]]}]}]}";

        InfluxdbClient client = new InfluxdbClient(null);
        // 带 token 的数据源（password 即 Token）
        DataSourceVO withToken = new DataSourceVO(
                null, "stub-influxdb", "influxdb", "127.0.0.1", port,
                null, "accorg", "my-token", null, 1, null, null, null, null,
                null, null, null, null, null, null, "3");
        InfluxdbClient.Target target = client.target(withToken);

        List<JsonObject> rows = client.v1Query(target, "accbucket", "SHOW MEASUREMENTS");
        assertEquals(1, rows.size());
        assertNotNull(authHeader.get(), "2.x 的 /query 不带 Token 会 401");
        assertTrue(authHeader.get().startsWith("Token "), "v2 用 Token 语义，实际=" + authHeader.get());
    }

    @Test
    @DisplayName("v1 语句错误（200 + results[].error）必须显式抛出，不能静默当零行")
    void v1StatementErrorIsNotSwallowed() {
        pingVersion = "v1.11.8";
        queryStatus = 200;
        queryResponse = "{\"results\":[{\"statement_id\":0,\"error\":\"error parsing query: boom\"}]}";

        InfluxdbClient client = new InfluxdbClient(null);
        InfluxdbClient.Target target = client.target(datasource(""));

        IllegalStateException error = null;
        try {
            client.v1Query(target, "_internal", "SHOW MEASUREMENTS");
        } catch (IllegalStateException e) {
            error = e;
        }
        assertNotNull(error, "语句错误必须抛出，返回空列表会把「语法错」伪装成「没有数据」");
        assertTrue(String.valueOf(error.getMessage()).contains("boom"),
                "错误信息要带服务端原文，实际: " + error.getMessage());
    }}