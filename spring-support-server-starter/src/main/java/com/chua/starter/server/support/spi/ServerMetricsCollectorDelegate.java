package com.chua.starter.server.support.spi;

import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.model.ServerMetricsDetail;
import com.chua.starter.server.support.model.ServerMetricsSnapshot;

/**
 * 服务器指标采集委派接口。
 *
 * <p>SPI 只负责协议分发，具体采集逻辑仍由服务层提供，
 * 这样可以在完成 SPI 化的同时复用现有稳定的采集实现。
 */
public interface ServerMetricsCollectorDelegate {

    /**
     * 采集本机指标快照。
     */
    ServerMetricsSnapshot collectLocalSnapshot(ServerHost host) throws Exception;

    /**
     * 采集 SSH 主机指标快照。
     */
    ServerMetricsSnapshot collectSshSnapshot(ServerHost host) throws Exception;

    /**
     * 采集 WinRM 主机指标快照。
     */
    ServerMetricsSnapshot collectWinRmSnapshot(ServerHost host) throws Exception;

    /**
     * 采集本机指标详情。
     */
    ServerMetricsDetail collectLocalDetail(ServerHost host, ServerMetricsSnapshot snapshot);

    /**
     * 采集 SSH 主机指标详情。
     */
    ServerMetricsDetail collectSshDetail(ServerHost host, ServerMetricsSnapshot snapshot);

    /**
     * 采集 WinRM 主机指标详情。
     */
    ServerMetricsDetail collectWinRmDetail(ServerHost host, ServerMetricsSnapshot snapshot);
}
