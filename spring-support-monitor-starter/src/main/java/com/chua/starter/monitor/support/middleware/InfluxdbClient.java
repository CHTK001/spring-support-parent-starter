package com.chua.starter.monitor.support.middleware;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.json.JsonArray;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * InfluxDB 2.x HTTP API 客户端。
 *
 * <p>凭据落在数据源行的两个字段上：{@code password} 存 API Token（请求头
 * {@code Authorization: Token xxx}），{@code username} 存 org 名。org 留空时向
 * {@code /api/v2/orgs} 取第一个组织并缓存——Token 本身就是按 org 授权的，
 * 单实例通常只有一个可选组织。</p>
 *
 * <p>{@code database} 字段沿用 Nacos 的口径：填完整 URL 时按 URL 走（反向代理挂子路径），
 * 填 {@code https} 时用 TLS，其余情况不影响本类型的地址拼接。</p>
 *
 * <p>本类只负责「带上凭据、发请求、把响应读成 JSON」，Flux 语句与结构解析在
 * {@code InfluxdbManageServiceImpl} 里完成。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InfluxdbClient {

    /**
     * 数据源类型标识
     */
    public static final String DS_TYPE = "influxdb";

    /**
     * 单次 Bucket 列表请求的条数上限，InfluxDB 服务端同值
     */
    private static final int BUCKET_PAGE_SIZE = 100;

    /**
     * Bucket 列表翻到多少条为止，避免超大实例把内存打满
     */
    private static final int BUCKET_LIMIT = 500;

    private static final String QUERY_PATH = "/api/v2/query";

    private final DatasourceService datasourceService;

    /**
     * 端点地址 → org 名，避免每次请求都回查一次 /api/v2/orgs
     */
    private final Map<String, String> orgCache = new ConcurrentHashMap<>();

    /**
     * 一次 InfluxDB 调用所需的「地址 + 凭据 + org」。
     *
     * @param endpoint 地址与凭据
     * @param org      组织名，未登记且探测不到时为空串
     */
    public record Target(MiddlewareEndpoint endpoint, String org) {

        /**
         * 带上 org 的查询参数。
         *
         * @param pairs 其余键值交替数组
         * @return 查询参数，org 为空时不带 org 键（由服务端按 Token 默认组织处理）
         */
        public Map<String, String> query(String... pairs) {
            String[] all = new String[pairs.length + (org.isEmpty() ? 0 : 2)];
            int i = 0;
            if (!org.isEmpty()) {
                all[i++] = "org";
                all[i++] = org;
            }
            System.arraycopy(pairs, 0, all, i, pairs.length);
            return MiddlewareNamespace.query(all);
        }
    }

    /**
     * 解析数据源对应的访问目标。
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 访问目标
     * @throws IllegalStateException 数据源不存在、类型不符或未登记
     */
    public Target target(Long datasourceId) {
        return target(requireDatasource(datasourceId));
    }

    /**
     * 用数据源行（可为未落库的草稿）解析访问目标。
     *
     * @param ds 数据源配置
     * @return 访问目标
     */
    public Target target(DataSourceVO ds) {
        MiddlewareEndpoint endpoint = endpoint(ds);
        return new Target(endpoint, org(endpoint));
    }

    /**
     * 查询健康状态，用于连通性测试与版本回显。
     *
     * @param target 访问目标
     * @return 健康响应
     * @throws IllegalStateException 请求失败
     */
    public JsonObject health(Target target) {
        return getObject(target, "/health", Map.of());
    }

    /**
     * 列出 org 下的全部 Bucket。
     *
     * @param target 访问目标
     * @return Bucket 原始条目
     * @throws IllegalStateException 请求失败
     */
    public JsonArray buckets(Target target) {
        JsonArray all = new JsonArray();
        int offset = 0;
        while (offset < BUCKET_LIMIT) {
            JsonObject json = getObject(target, "/api/v2/buckets",
                    target.query("offset", String.valueOf(offset), "limit", String.valueOf(BUCKET_PAGE_SIZE)));
            JsonArray page = json.getJsonArray("buckets");
            for (int i = 0; i < page.size(); i++) {
                all.add(page.get(i));
            }
            if (page.size() < BUCKET_PAGE_SIZE) {
                break;
            }
            offset += page.size();
        }
        return all;
    }

    /**
     * 执行一条 Flux 查询并把逐行 JSON 响应还原成行数据。
     *
     * <p>InfluxDB 在 {@code Accept: application/json} + {@code tableResponse: false} 下按行输出 JSON，
     * 每行一条记录；查询失败时会在流里补一行 {@code {"type":"error"}}。</p>
     *
     * @param target 访问目标
     * @param flux   Flux 语句
     * @return 行数据，保持服务端给出的列顺序
     * @throws IllegalStateException 请求失败或服务端回报查询错误
     */
    public List<JsonObject> query(Target target, String flux) {
        Map<String, Object> dialect = new LinkedHashMap<>();
        dialect.put("tableResponse", false);
        dialect.put("annotations", List.of());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", flux);
        body.put("type", "flux");
        body.put("dialect", dialect);
        String text = postJson(target, QUERY_PATH, target.query(), Json.toJson(body));
        List<JsonObject> rows = new ArrayList<>();
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            JsonObject item = Json.getJsonObject(trimmed);
            String type = text(item.getObject("type"));
            if ("error".equals(type)) {
                throw new IllegalStateException("Flux 查询失败: " + firstNonBlank(
                        text(item.getObject("error")), text(item.getObject("message")), trimmed));
            }
            if ("stats".equals(type)) {
                continue;
            }
            item.entrySet().removeIf(entry -> "type".equals(entry.getKey()));
            rows.add(item);
        }
        return rows;
    }

    /**
     * 读取组织列表，用于取 orgID 创建 Bucket 以及回显 Org 信息。
     *
     * @param target 访问目标
     * @return /api/v2/orgs 原始 JSON
     * @throws IllegalStateException 请求失败
     */
    public JsonObject orgs(Target target) {
        return getObject(target, "/api/v2/orgs", Map.of());
    }

    /**
     * 新建 Bucket。
     *
     * <p>创建必须带 {@code orgID}，因此先读一次 {@code /api/v2/orgs} 取首个组织的 ID；
     * {@code retentionHours} 为空或非正时表示永久保留（不传 retentionRules）。</p>
     *
     * @param target         访问目标
     * @param name           Bucket 名
     * @param retentionHours 保留时长（小时），空或非正表示永久
     * @param description    描述，可空
     * @return 创建后的 Bucket 原始 JSON
     * @throws IllegalStateException 请求失败或无可用组织
     */
    public JsonObject createBucket(Target target, String name, Long retentionHours, String description) {
        JsonArray orgs = orgs(target).getJsonArray("orgs");
        if (orgs == null || orgs.size() == 0) {
            throw new IllegalStateException("未找到可用的组织(org)，无法创建 Bucket");
        }
        String orgId = text(orgs.getJsonObject(0).get("id"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orgID", orgId);
        body.put("name", name);
        if (description != null && !description.isBlank()) {
            body.put("description", description);
        }
        if (retentionHours != null && retentionHours > 0L) {
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("type", "expire");
            rule.put("everySeconds", retentionHours * 3600L);
            body.put("retentionRules", List.of(rule));
        }
        String resp = postJson(target, "/api/v2/buckets", target.query(), Json.toJson(body));
        return Json.getJsonObject(resp);
    }

    /**
     * 删除 Bucket。
     *
     * @param target   访问目标
     * @param bucketId Bucket ID（UUID）
     * @throws IllegalStateException 请求失败
     */
    public void deleteBucket(Target target, String bucketId) {
        try {
            MiddlewareHttp.delete(target.endpoint().url("/api/v2/buckets/" + trim(bucketId)), target.query());
        } catch (Exception e) {
            throw failure(target.endpoint(), "/api/v2/buckets", e);
        }
    }

    /**
     * 编辑 Bucket 保留期（PATCH）。
     *
     * <p>{@code retentionHours} 为空或非正时传空 retentionRules，表示永久保留。</p>
     *
     * @param target         访问目标
     * @param bucketId       Bucket ID（UUID）
     * @param retentionHours 新的保留时长（小时），空或非正表示永久
     * @throws IllegalStateException 请求失败
     */
    public void updateBucketRetention(Target target, String bucketId, Long retentionHours) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (retentionHours != null && retentionHours > 0L) {
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("type", "expire");
            rule.put("everySeconds", retentionHours * 3600L);
            body.put("retentionRules", List.of(rule));
        } else {
            body.put("retentionRules", List.of());
        }
        Map<String, String> heads = new LinkedHashMap<>(headers(target.endpoint()));
        heads.put("Accept", "application/json");
        try {
            MiddlewareHttp.patchJson(target.endpoint().url("/api/v2/buckets/" + trim(bucketId)),
                    target.query(), Json.toJson(body), heads);
        } catch (Exception e) {
            throw failure(target.endpoint(), "/api/v2/buckets", e);
        }
    }

    /**
     * 写入数据点（line protocol）。
     *
     * @param target    访问目标
     * @param bucket    目标 Bucket 名
     * @param precision 时间精度（ns/us/ms/s），空按 ns
     * @param lines     line protocol 文本（一行一个点，换行分隔）
     * @throws IllegalStateException 请求失败
     */
    public void write(Target target, String bucket, String precision, String lines) {
        Map<String, String> heads = new LinkedHashMap<>(headers(target.endpoint()));
        heads.put("Accept", "application/json");
        try {
            MiddlewareHttp.postText(target.endpoint().url("/api/v2/write"),
                    target.query("bucket", bucket, "precision", (precision == null || precision.isBlank()) ? "ns" : precision),
                    lines, "text/plain; charset=utf-8", heads);
        } catch (Exception e) {
            throw failure(target.endpoint(), "/api/v2/write", e);
        }
    }

    /**
     * GET 并解析成 JSON 对象。
     *
     * @param target 访问目标
     * @param path   接口路径
     * @param query  查询参数
     * @return 解析结果，非 JSON 对象时为空对象
     * @throws IllegalStateException 请求失败
     */
    public JsonObject getObject(Target target, String path, Map<String, String> query) {
        try {
            return Json.getJsonObject(MiddlewareHttp.get(target.endpoint().url(path), query, headers(target.endpoint())));
        } catch (Exception e) {
            throw failure(target.endpoint(), path, e);
        }
    }

    /**
     * POST JSON 并返回响应体。
     *
     * @param target 访问目标
     * @param path   接口路径
     * @param query  查询参数
     * @param body   JSON 请求体
     * @return 响应体文本
     * @throws IllegalStateException 请求失败
     */
    public String postJson(Target target, String path, Map<String, String> query, String body) {
        MiddlewareEndpoint endpoint = target.endpoint();
        Map<String, String> heads = new LinkedHashMap<>(headers(endpoint));
        heads.put("Accept", "application/json");
        try {
            return MiddlewareHttp.postJson(endpoint.url(path), query, body, heads);
        } catch (Exception e) {
            throw failure(endpoint, path, e);
        }
    }

    /**
     * 组装请求头：有 Token 才带 Authorization。
     *
     * @param endpoint 端点
     * @return 请求头
     */
    private Map<String, String> headers(MiddlewareEndpoint endpoint) {
        return endpoint.password().isEmpty()
                ? Map.of()
                : Map.of("Authorization", "Token " + endpoint.password());
    }

    /**
     * 取请求要带的 org：优先数据源登记值，缺省回查一次 /api/v2/orgs。
     *
     * @param endpoint 端点
     * @return org 名，取不到时为空串
     */
    private String org(MiddlewareEndpoint endpoint) {
        if (!endpoint.username().isEmpty()) {
            return endpoint.username();
        }
        String key = endpoint.base() + endpoint.context();
        String cached = orgCache.get(key);
        if (cached != null) {
            return cached;
        }
        String resolved = "";
        JsonArray items = getQuiet(endpoint, "/api/v2/orgs").getJsonArray("orgs");
        if (items.size() > 0) {
            resolved = text(items.getJsonObject(0).getObject("name"));
        }
        if (!resolved.isEmpty()) {
            orgCache.put(key, resolved);
        }
        return resolved;
    }

    /**
     * 静默 GET，失败返回空对象。
     *
     * @param endpoint 端点
     * @param path     接口路径
     * @return 响应体解析结果
     */
    private JsonObject getQuiet(MiddlewareEndpoint endpoint, String path) {
        try {
            return Json.getJsonObject(MiddlewareHttp.get(endpoint.url(path), Map.of(), headers(endpoint)));
        } catch (Exception e) {
            log.warn("[influxdb] 读取 {} 失败: {}", path, e.getMessage());
            return new JsonObject();
        }
    }

    /**
     * 数据源行转成端点：InfluxDB 的 org 占用 username 字段，因此不能复用 {@code database} 作为上下文路径。
     *
     * @param ds 数据源配置
     * @return 端点
     */
    private MiddlewareEndpoint endpoint(DataSourceVO ds) {
        String database = trim(ds.database());
        if (isAbsoluteUrl(database)) {
            java.net.URI uri = java.net.URI.create(database);
            return new MiddlewareEndpoint(uri.getScheme() + "://" + uri.getAuthority(), trimSlash(uri.getPath()),
                    trim(ds.username()), blankToEmpty(ds.password()));
        }
        String scheme = "https".equalsIgnoreCase(database) ? "https" : "http";
        int port = ds.port() == null ? 0 : ds.port();
        return new MiddlewareEndpoint(scheme + "://" + trim(ds.host()) + (port > 0 ? ":" + port : ""), "",
                trim(ds.username()), blankToEmpty(ds.password()));
    }

    /**
     * 数据源行转成校验过类型的端点。
     *
     * @param datasourceId 数据源 ID
     * @return 数据源配置
     * @throws IllegalStateException 未登记或类型不符
     */
    private DataSourceVO requireDatasource(Long datasourceId) {
        return MiddlewareDatasources.require(datasourceService, datasourceId, DS_TYPE);
    }

    /**
     * 把 JSON 值转成文本。
     *
     * @param value JSON 值，可为空
     * @return 文本，null 转空串
     */
    private static String text(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof JsonObject || value instanceof JsonArray) {
            return Json.toJson(value);
        }
        return String.valueOf(value);
    }

    /**
     * 取第一个非空白值。
     *
     * @param values 候选值
     * @return 首个非空白值，全空白时为空串
     */
    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
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
     * 去掉路径的结尾斜杠，并补上前导斜杠。
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
        log.warn("[influxdb] 调用 {} 失败: {}", url, reason);
        return new IllegalStateException("调用 InfluxDB " + url + " 失败: " + reason, e);
    }
}
