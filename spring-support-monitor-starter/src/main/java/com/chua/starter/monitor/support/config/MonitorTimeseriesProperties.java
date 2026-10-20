package com.chua.starter.monitor.support.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 监控模块时序存储配置属性
 * <p>
 * 通过 {@code plugin.monitor.timeseries.engine} 选择存储引擎：
 * <ul>
 *   <li>{@code influxdb}：写入 InfluxDB（默认/向下兼容，需同时开启 plugin.monitor.influx.enabled）</li>
 *   <li>{@code redis}：写入 Redis ZSET 简化时序（小规模场景）</li>
 *   <li>{@code datasource}：写入 MySQL/H2 明细表（由 SeriesEngineConfiguration 装配 JDBC 实现）</li>
 *   <li>{@code none}：仅 MySQL 明细表，关闭时序双写</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@ConfigurationProperties(prefix = "plugin.monitor.timeseries")
public class MonitorTimeseriesProperties {

    /**
     * 时序引擎（influxdb / redis / datasource / 无），默认 influxdb（向后兼容）
     */
    private String engine = "influxdb";

    /**
     * 时序存储地址：influxdb / questdb 用作服务地址（如 http://127.0.0.1:8086）；
     * redis 配置为 redis:// 地址（或 host:port）时覆盖 spring.data.redis.*，否则回退后者
     */
    private String url = "http://127.0.0.1:8086";

    /**
     * 数据库名
     */
    private String database = "monitor";
}
