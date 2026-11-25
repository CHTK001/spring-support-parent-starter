package com.chua.starter.monitor.support.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 监控模块 InfluxDB 时序存储配置属性。
 *
 * <p>经 {@code MonitorAutoConfiguration} 的 {@code @EnableConfigurationProperties} 注册；
 * InfluxDB 写入器按 {@code plugin.monitor.influx.enabled} 条件装配。
 * 时序引擎的引擎选择见 {@link MonitorTimeseriesProperties}。</p>
 *
 * <p>完整配置示例（前缀 {@code plugin.monitor.influx}，值均为默认值）：</p>
 * <pre>
 * plugin:
 *   monitor:
 *     influx:
 *       enabled: false                 # 是否启用 influxdb 时序写入与查询
 *       url: http://127.0.0.1:8086     # influxdb v1 兼容端点
 *       database: monitor              # 数据库名
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@ConfigurationProperties(prefix = "plugin.monitor.influx")
public class MonitorInfluxProperties {

    /**
     * 是否启用 influxdb 时序写入与查询。
     *
     * <p>默认值：{@code false}（关闭；仅 MySQL 明细表，不写 InfluxDB）。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     influx:
     *       enabled: false
     * </pre>
     */
    private boolean enabled = false;

    /**
     * influxdb 地址（v1 兼容端点，如 http://127.0.0.1:8086）。
     *
     * <p>默认值：{@code http://127.0.0.1:8086}；为空时同样按该默认端点处理。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     influx:
     *       url: http://127.0.0.1:8086
     * </pre>
     */
    private String url = "http://127.0.0.1:8086";

    /**
     * 数据库名。
     *
     * <p>默认值：{@code monitor}；为空时同样按 {@code monitor} 处理。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     influx:
     *       database: monitor
     * </pre>
     */
    private String database = "monitor";
}
