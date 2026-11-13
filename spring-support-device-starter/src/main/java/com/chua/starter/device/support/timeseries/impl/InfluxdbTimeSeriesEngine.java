package com.chua.starter.device.support.timeseries.impl;

import com.chua.starter.device.support.timeseries.DeviceSeriesPoint;
import com.chua.starter.device.support.timeseries.DeviceSeriesQuery;
import com.chua.starter.device.support.timeseries.DeviceTimeSeriesEngine;
import com.chua.starter.device.support.timeseries.DeviceTimeSeriesProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 InfluxDB 1.x HTTP 接口的时序引擎。
 *
 * <p>直接用 JDK 的 {@link HttpClient} 调 {@code /write} 与 {@code /query}，不引入
 * influxdb-java：宿主只需要一个 InfluxDB 服务，不需要额外 jar。</p>
 *
 * <p>存储形态：measurement = {@code plugin.device.timeseries.measurement}（默认
 * {@code device_series}）；tag = {@code category / device / key / channel}；
 * field = {@code name / value / unit / extra}（全部字符串，,{@code extra} 为 JSON）。
 * 时间戳毫秒精度。</p>
 *
 * <p>InfluxQL 的标识符与字符串都会做转义；参数拼接面只剩「时间戳」与「条数」两个数字，
 * 其余一律走白名单/转义，避免注入。</p>
 *
 * @since 4.0.0.55
 * @author CH
 */
@Slf4j
public class InfluxdbTimeSeriesEngine implements DeviceTimeSeriesEngine {

    /**
     * 默认 measurement
     */
    private static final String DEFAULT_MEASUREMENT = "device_series";

    /**
     * 额外字段类型
     */
    private static final TypeReference<LinkedHashMap<String, String>> EXTRA_TYPE = new TypeReference<>() {
    };

    /**
     * JSON 解析
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * HTTP 200
     */
    private static final int HTTP_OK = 200;

    /**
     * HTTP 204
     */
    private static final int HTTP_NO_CONTENT = 204;

    /**
     * 服务地址（已去掉尾部斜杠），未配置时为 null
     */
    private final String baseUrl;

    /**
     * 库名
     */
    private final String database;

    /**
     * measurement 名
     */
    private final String measurement;

    /**
     * Basic 认证头，未配置用户名时为 null
     */
    private final String authorization;

    /**
     * HTTP 客户端
     */
    private final HttpClient httpClient;

    /**
     * 请求超时
     */
    private final Duration timeout;

    /**
     * 配置是否齐全
     */
    private final boolean configured;

    /**
     * 按配置构造。
     *
     * @param properties 时序配置
     */
    public InfluxdbTimeSeriesEngine(DeviceTimeSeriesProperties properties) {
        DeviceTimeSeriesProperties props = properties == null ? new DeviceTimeSeriesProperties() : properties;
        this.baseUrl = trimUrl(props.getUrl());
        this.database = props.getDatabase() == null || props.getDatabase().isBlank()
                ? "device" : props.getDatabase().trim();
        this.measurement = props.getMeasurement() == null || props.getMeasurement().isBlank()
                ? DEFAULT_MEASUREMENT : props.getMeasurement().trim();
        this.authorization = props.getUsername() == null || props.getUsername().isBlank()
                ? null
                : "Basic " + Base64.getEncoder().encodeToString(
                (props.getUsername() + ":" + (props.getPassword() == null ? "" : props.getPassword()))
                        .getBytes(StandardCharsets.UTF_8));
        this.timeout = Duration.ofSeconds(Math.max(props.getHttpTimeoutSeconds(), 1));
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.configured = this.baseUrl != null;
        if (!configured) {
            log.warn("[Device][Series] InfluxDB 引擎不可用：plugin.device.timeseries.url 未配置");
        }
    }

    @Override
    public String engine() {
        return "influxdb";
    }

    @Override
    public boolean isAvailable() {
        return configured;
    }

    @Override
    public String unavailableReason() {
        return configured ? "" : "plugin.device.timeseries.url 未配置";
    }

    @Override
    public void write(DeviceSeriesPoint point) {
        writeBatch(Collections.singletonList(point));
    }

    @Override
    public int writeBatch(List<DeviceSeriesPoint> points) {
        if (points == null || points.isEmpty()) {
            return 0;
        }
        requireAvailable();
        StringBuilder body = new StringBuilder();
        int accepted = 0;
        for (DeviceSeriesPoint point : points) {
            validate(point);
            body.append(line(point)).append('\n');
            accepted++;
        }
        String url = baseUrl + "/write?db=" + encode(database) + "&precision=ms";
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        HttpResponse<String> response = send(builder.build());
        if (response.statusCode() != HTTP_NO_CONTENT && response.statusCode() != HTTP_OK) {
            throw new IllegalStateException("InfluxDB 写入失败 HTTP " + response.statusCode() + ": " + response.body());
        }
        return accepted;
    }

    @Override
    public List<DeviceSeriesPoint> query(DeviceSeriesQuery query) {
        DeviceSeriesQuery condition = requireQuery(query);
        if (!configured) {
            return List.of();
        }
        String ql = "SELECT * FROM " + identifier(measurement) + where(condition)
                + " ORDER BY time DESC LIMIT " + condition.normalizedLimit();
        List<DeviceSeriesPoint> points = new ArrayList<>();
        for (JsonNode series : querySeries(ql)) {
            JsonNode columns = series.path("columns");
            JsonNode values = series.path("values");
            if (!columns.isArray() || !values.isArray()) {
                continue;
            }
            Map<String, Integer> index = new LinkedHashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                index.put(columns.get(i).asText(), i);
            }
            for (JsonNode row : values) {
                DeviceSeriesPoint point = toPoint(index, row);
                if (point != null) {
                    points.add(point);
                }
            }
        }
        if (condition.isAscending()) {
            Collections.reverse(points);
        }
        return points;
    }

    @Override
    public long count(DeviceSeriesQuery query) {
        DeviceSeriesQuery condition = requireQuery(query);
        if (!configured) {
            return 0L;
        }
        String ql = "SELECT COUNT(\"value\") FROM " + identifier(measurement) + where(condition);
        for (JsonNode series : querySeries(ql)) {
            JsonNode values = series.path("values");
            if (values.isArray() && !values.isEmpty()) {
                JsonNode row = values.get(0);
                if (row.isArray() && row.size() > 1) {
                    return row.get(1).asLong(0L);
                }
            }
        }
        return 0L;
    }

    @Override
    public int purge(String category, LocalDateTime before) {
        if (before == null) {
            throw new IllegalArgumentException("时序清理缺少必填参数: before");
        }
        if (!configured) {
            return 0;
        }
        StringBuilder ql = new StringBuilder("DELETE FROM ").append(identifier(measurement)).append(" WHERE");
        if (category != null && !category.isBlank()) {
            ql.append("\"category\" = ").append(literal(category.trim())).append(" AND ");
        }
        ql.append("time < ").append(toMillis(before)).append("ms");
        // InfluxQL 的 DELETE 不回传受影响行数，只能如实回报 -1（已清理）
        executeQuery(ql.toString());
        log.info("[Device][Series] InfluxDB 保留期清理: category={}, before={}", category, before);
        return -1;
    }

    /**
     * 执行查询并返回 series 数组。
     *
     * @param ql InfluxQL 语句
     * @return series 节点集合；无数据返回空集合
     */
    private List<JsonNode> querySeries(String ql) {
        JsonNode root = executeQuery(ql);
        List<JsonNode> result = new ArrayList<>();
        for (JsonNode item : root.path("results")) {
            if (item.has("error")) {
                throw new IllegalStateException("InfluxDB 查询错误: " + item.path("error").asText());
            }
            for (JsonNode series : item.path("series")) {
                result.add(series);
            }
        }
        return result;
    }

    /**
     * 执行一条 InfluxQL 并返回解析后的响应根节点。
     *
     * @param ql InfluxQL 语句
     * @return 响应 JSON
     */
    private JsonNode executeQuery(String ql) {
        requireAvailable();
        String url = baseUrl + "/query?db=" + encode(database) + "&epoch=ms&q=" + encode(ql);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET();
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        HttpResponse<String> response = send(builder.build());
        if (response.statusCode() != HTTP_OK) {
            throw new IllegalStateException("InfluxDB 查询失败 HTTP " + response.statusCode() + ": " + response.body());
        }
        try {
            return MAPPER.readTree(response.body());
        } catch (IOException ex) {
            throw new IllegalStateException("InfluxDB 响应不是合法 JSON: " + ex.getMessage(), ex);
        }
    }

    /**
     * 发送请求并把 IO 异常收敛成明确的后端错误。
     *
     * @param request 请求
     * @return 响应
     */
    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("InfluxDB 请求失败: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("InfluxDB 请求被中断", ex);
        }
    }

    /**
     * 时序点 → InfluxDB line protocol。
     *
     * @param point 时序点
     * @return 一行协议文本（不含换行）
     */
    private String line(DeviceSeriesPoint point) {
        StringBuilder tags = new StringBuilder();
        appendTag(tags, "category", point.getCategory());
        appendTag(tags, "device", point.getDeviceId());
        appendTag(tags, "key", point.getKey());
        appendTag(tags, "channel", point.getChannel());
        StringBuilder fields = new StringBuilder();
        appendField(fields, "name", point.getName());
        appendField(fields, "value", point.getValue());
        appendField(fields, "unit", point.getUnit());
        appendField(fields, "extra", toJson(point.getExtra()));
        if (fields.length() == 0) {
            // InfluxDB 要求至少一个 field；没有值的点确实没有存储意义
            throw new IllegalArgumentException("时序点没有可写入的字段: key=" + point.getKey());
        }
        return escapeMeasurement(measurement) + tags + " " + fields + " " + toMillis(point.getTime());
    }

    /**
     * 追加 tag。
     *
     * @param target 目标
     * @param name   tag 名
     * @param value  tag 值（空则跳过，InfluxDB 不接受空 tag）
     */
    private static void appendTag(StringBuilder target, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        target.append(',').append(name).append('=').append(escapeTag(value.trim()));
    }

    /**
     * 追加字符串 field。
     *
     * @param target 目标
     * @param name   field 名
     * @param value  field 值（空则跳过）
     */
    private static void appendField(StringBuilder target, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (target.length() > 0) {
            target.append(',');
        }
        target.append(name).append("=\"");
        for (char ch : value.toCharArray()) {
            if (ch == '"' || ch == '\\') {
                target.append('\\');
            }
            target.append(ch);
        }
        target.append('"');
    }

    /**
     * 结果行 → 时序点。
     *
     * @param index 列名 → 下标
     * @param row   行
     * @return 时序点；缺时间列返回 {@code null}
     */
    private static DeviceSeriesPoint toPoint(Map<String, Integer> index, JsonNode row) {
        Long millis = millisOf(row, index.get("time"));
        if (millis == null) {
            log.warn("[Device][Series] 丢弃无法解析时间的 InfluxDB 行");
            return null;
        }
        DeviceSeriesPoint point = new DeviceSeriesPoint();
        point.setCategory(text(row, index.get("category")));
        point.setDeviceId(text(row, index.get("device")));
        point.setKey(text(row, index.get("key")));
        point.setChannel(text(row, index.get("channel")));
        point.setName(text(row, index.get("name")));
        point.setValue(text(row, index.get("value")));
        point.setUnit(text(row, index.get("unit")));
        point.setTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()));
        point.setExtra(fromJson(text(row, index.get("extra"))));
        return point;
    }

    /**
     * 读取指定列的时间值。
     *
     * @param row   行
     * @param index 列下标
     * @return 毫秒时间戳；列不存在或无法解析返回 {@code null}
     */
    private static Long millisOf(JsonNode row, Integer index) {
        if (index == null || index >= row.size()) {
            return null;
        }
        JsonNode cell = row.get(index);
        if (cell == null || cell.isNull()) {
            return null;
        }
        if (cell.isNumber()) {
            return cell.asLong();
        }
        try {
            return Instant.parse(cell.asText()).toEpochMilli();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * 读取指定列的文本值。
     *
     * @param row   行
     * @param index 列下标
     * @return 文本；列不存在或为 null 返回 {@code null}
     */
    private static String text(JsonNode row, Integer index) {
        if (index == null || index >= row.size()) {
            return null;
        }
        JsonNode cell = row.get(index);
        return cell == null || cell.isNull() ? null : cell.asText();
    }

    /**
     * 拼装 WHERE 子句（含前导空格；无条件时返回空串）。
     *
     * @param query 查询条件
     * @return 子句
     */
    private static String where(DeviceSeriesQuery query) {
        StringBuilder sql = new StringBuilder(" WHERE \"category\" = ").append(literal(query.getCategory().trim()));
        if (query.getDeviceId() != null && !query.getDeviceId().isBlank()) {
            sql.append(" AND \"device\" = ").append(literal(query.getDeviceId().trim()));
        }
        if (query.getKey() != null && !query.getKey().isBlank()) {
            sql.append(" AND \"key\" = ").append(literal(query.getKey().trim()));
        }
        if (query.getFrom() != null) {
            sql.append(" AND time >= ").append(toMillis(query.getFrom())).append("ms");
        }
        if (query.getTo() != null) {
            sql.append(" AND time <= ").append(toMillis(query.getTo())).append("ms");
        }
        return sql.toString();
    }

    /**
     * 校验写入点。
     *
     * @param point 时序点
     */
    private static void validate(DeviceSeriesPoint point) {
        if (point == null) {
            throw new IllegalArgumentException("时序点不能为空");
        }
        if (point.getCategory() == null || point.getCategory().isBlank()) {
            throw new IllegalArgumentException("时序点缺少 category");
        }
        if (point.getDeviceId() == null || point.getDeviceId().isBlank()) {
            throw new IllegalArgumentException("时序点缺少 deviceId");
        }
        if (point.getKey() == null || point.getKey().isBlank()) {
            throw new IllegalArgumentException("时序点缺少 key");
        }
        if (point.getTime() == null) {
            throw new IllegalArgumentException("时序点缺少 time");
        }
    }

    /**
     * 校验查询条件。
     *
     * @param query 查询条件
     * @return 原条件
     */
    private static DeviceSeriesQuery requireQuery(DeviceSeriesQuery query) {
        if (query == null || query.getCategory() == null || query.getCategory().isBlank()) {
            throw new IllegalArgumentException("时序查询缺少必填参数: category");
        }
        return query;
    }

    /**
     * 引擎不可用时拒绝写入。
     */
    private void requireAvailable() {
        if (!configured) {
            throw new IllegalStateException("InfluxDB 时序引擎不可用：" + unavailableReason());
        }
    }

    /**
     * InfluxQL 标识符转义（双引号包裹）。
     *
     * @param name 标识符
     * @return 转义后的标识符
     */
    private static String identifier(String name) {
        return "\"" + name.replace("\"", "\\\"") + "\"";
    }

    /**
     * InfluxQL 字符串字面量（单引号包裹）。
     *
     * @param value 值
     * @return 转义后的字面量
     */
    private static String literal(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    /**
     * measurement 转义（逗号、空格）。
     *
     * @param value measurement
     * @return 转义后的 measurement
     */
    private static String escapeMeasurement(String value) {
        return value.replace("\\", "\\\\").replace(",", "\\,").replace(" ", "\\ ");
    }

    /**
     * tag 值转义（逗号、空格、等号）。
     *
     * @param value tag 值
     * @return 转义后的 tag 值
     */
    private static String escapeTag(String value) {
        return value.replace("\\", "\\\\").replace(",", "\\,")
                .replace(" ", "\\ ").replace("=", "\\=");
    }

    /**
     * 本地时间 → 毫秒时间戳。
     *
     * @param time 本地时间
     * @return 毫秒时间戳
     */
    private static long toMillis(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * 附加字段序列化。
     *
     * @param extra 附加字段
     * @return JSON 文本；空返回 {@code null}
     */
    private static String toJson(Map<String, String> extra) {
        if (extra == null || extra.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(extra);
        } catch (Exception ex) {
            log.warn("[Device][Series] 附加字段序列化失败，按空值写入: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * 附加字段反序列化。
     *
     * @param json JSON 文本
     * @return 附加字段；空或坏数据返回空 Map
     */
    private static Map<String, String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(json, EXTRA_TYPE);
        } catch (Exception ex) {
            log.warn("[Device][Series] 附加字段解析失败，按空值处理: {}", ex.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /**
     * URL 参数编码。
     *
     * @param value 原值
     * @return 编码后的值
     */
    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    /**
     * 地址归一：去空白、去尾部斜杠；空返回 null。
     *
     * @param url 配置地址
     * @return 归一化后的地址
     */
    private static String trimUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String value = url.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.isEmpty() ? null : value;
    }
}
