package com.chua.starter.monitor.support.spi.impl;

import com.chua.common.support.lang.datasource.series.SeriesEngine;
import com.chua.starter.monitor.support.spi.CollectResult;
import com.chua.starter.monitor.support.spi.MetricPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 时序写入门面
 * <p>
 * 采集主链路只有一次写入：把 {@link CollectResult} 的指标点摊平成
 * {@code (target, monitorId, metricName, value, timestamp)} 五元组，交给容器里唯一的
 * {@link SeriesEngine}（由 {@code monitor.timeseries.engine} 选定 JDBC / InfluxDB / Redis）。
 * 引擎不可用时静默跳过，MySQL 明细表与告警主链路不受影响。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricTimeSeriesWriter {

    /**
     * 未指定目标表时的通用明细表 / 测量名
     */
    private static final String DEFAULT_TARGET = "ms_monitor_metric";

    /**
     * 已装配的时序引擎
     */
    private final SeriesEngine seriesEngine;

    /**
     * 当前引擎是否可用。
     *
     * @return true 表示可写入、可查询
     */
    public boolean isAvailable() {
        return seriesEngine != null && seriesEngine.isAvailable();
    }

    /**
     * 写入整批采集结果。
     *
     * @param result 采集结果
     */
    public void write(CollectResult result) {
        if (!isAvailable() || result == null || result.getPoints() == null || result.getPoints().isEmpty()) {
            return;
        }
        if (result.getMonitor() == null || result.getMonitor().getId() == null) {
            log.debug("[monitor] 采集结果缺少监控标识，跳过时序写入");
            return;
        }
        Long monitorId = result.getMonitor().getId();
        long timestamp = toEpochMilli(result.getCollectTime());
        for (MetricPoint point : result.getPoints()) {
            writePoint(monitorId, point, timestamp);
        }
    }

    /**
     * 写入单个指标点，失败只记录不抛出（时序双写不能拖垮采集主链路）。
     *
     * @param monitorId 监控标识
     * @param point     指标点
     * @param timestamp 时间戳（毫秒）
     */
    private void writePoint(Long monitorId, MetricPoint point, long timestamp) {
        if (point == null || point.getMetricName() == null || point.getValue() == null) {
            return;
        }
        String target = point.getTargetTable() == null || point.getTargetTable().isEmpty()
                ? DEFAULT_TARGET : point.getTargetTable();
        try {
            seriesEngine.writePoint(target, monitorId, point.getMetricName(), point.getValue(), timestamp);
        } catch (Exception e) {
            log.warn("[monitor] 时序写入失败 engine={} target={} metric={}: {}",
                    seriesEngine.engine(), target, point.getMetricName(), e.getMessage());
        }
    }

    /**
     * 均值曲线查询。
     *
     * @param target    目标表 / 测量名
     * @param monitorId 监控标识
     * @param hours     回溯小时数
     * @param window    聚合窗口
     * @return {@code [[ts, value], ...]} 升序，引擎不可用时为空列表
     */
    public List<List<Object>> series(String target, Long monitorId, int hours, String window) {
        if (!isAvailable()) {
            return List.of();
        }
        return seriesEngine.series(target, monitorId, hours, window);
    }

    /**
     * 采集时间转毫秒时间戳。
     *
     * @param collectTime 采集时间，可为空
     * @return 毫秒时间戳，为空时取当前时间
     */
    private static long toEpochMilli(LocalDateTime collectTime) {
        if (collectTime == null) {
            return System.currentTimeMillis();
        }
        return collectTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
