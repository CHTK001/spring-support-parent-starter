package com.chua.starter.monitor.support.service.alarm;

import com.chua.common.support.lang.datasource.engine.Engine;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.influxdb.support.engine.InfluxDbEngine;
import com.chua.starter.monitor.support.config.MonitorInfluxProperties;
import com.chua.starter.monitor.support.spi.CollectResult;
import com.chua.starter.monitor.support.spi.MetricPoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * InfluxDB 指标写入器
 * <p>
 * 将采集管道中的 {@link MetricPoint} 以行协议写入 InfluxDB：
 * <ul>
 *   <li>measurement = MetricPoint.targetTable（与 MySQL 明细表同名，语义对齐）</li>
 *   <li>tag monitor_id = 监控目标 ID（查询过滤维度）</li>
 *   <li>原 tags 逐项平铺为 tag；field value = 指标值</li>
 * </ul>
 * 引擎经 {@link ServiceProvider} SPI(influxdb) 获取（utils-support-influxdb-starter
 * 为纯 SPI 扩展，无 Spring 自动配置）；通过 {@code monitor.influx.enabled=true}
 * 条件装配本组件，未启用时写入链路判空跳过，保证零侵入。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "monitor.influx", name = "enabled", havingValue = "true")
public class InfluxMetricWriter {

    /** SPI 扩展名 */
    private static final String SPI_NAME = "influxdb";

    /** InfluxDB 引擎（SPI 获取） */
    private final InfluxDbEngine influxDbEngine;

    /**
     * 构造时初始化 SPI 引擎并注册数据源
     *
     * @param properties 时序存储配置
     */
    public InfluxMetricWriter(MonitorInfluxProperties properties) {
        InfluxDbEngine engine = null;
        try {
            Engine spi = ServiceProvider.of(Engine.class).getExtension(SPI_NAME);
            if (spi instanceof InfluxDbEngine influxEngine) {
                engine = influxEngine;
                // 显式注册默认数据源, 确保 database 与配置一致
                engine.addDataSource("monitor", properties.getUrl(),
                        properties.getDatabase(), null, null);
            } else {
                log.error("[monitor] InfluxDB SPI 引擎类型异常: {}", spi == null ? "null" : spi.getClass().getName());
            }
        } catch (Exception e) {
            log.error("[monitor] InfluxDB 引擎初始化失败(将跳过时序写入): {}", e.getMessage());
        }
        this.influxDbEngine = engine;
    }

    /**
     * 是否可用（引擎装配成功）
     *
     * @return true 表示可写入
     */
    public boolean isAvailable() {
        return influxDbEngine != null;
    }
    }

    /**
     * 是否可用（引擎装配成功）
     *
     * @return true 表示可写入
     */
    public boolean isAvailable() {
        return influxDbEngine != null;
    }

    /**
     * 写入整批采集结果
     *
     * @param result 采集结果
     */
    public void write(CollectResult result) {
        if (!isAvailable() || result == null || result.getPoints() == null || result.getPoints().isEmpty()) {
            return;
        }
        Long monitorId = result.getMonitor() != null ? result.getMonitor().getId() : null;
        long timestamp = result.getCollectTime() != null
                ? result.getCollectTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                : System.currentTimeMillis();
        for (MetricPoint point : result.getPoints()) {
            writePoint(monitorId, point, timestamp);
        }
    }

    /**
     * 写入单个指标点
     *
     * @param monitorId 目标 ID
     * @param point     指标点
     * @param timestamp 时间戳（毫秒）
     */
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
            var builder = org.influxdb.dto.Point.measurement(measurement)
                    .time(timestamp, TimeUnit.MILLISECONDS)
                    .addField("value", point.getValue());
            tags.forEach(builder::tag);
            influxDbEngine.write(builder.build());
        } catch (Exception e) {
            // 时序写入失败不阻断 MySQL 主链路，仅告警日志
            log.warn("[monitor] InfluxDB 写入失败 metric={}: {}", point.getMetricName(), e.getMessage());
        }
    }
}
