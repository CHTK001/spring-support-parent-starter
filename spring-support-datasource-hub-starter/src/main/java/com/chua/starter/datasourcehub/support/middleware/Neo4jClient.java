package com.chua.starter.datasourcehub.support.middleware;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.json.JsonArray;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Neo4j HTTP 管理侧客户端：走事务提交接口 {@code POST /db/{database}/tx/commit}，不经 Bolt。
 *
 * <p>选 HTTP 而不是引 Bolt 驱动，是因为管理端只需要「发一句 Cypher、拿回列和行」，
 * HTTP 事务接口从 3.5 到 5.x 契约稳定，且不额外拖一个 netty 依赖进监控 starter。
 * 代价是只认 HTTP 端口（默认 7474），数据源行的 {@code port} 因此填 HTTP 端口而非 Bolt 的 7687。</p>
 *
 * <p>{@code database} 字段放图数据库名（留空按 {@value #DEFAULT_DATABASE}）；填完整 URL 时
 * 整段路径按反向代理前缀处理，图库名退回默认值，这一点与 Nacos/InfluxDB 的口径一致。</p>
 *
 * <p>Neo4j 把 Cypher 的报错随 400 一起放在响应体的 {@code errors} 数组里，状态码本身没有信息量，
 * 因此这里读 {@link MiddlewareHttp#postJsonResponse} 而不是被状态码截断的 {@code postJson}。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Neo4jClient {

    /**
     * 数据源类型标识
     */
    public static final String DS_TYPE = "neo4j";

    /**
     * 未登记库名时使用的图数据库名
     */
    public static final String DEFAULT_DATABASE = "neo4j";

    /**
     * Neo4j HTTP 端口，数据源未填端口时按此拼接
     */
    private static final int DEFAULT_HTTP_PORT = 7474;

    /**
     * Cypher 报错文本的回填长度，服务端报错常带整条语句与指针行
     */
    private static final int ERROR_TEXT_LIMIT = 300;

    /**
     * 连通性探测语句：不读任何业务数据，只证明地址通、凭据对、目标图数据库存在
     */
    private static final String PROBE_CYPHER = "RETURN 1 AS ok";

    private final DatasourceService datasourceService;

    /**
     * 一次 Neo4j 调用所需的「地址 + 凭据 + 图数据库名」。
     *
     * @param endpoint 地址与凭据
     * @param database 图数据库名
     * @author CH
     * @since 4.0.0.42
     */
    public record Target(MiddlewareEndpoint endpoint, String database) {

        /**
         * 事务提交接口路径。
         *
         * @return 以 {@code /} 开头的路径
         */
        public String commitPath() {
            return "/db/" + URLEncoder.encode(database, StandardCharsets.UTF_8) + "/tx/commit";
        }
    }

    /**
     * 一句 Cypher 的执行结果。
     *
     * @param columns 列名，保持服务端顺序
     * @param rows    行数据，列名 → 原始 JSON 值
     * @param server  响应头 {@code Server} 原文，形如 {@code Neo4j/5.26.0}，服务端不给时为空串
     * @author CH
     * @since 4.0.0.42
     */
    public record Result(List<String> columns, List<Map<String, Object>> rows, String server) {

        /**
         * 规范构造器：列名与行数据做防御性拷贝。
         *
         * <p>value class 前置条件——集合组件必须深不可变。列名取自 {@code String.valueOf}，
         * 行元素恒为非 null 的 LinkedHashMap，元素本身不会为 null，故可用
         * {@link List#copyOf}；但行内的值可能是 JSON null，将来若再加深拷贝
         * 不可改用 {@link Map#copyOf}（null 值会抛 NPE）。</p>
         *
         * @param columns 列名，保持服务端顺序
         * @param rows    行数据，列名 → 原始 JSON 值
         * @param server  响应头 {@code Server} 原文，形如 {@code Neo4j/5.26.0}，服务端不给时为空串
         */
        public Result {
            columns = List.copyOf(Objects.requireNonNull(columns, "columns 不能为 null"));
            rows = List.copyOf(Objects.requireNonNull(rows, "rows 不能为 null"));
        }
    }

    /**
     * 服务端版本探测结果。
     *
     * @param version 版本号，取不到时为空串
     * @param edition 版本类型（community/enterprise），取不到时为空串
     * @author CH
     * @since 4.0.0.42
     */
    public record Server(String version, String edition) {
    }

    /**
     * 解析数据源对应的访问目标。
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 访问目标
     * @throws IllegalStateException 数据源不存在、类型不符或未登记
     */
    public Target target(Long datasourceId) {
        return target(MiddlewareDatasources.require(datasourceService, datasourceId, DS_TYPE));
    }

    /**
     * 用数据源行（可为未落库的草稿）解析访问目标。
     *
     * @param ds 数据源配置
     * @return 访问目标
     */
    public Target target(DataSourceVO ds) {
        String database = trim(ds.database());
        if (isAbsoluteUrl(database)) {
            URI uri = URI.create(database);
            MiddlewareEndpoint endpoint = new MiddlewareEndpoint(uri.getScheme() + "://" + uri.getAuthority(),
                    trimSlash(uri.getPath()), trim(ds.username()), blankToEmpty(ds.password()));
            return new Target(endpoint, DEFAULT_DATABASE);
        }
        String scheme = "https".equalsIgnoreCase(database) ? "https" : "http";
        int port = ds.port() == null ? DEFAULT_HTTP_PORT : ds.port();
        String host = trim(ds.host());
        MiddlewareEndpoint endpoint = new MiddlewareEndpoint(scheme + "://" + host + (port > 0 ? ":" + port : ""), "",
                trim(ds.username()), blankToEmpty(ds.password()));
        return new Target(endpoint, database.isEmpty() || "https".equalsIgnoreCase(database)
                ? DEFAULT_DATABASE : database);
    }

    /**
     * 探测连通性并尽力取版本信息。
     *
     * <p>先跑一句不碰业务数据的 {@code RETURN 1 AS ok}：地址不通、凭据不对、图数据库不存在都会在
     * 这一步暴露出来。根资源 {@code GET /} 在部分部署里被关闭，因此只作为版本号的尽力来源，
     * 拿不到就退回事务响应的 {@code Server} 头，探测结论不受它影响。</p>
     *
     * @param target 访问目标
     * @return 版本与版本类型，取不到时为空串
     * @throws IllegalStateException 连接失败、凭据不符或 Cypher 被拒
     */
    public Server probeServer(Target target) {
        Result probe = run(target, PROBE_CYPHER);
        String version = "";
        String edition = "";
        try {
            MiddlewareHttp.Response root = MiddlewareHttp.getResponse(target.endpoint().url("/"), Map.of(),
                    auth(target.endpoint()));
            if (root.status() >= 200 && root.status() < 300) {
                JsonObject json = Json.getJsonObject(root.body());
                version = text(json.get("neo4j_version"));
                edition = text(json.get("neo4j_edition"));
            }
        } catch (Exception e) {
            log.debug("[neo4j] 根资源不可用: {}", e.getMessage());
        }
        return new Server(version.isEmpty() ? fromServerHeader(probe.server()) : version, edition);
    }

    /**
     * 从 {@code Server} 头取版本号，形如 {@code Neo4j/5.26.0}。
     *
     * @param server 响应头原文
     * @return 版本号，取不到为空串
     */
    private static String fromServerHeader(String server) {
        String value = trim(server);
        int slash = value.indexOf('/');
        return slash >= 0 && slash + 1 < value.length() ? value.substring(slash + 1).trim() : "";
    }

    /**
     * 执行一句 Cypher。
     *
     * @param target 访问目标
     * @param cypher 语句，原样提交
     * @return 列名与行数据
     * @throws IllegalStateException 请求失败或服务端回报 Cypher 错误
     */
    public Result run(Target target, String cypher) {
        Map<String, Object> statement = new LinkedHashMap<>();
        statement.put("statement", cypher);
        String body = Json.toJson(Map.of("statements", List.of(statement)));
        String path = target.commitPath();
        MiddlewareHttp.Response response;
        try {
            response = MiddlewareHttp.postJsonResponse(target.endpoint().url(path), Map.of(), body,
                    auth(target.endpoint()));
        } catch (Exception e) {
            throw failure(target.endpoint(), path, e);
        }
        JsonObject json = Json.getJsonObject(response.body());
        String error = errorOf(json);
        if (error != null) {
            throw new IllegalStateException(error);
        }
        int status = response.status();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("Neo4j 返回 " + status + "：" + abbreviate(response.body()));
        }
        return parse(json, response.header("Server"));
    }

    /**
     * 把事务响应还原成列名与行数据。
     *
     * @param json   响应体
     * @param server 响应头 {@code Server} 原文
     * @return 执行结果，服务端未给 results 时为空表
     */
    private static Result parse(JsonObject json, String server) {
        JsonArray results = json.getJsonArray("results");
        if (results.size() == 0) {
            return new Result(List.of(), List.of(), server);
        }
        JsonObject first = results.getJsonObject(0);
        List<String> columns = new ArrayList<>();
        JsonArray rawColumns = first.getJsonArray("columns");
        for (int i = 0; i < rawColumns.size(); i++) {
            columns.add(String.valueOf(rawColumns.get(i)));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        JsonArray data = first.getJsonArray("data");
        for (int i = 0; i < data.size(); i++) {
            JsonArray row = data.getJsonObject(i).getJsonArray("row");
            Map<String, Object> mapped = new LinkedHashMap<>();
            for (int c = 0; c < columns.size() && c < row.size(); c++) {
                mapped.put(columns.get(c), row.get(c));
            }
            rows.add(mapped);
        }
        return new Result(columns, rows, server);
    }

    /**
     * 取事务响应里的第一条 Cypher 错误。
     *
     * @param json 响应体
     * @return {@code 错误码: 错误信息}，无错误时为 null
     */
    private static String errorOf(JsonObject json) {
        JsonArray errors = json.getJsonArray("errors");
        if (errors.size() == 0) {
            return null;
        }
        JsonObject first = errors.getJsonObject(0);
        String code = text(first.get("code"));
        String message = text(first.get("message"));
        String detail = abbreviate(message.isEmpty() ? code : (code.isEmpty() ? message : code + ": " + message));
        return detail.isEmpty() ? "Neo4j 未给出失败原因" : detail;
    }

    /**
     * 组装请求头：登记了用户名才带 Basic 凭据。
     *
     * @param endpoint 端点
     * @return 请求头
     */
    private static Map<String, String> auth(MiddlewareEndpoint endpoint) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        if (!endpoint.authed()) {
            return headers;
        }
        String raw = endpoint.username() + ":" + endpoint.password();
        headers.put("Authorization", "Basic " + Base64.getEncoder()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
        return headers;
    }

    /**
     * 把传输层异常换成带地址的可读原因。
     *
     * @param endpoint 端点
     * @param path     请求路径
     * @param e        原始异常
     * @return 统一异常
     */
    private static IllegalStateException failure(MiddlewareEndpoint endpoint, String path, Exception e) {
        String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        String url = endpoint == null ? path : endpoint.url(path);
        log.warn("[neo4j] 调用 {} 失败: {}", url, reason);
        return new IllegalStateException("调用 Neo4j " + url + " 失败: " + reason, e);
    }

    /**
     * 截断过长的错误文本。
     *
     * @param text 原文
     * @return 可读片段
     */
    private static String abbreviate(String text) {
        String value = trim(text).replaceAll("\\s+", " ");
        return value.length() <= ERROR_TEXT_LIMIT ? value : value.substring(0, ERROR_TEXT_LIMIT) + "...";
    }

    /**
     * 把 JSON 值转成文本。
     *
     * @param value JSON 值，可为空
     * @return 文本，null 转空串
     */
    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /**
     * 判断填写值是否已经是完整 URL。
     *
     * @param value 库名字段原值
     * @return 以 http(s):// 开头为真
     */
    private static boolean isAbsoluteUrl(String value) {
        return value.regionMatches(true, 0, "http://", 0, 7)
                || value.regionMatches(true, 0, "https://", 0, 8);
    }

    /**
     * 规整路径：补前导斜杠、去结尾斜杠。
     *
     * @param path 原始路径
     * @return 规整结果，空值返回空串
     */
    private static String trimSlash(String path) {
        String value = trim(path);
        if (value.isEmpty() || "/".equals(value)) {
            return "";
        }
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    /**
     * 空值安全的去空白。
     *
     * @param value 原值
     * @return 去空白结果，null 转空串
     */
    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * null 转空串。
     *
     * @param value 原值
     * @return 非 null 值
     */
    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}
