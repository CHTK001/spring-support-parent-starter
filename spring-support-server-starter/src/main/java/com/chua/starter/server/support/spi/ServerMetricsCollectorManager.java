package com.chua.starter.server.support.spi;

import com.chua.common.support.core.utils.ServiceProvider;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.util.ServerHostMetadataSupport;
import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * 服务器指标采集 SPI 管理器。
 */
public class ServerMetricsCollectorManager {

    private final com.chua.common.support.core.spi.ServiceProvider<ServerMetricsCollectorSpi> provider =
            ServiceProvider.of(ServerMetricsCollectorSpi.class);

    /**
     * 根据协议分派采集快照。
     */
    public com.chua.starter.server.support.model.ServerMetricsSnapshot collectSnapshot(
            ServerMetricsSpiContext context
    ) throws Exception {
        ServerMetricsCollectorSpi collector = resolveCollector(context == null ? null : context.getHost());
        if (collector == null) {
            throw new IllegalStateException("未找到服务器指标采集 SPI: " + resolveType(context == null ? null : context.getHost()));
        }
        return collector.collectSnapshot(context);
    }

    /**
     * 根据协议分派采集详情。
     */
    public com.chua.starter.server.support.model.ServerMetricsDetail collectDetail(
            ServerMetricsSpiContext context
    ) throws Exception {
        ServerMetricsCollectorSpi collector = resolveCollector(context == null ? null : context.getHost());
        if (collector == null) {
            throw new IllegalStateException("未找到服务器指标采集 SPI: " + resolveType(context == null ? null : context.getHost()));
        }
        return collector.collectDetail(context);
    }

    /**
     * 获取协议对应的采集器，默认回落到 local。
     */
    private ServerMetricsCollectorSpi resolveCollector(ServerHost host) {
        ServerMetricsCollectorSpi collector = provider.getExtension(resolveType(host));
        return collector != null ? collector : provider.getExtension("local");
    }

    /**
     * 标准化协议名称，避免大小写与空值影响查找。
     */
    private String resolveType(ServerHost host) {
        String providerType = ServerHostMetadataSupport.resolveMetricsProvider(host);
        if (StringUtils.hasText(providerType)) {
            return providerType.trim().toLowerCase(Locale.ROOT);
        }
        if (host == null || !StringUtils.hasText(host.getServerType())) {
            return "local";
        }
        return host.getServerType().trim().toLowerCase(Locale.ROOT);
    }
}
