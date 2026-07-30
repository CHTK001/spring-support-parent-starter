package com.chua.starter.server.support.spi;

import com.chua.starter.server.support.model.ServerMetricsDetail;
import com.chua.starter.server.support.model.ServerMetricsSnapshot;

/**
 * 服务器指标采集 SPI。
 */
public interface ServerMetricsCollectorSpi {

    /**
     * 采集协议对应的最新指标快照。
     */
    ServerMetricsSnapshot collectSnapshot(ServerMetricsSpiContext context) throws Exception;

    /**
     * 采集协议对应的指标详情。
     */
    ServerMetricsDetail collectDetail(ServerMetricsSpiContext context) throws Exception;
}
