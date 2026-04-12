package com.chua.starter.server.support.spi.metrics;

import com.chua.common.support.core.annotation.Spi;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.model.ServerMetricsDetail;
import com.chua.starter.server.support.model.ServerMetricsSnapshot;
import com.chua.starter.server.support.spi.ServerMetricsCollectorDelegate;
import com.chua.starter.server.support.spi.ServerMetricsCollectorSpi;
import com.chua.starter.server.support.spi.ServerMetricsSpiContext;

/**
 * 本机指标采集 SPI。
 */
@Spi({"local", "LOCAL"})
public class LocalServerMetricsCollectorSpi implements ServerMetricsCollectorSpi {

    @Override
    public ServerMetricsSnapshot collectSnapshot(ServerMetricsSpiContext context) throws Exception {
        ServerMetricsCollectorDelegate delegate = context.getDelegate();
        ServerHost host = context.getHost();
        return delegate.collectLocalSnapshot(host);
    }

    @Override
    public ServerMetricsDetail collectDetail(ServerMetricsSpiContext context) {
        ServerMetricsCollectorDelegate delegate = context.getDelegate();
        ServerHost host = context.getHost();
        return delegate.collectLocalDetail(host, context.getSnapshot());
    }
}
