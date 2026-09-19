package com.chua.starter.monitor.support.config;

import com.chua.common.support.lang.datasource.engine.Engine;
import com.chua.common.support.lang.datasource.series.SeriesEngine;
import com.chua.common.support.lang.datasource.series.impl.DataSourceSeriesEngine;
import com.chua.starter.monitor.support.spi.MetricPoint;
import com.chua.starter.monitor.support.spi.MetricTimeSeriesProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;

/**
 * 时序引擎配置。
 *
 * <p>按 {@code monitor.timeseries.engine} 配置初始化对应的 {@link SeriesEngine} Spring Bean：</p>
 * <ul>
 *   <li>{@code datasource}：JDBC DataSource 存储（用 Engine + SqlExecutor 直写明细表）</li>
 *   <li>{@code influxdb} / {@code redis}：桥接到已装配的 {@link MetricTimeSeriesProvider}</li>
 *   <li>{@code none} 或依赖缺失：空实现（不影响主链路）</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Configuration
public class SeriesEngineConfiguration {

    /**
     * 初始化 SeriesEngine：datasource 走 JDBC 明细表，其余配置桥接到时序提供者。
     *
     * @param properties 时序配置
     * @param engine     数据源引擎（可选，仅 datasource 使用）
     * @param providers  已装配的时序提供者（可为空）
     * @return SeriesEngine Bean（永不为 null）
     */
    @Bean
    public SeriesEngine seriesEngine(MonitorTimeseriesProperties properties,
                                     @Autowired(required = false) Engine engine,
                                     @Autowired(required = false) List<MetricTimeSeriesProvider> providers) {
        String selected = properties.getEngine() == null ? "" : properties.getEngine().trim().toLowerCase();
        if ("datasource".equals(selected)) {
            if (engine == null) {
                log.warn("[SeriesEngine] engine=datasource 但未找到 Engine Bean，时序存储不可用");
                return new NoopSeriesEngine(selected);
            }
            log.info("[SeriesEngine] 初始化 DataSourceSeriesEngine（JDBC 时序存储）");
            return new DataSourceSeriesEngine(engine);
        }
        MetricTimeSeriesProvider provider = resolveProvider(selected, providers);
        if (provider == null) {
            log.info("[SeriesEngine] 时序引擎 {} 无可用实现，装配空实现", selected);
            return new NoopSeriesEngine(selected);
        }
        log.info("[SeriesEngine] 桥接 SeriesEngine 到提供者 {}", provider.engine());
        return new ProviderBackedSeriesEngine(provider);
    }

    /**
     * 按配置选择引擎标识匹配且可用的提供者（可用性每次调用时动态复核）。
     *
     * @param selected  配置的引擎名
     * @param providers 已装配的提供者列表
     * @return 匹配的提供者，无则 空
     */
    private MetricTimeSeriesProvider resolveProvider(String selected, List<MetricTimeSeriesProvider> providers) {
        if (selected.isEmpty() || "none".equals(selected) || providers == null) {
            return null;
        }
        for (MetricTimeSeriesProvider provider : providers) {
            if (selected.equals(provider.engine())) {
                return provider;
            }
        }
        return null;
    }

    /**
     * 将 {@link MetricTimeSeriesProvider} 适配为 {@link SeriesEngine} 契约。
     */
    private static final class ProviderBackedSeriesEngine implements SeriesEngine {

        /**
         * 被适配的提供者
        */
        private final MetricTimeSeriesProvider provider;

        /**
         * 构造桥接引擎。
         *
         * @param provider 提供者
         */
        private ProviderBackedSeriesEngine(MetricTimeSeriesProvider provider) {
            this.provider = provider;
        }

        @Override
        public String engine() {
            return provider.engine();
        }

        @Override
        public boolean isAvailable() {
            return provider.isAvailable();
        }

        @Override
        public void writePoint(String target, Long monitorId, String metricName, double value, long timestamp) {
            if (target == null || monitorId == null || metricName == null) {
                return;
            }
            MetricPoint point = MetricPoint.builder()
                    .metricName(metricName)
                    .value(value)
                    .targetTable(target)
                    .tags(Collections.emptyMap())
                    .build();
            provider.writePoint(monitorId, point, timestamp);
        }

        @Override
        public List<List<Object>> series(String target, Long monitorId, int hours, String window) {
            return provider.series(target, monitorId, hours, window);
        }
    }

    /**
     * 时序存储空实现：配置为 none 或依赖缺失时装配，保证消费方注入不为 null。
     */
    private static final class NoopSeriesEngine implements SeriesEngine {

        /**
         * 配置的引擎名
        */
        private final String engineName;

        /**
         * 构造空实现。
         *
         * @param engineName 配置的引擎名
         */
        private NoopSeriesEngine(String engineName) {
            this.engineName = engineName;
        }

        @Override
        public String engine() {
            return engineName.isEmpty() ? "none" : engineName;
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public void writePoint(String target, Long monitorId, String metricName, double value, long timestamp) {
            // 空实现
        }

        @Override
        public List<List<Object>> series(String target, Long monitorId, int hours, String window) {
            return List.of();
        }
    }
}
