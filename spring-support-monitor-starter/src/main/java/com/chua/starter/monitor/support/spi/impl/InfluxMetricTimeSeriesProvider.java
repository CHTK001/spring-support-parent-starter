package com.chua.starter.monitor.support.spi.impl;

import com.chua.common.support.lang.datasource.dialect.Dialect;
import com.chua.common.support.lang.datasource.engine.Engine;
import com.chua.common.support.lang.datasource.engine.EngineDataSource;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.influxdb.support.engine.InfluxDbEngine;
import com.chua.starter.monitor.support.config.MonitorTimeseriesProperties;
import com.chua.starter.monitor.support.spi.CollectResult;
import com.chua.starter.monitor.support.spi.MetricPoint;
import com.chua.starter.monitor.support.spi.MetricTimeSeriesProvider;
import lombok.extern.slf4j.Slf4j;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * influxdb 时序存储实现
 * <p>
 * 由 {@code monitor.timeseries.engine=influxdb} 条件装配。
 * 采集明细以行协议写入，测量 与 MySQL 明细表同名（语义对齐），
 * 标签 包含 监控_标识；支持 mean 聚合曲线查询。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "monitor.timeseries", name = "engine", havingValue = "influxdb", matchIfMissing = true)
public class InfluxMetricTimeSeriesProvider implements MetricTimeSeriesProvider {

    /** SPI 扩展名 */
    private static final String SPI_NAME = "influxdb";

    /** 单次曲线返回点数上限（防止超大响应） */
    private static final int MAX_POINTS = 1440;

    /** 数据源名称（引擎内注册标识） */
    private static final String DATA_SOURCE_NAME = "monitor";

    /**
    * influxdb 引擎（SPI 获取，内存过滤引擎；写入/查询经下方 influxdb-client 直连）
    */
    private final InfluxDbEngine influxDbEngine;

    /** influxdb 客户端（InfluxDB v1 兼容端点直连） */
    private final InfluxDB influxClient;

    /** 时序配置（提供库名等） */
    private final MonitorTimeseriesProperties properties;

    /**
    * 构造时初始化 SPI 引擎并注册数据源
    *
    * @param properties 时序存储配置
    */
    public InfluxMetricTimeSeriesProvider(MonitorTimeseriesProperties properties) {
        this.properties = properties;
        InfluxDbEngine engine = null;
        InfluxDB client = null;
        try {
            Engine spi = ServiceProvider.of(Engine.class).getExtension(SPI_NAME);
            if (spi instanceof InfluxDbEngine influxEngine) {
                engine = influxEngine;
                // 注册内存数据源：承载查询缓存，写入走下方 influxdb-client 直连
                engine.addDataSource(DATA_SOURCE_NAME, newInfluxDataSource(properties));
            } else {
                log.error("[monitor] InfluxDB SPI 引擎类型异常: {}", spi == null ? "null" : spi.getClass().getName());
            }
            client = InfluxDBFactory.connect(properties.getUrl());
        } catch (Exception e) {
            log.error("[monitor] InfluxDB 引擎初始化失败(将跳过时序写入): {}", e.getMessage());
        }
        this.influxDbEngine = engine;
        this.influxClient = client;
    }

    /**
    * 构造 InfluxDB 内存数据源（承载查询过滤，无方言）
    *
    * @param properties 时序配置
    * @return 数据源实例
    */
    private <T> EngineDataSource<T> newInfluxDataSource(MonitorTimeseriesProperties properties) {
        return new EngineDataSource<T>() {
            @Override
            public String name() {
                return DATA_SOURCE_NAME;
            }

            @Override
            public T getSource() {
                return null;
            }

            @Override
            public EngineDataSource<T> setSource(Object source) {
                return this;
            }

            @Override
            public Dialect getDialect() {
                return null;
            }

            @Override
            public EngineDataSource<T> setDialect(Dialect dialect) {
                return this;
            }

            @Override
            public String url() {
                return properties.getUrl();
            }

            @Override
            public String username() {
                return null;
            }

            @Override
            public String password() {
                return null;
            }

            @Override
            public String database() {
                return properties.getDatabase();
            }
        };
    }

    @Override
    public String engine() {
        return "influxdb";
    }

    @Override
    public boolean isAvailable() {
        return influxClient != null;
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
            Map<String, String> tags = new HashMap<>();
            if (monitorId != null) {
                tags.put("monitor_id", String.valueOf(monitorId));
            }
            if (point.getTags() != null) {
                tags.putAll(point.getTags());
            }
            String measurement = point.getTargetTable() == null || point.getTargetTable().isEmpty()
                    ? "ms_monitor_metric" : point.getTargetTable();
            var builder = Point.measurement(measurement)
                    .time(timestamp, TimeUnit.MILLISECONDS)
                    .addField("value", point.getValue());
            tags.forEach(builder::tag);
            influxClient.write(builder.build());
        } catch (Exception e) {
            // 时序写入失败不阻断 MySQL 主链路，仅告警日志
            log.warn("[monitor] InfluxDB 写入失败 metric={}: {}", point.getMetricName(), e.getMessage());
        }
    }

    @Override
    public List<List<Object>> series(String target, Long monitorId, int hours, String window) {
        if (!isAvailable()) {
            return List.of();
        }
        if (target == null || !target.matches("[a-zA-Z0-9_]+")) {
            /**
            * illegal参数异常。
            * @param target" Target"
            * @return illegal参数异常的结果
            */
            throw new IllegalArgumentException("非法 target");
        }
        if (window == null || !window.matches("\\d+[smhd]")) {
            /**
            * illegal参数异常。
            * @param window" 窗口"
            * @return illegal参数异常的结果
            */
            throw new IllegalArgumentException("非法 window");
        }
        int safeHours = Math.max(1, Math.min(hours, 24 * 7));
        // Target/窗口 已正则白名单化、监控标识 为 Long 强类型（influxql 非 JDBC，无注入面）
        String influxql = """
                /**
                * mean。
                * @param value 价值观
                * @return mean的结果
                */
                SELECT mean(value) AS value FROM "%s"
                WHERE monitor_id='%s' AND time > now() - %sh
                /**
                * 时间。
                * @param %s %s
                * @return 时间的结果
                */
                GROUP BY time(%s) fill(none)
                """.formatted(target, monitorId, safeHours, window);
        try {
            QueryResult result = influxClient.query(new Query(influxql, properties.getDatabase()));
            List<List<Object>> points = new ArrayList<>();
            if (result != null && result.getResults() != null) {
                for (QueryResult.Result r : result.getResults()) {
                    if (r.getSeries() == null) {
                        continue;
                    }
                    for (QueryResult.Series s : r.getSeries()) {
                        int timeIdx = s.getColumns().indexOf("time");
                        int valueIdx = s.getColumns().indexOf("value");
                        for (List<Object> row : s.getValues()) {
                            Object ts = row.get(timeIdx);
                            Object value = valueIdx >= 0 ? row.get(valueIdx) : null;
                            if (ts == null || value == null) {
                                continue; // fill(无) 空窗跳过
                            }
                            List<Object> pair = new ArrayList<>(2);
                            pair.add(toEpochMilli(String.valueOf(ts)));
                            pair.add(Double.parseDouble(String.valueOf(value)));
                            points.add(pair);
                        }
                    }
                }
            }
            // 截断保护：仅保留最近 最大_POINTS 个点
            if (points.size() > MAX_POINTS) {
                points = points.subList(points.size() - MAX_POINTS, points.size());
            }
            return points;
        } catch (Exception e) {
            log.warn("[monitor] series 查询失败 target={} monitor={}: {}", target, monitorId, e.getMessage());
            /**
            * runtime异常。
            * @param e.getMessage( e.获取消息(
            * @return runtime异常的结果
            */
            throw new RuntimeException("查询失败: " + e.getMessage(), e);
        }
    }

    /**
    * influxdb 时间字符串(RFC3339)转毫秒时间戳
    *
    * @param iso 时间串
    * @return 毫秒
    */
    private long toEpochMilli(String iso) {
        try {
            return java.time.Instant.parse(iso).toEpochMilli();
        } catch (Exception e) {
            return 0L;
        }
    }
}
