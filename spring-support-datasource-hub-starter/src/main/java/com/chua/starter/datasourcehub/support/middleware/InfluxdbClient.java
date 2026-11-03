// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasourcehub.support.middleware;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.json.JsonArray;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
        /** InfluxDB 1.x（只有 InfluxQL，无 Flux、无组织、无桶） */
        V1,
        /** InfluxDB 2.x */
        V2,
        /** InfluxDB 3.x */
        V3
    }

    /**
     * 查询方言。
     *
     * <p><b>与 {@link Version} 正交，不可互相替代</b>：大版本决定服务端长什么样
     * （v2 = 组织/桶，v3 = 数据库），方言决定语句怎么写。两者必须分开，原因是
     * <b>InfluxDB 2.x 的 Flux 是可选组件</b>：官方镜像默认 {@code flux-enabled=false}，
     * 此时 {@code /api/v2/query} 只认 InfluxQL（{@code type=sql}），发 Flux 会得到
     * 403 {@code Flux query service disabled}。也就是说「2.x + SQL」是一个真实存在的
     * 组合，把它硬塞进 Version 里就会出现「明明是 v2 却只能按 v3 处理」的矛盾。</p>
     *
     * <p>方言取数据源登记的 {@code datasource_version}（用户显式选择）；
     * 登记为空（存量记录）时才回落到按大版本推断，保持升级前的行为不变。</p>
     *
     * @author CH
     * @since 4.0.0.42
     */
    public enum Dialect {
        /** Flux（{@code from(bucket:...)}） */
        FLUX,
        /** SQL / InfluxQL */
        SQL
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

    /**
     * 数据源登记的版本值：SQL / InfluxQL 方言（前端「v3」选项写入）。
     */
    private static final String DS_VERSION_SQL = "3";

    /**
     * 数据源登记的版本值：Flux 方言（前端「v2」选项写入）。
     */
    private static final String DS_VERSION_FLUX = "2";

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
     * 一次 InfluxDB 调用所需的「地址 + 凭据 + org + 大版本 + 方言」。
     *
     * @param endpoint 地址与凭据
     * @param org      组织名，未登记且探测不到时为空串（v3 恒为空）
     * @param version  服务端大版本
     * @param dialect  查询方言，来自数据源登记的版本；登记为空时按 version 推断
     */
    public record Target(MiddlewareEndpoint endpoint, String org, Version version, Dialect dialect) {

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

        /**
         * 是否按 Flux 发查询。
         *
         * <p>v3 一律不发 Flux（服务端已移除），因此这里只反映登记的方言。
         * v2 且方言为 Flux 时才走 {@code /api/v2/query} 的 {@code type=flux}。</p>
         *
         * @return 用 Flux 为 true
         */
        public boolean usesFlux() {
            return version == Version.V2 && dialect == Dialect.FLUX;
        }

        /**
         * 是否 InfluxDB 1.x。
         *
         * <p>1.x 既没有 Flux 也没有组织与桶：查询只有 InfluxQL，容器是「数据库」
         * （v1 的 database，对应 v2 的 bucket、v3 的 database）。</p>
         *
         * @return v1 为 true
         */
        public boolean isV1() {
            return version == Version.V1;
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
        Version version = detectVersion(endpoint);
        return new Target(endpoint, org(endpoint), version, dialect(ds, version));
    }

    /**
     * 取查询方言：登记的 {@code datasource_version} 为准，登记为空才按大版本推断。
     *
     * <p>登记优先是刻意的：这个字段就是用户在数据源上做的选择，而「2.x」并不蕴含
     * 「有 Flux」。反之若一律按大版本推断，用户在界面上把版本改成 3 也不会有任何效果，
     * 选择的语义就丢了（这正是原先 version 只是个 UI 提示、查不通的原因）。</p>
     *
     * @param ds      数据源配置
     * @param version 已探测的服务端大版本
     * @return 查询方言
     */
    private Dialect dialect(DataSourceVO ds, Version version) {
        // 1.x 只有 InfluxQL：登记什么都不选，直接钉死 SQL。
        // 不做这一步的话，选了「v2/Flux」的 1.x 实例会一路发 Flux 到不存在的
        // /api/v2/query，表现为查询恒 403（Flux query service disabled）。
        if (version == Version.V1) {
            return Dialect.SQL;
        }
        String registered = ds == null ? null : trim(ds.version());
        if (DS_VERSION_SQL.equals(registered)) {
            return Dialect.SQL;
        }
        if (DS_VERSION_FLUX.equals(registered)) {
            return Dialect.FLUX;
        }
        return version == Version.V3 ? Dialect.SQL : Dialect.FLUX;
    }

    /**
 * 读 {@code /ping} 的 {@code X-Influxdb-Version} 头，作为版本号回显。
 *
 * <p>三个大版本都带这个头，v1 更是<b>只能</b>从这里拿（没有 {@code /health}）。
 * 读不到返回 null，由调用方决定降级。</p>
 *
 * @param target 访问目标
 * @return 版本号原值（如 {@code v1.11.8} / {@code 2.7.11} / {@code 3.11.4}），读不到为 null
 */
    public String serverVersion(Target target) {
        try {
            MiddlewareHttp.Response ping = MiddlewareHttp.getResponse(
                    target.endpoint().url("/ping"), Map.of(), authHeaders(target));
            String version = ping.header("X-Influxdb-Version");
            return version == null || version.isBlank() ? null : version.trim();
        } catch (Exception e) {
            log.debug("[influxdb] 读取 /ping 版本头失败: {}", e.getMessage());
            return null;
        }
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
        return parseQueryResponse(text);
    }

    /**
     * 还原 {@code /api/v2/query} 的行数据（Flux 与 InfluxQL 共用此解析）。
     *
     * <p>实测（2.7.11）：{@code /api/v2/query} 返回的是<b>注解 CSV</b>，不是 JSON-lines。
     * 早先按「每行一个 JSON」解析，结果<b>每一行都解析成空对象</b>且不报错——
     * 表现是「/query 有行数但表格全空、/measurements 恒空、字段/标签统计恒空」，
     * 整块读数能力静默失效。这里先探测首行：以 '{' 开头才走 JSON 分支（兼容早期实现）。</p>
     *
     * @param text 响应体
     * @return 行数据
     */
    private List<JsonObject> parseQueryResponse(String text) {
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
     * 执行 InfluxQL 查询（v1 与 v2 通用）。
     *
     * <p>入口是 <b>v1 兼容的 {@code POST /query}</b>：表单带 {@code db} 与 {@code q}，
     * 响应是 {@code {"results":[{"series":[{"columns":..,"values":..}]}]}}。
     * 1.x 与 2.x 都提供这个入口——<b>2.x 的 {@code /api/v2/query} 只吃 Flux</b>
     * （实测 2.7.11：{@code type=sql} / {@code type=influxql} 一律 400），
     * 所以 InfluxQL 必须走这里，不能走 v2 的查询接口。</p>
     *
     * <p>这里把 series 摊平成「一行一个对象、列为键」，与 v3 的行结构对齐，
     * 上层表格与列推导不必分叉。语句错误不走 HTTP 状态码，而是以
     * {@code results[].error} 回一个 200，故必须显式判错。</p>
     *
     * <p>用表单体而非查询串承载语句：{@code q} 里的换行/引号/中文在查询串里
     * 需要多次转义，放体里只需一次 URL 编码，且 2.x 要求带 Token 才认。</p>
     *
     * @param target   访问目标
     * @param database 目标数据库/桶名（v2 的 {@code db} 即桶名）
     * @param sql      InfluxQL 语句
     * @return 行数据，保持服务端给出的列顺序
     * @throws IllegalStateException 请求失败或服务端回报语句错误
     */
    public List<JsonObject> v1Query(Target target, String database, String sql) {
        Map<String, String> params = new LinkedHashMap<>();
        if (!database.isEmpty()) {
            params.put("db", database);
        }
        String body = "q=" + URLEncoder.encode(sql, StandardCharsets.UTF_8);
        try {
            String text = MiddlewareHttp.postText(target.endpoint().url("/query"), params, body,
                    "application/x-www-form-urlencoded; charset=utf-8", authHeaders(target));
            return parseV1Rows(text);
        } catch (Exception e) {
            throw failure(target.endpoint(), "/query", e);
        }
    }

    /**
     * 还原 v1 {@code /query} 响应为行数据。
     *
     * <p>结构：{@code results[].series[]}，每个 series 带 {@code name} 与
     * {@code columns}/{@code values}（values 与 columns 按位对应）。
     * 语句错误不会体现在 HTTP 状态码上，而是以 {@code results[].error} 回一个 200，
     * 所以这里必须显式判错，否则「语法错」会被当成「零行」静默吞掉。</p>
     *
     * @param text 响应体
     * @return 行数据
     * @throws IllegalStateException 语句错误
     */
    private List<JsonObject> parseV1Rows(String text) {
        JsonArray results = Json.getJsonObject(text).getJsonArray("results");
        List<JsonObject> rows = new ArrayList<>();
        if (results == null) {
            return rows;
        }
        for (int i = 0; i < results.size(); i++) {
            JsonObject result = results.getJsonObject(i);
            String error = text(result.get("error"));
            if (!error.isEmpty()) {
                throw new IllegalStateException("InfluxQL 执行失败: " + error);
            }
            JsonArray series = result.getJsonArray("series");
            if (series == null) {
                continue;
            }
            for (int s = 0; s < series.size(); s++) {
                JsonObject one = series.getJsonObject(s);
                JsonArray columns = one.getJsonArray("columns");
                JsonArray values = one.getJsonArray("values");
                if (columns == null || values == null) {
                    continue;
                }
                for (int r = 0; r < values.size(); r++) {
                    JsonArray cells = values.getJsonArray(r);
                    if (cells == null) {
                        continue;
                    }
                    JsonObject row = new JsonObject();
                    for (int c = 0; c < columns.size(); c++) {
                        row.put(text(columns.get(c)), c < cells.size() ? cells.get(c) : null);
                    }
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    /**
     * 列出 v1 实例的全部数据库。
     *
     * @param target 访问目标
     * @return 数据库名列表
     * @throws IllegalStateException 请求失败或服务端回报语句错误
     */
    public List<String> v1Databases(Target target) {
        List<String> names = new ArrayList<>();
        for (JsonObject row : v1Query(target, "", "SHOW DATABASES")) {
            String name = text(row.get("name"));
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * 向 v1 数据库写入 line protocol 数据点。
     *
     * <p>v1 的写入入口是 {@code POST /write?db=..&precision=..}（v2 起才换成
     * {@code /api/v2/write}）。</p>
     *
     * @param target    访问目标
     * @param database  目标数据库
     * @param precision 时间精度（ns/us/ms/s），空按 ns
     * @param lines     line protocol 文本
     * @throws IllegalStateException 请求失败
     */
    public void v1Write(Target target, String database, String precision, String lines) {
        try {
            // line protocol 必须作为**请求体**原样发，不能塞进 q= 表单字段：
            // v1 的 /write 读的是 body，用 postForm(q=...) 时服务端会把字面量
            // "q=acctest,host=h1 value=..." 当成一条数据解析，报 missing fields。
            MiddlewareHttp.postText(target.endpoint().url("/write"),
                    MiddlewareNamespace.query("db", database,
                            "precision", (precision == null || precision.isBlank()) ? "ns" : precision),
                    lines, "text/plain; charset=utf-8", authHeaders(target));
        } catch (Exception e) {
            throw failure(target.endpoint(), "/write", e);
        }
    }

    /**
     * 执行 v1 InfluxQL 维护语句（CREATE / DROP DATABASE、ALTER DURATION 等）。
     *
     * @param target   访问目标
     * @param database 目标数据库（DDL 可为空）
     * @param sql      InfluxQL 语句
     * @throws IllegalStateException 请求失败或服务端回报语句错误
     */
    public void v1Execute(Target target, String database, String sql) {
        v1Query(target, database, sql);
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
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("#")) {
                // 每个 table 前都会重复一段注解（#group / #datatype / #default）再跟一行表头。
                // 只认第一个表头会把后续 table 的表头当成数据行（_start=_start 这种脏行），
                // 所以每遇到 #datatype 就把 header 置空，让下一行非注解行重新当表头。
                if (line.startsWith("#datatype")) {
                    header = null;
                }
                continue;
            }
            List<String> cells = splitCsv(line);
            if (header == null) {
                header = cells;
                continue;
            }
            // 兜底：没有 #datatype 但表头行重复出现时，按「与表头完全一致」识别并跳过
            if (cells.equals(header)) {
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
            Version parsed = parseVersion(version);
            if (parsed != null) {
                return parsed;
            }
            log.debug("[influxdb] /ping 未带版本头（{}），退回 v3 路由判据", version == null ? "null" : version);
        } catch (java.io.IOException e) {
            log.debug("[influxdb] /ping 探测失败，退回 v3 路由判据: {}", e.getMessage());
        }
        return probeVersionViaV3Route(endpoint);
    }

    /**
     * 解析 {@code X-Influxdb-Version} 头，认不出返回 null。
     *
     * <p>三种形态都要认：1.x 给 {@code v1.11.8}（<b>带 v 前缀</b>，不认它就会被
     * 当成 v2 而发 Flux，1.x 没有 Flux 也没有 {@code /api/v2/*}，于是查询恒 403）；
     * 2.x 给 {@code 2.7.11}；3.x 给 {@code 3.11.4}。</p>
     *
     * @param header 版本头原值
     * @return 大版本，认不出为 null
     */
    private static Version parseVersion(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String normalized = header.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("1.") || "1".equals(normalized)) {
            return Version.V1;
        }
        if (normalized.startsWith("3.")) {
            return Version.V3;
        }
        if (normalized.startsWith("2.")) {
            return Version.V2;
        }
        return null;
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
        // v3 已移除组织概念，v1 压根没有组织，都不回查 /api/v2/orgs（两者上恒 404）
        if (detectVersion(endpoint) != Version.V2) {
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
