package com.chua.starter.monitor.support.model;

/**
 * 指标小时聚合查询结果。
 */
public record MetricAggregate(
        String source,
        Long monitorId,
        String metricName,
        Double avgValue,
        Double minValue,
        Double maxValue,
        Long countValue) {
}
