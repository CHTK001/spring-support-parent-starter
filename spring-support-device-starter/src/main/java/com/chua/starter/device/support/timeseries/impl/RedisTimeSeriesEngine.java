package com.chua.starter.device.support.timeseries.impl;

import com.chua.starter.device.support.timeseries.DeviceSeriesPoint;
import com.chua.starter.device.support.timeseries.DeviceSeriesQuery;
import com.chua.starter.device.support.timeseries.DeviceTimeSeriesEngine;
import com.chua.starter.device.support.timeseries.DeviceTimeSeriesProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基于 Redis ZSET 的时序引擎。
 *
 * <p>键设计：</p>
 * <ul>
 *   <li>序列：{@code {prefix}:{category}:{deviceId}:{key}} → ZSET，score=毫秒时间戳，
 *       member=时序点 JSON（保留 name/unit/extra，不需要额外的 hash）；</li>
 *   <li>设备下序列键索引：{@code {prefix}:keys:{category}:{deviceId}} → SET；</li>
 *   <li>分类下设备索引：{@code {prefix}:devices:{category}} → SET。</li>
 * </ul>
 *
 * <p>有索引就不需要 {@code KEYS/SCAN} 全库扫：跨设备查询先读设备索引，再读每台设备的序列键索引。
 * 单条序列超过 {@code max-points-per-series} 时按时间裁剪最旧的点。</p>
 *
 * <p>连接复用宿主已经装配好的 {@link StringRedisTemplate}（{@code spring.data.redis.*}），
 * 引擎自己不再建连；宿主没有 redis 时该引擎不可用，页面会给出原因。</p>
 *
 * @since 4.0.0.55
 * @author CH
 */
@Slf4j
public class RedisTimeSeriesEngine implements DeviceTimeSeriesEngine {

    /**
     * JSON 读写
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 一个分类下最多遍历的设备数（防止跨设备查询退化成全表扫描）
     */
    private static final int MAX_DEVICES = 2000;

    /**
     * Redis 模板，未引入 spring-data-redis 时为 null
     */
    private final StringRedisTemplate redis;

    /**
     * 键前缀
     */
    private final String keyPrefix;

    /**
     * 单条序列最大点数
     */
    private final int maxPointsPerSeries;

    /**
     * 按配置构造。
     *
     * @param properties 时序配置
     * @param redis      Redis 模板，可为 null（宿主未引入 redis）
     */
    public RedisTimeSeriesEngine(DeviceTimeSeriesProperties properties, StringRedisTemplate redis) {
        DeviceTimeSeriesProperties props = properties == null ? new DeviceTimeSeriesProperties() : properties;
        this.redis = redis;
        this.keyPrefix = props.getKeyPrefix() == null || props.getKeyPrefix().isBlank()
                ? "device:series" : props.getKeyPrefix().trim();
        this.maxPointsPerSeries = Math.max(props.getMaxPointsPerSeries(), 1000); // [P3C A20 豁免] 引擎最小点数保护
        if (redis == null) {
            log.warn("[Device][Series] Redis 引擎不可用：宿主未提供 StringRedisTemplate");
        } else if (props.getDatabase() != null && props.getDatabase().trim().matches("\\d+")) {
            // 逻辑库由 spring.data.redis.database 决定；这里只提示，不静默改连
            log.info("[Device][Series] Redis 逻辑库取自 spring.data.redis.database，"
                    + "plugin.device.timeseries.database={} 仅用于 influxdb", props.getDatabase());
        }
    }

    @Override
    public String engine() {
        return "redis";
    }

    @Override
    public boolean isAvailable() {
        return redis != null;
    }

    @Override
    public String unavailableReason() {
        return redis == null ? "宿主未引入 spring-data-redis 或未配置 spring.data.redis" : "";
    }

    @Override
    public void write(DeviceSeriesPoint point) {
        writeBatch(List.of(point));
    }

    @Override
    public int writeBatch(List<DeviceSeriesPoint> points) {
        if (points == null || points.isEmpty()) {
            return 0;
        }
        requireAvailable();
        int written = 0;
        for (DeviceSeriesPoint point : points) {
            validate(point);
            String json = toJson(point);
            long score = toMillis(point.getTime());
            String zsetKey = zsetKey(point.getCategory(), point.getDeviceId(), point.getKey());
            redis.opsForZSet().add(zsetKey, json, score);
            redis.opsForSet().add(keysIndexKey(point.getCategory(), point.getDeviceId()), point.getKey());
            redis.opsForSet().add(devicesIndexKey(point.getCategory()), point.getDeviceId());
            redis.opsForSet().add(prefix() + ":categories", point.getCategory().trim());
            trim(zsetKey);
            written++;
        }
        return written;
    }

    @Override
    public List<DeviceSeriesPoint> query(DeviceSeriesQuery query) {
        DeviceSeriesQuery condition = requireQuery(query);
        if (!isAvailable()) {
            return List.of();
        }
        int limit = condition.normalizedLimit();
        Double min = condition.getFrom() == null ? null : (double) toMillis(condition.getFrom());
        Double max = condition.getTo() == null ? null : (double) toMillis(condition.getTo());
        List<DeviceSeriesPoint> points = new ArrayList<>();
        for (String deviceId : devices(condition)) {
            for (String key : keys(condition, deviceId)) {
                // 每个序列先取「最近 limit 条」，跨序列合并后再统一排序截断，
                // 这样 limit 的语义与其它引擎一致：最近的 N 条，而不是最老的 N 条。
                Set<String> members = range(zsetKey(condition.getCategory(), deviceId, key), min, max, limit);
                for (String member : members) {
                    DeviceSeriesPoint point = fromJson(member);
                    if (matches(point, condition, deviceId, key)) {
                        points.add(point);
                    }
                }
            }
        }
        Comparator<DeviceSeriesPoint> order = Comparator.comparing(DeviceSeriesPoint::getTime);
        points.sort(condition.isAscending() ? order : order.reversed());
        return points.size() > limit ? new ArrayList<>(points.subList(0, limit)) : points;
    }

    @Override
    public long count(DeviceSeriesQuery query) {
        DeviceSeriesQuery condition = requireQuery(query);
        if (!isAvailable()) {
            return 0L;
        }
        Double min = condition.getFrom() == null ? -1D : (double) toMillis(condition.getFrom());
        Double max = condition.getTo() == null ? -1D : (double) toMillis(condition.getTo());
        long total = 0L;
        for (String deviceId : devices(condition)) {
            for (String key : keys(condition, deviceId)) {
                String zsetKey = zsetKey(condition.getCategory(), deviceId, key);
                if (min < 0 || max < 0) {
                    Long size = redis.opsForZSet().zCard(zsetKey);
                    total += size == null ? 0L : size;
                } else {
                    Long size = redis.opsForZSet().count(zsetKey, min, max);
                    total += size == null ? 0L : size;
                }
            }
        }
        return total;
    }

    @Override
    public int purge(String category, LocalDateTime before) {
        if (before == null) {
            throw new IllegalArgumentException("时序清理缺少必填参数: before");
        }
        if (!isAvailable()) {
            return 0;
        }
        Set<String> categories = new LinkedHashSet<>();
        if (category == null || category.isBlank()) {
            Set<String> members = redis.opsForSet().members(prefix() + ":categories");
            if (members != null) {
                categories.addAll(members);
            }
        } else {
            categories.add(category.trim());
        }
        long upper = toMillis(before) - 1;
        int deleted = 0;
        for (String current : categories) {
            Set<String> devices = redis.opsForSet().members(devicesIndexKey(current));
            if (devices == null) {
                continue;
            }
            for (String deviceId : devices) {
                Set<String> keys = redis.opsForSet().members(keysIndexKey(current, deviceId));
                if (keys == null) {
                    continue;
                }
                for (String key : keys) {
                    String zsetKey = zsetKey(current, deviceId, key);
                    Long removed = upper < 0 ? 0L : redis.opsForZSet().removeRangeByScore(zsetKey, 0, upper);
                    deleted += removed == null ? 0 : removed.intValue();
                }
            }
        }
        log.info("[Device][Series] Redis 保留期清理: category={}, before={}, deleted={}", category, before, deleted);
        return deleted;
    }

    /**
     * 取本次查询涉及的设备集合。
     *
     * @param query 条件
     * @return 设备 ID 集合
     */
    private Set<String> devices(DeviceSeriesQuery query) {
        if (query.getDeviceId() != null && !query.getDeviceId().isBlank()) {
            return Set.of(query.getDeviceId().trim());
        }
        Set<String> members = redis.opsForSet().members(devicesIndexKey(query.getCategory().trim()));
        if (members == null || members.isEmpty()) {
            return Set.of();
        }
        if (members.size() <= MAX_DEVICES) {
            return members;
        }
        log.warn("[Device][Series] 分类 {} 下设备数 {} 超过遍历上限 {}，本次只查前 {} 台",
                query.getCategory(), members.size(), MAX_DEVICES, MAX_DEVICES);
        return new LinkedHashSet<>(new ArrayList<>(members).subList(0, MAX_DEVICES));
    }

    /**
     * 取某设备下本次查询涉及的序列键。
     *
     * @param query    条件
     * @param deviceId 设备
     * @return 序列键集合
     */
    private Set<String> keys(DeviceSeriesQuery query, String deviceId) {
        if (query.getKey() != null && !query.getKey().isBlank()) {
            return Set.of(query.getKey().trim());
        }
        Set<String> members = redis.opsForSet().members(keysIndexKey(query.getCategory().trim(), deviceId));
        return members == null ? Set.of() : members;
    }

    /**
     * 按分数区间取最近的成员（倒序）。
     *
     * @param zsetKey ZSET 键
     * @param min     最小分数，null 表示无穷小
     * @param max     最大分数，null 表示无穷大
     * @param limit   条数
     * @return 成员集合（最新在前）
     */
    private Set<String> range(String zsetKey, Double min, Double max, int limit) {
        ZSetOperations<String, String> ops = redis.opsForZSet();
        Set<String> members;
        if (min == null && max == null) {
            members = ops.reverseRange(zsetKey, 0, limit - 1L);
        } else {
            double low = min == null ? Double.NEGATIVE_INFINITY : min;
            double high = max == null ? Double.POSITIVE_INFINITY : max;
            members = ops.reverseRangeByScore(zsetKey, low, high, 0, limit);
        }
        return members == null ? Set.of() : members;
    }

    /**
     * 裁剪超出上限的最旧点。
     *
     * @param zsetKey ZSET 键
     */
    private void trim(String zsetKey) {
        Long size = redis.opsForZSet().zCard(zsetKey);
        if (size == null || size <= maxPointsPerSeries) {
            return;
        }
        Long removed = redis.opsForZSet().removeRange(zsetKey, 0, size - maxPointsPerSeries - 1);
        if (removed != null && removed > 0) {
            log.debug("[Device][Series] 裁剪 Redis 序列 {} 最旧 {} 个点", zsetKey, removed);
        }
    }

    /**
     * 判断解析出来的点是否与查询条件一致。
     *
     * <p>跨设备查询时，序列键里可能含通配意义的字符；这里按解析后的字段再核一次，
     * 保证「查出来的点就是查的那条序列」。</p>
     *
     * @param point    点
     * @param query    条件
     * @param deviceId 期望设备
     * @param key      期望序列键
     * @return 匹配返回 true
     */
    private static boolean matches(DeviceSeriesPoint point, DeviceSeriesQuery query, String deviceId, String key) {
        if (point == null || point.getTime() == null) {
            return false;
        }
        if (query.getCategory() != null && !query.getCategory().trim().equals(point.getCategory())) {
            return false;
        }
        if (!deviceId.equals(point.getDeviceId()) || !key.equals(point.getKey())) {
            return false;
        }
        if (query.getFrom() != null && point.getTime().isBefore(query.getFrom())) {
            return false;
        }
        return query.getTo() == null || !point.getTime().isAfter(query.getTo());
    }

    /**
     * 序列 ZSET 键。
     *
     * @param category 分类
     * @param deviceId 设备
     * @param key      序列键
     * @return 键
     */
    private String zsetKey(String category, String deviceId, String key) {
        return prefix() + ':' + category.trim() + ':' + deviceId.trim() + ':' + key.trim();
    }

    /**
     * 设备下序列键索引。
     *
     * @param category 分类
     * @param deviceId 设备
     * @return 键
     */
    private String keysIndexKey(String category, String deviceId) {
        return prefix() + ":keys:" + category.trim() + ':' + deviceId.trim();
    }

    /**
     * 分类下设备索引。
     *
     * @param category 分类
     * @return 键
     */
    private String devicesIndexKey(String category) {
        return prefix() + ":devices:" + category.trim();
    }

    /**
     * 键前缀。
     *
     * @return 前缀
     */
    private String prefix() {
        return keyPrefix;
    }

    /**
     * 时序点 → JSON。
     *
     * @param point 时序点
     * @return JSON 文本
     */
    private static String toJson(DeviceSeriesPoint point) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("category", point.getCategory());
        body.put("deviceId", point.getDeviceId());
        body.put("channel", point.getChannel());
        body.put("key", point.getKey());
        body.put("name", point.getName());
        body.put("value", point.getValue());
        body.put("unit", point.getUnit());
        body.put("time", toMillis(point.getTime()));
        body.put("extra", point.getExtra());
        try {
            return MAPPER.writeValueAsString(body);
        } catch (Exception ex) {
            throw new IllegalStateException("时序点序列化失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * JSON → 时序点。
     *
     * @param json JSON 文本
     * @return 时序点；坏数据返回 {@code null}
     */
    private static DeviceSeriesPoint fromJson(String json) {
        try {
            JsonNode node = MAPPER.readTree(json);
            DeviceSeriesPoint point = new DeviceSeriesPoint();
            point.setCategory(text(node, "category"));
            point.setDeviceId(text(node, "deviceId"));
            point.setChannel(text(node, "channel"));
            point.setKey(text(node, "key"));
            point.setName(text(node, "name"));
            point.setValue(text(node, "value"));
            point.setUnit(text(node, "unit"));
            long millis = node.path("time").asLong(0L);
            if (millis > 0) {
                point.setTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()));
            }
            JsonNode extra = node.path("extra");
            if (extra.isObject()) {
                extra.fields().forEachRemaining(entry -> point.extra(entry.getKey(), entry.getValue().asText()));
            }
            return point;
        } catch (Exception ex) {
            log.warn("[Device][Series] Redis 成员解析失败，已跳过: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * 读取指定字段文本。
     *
     * @param node  JSON 节点
     * @param field 字段名
     * @return 文本；空返回 {@code null}
     */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
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
     * @param query 条件
     * @return 原条件
     */
    private static DeviceSeriesQuery requireQuery(DeviceSeriesQuery query) {
        if (query == null || query.getCategory() == null || query.getCategory().isBlank()) {
            throw new IllegalArgumentException("时序查询缺少必填参数: category");
        }
        return query;
    }

    /**
     * 引擎不可用拒绝写入。
     */
    private void requireAvailable() {
        if (!isAvailable()) {
            throw new IllegalStateException("Redis 时序引擎不可用：" + unavailableReason());
        }
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
}
