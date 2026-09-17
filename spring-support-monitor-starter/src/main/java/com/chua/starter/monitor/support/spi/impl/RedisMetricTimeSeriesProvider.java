package com.chua.starter.monitor.support.spi.impl;

import com.chua.starter.monitor.support.spi.CollectResult;
import com.chua.starter.monitor.support.spi.MetricPoint;
import com.chua.starter.monitor.support.spi.MetricTimeSeriesProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
* Redis 时序存储实现（基于 ZSET 简化时序）
* <p>
* 由 {@code monitor.timeseries.engine=redis} 条件装配。
* 每个指标一条 ZSET（member=时间戳, score=时间戳），价值观 存于 member，
* 以 {@code monitor:metric:<target>:<monitorId>:<metricName>} 为键。
* 查询按时间范围 {@code ZRANGEBYSCORE} 取点，按窗口抽样返回（简化 mean 聚合）。
* </p>
* <p>
* 注意：Redis 版为轻量实现，适合小规模指标；大规模场景建议 influxdb 等专用时序库。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "monitor.timeseries", name = "engine", havingValue = "redis")
public class RedisMetricTimeSeriesProvider implements MetricTimeSeriesProvider {

    /** ZSET 键 前缀 */
    private static final String KEY_PREFIX = "monitor:metric:";

    /** 单条 ZSET 上限（防止无限增长，保留最近 N 点） */
    private static final int MAX_POINTS_PER_METRIC = 100_000;

    /** 单次曲线返回点数上限 */
    private static final int MAX_SERIES_POINTS = 1440;

    /** Redis 客户端 */
    private final StringRedisTemplate redisTemplate;

    /** 是否可用（Redis 可达） */
    private final boolean available;

    /**
    * 构造
    *
    * @param redisTemplate Redis 模板
    */
    public RedisMetricTimeSeriesProvider(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        boolean ok = false;
        if (redisTemplate != null && redisTemplate.getConnectionFactory() != null) {
            try {
                redisTemplate.getConnectionFactory().getConnection().ping();
                ok = true;
            } catch (Exception e) {
                log.warn("[monitor] Redis 不可用，时序写入降级跳过: {}", e.getMessage());
            }
        } else {
            log.warn("[monitor] Redis 未配置（连接工厂为空），时序写入降级跳过");
        }
        this.available = ok;
    }

    @Override
    public String engine() {
        return "redis";
    }

    @Override
    public boolean isAvailable() {
        return available && redisTemplate != null;
    }

    @Override
    public void write(CollectResult result) {
        if (!isAvailable() || result == null || result.getPoints() == null || result.getPoints().isEmpty()) {
            return;
        }
        Long monitorId = result.getMonitor() != null ? result.getMonitor().getId() : null;
        long timestamp = result.getCollectTime() != null
                ? result.getCollectTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                : System.currentTimeMillis();
        for (MetricPoint point : result.getPoints()) {
            /**
            * 写入point。
            * @param monitorId 监控标识
            * @param point point
            * @param timestamp 时间戳
            */
            writePoint(monitorId, point, timestamp);
        }
    }

    @Override
    public void writePoint(Long monitorId, MetricPoint point, long timestamp) {
        if (!isAvailable()) {
            return;
        }
        try {
            String measurement = point.getTargetTable() == null || point.getTargetTable().isEmpty()
                    ? "ms_monitor_metric" : point.getTargetTable();
            String metric = point.getMetricName() == null ? "value" : point.getMetricName();
            String key = key(measurement, monitorId, metric);
 // member = 价值观 + ":" + ts（避免同 ts 覆盖），score = ts
            String member = point.getValue() + ":" + timestamp;
            redisTemplate.opsForZSet().add(key, member, timestamp);
            // 裁剪：删除最旧的超限记录
            Long size = redisTemplate.opsForZSet().size(key);
            if (size != null && size > MAX_POINTS_PER_METRIC) {
                long remove = size - MAX_POINTS_PER_METRIC;
                redisTemplate.opsForZSet().removeRange(key, 0, remove - 1);
            }
        } catch (Exception e) {
 // 时序写入失败不阻断 MySQL 主链路
            log.warn("[monitor] Redis 时序写入失败 metric={}: {}", point.getMetricName(), e.getMessage());
        }
    }

    @Override
    public List<List<Object>> series(String target, Long monitorId, int hours, String window) {
        if (!isAvailable()) {
            return List.of();
        }
        if (target == null || !target.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("非法 target");
        }
        long nowMs = System.currentTimeMillis();
        long startMs = nowMs - (long) Math.max(1, Math.min(hours, 24 * 7)) * 3600_000L;
        // 简化：不做精确窗口聚合，按升序取范围内点后按窗口等距抽样（近似 mean）
        String pattern = KEY_PREFIX + target + ":" + (monitorId == null ? "*" : monitorId) + ":*";
        List<List<Object>> points = new ArrayList<>(Math.min(MAX_SERIES_POINTS, 128));
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys == null || keys.isEmpty()) {
                return points;
            }
            for (String key : keys) {
 // score=ts, member=价值观:ts
                Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> tuples =
                        redisTemplate.opsForZSet().rangeByScoreWithScores(key, startMs, nowMs);
                if (tuples == null) {
                    continue;
                }
                for (var tuple : tuples) {
                    Double score = tuple.getScore();
                    String member = tuple.getValue();
                    if (score == null || member == null) {
                        continue;
                    }
                    int sep = member.lastIndexOf(':');
                    if (sep <= 0) {
                        continue;
                    }
                    try {
                        double value = Double.parseDouble(member.substring(0, sep));
                        List<Object> pair = new ArrayList<>(2);
                        pair.add(score.longValue());
                        pair.add(value);
                        points.add(pair);
                    } catch (NumberFormatException e) {
                        log.debug("[monitor] Redis 指标值解析失败: {}", member);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[monitor] Redis 时序查询失败 target={} monitor={}: {}", target, monitorId, e.getMessage());
            /**
            * runtime异常。
            * @param e.getMessage( e.获取消息(
            * @return runtime异常的结果
            */
            throw new RuntimeException("查询失败: " + e.getMessage(), e);
        }
        points.sort((a, b) -> Long.compare(((Number) a.getFirst()).longValue(), ((Number) b.getFirst()).longValue()));
        // 等距抽样降采样（近似窗口聚合）
        if (points.size() > MAX_SERIES_POINTS) {
            int step = (int) Math.ceil((double) points.size() / MAX_SERIES_POINTS);
            List<List<Object>> sampled = new ArrayList<>();
            for (int i = 0; i < points.size(); i += step) {
                sampled.add(points.get(i));
            }
            points = sampled;
        }
        return points;
    }

    /**
    * 构造 ZSET 键
    *
    * @param target    目标表
    * @param monitorId 监控 标识
    * @param metric    指标名
    * @return key
    */
    private String key(String target, Long monitorId, String metric) {
        return KEY_PREFIX + target + ":" + monitorId + ":" + metric;
    }
}
