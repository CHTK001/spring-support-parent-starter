package com.chua.starter.monitor.support.spi.impl;

import com.chua.starter.monitor.support.config.MonitorTimeseriesProperties;
import com.chua.starter.monitor.support.spi.CollectResult;
import com.chua.starter.monitor.support.spi.MetricPoint;
import com.chua.starter.monitor.support.spi.MetricTimeSeriesProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
* 时序存储统一门面
* <p>
* 对外就一个写入器：内部按 {@code monitor.timeseries.engine} 选择
* 可用 提供者（influxdb / Redis），均不可用时静默跳过。
* 采集主链路（指标服务impl）与查询控制层只依赖本类，
* 无需感知具体引擎，实现"后续方便切换"。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricTimeSeriesWriter {

    /** 已装配的时序 提供者（通常只有一个） */
    private final List<MetricTimeSeriesProvider> providers;

    /** 时序配置 */
    private final MonitorTimeseriesProperties properties;

    /**
    * 按配置选择可用 提供者
    *
    * @return 可用的实现或 空
    */
    private MetricTimeSeriesProvider resolve() {
        String engine = properties.getEngine() == null ? "" : properties.getEngine().trim().toLowerCase();
        if (engine.isEmpty() || "none".equals(engine)) {
            return null;
        }
        for (MetricTimeSeriesProvider provider : providers) {
            if (engine.equals(provider.engine()) && provider.isAvailable()) {
                return provider;
            }
        }
        log.debug("[monitor] 时序引擎 {} 无可用实现（时序双写跳过）", engine);
        return null;
    }

    /**
    * 是否可用
    *
    * @return true 表示当前配置引擎可用
    */
    public boolean isAvailable() {
        return resolve() != null;
    }

    /**
    * 写入整批采集结果
    *
    * @param result 采集结果
    */
    public void write(CollectResult result) {
        MetricTimeSeriesProvider provider = resolve();
        if (provider == null) {
            return;
        }
        try {
            provider.write(result);
        } catch (Exception e) {
            log.warn("[monitor] 时序写入失败 engine={}: {}", provider.engine(), e.getMessage());
        }
    }

    /**
    * 均值曲线查询
    *
    * @param target    目标表
    * @param monitorId 监控 标识
    * @param hours     回溯小时数
    * @param window    聚合窗口
    * @return [[ts,value],...] 升序
    */
    public List<List<Object>> series(String target, Long monitorId, int hours, String window) {
        MetricTimeSeriesProvider provider = resolve();
        if (provider == null) {
            return List.of();
        }
        return provider.series(target, monitorId, hours, window);
    }
}
