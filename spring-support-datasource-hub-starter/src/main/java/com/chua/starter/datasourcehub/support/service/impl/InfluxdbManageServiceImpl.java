package com.chua.starter.datasourcehub.support.service.impl;

import com.chua.common.support.lang.json.JsonArray;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.starter.datasourcehub.support.middleware.InfluxdbClient;
import com.chua.starter.datasourcehub.support.middleware.MiddlewareNamespace;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.datasourcehub.support.model.InfluxdbBucketVO;
import com.chua.starter.datasourcehub.support.model.InfluxdbMeasurementVO;
import com.chua.starter.datasourcehub.support.model.InfluxdbOrgVO;
import com.chua.starter.datasourcehub.support.model.InfluxdbQueryResultVO;
import com.chua.starter.datasourcehub.support.model.MiddlewareTestVO;
import com.chua.starter.datasourcehub.support.model.dto.InfluxdbBucketDTO;
import com.chua.starter.datasourcehub.support.model.dto.InfluxdbQueryDTO;
import com.chua.starter.datasourcehub.support.model.dto.InfluxdbWriteDTO;
import com.chua.starter.datasourcehub.support.model.dto.MiddlewareTestDTO;
import com.chua.starter.datasourcehub.support.service.InfluxdbManageService;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * InfluxDB 管理实现：按服务端大版本分发。
 *
 * <p>v2（{@code /api/v2/*} + Flux + 组织/桶）与 v3（{@code /api/v3/*} +
 * SQL/InfluxQL + 数据库）是两套后台实现，由 {@code InfluxdbClient.Target#isV3()}
 * 判别：v2 的 Bucket 走 {@code /api/v2/buckets}、度量与查询走 Flux；
 * v3 的数据库走 {@code /api/v3/configure/database}、查询走 SQL，
 * 度量名与元数据走 InfluxQL。</p>
 *
 * <p>度量列表按「一次测量名 + 聚合统计」取数，统计失败只把对应列留空，
 * 不让整个列表失败；v2 的最后写入时间只回看 {@value #LAST_WRITE_WINDOW}，
 * 窗口内无数据的度量留空而不是谎报（v3 取每张表最新一行的时间）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InfluxdbManageServiceImpl implements InfluxdbManageService {

    /**
     * 度量列表一次返回的上限，超大 Bucket 靠页面搜索再收窄
     */
    private static final int MEASUREMENT_LIMIT = 500;

    /**
     * 查询结果一次返回的行数上限，超过即截断并回显 truncated
     */
    private static final int QUERY_ROW_LIMIT = 2000;

    /**
     * 统计最后写入时间回看的时间窗口
     */
    private static final String LAST_WRITE_WINDOW = "-30d";

    /**
     * InfluxDB 客户端
     */
    private final InfluxdbClient client;

    @Override
    public MiddlewareTestVO test(MiddlewareTestDTO request) {
        InfluxdbClient.Target target = testTarget(request);
        try {
            if (target.isV3()) {
                // v3：数据库配置接口在鉴权开启时必带凭据，连通即成功
                client.v3Databases(target);
            } else if (target.isV1()) {
                // v1：没有组织与桶，用必带凭据的 SHOW DATABASES 探活
                // （v1 上 /api/v2/orgs 恒 404，拿它当连通判据会把好端端的实例判成连不上）
                client.v1Databases(target);
            } else {
                // /health 不校验 Token，只探它会把「地址通、凭据错」判成连接成功，因此以必带凭据的 orgs 接口为准
                client.getObject(target, "/api/v2/orgs", Map.of());
            }
        } catch (IllegalStateException e) {
            return new MiddlewareTestVO(false, null, e.getMessage());
        }
        return new MiddlewareTestVO(true, version(target), "连接成功");
    }

    /**
     * 尽力取版本号，健康接口在部分部署下被关闭。
     *
     * @param target 访问目标
     * @return 版本号，取不到为 null
     */
    private String version(InfluxdbClient.Target target) {
        try {
            if (target.isV1()) {
                // v1 没有 /health（恒 404），版本只能从 /ping 的响应头拿；
                // 拿不到版本号前端就分不出 v1，会按 v2 去调根本不存在的 /api/v2/*
                return emptyToNull(client.serverVersion(target));
            }
            if (target.isV3()) {
                // v3 的 /ping 回 JSON：{"product_name":..,"version":"3.x",..}
                return emptyToNull(str(client.getObject(target, "/ping", Map.of()).get("version")));
            }
            return emptyToNull(str(client.health(target).get("version")));
        } catch (IllegalStateException e) {
            log.debug("[influxdb] 健康接口不可用: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public List<InfluxdbBucketVO> buckets(Long datasourceId) {
        InfluxdbClient.Target target = client.target(datasourceId);
        if (target.isV3()) {
            return v3Buckets(target);
        }
        if (target.isV1()) {
            return v1Buckets(target);
        }
        JsonArray items = client.buckets(target);
        List<InfluxdbBucketVO> result = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            JsonObject item = items.getJsonObject(i);
            result.add(new InfluxdbBucketVO(str(item.get("id")), str(item.get("name")),
                    retentionHours(item.get("retentionRules")),
                    readableTime(firstNonBlank(str(item.get("createdAt")), str(item.get("created"))))));
        }
        return result;
    }

    /**
     * 列出 v3 实例的数据库。
     *
     * <p>v3 的列表接口只回数据库名，保留期与创建时间需逐库查询，先留白；
     * {@code _internal} 是系统库，不展示。</p>
     *
     * @param target 访问目标
     * @return 数据库列表，id 与 name 同为数据库名
     */
    private List<InfluxdbBucketVO> v3Buckets(InfluxdbClient.Target target) {
        JsonArray items = client.v3Databases(target);
        List<InfluxdbBucketVO> result = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            String name = str(items.getJsonObject(i).get("iox::database"));
            if (!name.isEmpty() && !"_internal".equals(name)) {
                result.add(new InfluxdbBucketVO(name, name, null, null));
            }
        }
        return result;
    }

    /**
     * 列出 v1 实例的数据库。
 *
 * <p>v1 的容器就是 database（对应 v2 的 bucket、v3 的 database），
 * {@code SHOW DATABASES} 只回名字，保留期与创建时间留白；{@code _internal}
 * 是系统库，不展示。</p>
 *
 * @param target 访问目标
 * @return 数据库列表，id 与 name 同为数据库名
 */
    private List<InfluxdbBucketVO> v1Buckets(InfluxdbClient.Target target) {
        List<String> names = client.v1Databases(target);
        List<InfluxdbBucketVO> result = new ArrayList<>(names.size());
        for (String name : names) {
            // v1 的 _internal 是实时监控库，可正常查询；只藏它会让「只有系统库」的实例
            // 在界面上看起来一个库都没有，控制台直接没法用，故这里不过滤。
            if (!name.isEmpty()) {
                result.add(new InfluxdbBucketVO(name, name, null, null));
            }
        }
        return result;
    }

    @Override
    public List<InfluxdbMeasurementVO> measurements(Long datasourceId, String bucket) {
        InfluxdbClient.Target target = client.target(datasourceId);
        String name = trim(bucket);
        if (name.isEmpty()) {
            name = firstBucket(target);
        }
        if (target.isV3()) {
            return v3Measurements(target, name);
        }
        if (!target.usesFlux()) {
            return influxqlMeasurements(target, name);
        }
        List<String> names = measurements(name, target);
        if (names.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> tagCounts = countBy(target, name, true);
        Map<String, Integer> fieldCounts = countBy(target, name, false);
        Map<String, String> lastWrites = lastWrites(target, name);
        List<InfluxdbMeasurementVO> result = new ArrayList<>(names.size());
        for (String measurement : names) {
            result.add(new InfluxdbMeasurementVO(measurement, fieldCounts.get(measurement),
                    tagCounts.get(measurement), lastWrites.get(measurement)));
        }
        return result;
    }

    /**
     * 列出 v3 数据库的度量及统计。
     *
     * @param target    访问目标
     * @param database  目标数据库
     * @return 度量列表
     */
    private List<InfluxdbMeasurementVO> v3Measurements(InfluxdbClient.Target target, String database) {
        List<String> names = v3MeasurementNames(target, database);
        if (names.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> tagCounts = v3CountBy(target, database, names, true);
        Map<String, Integer> fieldCounts = v3CountBy(target, database, names, false);
        Map<String, String> lastWrites = v3LastWrites(target, database, names);
        List<InfluxdbMeasurementVO> result = new ArrayList<>(names.size());
        for (String measurement : names) {
            result.add(new InfluxdbMeasurementVO(measurement, fieldCounts.get(measurement),
                    tagCounts.get(measurement), lastWrites.get(measurement)));
        }
        return result;
    }

    /**
     * 读取 v3 数据库的度量名（InfluxQL {@code SHOW MEASUREMENTS}）。
     *
     * @param target   访问目标
     * @param database 目标数据库
     * @return 度量名列表，保持服务端顺序
     */
    private List<String> v3MeasurementNames(InfluxdbClient.Target target, String database) {
        List<JsonObject> rows = client.v3QueryInfluxql(target, database, "SHOW MEASUREMENTS");
        List<String> names = new ArrayList<>(Math.min(rows.size(), MEASUREMENT_LIMIT));
        for (JsonObject row : rows) {
            String name = firstNonBlank(str(row.get("name")), str(row.get("_value")));
            if (!name.isEmpty() && !names.contains(name) && names.size() < MEASUREMENT_LIMIT) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * 统计每个度量的标签数或字段数（v3 走 {@code information_schema.columns}，
     * 一次拉全部列定义按度量分组，避免逐个度量发请求）。
     *
     * <p>v3 把标签存为字典编码列（{@code Dictionary(.., Utf8)}），
     * 字段存为数值/布尔列；{@code time} 列是时间戳，不计入任何一侧。</p>
     *
     * @param target   访问目标
     * @param database 目标数据库
     * @param names    度量名列表
     * @param tags     true 统计标签数，false 统计字段数
     * @return 度量名 → 数量
     */
    private Map<String, Integer> v3CountBy(InfluxdbClient.Target target, String database,
                                           List<String> names, boolean tags) {
        try {
            Set<String> wanted = new HashSet<>(names);
            String sql = "SELECT table_name, column_name, data_type FROM information_schema.columns";
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (JsonObject column : client.v3QuerySql(target, database, sql)) {
                String table = str(column.get("table_name"));
                if (!wanted.contains(table)) {
                    continue;
                }
                String type = str(column.get("data_type"));
                boolean dictionary = type.startsWith("Dictionary(");
                boolean timestamp = type.startsWith("Timestamp(");
                if (tags ? dictionary : (!dictionary && !timestamp)) {
                    counts.merge(table, 1, Integer::sum);
                }
            }
            return counts;
        } catch (IllegalStateException e) {
            log.debug("[influxdb] 统计 {} 失败: {}", tags ? "标签数" : "字段数", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 统计每个度量最新一行的时间（v3 取每张表 {@code time} 列最大值）。
     *
     * @param target   访问目标
     * @param database 目标数据库
     * @param names    度量名列表
     * @return 度量名 → 可读时间
     */
    private Map<String, String> v3LastWrites(InfluxdbClient.Target target, String database,
                                             List<String> names) {
        Map<String, String> times = new LinkedHashMap<>();
        try {
            for (String measurement : names) {
                String sql = "SELECT time FROM " + sqlIdentifier(measurement)
                        + " ORDER BY time DESC LIMIT 1";
                List<JsonObject> rows = client.v3QuerySql(target, database, sql);
                if (!rows.isEmpty()) {
                    String time = str(rows.get(0).get("time"));
                    if (!time.isEmpty()) {
                        times.put(measurement, readableTime(time));
                    }
                }
            }
        } catch (IllegalStateException e) {
            log.debug("[influxdb] 统计最后写入时间失败: {}", e.getMessage());
        }
        return times;
    }

    /**
     * 发一条 InfluxQL，按服务端大版本走对应入口。
     *
     * <p>v1 与 v2 都能跑 InfluxQL，但入口完全不同：v1 是 {@code POST /query}，
     * v2 是 {@code /api/v2/query} 且必须带 {@code type=sql}（v2 关掉 flux-enabled
     * 时这是唯一可用通道）。因此凡是用 InfluxQL 的地方都从这里走，不要各处直连。</p>
     *
     * @param target   访问目标
     * @param database 目标数据库/桶名
     * @param sql      InfluxQL 语句
     * @return 行数据
     */
    private List<JsonObject> influxql(InfluxdbClient.Target target, String database, String sql) {
        // 1.x 与 2.x 的 InfluxQL 都走 v1 兼容 /query：2.x 的 /api/v2/query 只吃 Flux
        // （实测 2.7.11：type=sql/type=influxql 一律 400），走那里必失败。
        return client.v1Query(target, database, sql);
    }

    /**
     * 列出度量及统计（方言为 InfluxQL 时，覆盖 v1 与 v2）。
     *
     * <p>Flux 那条路是一条聚合查询拉回全部统计；InfluxQL 没有 {@code schema.*}，
     * 只能逐个度量发 {@code SHOW TAG KEYS / SHOW FIELD KEYS} 与
     * {@code ORDER BY time DESC LIMIT 1}，故这里的请求数随度量数线性增长。
     * 单项失败只留空该列，不影响其余度量（与 Flux 路一致）。</p>
     *
     * @param target 访问目标
     * @param bucket 目标桶/库名
     * @return 度量列表
     */
    private List<InfluxdbMeasurementVO> influxqlMeasurements(InfluxdbClient.Target target, String bucket) {
        List<String> names = sqlMeasurementNames(target, bucket);
        if (names.isEmpty()) {
            return List.of();
        }
        List<InfluxdbMeasurementVO> result = new ArrayList<>(names.size());
        for (String measurement : names) {
            result.add(new InfluxdbMeasurementVO(measurement,
                    sqlKeyCount(target, bucket, measurement, false),
                    sqlKeyCount(target, bucket, measurement, true),
                    sqlLastWrite(target, bucket, measurement)));
        }
        return result;
    }

    /**
     * 用 InfluxQL {@code SHOW MEASUREMENTS} 列出桶内的度量名。
     *
     * @param target 访问目标
     * @param bucket 目标桶名
     * @return 度量名列表，保持服务端顺序
     */
    private List<String> sqlMeasurementNames(InfluxdbClient.Target target, String bucket) {
        List<String> names = new ArrayList<>(Math.min(MEASUREMENT_LIMIT, 64));
        for (JsonObject row : influxql(target, bucket, "SHOW MEASUREMENTS")) {
            String name = firstNonBlank(str(row.get("name")), str(row.get("_value")));
            if (!name.isEmpty() && !names.contains(name) && names.size() < MEASUREMENT_LIMIT) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * 统计单个度量的标签键或字段键数量（InfluxQL {@code SHOW TAG KEYS / SHOW FIELD KEYS}）。
     *
     * @param target     访问目标
     * @param bucket     目标桶名
     * @param measurement 度量名
     * @param tags       true 统计标签键，false 统计字段键
     * @return 键数量，失败返回 null（留空）
     */
    private Integer sqlKeyCount(InfluxdbClient.Target target, String bucket, String measurement, boolean tags) {
        try {
            // 只写度量名，不写「桶.度量」：InfluxQL 的 SHOW FIELD/TAG KEYS 的 FROM
            // 子句只接受度量名，写成 "db"."measurement" 会被当成保留策略，
            // 报 retention policy not found（实测 1.11.8）。库由请求参数 db/bucket 指定。
            String sql = "SHOW " + (tags ? "TAG KEYS" : "FIELD KEYS")
                    + " FROM " + sqlIdentifier(measurement);
            return influxql(target, bucket, sql).size();
        } catch (IllegalStateException e) {
            log.debug("[influxdb] 统计 {} 失败: measurement={}", tags ? "标签数" : "字段数", measurement, e);
            return null;
        }
    }

    /**
     * 读取单个度量最近一行的写入时间（InfluxQL）。
     *
     * @param target     访问目标
     * @param bucket     目标桶名
     * @param measurement 度量名
     * @return 可读时间，无数据或失败返回 null（留空）
     */
    private String sqlLastWrite(InfluxdbClient.Target target, String bucket, String measurement) {
        try {
            String sql = "SELECT * FROM " + sqlIdentifier(measurement)
                    + " ORDER BY time DESC LIMIT 1";
            List<JsonObject> rows = influxql(target, bucket, sql);
            if (rows.isEmpty()) {
                return null;
            }
            String time = firstNonBlank(str(rows.get(0).get("time")), str(rows.get(0).get("_time")));
            return time.isEmpty() ? null : readableTime(time);
        } catch (IllegalStateException e) {
            log.debug("[influxdb] 统计最后写入时间失败: measurement={}", measurement, e);
            return null;
        }
    }

    /**
     * 确保 v1 数据库上存在名为 {@code default} 的保留策略并设置其保留期。
     *
     * <p>v1 的保留期不是库属性而是**保留策略**属性。刚建的库只有隐式 autogen
     * （{@code SHOW RETENTION POLICIES} 里查不到名为 default 的策略），此时 ALTER 会
     * 报 {@code retention policy not found}，必须先 CREATE；策略已存在则只能 ALTER
     * （重复 CREATE 会冲突）。所以先查一次再决定走哪条，而不是盲试。</p>
     *
     * <p>完整语法（缺 POLICY/ON/REPLICATION 任一段解析器都会拒绝，实测 1.11.8）：
     * {@code CREATE|ALTER RETENTION POLICY "default" ON "<db>" DURATION <n>h REPLICATION 1}。</p>
     *
     * @param target   访问目标
     * @param database 目标数据库
     * @param hours    保留小时数，0 表示永久
     * @throws IllegalStateException 语句执行失败
     */
    private void ensureRetentionPolicy(InfluxdbClient.Target target, String database, long hours) {
        String db = sqlIdentifier(database);
        boolean exists = false;
        for (JsonObject row : influxql(target, database, "SHOW RETENTION POLICIES ON " + db)) {
            if ("default".equalsIgnoreCase(str(row.get("name")))) {
                exists = true;
                break;
            }
        }
        String head = exists ? "ALTER RETENTION POLICY " : "CREATE RETENTION POLICY ";
        client.v1Execute(target, "", head + sqlIdentifier("default") + " ON " + db
                + " DURATION " + hours + "h REPLICATION 1");
    }

    @Override
    public InfluxdbQueryResultVO query(InfluxdbQueryDTO request) {
        InfluxdbClient.Target target = client.target(request == null ? null : request.datasourceId());
        boolean flux = target.usesFlux();
        String statement = requireText(request == null ? null : request.flux(),
                flux ? "Flux 查询语句不能为空" : "SQL 查询语句不能为空");
        long start = System.nanoTime();
        // 三条路而不是两条：v3 只有 SQL；v2 按登记方言在 Flux 与 InfluxQL 间切换
        // （v2 关掉 flux-enabled 时只剩 InfluxQL 可用）。
        List<JsonObject> raw;
        if (target.isV3()) {
            raw = client.v3QuerySql(target, database(request, target), statement);
        } else if (target.isV1()) {
            raw = client.v1Query(target, database(request, target), statement);
        } else if (flux) {
            raw = client.query(target, statement);
        } else {
            // v2 的 InfluxQL 走 v1 兼容 /query（/api/v2/query 只支持 Flux）
            raw = client.v1Query(target, database(request, target), statement);
        }
        long elapsed = Math.max(0L, (System.nanoTime() - start) / 1_000_000L);
        boolean truncated = raw.size() > QUERY_ROW_LIMIT;
        List<Map<String, Object>> rows = new ArrayList<>(Math.min(raw.size(), QUERY_ROW_LIMIT));
        for (int i = 0; i < raw.size() && i < QUERY_ROW_LIMIT; i++) {
            rows.add(raw.get(i));
        }
        return new InfluxdbQueryResultVO(statement, elapsed, rows, rows.size(), raw.size(), truncated);
    }

    /**
     * v3 查询的目标数据库：优先请求里携带的，缺省取实例第一个数据库。
     *
     * @param request 查询请求
     * @param target  访问目标
     * @return 数据库名
     */
    private String database(InfluxdbQueryDTO request, InfluxdbClient.Target target) {
        String database = trim(request == null ? null : request.database());
        return database.isEmpty() ? firstBucket(target) : database;
    }

    @Override
    public InfluxdbOrgVO org(Long datasourceId) {
        InfluxdbClient.Target target = client.target(datasourceId);
        if (target.isV3()) {
            // v3 已移除组织概念，返回空占位，前端据此隐藏 Org 面板
            return new InfluxdbOrgVO("", "");
        }
        if (target.isV1()) {
            // v1 也没有组织（database 直挂实例），返回空占位而不是去打 /api/v2/orgs（恒 404）
            return new InfluxdbOrgVO("", "");
        }
        JsonArray orgs = client.orgs(target).getJsonArray("orgs");
        if (orgs == null || orgs.size() == 0) {
            throw new IllegalStateException("当前 Token 未关联任何组织(org)");
        }
        JsonObject first = orgs.getJsonObject(0);
        return new InfluxdbOrgVO(str(first.get("id")), str(first.get("name")));
    }

    @Override
    public InfluxdbBucketVO createBucket(InfluxdbBucketDTO request) {
        InfluxdbClient.Target target = client.target(request == null ? null : request.datasourceId());
        String name = requireText(request == null ? null : request.name(),
                target.isV3() ? "数据库名称不能为空" : "Bucket 名称不能为空");
        if (target.isV3()) {
            // v3 的创建接口不回 body，名称即数据库名
            client.v3CreateDatabase(target, name, request.retentionHours());
            return new InfluxdbBucketVO(name, name, request.retentionHours(), null);
        }
        if (target.isV1()) {
            // v1 用 InfluxQL DDL 建库；保留期挂在**保留策略**上（不是库属性）：
            // 语法是 CREATE/ALTER RETENTION POLICY <policy> ON <db> DURATION <n>h REPLICATION 1，
            // 缺 POLICY/ON/REPLICATION 任一段都会被解析器拒绝。策略名固定 default。
            client.v1Execute(target, "", "CREATE DATABASE " + sqlIdentifier(name));
            if (request.retentionHours() != null && request.retentionHours() > 0) {
                ensureRetentionPolicy(target, name, request.retentionHours());
            }
            return new InfluxdbBucketVO(name, name, request.retentionHours(), null);
        }
        JsonObject created = client.createBucket(target, name,
                request.retentionHours(), request.description());
        return new InfluxdbBucketVO(str(created.get("id")), str(created.get("name")),
                retentionHours(created.get("retentionRules")),
                readableTime(firstNonBlank(str(created.get("createdAt")), str(created.get("created")))));
    }

    @Override
    public void deleteBucket(Long datasourceId, String bucketId) {
        requireText(bucketId, "Bucket ID 不能为空");
        InfluxdbClient.Target target = client.target(datasourceId);
        if (target.isV3()) {
            // v3 以数据库名为标识
            client.v3DeleteDatabase(target, bucketId);
            return;
        }
        if (target.isV1()) {
            // v1 以数据库名为标识，走 InfluxQL DDL
            client.v1Execute(target, "", "DROP DATABASE " + sqlIdentifier(bucketId));
            return;
        }
        client.deleteBucket(target, bucketId);
    }

    @Override
    public void updateBucketRetention(Long datasourceId, String bucketId, Long retentionHours) {
        requireText(bucketId, "Bucket ID 不能为空");
        InfluxdbClient.Target target = client.target(datasourceId);
        if (target.isV3()) {
            client.v3UpdateRetention(target, bucketId, retentionHours);
            return;
        }
        if (target.isV1()) {
            // v1 保留期挂在保留策略上，语法见 ensureRetentionPolicy；
            // 传 null 表示取消保留期（DURATION 0h = 永久）
            long hours = retentionHours == null ? 0L : retentionHours;
            ensureRetentionPolicy(target, bucketId, hours);
            return;
        }
        client.updateBucketRetention(target, bucketId, retentionHours);
    }

    @Override
    public void write(InfluxdbWriteDTO request) {
        String bucket = requireText(request == null ? null : request.bucket(), "目标 Bucket 不能为空");
        String lines = requireText(request == null ? null : request.lines(), "写入内容(lines)不能为空");
        InfluxdbClient.Target target = client.target(request == null ? null : request.datasourceId());
        if (target.isV3()) {
            client.v3Write(target, bucket, request.precision(), lines);
            return;
        }
        if (target.isV1()) {
            client.v1Write(target, bucket, request.precision(), lines);
            return;
        }
        client.write(target, bucket, request.precision(), lines);
    }

    /**
     * 解析测试目标：优先库中数据源，其次即席填写的地址，最后落到第一个 influxdb 数据源。
     *
     * @param request 测试请求，可为空
     * @return 访问目标
     */
    private InfluxdbClient.Target testTarget(MiddlewareTestDTO request) {
        if (request == null || request.host() == null || request.host().isBlank()) {
            return client.target(request == null ? null : request.datasourceId());
        }
        if (request.datasourceId() != null && request.datasourceId() > 0L) {
            return client.target(request.datasourceId());
        }
        DataSourceVO adhoc = new DataSourceVO(null, "ad-hoc", InfluxdbClient.DS_TYPE, request.host().trim(),
                request.port(), request.contextPath(), request.username(), request.password(), null, 1, null, null, null,
                null, null, null, null, null, null, null, null);
        return client.target(adhoc);
    }

    /**
     * 读取 Bucket 下的度量名。
     *
     * @param bucket 度量所属 Bucket 名
     * @param target 访问目标
     * @return 度量名列表，保持服务端顺序
     */
    private List<String> measurements(String bucket, InfluxdbClient.Target target) {
        String flux = "import \"influxdata/influxdb/schema\"\n"
                + "schema.measurements(bucket: " + quote(bucket) + ")";
        List<JsonObject> rows = client.query(target, flux);
        List<String> names = new ArrayList<>(Math.min(rows.size(), MEASUREMENT_LIMIT));
        for (JsonObject row : rows) {
            String name = firstNonBlank(str(row.get("_value")), str(row.get("_measurement")), str(row.get("name")));
            if (!name.isEmpty() && !names.contains(name) && names.size() < MEASUREMENT_LIMIT) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * 统计每个度量的标签数或字段数，聚合失败时返回空表由上层留白。
     *
     * @param target 访问目标
     * @param bucket 度量所属 Bucket 名
     * @param tags   true 统计标签键，false 统计字段键
     * @return 度量名 → 数量
     */
    private Map<String, Integer> countBy(InfluxdbClient.Target target, String bucket, boolean tags) {
        try {
            String flux = "import \"influxdata/influxdb/schema\"\n"
                    + "schema." + (tags ? "tagKeys" : "fieldKeys") + "(bucket: " + quote(bucket) + ")\n"
                    + "  |> group(columns: [\"_measurement\"])\n"
                    + "  |> count()\n"
                    + "  |> rename(columns: {_value: \"count\"})";
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (JsonObject row : client.query(target, flux)) {
                String measurement = str(row.get("_measurement"));
                Integer count = intOrNull(firstNonNull(row.get("count"), row.get("_value")));
                if (!measurement.isEmpty() && count != null) {
                    counts.put(measurement, count);
                }
            }
            return counts;
        } catch (IllegalStateException e) {
            log.debug("[influxdb] 统计 {} 失败: {}", tags ? "标签数" : "字段数", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 统计每个度量在回看窗口内的最后写入时间，失败或窗口内无数据时留空。
     *
     * @param target 访问目标
     * @param bucket 度量所属 Bucket 名
     * @return 度量名 → 可读时间
     */
    private Map<String, String> lastWrites(InfluxdbClient.Target target, String bucket) {
        try {
            String flux = "from(bucket: " + quote(bucket) + ")\n"
                    + "  |> range(start: " + LAST_WRITE_WINDOW + ")\n"
                    + "  |> group(columns: [\"_measurement\"])\n"
                    + "  |> max(column: \"_time\")";
            Map<String, String> times = new LinkedHashMap<>();
            for (JsonObject row : client.query(target, flux)) {
                String measurement = str(row.get("_measurement"));
                String time = str(row.get("_time"));
                if (!measurement.isEmpty() && !time.isEmpty()) {
                    times.put(measurement, readableTime(time));
                }
            }
            return times;
        } catch (IllegalStateException e) {
            log.debug("[influxdb] 统计最后写入时间失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 未指定 Bucket 时取第一个。
     *
     * @param target 访问目标
     * @return Bucket 名
     * @throws IllegalStateException org 下没有任何 Bucket
     */
    private String firstBucket(InfluxdbClient.Target target) {
        if (target.isV3()) {
            JsonArray items = client.v3Databases(target);
            for (int i = 0; i < items.size(); i++) {
                String name = str(items.getJsonObject(i).get("iox::database"));
                if (!name.isEmpty() && !"_internal".equals(name)) {
                    return name;
                }
            }
            throw new IllegalStateException("未指定数据库且当前实例没有可用数据库");
        }
        if (target.isV1()) {
            List<String> databases = client.v1Databases(target);
            if (!databases.isEmpty()) {
                return databases.get(0);
            }
            throw new IllegalStateException("未指定数据库且当前实例没有可用数据库");
        }
        JsonArray items = client.buckets(target);
        for (int i = 0; i < items.size(); i++) {
            String name = str(items.getJsonObject(i).get("name"));
            if (!name.isEmpty()) {
                return name;
            }
        }
        throw new IllegalStateException("未指定 Bucket 且当前组织下没有可用 Bucket");
    }

    /**
     * 保留时长换算成小时。
     *
     * @param rules retentionRules 原始值
     * @return 小时数，{@code 0} 表示永久保留；服务端未给规则时为 null
     */
    private static Long retentionHours(Object rules) {
        if (!(rules instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        if (!(list.get(0) instanceof Map<?, ?> rule)) {
            return null;
        }
        Long everySeconds = longOrNull(rule.get("everySeconds"));
        if (everySeconds != null) {
            return everySeconds / 3600L;
        }
        // InfluxDB 用 {"type":"drop"} 表示不设保留策略
        return "drop".equalsIgnoreCase(str(rule.get("type"))) ? 0L : null;
    }

    /**
     * Flux 字符串字面量加引号并转义，避免度量名里的引号改写语句。
     *
     * @param value 原值
     * @return 可直接拼进 Flux 的字面量
     */
    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /**
     * SQL 标识符加双引号并转义，避免度量名里的引号改写语句。
     *
     * @param name 原值
     * @return 可直接拼进 SQL 的标识符
     */
    private static String sqlIdentifier(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }

    /**
     * 把 RFC3339 时间换成本地可读时间，解析不了则原样返回。
     *
     * @param value 时间文本
     * @return 可读时间，空值转 null
     */
    private static String readableTime(String value) {
        String text = trim(value);
        if (text.isEmpty()) {
            return null;
        }
        try {
            long epochMillis = ZonedDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .toInstant().toEpochMilli();
            return MiddlewareNamespace.format(epochMillis);
        } catch (RuntimeException e) {
            try {
                return MiddlewareNamespace.format(Instant.parse(text).toEpochMilli());
            } catch (RuntimeException ignored) {
                return text;
            }
        }
    }

    /**
     * 要求字符串字段非空。
     *
     * @param value   原值
     * @param message 为空时的提示
     * @return 去空白后的值
     * @throws IllegalStateException 值为空
     */
    private static String requireText(String value, String message) {
        String text = trim(value);
        if (text.isEmpty()) {
            throw new IllegalStateException(message);
        }
        return text;
    }

    /**
     * 宽松取字符串。
     *
     * @param value 原始值
     * @return 文本，null 转空串
     */
    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
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
     * 取第一个非空值。
     *
     * @param values 候选值
     * @return 首个非空值，全空时为 null
     */
    private static Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 宽松取整数。
     *
     * @param value 原始值
     * @return 整数，取不到时为 null
     */
    private static Integer intOrNull(Object value) {
        Long parsed = longOrNull(value);
        return parsed == null ? null : parsed.intValue();
    }

    /**
     * 宽松取长整数，兼容 JSON 里的 Double 与字符串。
     *
     * @param value 原始值
     * @return 长整数，取不到时为 null
     */
    private static Long longOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            try {
                return (long) Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }

    /**
     * 空串归一为 null，配合全局 NON_NULL 策略从 JSON 中省略。
     *
     * @param value 原值
     * @return null 或原值
     */
    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
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
}
