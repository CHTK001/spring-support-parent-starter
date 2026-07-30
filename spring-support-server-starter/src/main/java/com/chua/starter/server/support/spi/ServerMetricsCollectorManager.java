package com.chua.starter.server.support.spi;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.util.ServerHostMetadataSupport;
import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * 服务器指标采集 SPI 管理器。
 *
 * @author CH
 */
public class ServerMetricsCollectorManager {

    /**
     * 指标采集 SPI 提供器，按扩展名加载不同协议实现。
     */
    private final com.chua.common.support.spi.ServiceProvider<ServerMetricsCollectorSpi> provider =
            ServiceProvider.of(ServerMetricsCollectorSpi.class);

    /**
     * 根据协议分派采集快照。
     *
     * @param context 指标采集上下文
     * @return 采集到的指标快照
     * @throws Exception SPI 调用异常
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
     *
     * @param context 指标采集上下文
     * @return 采集到的指标详情
     * @throws Exception SPI 调用异常
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
     *
     * @param host 服务器主机
     * @return 采集 SPI 实现
     */
    private ServerMetricsCollectorSpi resolveCollector(ServerHost host) {
        ServerMetricsCollectorSpi collector = provider.getExtension(resolveType(host));
        return collector != null ? collector : provider.getExtension("local");
    }

    /**
     * 标准化协议名称，避免大小写与空值影响查找。
     *
     * @param host 服务器主机
     * @return 规范化后的协议类型
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
