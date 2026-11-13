package com.chua.starter.device.support.timeseries.impl;

import com.chua.starter.device.support.timeseries.DeviceSeriesPoint;
import com.chua.starter.device.support.timeseries.DeviceSeriesQuery;
import com.chua.starter.device.support.timeseries.DeviceTimeSeriesEngine;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 基于宿主数据源的时序引擎（默认实现）。
 *
 * <p>把时序点写进一张明细表 {@code device_series_point}，按
 * {@code (category, device_id, key, time)} 建索引，查询走「分类 + 时间窗 + 条数上限」。
 * 不依赖任何额外中间件，随宿主 MySQL 一起备份、一起迁移。</p>
 *
 * <p>表结构见 {@code db/init/V2.0__init_all_device.sql} 与
 * {@code db/init/V2.2__device_timeseries.sql}。</p>
 *
 * @since 4.0.0.55
 * @author CH
 */
@Slf4j
public class BeanTimeSeriesEngine implements DeviceTimeSeriesEngine {

    /**
     * 默认表名
     */
    private static final String DEFAULT_TABLE = "device_series_point";

    /**
     * 表名白名单：只允许字母/数字/下划线，避免配置注入
     */
    private static final String TABLE_PATTERN = "[A-Za-z0-9_]+";

    /**
     * 明细列（查询与写入共用，保证两边顺序一致）
     */
    private static final String COLUMNS = "device_series_category, device_series_device_id,"
            + " device_series_channel, device_series_key, device_series_name, device_series_value,"
            + " device_series_unit, device_series_time, device_series_extra";

    /**
     * JSON 读写（额外字段序列化）
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 额外字段类型
     */
    private static final TypeReference<LinkedHashMap<String, String>> EXTRA_TYPE = new TypeReference<>() {
    };

    /**
     * JDBC 模板
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 明细表名
     */
    private final String table;

    /**
     * 单条写入语句
     */
    private final String insertSql;

    /**
     * 构造 bean 引擎。
     *
     * @param jdbcTemplate 宿主 JDBC 模板
     * @param table        明细表名，空则取 {@value #DEFAULT_TABLE}
     */
    public BeanTimeSeriesEngine(JdbcTemplate jdbcTemplate, String table) {
        this.jdbcTemplate = jdbcTemplate;
        this.table = normalizeTable(table);
        this.insertSql = "INSERT INTO " + this.table + " (device_series_point_id, " + COLUMNS
                + ", device_series_created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    @Override
    public String engine() {
        return "bean";
    }

    @Override
    public boolean isAvailable() {
        return jdbcTemplate != null;
    }

    @Override
    public String unavailableReason() {
        return jdbcTemplate == null ? "宿主未提供 DataSource / JdbcTemplate" : "";
    }

    @Override
    public void write(DeviceSeriesPoint point) {
        jdbcTemplate.update(insertSql, argsOf(point));
    }

    @Override
    public int writeBatch(List<DeviceSeriesPoint> points) {
        if (points == null || points.isEmpty()) {
            return 0;
        }
        List<Object[]> batch = new ArrayList<>(points.size());
        for (DeviceSeriesPoint point : points) {
            batch.add(argsOf(point));
        }
        int[] affected = jdbcTemplate.batchUpdate(insertSql, batch);
        int written = 0;
        for (int value : affected) {
            // batchUpdate 用 SUCCESS_NO_INFO(-2) 表示「执行成功但驱动不给行数」，不能当失败
            if (value > 0 || value == java.sql.Statement.SUCCESS_NO_INFO) {
                written++;
            }
        }
        return written;
    }

    @Override
    public List<DeviceSeriesPoint> query(DeviceSeriesQuery query) {
        DeviceSeriesQuery condition = requireQuery(query);
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT ").append(COLUMNS)
                .append(" FROM ").append(table)
                .append(" WHERE device_series_category = ?");
        args.add(condition.getCategory());
        appendFilters(sql, args, condition);
        // 语义：limit = 「最近的 N 条」，再按 order 返回。
        // 先倒序取 N 再在内存里翻转，保证升序视图拿到的是最新的一段，而不是最老的一段。
        sql.append(" ORDER BY device_series_time DESC LIMIT ?");
        args.add(condition.normalizedLimit());
        List<DeviceSeriesPoint> points = jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> toPoint(rs), args.toArray());
        if (condition.isAscending()) {
            Collections.reverse(points);
        }
        return points;
    }

    @Override
    public long count(DeviceSeriesQuery query) {
        DeviceSeriesQuery condition = requireQuery(query);
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(table)
                .append(" WHERE device_series_category = ?");
        args.add(condition.getCategory());
        appendFilters(sql, args, condition);
        Long total = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return total == null ? 0L : total;
    }

    @Override
    public int purge(String category, LocalDateTime before) {
        if (before == null) {
            throw new IllegalArgumentException("时序清理缺少必填参数: before");
        }
        if (category == null || category.isBlank()) {
            return jdbcTemplate.update("DELETE FROM " + table + " WHERE device_series_time < ?",
                    Timestamp.valueOf(before));
        }
        return jdbcTemplate.update("DELETE FROM " + table
                        + " WHERE device_series_category = ? AND device_series_time < ?",
                category.trim(), Timestamp.valueOf(before));
    }

    /**
     * 拼装通用筛选条件（设备 / 序列键 / 时间窗）。
     *
     * @param sql   目标 SQL
     * @param args  参数容器
     * @param query 查询条件
     */
    private static void appendFilters(StringBuilder sql, List<Object> args, DeviceSeriesQuery query) {
        if (query.getDeviceId() != null && !query.getDeviceId().isBlank()) {
            sql.append(" AND device_series_device_id = ?");
            args.add(query.getDeviceId().trim());
        }
        if (query.getKey() != null && !query.getKey().isBlank()) {
            sql.append(" AND device_series_key = ?");
            args.add(query.getKey().trim());
        }
        if (query.getFrom() != null) {
            sql.append(" AND device_series_time >= ?");
            args.add(Timestamp.valueOf(query.getFrom()));
        }
        if (query.getTo() != null) {
            sql.append(" AND device_series_time <= ?");
            args.add(Timestamp.valueOf(query.getTo()));
        }
    }

    /**
     * 校验查询条件并补齐分类。
     *
     * @param query 原始条件
     * @return 归一化后的条件
     */
    private static DeviceSeriesQuery requireQuery(DeviceSeriesQuery query) {
        if (query == null || query.getCategory() == null || query.getCategory().isBlank()) {
            throw new IllegalArgumentException("时序查询缺少必填参数: category");
        }
        return query;
    }

    /**
     * 时序点 → 写入参数。
     *
     * @param point 时序点
     * @return 参数数组
     */
    private Object[] argsOf(DeviceSeriesPoint point) {
        validate(point);
        return new Object[]{
                UUID.randomUUID().toString().replace("-", ""),
                point.getCategory().trim(),
                point.getDeviceId().trim(),
                blankToNull(point.getChannel()),
                point.getKey().trim(),
                blankToNull(point.getName()),
                blankToNull(point.getValue()),
                blankToNull(point.getUnit()),
                Timestamp.valueOf(point.getTime()),
                toJson(point.getExtra()),
                Timestamp.valueOf(LocalDateTime.now())
        };
    }

    /**
     * 写入前校验必填字段。
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
     * 结果集行 → 时序点。
     *
     * @param rs 结果集
     * @return 时序点
     * @throws SQLException 读取失败
     */
    private static DeviceSeriesPoint toPoint(ResultSet rs) throws SQLException {
        DeviceSeriesPoint point = new DeviceSeriesPoint();
        point.setCategory(rs.getString("device_series_category"));
        point.setDeviceId(rs.getString("device_series_device_id"));
        point.setChannel(rs.getString("device_series_channel"));
        point.setKey(rs.getString("device_series_key"));
        point.setName(rs.getString("device_series_name"));
        point.setValue(rs.getString("device_series_value"));
        point.setUnit(rs.getString("device_series_unit"));
        Timestamp time = rs.getTimestamp("device_series_time");
        point.setTime(time == null ? null : time.toLocalDateTime());
        point.setExtra(fromJson(rs.getString("device_series_extra")));
        return point;
    }

    /**
     * 附加字段序列化为 JSON。
     *
     * @param extra 附加字段
     * @return JSON 文本
     */
    private static String toJson(Map<String, String> extra) {
        if (extra == null || extra.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(extra);
        } catch (Exception ex) {
            log.warn("[Device][Series] 附加字段序列化失败，按空值落库: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * JSON 反序列化为附加字段。
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
     * 空串归一为 null（避免唯一/索引上出现大量空串）。
     *
     * @param value 原值
     * @return null 或原值
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 表名归一化与白名单校验。
     *
     * @param table 配置值
     * @return 可安全拼进 SQL 的表名
     */
    private static String normalizeTable(String table) {
        String value = table == null || table.isBlank() ? DEFAULT_TABLE : table.trim();
        if (!value.matches(TABLE_PATTERN)) {
            throw new IllegalArgumentException("非法时序表名: " + table + "（只允许字母、数字、下划线）");
        }
        return value;
    }
}
