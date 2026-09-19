package com.chua.starter.monitor.support.config;

import com.chua.common.support.lang.datasource.engine.Engine;
import com.chua.common.support.lang.datasource.series.SeriesEngine;
import com.chua.common.support.lang.datasource.series.impl.DataSourceSeriesEngine;
import com.chua.common.support.spi.ServiceProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Properties;

/**
 * 时序引擎装配。
 *
 * <p>全系统只有一个时序契约 {@link SeriesEngine}，按 {@code monitor.timeseries.engine} 装配唯一实现：</p>
 * <ul>
 *   <li>{@code datasource}：JDBC 明细表，需要容器里的 {@link Engine} 数据源引擎，故由 Spring 构造</li>
 *   <li>{@code influxdb} / {@code redis}：走自研 SPI 发现（各 starter 的
 *       {@code META-INF/extensions/…SeriesEngine}），以 {@link Properties} 传连接参数</li>
 *   <li>{@code none} / 空 / 扩展缺失：空实现，保证消费方注入不为 null</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Configuration
public class SeriesEngineConfiguration {

    /**
     * JDBC 明细表引擎名
     */
    private static final String ENGINE_DATASOURCE = "datasource";

    /**
     * Redis 引擎名
     */
    private static final String ENGINE_REDIS = "redis";

    /**
     * 装配唯一时序引擎。
     *
     * @param properties  时序配置
     * @param engine      数据源引擎（可选，仅 datasource 使用）
     * @param environment 容器环境（redis 复用 spring.data.redis 连接参数）
     * @return SeriesEngine Bean，永不为 null
     */
    @Bean
    public SeriesEngine seriesEngine(MonitorTimeseriesProperties properties,
                                     @Autowired(required = false) Engine engine,
                                     Environment environment) {
        String selected = normalize(properties.getEngine());
        if (selected.isEmpty() || "none".equals(selected)) {
            log.info("[SeriesEngine] 未启用时序双写（engine={}）", properties.getEngine());
            return new NoopSeriesEngine("none");
        }
        if (ENGINE_DATASOURCE.equals(selected)) {
            if (engine == null) {
                log.warn("[SeriesEngine] engine=datasource 但未找到 Engine Bean，时序存储不可用");
                return new NoopSeriesEngine(selected);
            }
            log.info("[SeriesEngine] 装配 DataSourceSeriesEngine（JDBC 明细表）");
            return new DataSourceSeriesEngine(engine);
        }

        SeriesEngine seriesEngine = ServiceProvider.of(SeriesEngine.class)
                .getNewExtension(selected, spiProperties(selected, properties, environment));
        if (seriesEngine == null) {
            log.warn("[SeriesEngine] 扩展点无 engine={} 的实现（检查对应 starter 与注册文件），装配空实现", selected);
            return new NoopSeriesEngine(selected);
        }
        log.info("[SeriesEngine] 装配 {}（engine={}，可用={})",
                seriesEngine.getClass().getSimpleName(), seriesEngine.engine(), seriesEngine.isAvailable());
        return seriesEngine;
    }

    /**
     * 组装 SPI 构造参数：各引擎的 url 语义不同，redis 取容器已配的 Redis 地址。
     *
     * @param selected    配置的引擎名
     * @param properties  时序配置
     * @param environment 容器环境
     * @return 构造参数
     */
    private Properties spiProperties(String selected, MonitorTimeseriesProperties properties, Environment environment) {
        Properties props = new Properties();
        if (ENGINE_REDIS.equals(selected)) {
            props.setProperty("url", redisUrl(environment));
            return props;
        }
        props.setProperty("url", properties.getUrl());
        props.setProperty("database", properties.getDatabase());
        return props;
    }

    /**
     * 从 {@code spring.data.redis.*} 拼出 Lettuce 连接串，与告警静默期共用同一 Redis。
     *
     * @param environment 容器环境
     * @return redis:// 或 rediss:// 连接串
     */
    private String redisUrl(Environment environment) {
        String host = environment.getProperty("spring.data.redis.host", "127.0.0.1");
        String port = environment.getProperty("spring.data.redis.port", "6379");
        String password = environment.getProperty("spring.data.redis.password", "");
        boolean ssl = Boolean.parseBoolean(environment.getProperty("spring.data.redis.ssl.enabled", "false"));
        String authority = password.isEmpty() ? "" : ":" + password + "@";
        return (ssl ? "rediss://" : "redis://") + authority + host + ":" + port;
    }

    /**
     * 归一化引擎名。
     *
     * @param engine 配置值
     * @return 去空白转小写后的引擎名
     */
    private static String normalize(String engine) {
        return engine == null ? "" : engine.trim().toLowerCase();
    }

    /**
     * 时序存储空实现：配置为 none 或实现缺失时装配，保证消费方注入不为 null。
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
        public java.util.List<java.util.List<Object>> series(String target, Long monitorId, int hours, String window) {
            return java.util.List.of();
        }
    }
}
