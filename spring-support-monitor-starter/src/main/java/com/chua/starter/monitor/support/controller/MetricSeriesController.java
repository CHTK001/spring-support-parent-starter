package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.datasource.engine.Engine;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.monitor.support.config.MonitorInfluxProperties;
import com.chua.influxdb.support.engine.InfluxDbEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 指标时序曲线查询接口（数据源 InfluxDB）
 * <p>
 * 仅当 {@code monitor.influx.enabled=true} 且引擎可用时装配；
 * 返回结构对齐前端 echarts 折线图：{@code [[时间戳ms, 值], ...]} 升序。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/ms/monitor/metric")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "monitor.influx", name = "enabled", havingValue = "true")
public class MetricSeriesController {

    /** 默认查询窗口 */
    private static final String DEFAULT_WINDOW = "1m";

    /** 默认查询时长（小时） */
    private static final int DEFAULT_HOURS = 1;

    /** 单次返回点数上限（防止超大响应） */
    private static final int MAX_POINTS = 1440;

    /** 时序存储配置（提供库名） */
    private final MonitorInfluxProperties influxProperties;

    /**
     * 获取可用的 InfluxDB 引擎（SPI 方式, 与 InfluxMetricWriter 一致）
     *
     * @return 引擎或 null
     */
    private InfluxDbEngine engine() {
        Engine spi = ServiceProvider.of(Engine.class).getExtension("influxdb");
        return spi instanceof InfluxDbEngine influxEngine ? influxEngine : null;
    }

    /**
     * 指标均值曲线
     *
     * @param target    目标表(measurement)，如 ms_monitor_metric_cpu
     * @param monitorId 监控目标 ID
     * @param hours     回溯小时数，默认 1
     * @param window    聚合窗口，如 1m/5m/1h，默认 1m
     * @return data 为 [[ts,value],...]
     */
    @GetMapping("/series")
    public ReturnResult<List<List<Object>>> series(
            @RequestParam(defaultValue = "ms_monitor_metric") String target,
            @RequestParam Long monitorId,
            @RequestParam(defaultValue = "1") int hours,
            @RequestParam(defaultValue = DEFAULT_WINDOW) String window) {
        InfluxDbEngine engine = engine();
        if (engine == null) {
            return ReturnResult.error("InfluxDB 引擎未装配");
        }
        // 参数防御：白名单化 measurement 与窗口，防注入
        if (!target.matches("[a-zA-Z0-9_]+")) {
            return ReturnResult.error("非法 target");
        }
        if (!window.matches("\\d+[smhd]")) {
            return ReturnResult.error("非法 window");
        }
        int safeHours = Math.max(1, Math.min(hours, 24 * 7));
        String influxql = "SELECT mean(value) AS value FROM \"" + target + "\""
                + " WHERE monitor_id='" + monitorId + "' AND time > now() - " + safeHours + "h"
                + " GROUP BY time(" + window + ") fill(none)";
        try {
            QueryResult result = engine.client().query(new Query(influxql, influxProperties.getDatabase()));
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
                                continue; // fill(none) 空窗跳过
                            }
                            List<Object> pair = new ArrayList<>(2);
                            pair.add(toEpochMilli(String.valueOf(ts)));
                            pair.add(Double.parseDouble(String.valueOf(value)));
                            points.add(pair);
                        }
                    }
                }
            }
            // 截断保护：仅保留最近 MAX_POINTS 个点
            if (points.size() > MAX_POINTS) {
                points = points.subList(points.size() - MAX_POINTS, points.size());
            }
            return ReturnResult.ok(points);
        } catch (Exception e) {
            log.warn("[monitor] series 查询失败 target={} monitor={}: {}", target, monitorId, e.getMessage());
            return ReturnResult.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * InfluxDB 时间字符串(RFC3339)转毫秒时间戳
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
