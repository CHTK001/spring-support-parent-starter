package com.chua.starter.monitor.support.config;

import com.chua.common.support.lang.datasource.engine.Engine;
import com.chua.common.support.lang.datasource.series.SeriesEngine;
import com.chua.common.support.lang.datasource.series.impl.DataSourceSeriesEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 时序引擎配置。
 *
 * <p>按 {@code monitor.timeseries.engine} 配置初始化对应的 {@link SeriesEngine} Spring Bean：</p>
 * <ul>
 *   <li>{@code datasource}：JDBC DataSource 存储（默认，用 Engine + SqlExecutor）</li>
 *   <li>{@code redis}：Redis ZSET 存储</li>
 *   <li>{@code influxdb}：InfluxDB 存储</li>
 *   <li>{@code none}：跳过时序存储</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Configuration
public class SeriesEngineConfiguration {

    /**
    * 初始化 DataSourceSeriesEngine（JDBC 时序存储）。
    * 如果没有 Engine Bean，则创建空实现（不影响主链路）。
    *
    * @param engine 数据源引擎（可选，没有则为空实现）
    * @return SeriesEngine Bean
    */
    @Bean
    @ConditionalOnProperty(prefix = "monitor.timeseries", name = "engine", havingValue = "datasource")
    public SeriesEngine dataSourceSeriesEngine(@Autowired(required = false) Engine engine) {
        if (engine == null) {
            log.warn("[SeriesEngine] 未找到 Engine Bean，创建空实现（时序存储不可用）");
            return new SeriesEngine() {
                @Override
                public String engine() {
                    return "none";
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
            };
        }
        log.info("[SeriesEngine] 初始化 DataSourceSeriesEngine（JDBC 时序存储）");
        return new DataSourceSeriesEngine(engine);
    }
}
