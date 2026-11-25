package com.chua.starter.monitor.support.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 监控模块时序存储配置属性。
 *
 * <p>经 {@code MonitorAutoConfiguration} 的 {@code @EnableConfigurationProperties} 注册。
 * 通过 {@code plugin.monitor.timeseries.engine} 选择存储引擎：
 * <ul>
 *   <li>{@code influxdb}：写入 InfluxDB（默认/向下兼容，需同时开启 plugin.monitor.influx.enabled）</li>
 *   <li>{@code redis}：写入 Redis ZSET 简化时序（小规模场景）</li>
 *   <li>{@code datasource}：写入 MySQL/H2 明细表（由 SeriesEngineConfiguration 装配 JDBC 实现）</li>
 *   <li>{@code none}：仅 MySQL 明细表，关闭时序双写</li>
 * </ul>
 * 配置为 {@code none} 或引擎扩展缺失时回落 {@code NoopSeriesEngine}，不会启动失败。</p>
 *
 * <p>完整配置示例（前缀 {@code plugin.monitor.timeseries}，值均为默认值）：</p>
 * <pre>
 * plugin:
 *   monitor:
 *     timeseries:
 *       engine: influxdb               # influxdb / redis / datasource / none
 *       url: http://127.0.0.1:8086     # redis 引擎时可写 redis://host:port（或 host:port）
 *       database: monitor              # 数据库名（redis 引擎下为逻辑库下标）
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@ConfigurationProperties(prefix = "plugin.monitor.timeseries")
public class MonitorTimeseriesProperties {

    /**
     * 时序引擎（influxdb / redis / datasource / 无），默认 influxdb（向后兼容）。
     *
     * <p>默认值：{@code influxdb}；配置为 {@code none} 或引擎扩展缺失时，
     * {@code SeriesEngineConfiguration#seriesEngine} 装配 {@code NoopSeriesEngine} 兜底。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     timeseries:
     *       engine: influxdb
     * </pre>
     */
    private String engine = "influxdb";

    /**
     * 时序存储地址：influxdb / questdb 用作服务地址（如 http://127.0.0.1:8086）；
     * redis 配置为 redis:// 地址（或 host:port）时覆盖 spring.data.redis.*，否则回退后者。
     *
     * <p>默认值：{@code http://127.0.0.1:8086}；为空时按各引擎的默认连接处理
     * （redis 引擎回退 {@code spring.data.redis.*}）。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     timeseries:
     *       url: http://127.0.0.1:8086
     * </pre>
     */
    private String url = "http://127.0.0.1:8086";

    /**
     * 数据库名。
     *
     * <p>默认值：{@code monitor}；redis 引擎下为逻辑库下标，为空时按 {@code monitor} 处理。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     timeseries:
     *       database: monitor
     * </pre>
     */
    private String database = "monitor";
}
