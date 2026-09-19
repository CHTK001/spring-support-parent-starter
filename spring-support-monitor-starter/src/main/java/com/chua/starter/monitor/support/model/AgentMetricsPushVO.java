package com.chua.starter.monitor.support.model;

import com.chua.starter.monitor.support.spi.MetricPoint;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Agent 指标推送负载
 * <p>
 * 请求体形如 {@code {"points":[{"metricName":"cpu_usage","value":35.2,"unit":"%",
 * "tags":{"model":"x86"},"targetTable":"ms_monitor_metric_cpu"}]}}；
 * {@code targetTable} 缺省时由服务端落通用表。
 * </p>
 *
 * @param points 指标点列表
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentMetricsPushVO(List<MetricPoint> points) {
}
