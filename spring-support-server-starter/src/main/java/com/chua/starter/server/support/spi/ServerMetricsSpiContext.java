package com.chua.starter.server.support.spi;

import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.model.ServerMetricsSnapshot;

/**
 * 服务器指标 SPI 上下文。
 */
public class ServerMetricsSpiContext {

    private final ServerHost host;
    private final ServerMetricsSnapshot snapshot;
    private final ServerMetricsCollectorDelegate delegate;

    public ServerMetricsSpiContext(
            ServerHost host,
            ServerMetricsSnapshot snapshot,
            ServerMetricsCollectorDelegate delegate
    ) {
        this.host = host;
        this.snapshot = snapshot;
        this.delegate = delegate;
    }

    public ServerHost getHost() {
        return host;
    }

    public ServerMetricsSnapshot getSnapshot() {
        return snapshot;
    }

    public ServerMetricsCollectorDelegate getDelegate() {
        return delegate;
    }
}
