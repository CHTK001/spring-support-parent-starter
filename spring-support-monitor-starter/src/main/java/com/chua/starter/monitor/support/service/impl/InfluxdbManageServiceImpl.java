package com.chua.starter.monitor.support.service.impl;

import com.chua.common.support.lang.json.JsonArray;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.starter.monitor.support.middleware.InfluxdbClient;
import com.chua.starter.monitor.support.middleware.MiddlewareNamespace;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.monitor.support.model.InfluxdbBucketVO;
import com.chua.starter.monitor.support.model.InfluxdbMeasurementVO;
import com.chua.starter.monitor.support.model.InfluxdbOrgVO;
import com.chua.starter.monitor.support.model.InfluxdbQueryResultVO;
import com.chua.starter.monitor.support.model.MiddlewareTestVO;
import com.chua.starter.monitor.support.model.dto.InfluxdbBucketDTO;
import com.chua.starter.monitor.support.model.dto.InfluxdbQueryDTO;
import com.chua.starter.monitor.support.model.dto.InfluxdbWriteDTO;
import com.chua.starter.monitor.support.model.dto.MiddlewareTestDTO;
import com.chua.starter.monitor.support.service.InfluxdbManageService;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * InfluxDB 管理实现：Bucket 走 {@code /api/v2/buckets}，度量与查询走 Flux。
 *
 * <p>度量列表按「一次测量名 + 三次聚合」取数，聚合失败只把对应列留空，不让整个列表失败；
 * 最后写入时间只回看 {@value #LAST_WRITE_WINDOW}，窗口内无数据的度量留空而不是谎报。</p>
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
            // /health 不校验 Token，只探它会把「地址通、凭据错」判成连接成功，因此以必带凭据的 orgs 接口为准
            client.getObject(target, "/api/v2/orgs", Map.of());
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
            return emptyToNull(str(client.health(target).get("version")));
        } catch (IllegalStateException e) {
            log.debug("[influxdb] 健康接口不可用: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public List<InfluxdbBucketVO> buckets(Long datasourceId) {
        InfluxdbClient.Target target = client.target(datasourceId);
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

    @Override
    public List<InfluxdbMeasurementVO> measurements(Long datasourceId, String bucket) {
        InfluxdbClient.Target target = client.target(datasourceId);
        String name = trim(bucket);
        if (name.isEmpty()) {
            name = firstBucket(target);
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

    @Override
    public InfluxdbQueryResultVO query(InfluxdbQueryDTO request) {
        String flux = requireText(request == null ? null : request.flux(), "Flux 查询语句不能为空");
        InfluxdbClient.Target target = client.target(request.datasourceId());
        long start = System.nanoTime();
        List<JsonObject> raw = client.query(target, flux);
        long elapsed = Math.max(0L, (System.nanoTime() - start) / 1_000_000L);
        boolean truncated = raw.size() > QUERY_ROW_LIMIT;
        List<Map<String, Object>> rows = new ArrayList<>(Math.min(raw.size(), QUERY_ROW_LIMIT));
        for (int i = 0; i < raw.size() && i < QUERY_ROW_LIMIT; i++) {
            rows.add(raw.get(i));
        }
        return new InfluxdbQueryResultVO(flux, elapsed, rows, rows.size(), raw.size(), truncated);
    }

    @Override
    public InfluxdbOrgVO org(Long datasourceId) {
        InfluxdbClient.Target target = client.target(datasourceId);
        JsonArray orgs = client.orgs(target).getJsonArray("orgs");
        if (orgs == null || orgs.size() == 0) {
            throw new IllegalStateException("当前 Token 未关联任何组织(org)");
        }
        JsonObject first = orgs.getJsonObject(0);
        return new InfluxdbOrgVO(str(first.get("id")), str(first.get("name")));
    }

    @Override
    public InfluxdbBucketVO createBucket(InfluxdbBucketDTO request) {
        String name = requireText(request == null ? null : request.name(), "Bucket 名称不能为空");
        InfluxdbClient.Target target = client.target(request == null ? null : request.datasourceId());
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
        client.deleteBucket(target, bucketId);
    }

    @Override
    public void updateBucketRetention(Long datasourceId, String bucketId, Long retentionHours) {
        requireText(bucketId, "Bucket ID 不能为空");
        InfluxdbClient.Target target = client.target(datasourceId);
        client.updateBucketRetention(target, bucketId, retentionHours);
    }

    @Override
    public void write(InfluxdbWriteDTO request) {
        String bucket = requireText(request == null ? null : request.bucket(), "目标 Bucket 不能为空");
        String lines = requireText(request == null ? null : request.lines(), "写入内容(lines)不能为空");
        InfluxdbClient.Target target = client.target(request == null ? null : request.datasourceId());
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
                null, null, null, null, null, null);
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
