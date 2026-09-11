package com.chua.starter.monitor.support.spi;

import java.util.List;

/**
 * 指标时序存储 Provider SPI
 * <p>
 * 解耦采集写入链路与具体时序引擎：采集方只面向本接口写入，
 * 由实现按配置 {@code monitor.timeseries.engine} 选择存储后端
 * （如 {@code influxdb} / {@code redis}），未配置或实现缺失
 * 时装配空实现，保证主链路（MySQL 明细表 + 告警）不受影响。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface MetricTimeSeriesProvider {

    /**
     * 引擎标识（与配置 monitor.timeseries.engine 对应）
     *
     * @return 引擎名，如 influxdb / redis
     */
    String engine();

    /**
     * 是否可用（依赖装配成功且服务可达）
     *
     * @return true 表示可写入/可查询
     */
    boolean isAvailable();

    /**
     * 写入整批采集结果
     *
     * @param result 采集结果
     */
    void write(CollectResult result);

    /**
     * 写入单个指标点
     *
     * @param monitorId 目标 ID
     * @param point     指标点
     * @param timestamp 时间戳（毫秒）
     */
    void writePoint(Long monitorId, MetricPoint point, long timestamp);

    /**
     * 均值曲线查询（前端折线图）
     *
     * @param target    目标表(measurement)，如 ms_monitor_metric_cpu
     * @param monitorId 监控目标 ID
     * @param hours     回溯小时数
     * @param window    聚合窗口，如 1m/5m/1h
     * @return [[ts,value],...] 升序
     */
    List<List<Object>> series(String target, Long monitorId, int hours, String window);
}
