package com.chua.starter.datasourcehub.support.middleware;

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
 * InfluxDB HTTP API 客户端（2.x / 3.x 双版本）。
 *
 * <p>凭据落在数据源行的两个字段上：{@code password} 存 API Token——
 * v2 请求头为 {@code Authorization: Token xxx}，v3 为
 * {@code Authorization: Bearer xxx}；{@code username} 存 org 名
 * （仅 v2 使用，v3 已移除组织）。org 留空时向 {@code /api/v2/orgs}
 * 取第一个组织并缓存——Token 本身就是按 org 授权的，单实例通常只有一个可选组织。</p>
 *
 * <p>v2 与 v3 是两套完全不同的实现：v2 为 Go 单体 + TSM 引擎，走
 * {@code /api/v2/*}、Flux、组织/桶模型；v3 为 Rust 重写
 * （Arrow/DataFusion/Parquet），走 {@code /api/v3/*}、SQL + InfluxQL、
 * 数据库模型，已移除 Flux 与组织。大版本在解析目标时探测一次并缓存：
 * v3 专属路由在 v2 上恒 404，命中即 v3。</p>
 *
 * <p>{@code database} 字段沿用 Nacos 的口径：填完整 URL 时按 URL 走（反向代理挂子路径），
 * 填 {@code https} 时用 TLS，其余情况不影响本类型的地址拼接。</p>
 *
 * <p>本类只负责「带上凭据、发请求、把响应读成 JSON」，查询语句与结构解析在
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
     * InfluxDB 大版本。
     *
     * <p>v2 与 v3 的后台实现完全不同：v2 走 {@code /api/v2/*} + Flux +
     * 组织/桶；v3 走 {@code /api/v3/*} + SQL/InfluxQL + 数据库，
     * 客户端库也不通用（{@code influxdb-client-java} 只支持 v2）。</p>
     *
     * @author CH
     * @since 4.0.0.42
     */
    public enum Version {
        /** InfluxDB 2.x */
        V2,
        /** InfluxDB 3.x */
        V3
    }

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
     * 端点地址 → 已探测的大版本，避免每次解析目标都多打一次探测请求
     */
    private final Map<String, Version> versionCache = new ConcurrentHashMap<>();

    /**
     * 一次 InfluxDB 调用所需的「地址 + 凭据 + org + 大版本」。
     *
     * @param endpoint 地址与凭据
     * @param org      组织名，未登记且探测不到时为空串（v3 恒为空）
     * @param version  服务端大版本
     */
    public record Target(MiddlewareEndpoint endpoint, String org, Version version) {

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

        /**
         * 是否 InfluxDB 3.x。
         *
         * @return v3 为 true
         */
        public boolean isV3() {
            return version == Version.V3;
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
        return new Target(endpoint, org(endpoint), detectVersion(endpoint));
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
        // 注：tableResponse 是照早期实现留的，InfluxDB 2.7 实测**忽略**它
        // （无论 Accept 给 application/json 还是别的，都回注解 CSV），
        // 所以下面的解析不依赖它。
        dialect.put("tableResponse", false);
        dialect.put("annotations", List.of());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", flux);
        body.put("type", "flux");
        body.put("dialect", dialect);
        String text = postJson(target, QUERY_PATH, target.query(), Json.toJson(body));
        // 实测（2.7.11）：/api/v2/query 返回的是**注解 CSV**，不是 JSON-lines。
        // 早先按"每行一个 JSON"解析，结果**每一行都解析成空对象**且不报错——
        // 表现是「/query 有行数但表格全空、/measurements 恒空、字段/标签统计恒空」，
        // 整块读数能力静默失效。这里先探测首行：以 '{' 开头才走 JSON 分支（兼容早期实现）。
        for (String line : text.split("\r?\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            return trimmed.startsWith("{") ? parseJsonLines(text) : parseCsv(text);
        }
        return List.of();
    }

    /**
     * 解析 JSON-lines 响应（早期 InfluxDB 在 {@code tableResponse=false} 下的形态）。
     *
     * @param text 响应体
     * @return 行数据
     * @throws IllegalStateException 服务端在流里回报查询错误
     */
    private List<JsonObject> parseJsonLines(String text) {
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
     * 解析 InfluxDB 的注解 CSV。
     *
     * <p>形态（{@code #} 开头是注解行，第一行非注解行是表头，且**首列为空**）：</p>
     * <pre>
     * #datatype,string,long,string
     * ,result,table,_value
     * ,_result,0,diag_123
     * </pre>
     *
     * <p>值为字符串，按数值/布尔尽可能还原类型——调用方（如 {@code countBy}）会做
     * {@code intOrNull(row.get("count"))}，全留字符串会统计不出来。字段内可能含逗号或换行，
     * 因此必须用引号感知的切分，不能直接 {@code split(",")}。</p>
     *
     * @param text 响应体
     * @return 行数据
     */
    private List<JsonObject> parseCsv(String text) {
        List<JsonObject> rows = new ArrayList<>();
        List<String> header = null;
        for (String line : text.split("\r?\n")) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            List<String> cells = splitCsv(line);
            if (header == null) {
                header = cells;
                continue;
            }
            JsonObject row = new JsonObject();
            for (int i = 0; i < header.size(); i++) {
                String key = header.get(i).trim();
                if (key.isEmpty()) {
                    continue;
                }
                row.fluentPut(key, coerceCsvValue(i < cells.size() ? cells.get(i) : ""));
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 引号感知的 CSV 切分。
     *
     * @param line 一行 CSV
     * @return 单元格列表
     */
    private static List<String> splitCsv(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                cells.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        cells.add(cur.toString());
        return cells;
    }

    /**
     * CSV 单元格取值并还原基础类型。
     *
     * @param raw 原始单元格
     * @return Boolean / Long / Double / String；空串保持空串
     */
    private static Object coerceCsvValue(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty()) {
            return "";
        }
        if ("true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v)) {
            return Boolean.parseBoolean(v);
        }
        if (v.matches("[-+]?\\d+")) {
            try {
                return Long.parseLong(v);
            } catch (NumberFormatException ignore) {
                return v;
            }
        }
        if (v.matches("[-+]?(\\d+\\.\\d*|\\.\\d+)([eE][-+]?\\d+)?")) {
            try {
                return Double.parseDouble(v);
            } catch (NumberFormatException ignore) {
                return v;
            }
        }
        return v;
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
            // 必须带 Authorization：实测两参数的 delete 不带任何头，InfluxDB 直接回 401，
            // 表现为"删除桶点了没反应/报未授权"，而创建与改保留期都正常。
            MiddlewareHttp.delete(target.endpoint().url("/api/v2/buckets/" + trim(bucketId)),
                    target.query(), headers(target.endpoint()));
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
     * 列出 v3 实例的全部数据库。
     *
     * <p>v3 用数据库取代 v2 的组织+桶，列表接口为
     * {@code GET /api/v3/configure/database?format=json}，
     * 响应是裸 JSON 数组：{@code [{"iox::database":"name"}]}</p>
     *
     * @param target 访问目标
     * @return 数据库原始条目
     * @throws IllegalStateException 请求失败
     */
    public JsonArray v3Databases(Target target) {
        try {
            String text = MiddlewareHttp.get(target.endpoint().url("/api/v3/configure/database"),
                    Map.of("format", "json"), authHeaders(target));
            return Json.getJsonArray(text);
        } catch (Exception e) {
            throw failure(target.endpoint(), "/api/v3/configure/database", e);
        }
    }

    /**
     * 新建 v3 数据库。
     *
     * <p>创建接口是 {@code POST /api/v3/configure/database}，
     * 保留期用 humantime 格式（如 {@code 168h}），省略表示永久保留。
     * v3 没有描述字段，{@code description} 被忽略。</p>
     *
     * @param target         访问目标
     * @param name           数据库名
     * @param retentionHours 保留时长（小时），空或非正表示永久
     * @throws IllegalStateException 请求失败
     */
    public void v3CreateDatabase(Target target, String name, Long retentionHours) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("db", name);
        String retention = humantime(retentionHours);
        if (retention != null) {
            body.put("retention_period", retention);
        }
        try {
            MiddlewareHttp.postJson(target.endpoint().url("/api/v3/configure/database"),
                    Map.of(), Json.toJson(body), authHeaders(target));
        } catch (Exception e) {
            throw failure(target.endpoint(), "/api/v3/configure/database", e);
        }
    }

    /**
     * 更新 v3 数据库配置（保留期）。
     *
     * <p>更新接口是 {@code PUT /api/v3/configure/database}，
     * {@code retentionHours} 空或非正时省略保留期字段，表示清除保留期（永久保留）。</p>
     *
     * @param target         访问目标
     * @param name           数据库名
     * @param retentionHours 新的保留时长（小时），空或非正表示永久
     * @throws IllegalStateException 请求失败
     */
    public void v3UpdateRetention(Target target, String name, Long retentionHours) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("db", name);
        String retention = humantime(retentionHours);
        if (retention != null) {
            body.put("retention_period", retention);
        }
        Map<String, String> heads = new LinkedHashMap<>(authHeaders(target));
        heads.put("Accept", "application/json");
        try {
            MiddlewareHttp.putJson(target.endpoint().url("/api/v3/configure/database"),
                    Map.of(), Json.toJson(body), heads);
        } catch (Exception e) {
            throw failure(target.endpoint(), "/api/v3/configure/database", e);
        }
    }

    /**
     * 删除 v3 数据库（软删除）。
     *
     * <p>删除接口是 {@code DELETE /api/v3/configure/database?db=..}，
     * 以数据库名为标识（v3 无 UUID）。</p>
     *
     * @param target 访问目标
     * @param name   数据库名
     * @throws IllegalStateException 请求失败
     */
    public void v3DeleteDatabase(Target target, String name) {
        try {
            MiddlewareHttp.delete(target.endpoint().url("/api/v3/configure/database"),
                    Map.of("db", name), authHeaders(target));
        } catch (Exception e) {
            throw failure(target.endpoint(), "/api/v3/configure/database", e);
        }
    }

    /**
     * 向 v3 数据库写入 line protocol 数据点。
     *
     * <p>v3 写入接口是 {@code POST /api/v3/write_lp?db=..&precision=..}</p>
     *
     * @param target    访问目标
     * @param database  目标数据库
     * @param precision 时间精度（ns/us/ms/s），空按 ns
     * @param lines     line protocol 文本（一行一个点，换行分隔）
     * @throws IllegalStateException 请求失败
     */
    public void v3Write(Target target, String database, String precision, String lines) {
        Map<String, String> heads = new LinkedHashMap<>(authHeaders(target));
        heads.put("Accept", "application/json");
        try {
            MiddlewareHttp.postText(target.endpoint().url("/api/v3/write_lp"),
                    target.query("db", database, "precision", (precision == null || precision.isBlank()) ? "ns" : precision),
                    lines, "text/plain; charset=utf-8", heads);
        } catch (Exception e) {
            throw failure(target.endpoint(), "/api/v3/write_lp", e);
        }
    }

    /**
     * 执行 v3 SQL 查询并把逐行 JSON 响应还原成行数据。
     *
     * <p>v3 的 SQL 接口是 {@code POST /api/v3/query_sql}，目标数据库与
     * SQL 语句都放在 JSON 体里：{@code {"db":"..","q":".."}}，
     * 响应为裸 JSON 数组，每行一个对象、列为键。</p>
     *
     * @param target   访问目标
     * @param database 目标数据库
     * @param sql      SQL 语句
     * @return 行数据，保持服务端给出的列顺序
     * @throws IllegalStateException 请求失败
     */
    public List<JsonObject> v3QuerySql(Target target, String database, String sql) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("db", database);
        body.put("q", sql);
        Map<String, String> heads = new LinkedHashMap<>(authHeaders(target));
        heads.put("Accept", "application/json");
        try {
            String text = MiddlewareHttp.postJson(target.endpoint().url("/api/v3/query_sql"),
                    Map.of(), Json.toJson(body), heads);
            return parseV3Rows(text);
        } catch (Exception e) {
            throw failure(target.endpoint(), "/api/v3/query_sql", e);
        }
    }

    /**
     * 执行 v3 InfluxQL 查询。
     *
     * <p>v3 的 InfluxQL 接口是 {@code GET /api/v3/query_influxql?db=..&q=..}，
     * 用于 SHOW MEASUREMENTS 等 SQL 未覆盖的元数据语句。</p>
     *
     * @param target    访问目标
     * @param database  目标数据库
     * @param influxql  InfluxQL 语句
     * @return 行数据
     * @throws IllegalStateException 请求失败
     */
    public List<JsonObject> v3QueryInfluxql(Target target, String database, String influxql) {
        Map<String, String> heads = new LinkedHashMap<>(authHeaders(target));
        heads.put("Accept", "application/json");
        try {
            String text = MiddlewareHttp.get(target.endpoint().url("/api/v3/query_influxql"),
                    target.query("db", database, "q", influxql), heads);
            return parseV3Rows(text);
        } catch (Exception e) {
            throw failure(target.endpoint(), "/api/v3/query_influxql", e);
        }
    }

    /**
     * 解析 v3 查询响应：裸 JSON 数组，每行一个对象。
     *
     * @param text 响应体文本
     * @return 行数据
     */
    private static List<JsonObject> parseV3Rows(String text) {
        JsonArray array = text == null || text.trim().isEmpty()
                ? new JsonArray()
                : Json.getJsonArray(text);
        List<JsonObject> rows = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            rows.add(array.getJsonObject(i));
        }
        return rows;
    }

    /**
     * 小时数转 humantime 时长文本（v3 保留期接口的格式）。
     *
     * @param retentionHours 保留时长（小时）
     * @return 如 {@code 168h}；空或非正表示永久（省略该字段），返回 null
     */
    private static String humantime(Long retentionHours) {
        if (retentionHours == null || retentionHours <= 0L) {
            return null;
        }
        return retentionHours + "h";
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
            return Json.getJsonObject(MiddlewareHttp.get(target.endpoint().url(path), query, authHeaders(target)));
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
        Map<String, String> heads = new LinkedHashMap<>(authHeaders(target));
        heads.put("Accept", "application/json");
        try {
            return MiddlewareHttp.postJson(endpoint.url(path), query, body, heads);
        } catch (Exception e) {
            throw failure(endpoint, path, e);
        }
    }

    /**
     * 组装请求头：有 Token 才带 Authorization（v2 为 Token 语义，v3 为 Bearer 语义）。
     *
     * @param target 访问目标
     * @return 请求头
     */
    private Map<String, String> authHeaders(Target target) {
        MiddlewareEndpoint endpoint = target.endpoint();
        if (endpoint.password().isEmpty()) {
            return Map.of();
        }
        return target.isV3()
                ? Map.of("Authorization", "Bearer " + endpoint.password())
                : Map.of("Authorization", "Token " + endpoint.password());
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
     * 探测服务端大版本并按端点缓存。
     *
     * @param endpoint 端点
     * @return 大版本
     */
    private Version detectVersion(MiddlewareEndpoint endpoint) {
        String key = endpoint.base() + endpoint.context();
        Version cached = versionCache.get(key);
        if (cached != null) {
            return cached;
        }
        Version version = probeVersion(endpoint);
        versionCache.put(key, version);
        return version;
    }

    /**
     * 探测大版本：以 {@code /ping} 的 {@code X-Influxdb-Version} 响应头为准。
     *
     * <p>旧实现只探测 v3 专属路由 {@code /api/v3/configure/database} 是否 404（非 404 即 v3），
     * 但 <b>InfluxDB 2.7.11 实测对该路由返回 200</b>（v2 提供了兼容桩），会把 v2 误判成 v3，
     * 导致所有调用走 v3 分支（数据库/测量恒空、Flux 查询被当 SQL 管理），
     * 且该误判按端点缓存、后端不重启不消失 —— 表现为「同样的数据源换一次后端进程可能就坏」。
     * v2（{@code v2.7.11}）与 v3（{@code 3.11.4}）都在 {@code /ping} 上暴露
     * {@code X-Influxdb-Version} 头，以 {@code 3.} 开头即 v3，是可靠判据。</p>
     *
     * <p>无响应（连接失败）或响应无版本头时退回旧判据，语义与原来一致
     * （连通性问题由调用方的测试接口负责报错）。</p>
     *
     * @param endpoint 端点
     * @return 大版本
     */
    private Version probeVersion(MiddlewareEndpoint endpoint) {
        try {
            MiddlewareHttp.Response ping = MiddlewareHttp.getResponse(
                    endpoint.url("/ping"), Map.of(), Map.of());
            String version = ping.header("X-Influxdb-Version");
            if (version != null && version.startsWith("3.")) {
                return Version.V3;
            }
            if (version != null && !version.isBlank()) {
                return Version.V2;
            }
            log.debug("[influxdb] /ping 未带版本头（{}），退回 v3 路由判据", version == null ? "null" : version);
        } catch (java.io.IOException e) {
            log.debug("[influxdb] /ping 探测失败，退回 v3 路由判据: {}", e.getMessage());
        }
        return probeVersionViaV3Route(endpoint);
    }

    /**
     * 旧判据：v3 专属路由（数据库配置接口）在 v2 上应 404；非 404 即 v3。
     *
     * @param endpoint 端点
     * @return 大版本
     */
    private Version probeVersionViaV3Route(MiddlewareEndpoint endpoint) {
        try {
            MiddlewareHttp.Response response = MiddlewareHttp.getResponse(
                    endpoint.url("/api/v3/configure/database"),
                    Map.of("format", "json"),
                    Map.of());
            if (response.status() != 404) {
                return Version.V3;
            }
        } catch (java.io.IOException e) {
            log.debug("[influxdb] v3 路由探测失败，按 v2 处理: {}", e.getMessage());
        }
        return Version.V2;
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
        // v3 已移除组织概念，不回查 /api/v2/orgs（v3 上恒 404）
        if (detectVersion(endpoint) == Version.V3) {
            return "";
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
