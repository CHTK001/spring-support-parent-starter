package com.chua.starter.server.support.spi.metrics;

import com.chua.common.support.core.annotation.Spi;
import com.chua.starter.server.support.model.ServerMetricsDetail;
import com.chua.starter.server.support.model.ServerMetricsSnapshot;
import com.chua.starter.server.support.spi.ServerMetricsCollectorSpi;
import com.chua.starter.server.support.spi.ServerMetricsSpiContext;
import com.chua.starter.server.support.util.ServerPrometheusMetricsSupport;

/**
 * Prometheus 指标采集 SPI。
 */
@Spi({"prometheus", "PROMETHEUS"})
public class PrometheusServerMetricsCollectorSpi implements ServerMetricsCollectorSpi {

    /**
     * 通过 Prometheus 采集快照。
     */
    @Override
    public ServerMetricsSnapshot collectSnapshot(ServerMetricsSpiContext context) throws Exception {
        return ServerPrometheusMetricsSupport.collectSnapshot(context.getHost());
    }

    /**
     * 通过 Prometheus 采集详情。
     */
    @Override
    public ServerMetricsDetail collectDetail(ServerMetricsSpiContext context) throws Exception {
        return ServerPrometheusMetricsSupport.collectDetail(context.getHost(), context.getSnapshot());
    }
}
