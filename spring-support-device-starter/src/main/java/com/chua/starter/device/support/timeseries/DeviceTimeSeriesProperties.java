// [P3C A20 豁免] 配置属性默认值。
package com.chua.starter.device.support.timeseries;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 设备时序引擎配置（{@code plugin.device.timeseries.*}）。
 *
 * <p>默认 {@code engine=bean}，即直接落宿主数据源的 MySQL 明细表，不需要任何额外中间件；
 * 需要更高写入吞吐时把 {@code engine} 改成 {@code influxdb} 或 {@code redis} 即可，
 * 业务代码与页面不用动。</p>
 *
 * <p>完整 YAML 配置示例（前缀 {@code plugin.device.timeseries}，含关键项注释）：</p>
 * <pre>{@code
 * plugin:
 *   device:
 *     timeseries:
 *       engine: bean                       # 引擎：bean / influxdb / redis / none（off、disabled 等价 none）
 *       retention-days: 30                 # 历史保留天数；<=0 不自动清理
 *       table: device_series_point         # bean 引擎明细表名（仅字母 / 数字 / 下划线）
 *       url: http://172.16.0.40:8086       # influxdb 服务地址（influxdb 引擎必填）
 *       database: device                   # influxdb 库名（redis 逻辑库由 spring.data.redis.database 决定）
 *       username: your-username            # influxdb 1.x 用户名（可空）
 *       password: your-password            # influxdb 1.x 密码（可空）
 *       measurement: device_series         # influxdb measurement 名
 *       key-prefix: device:series          # redis 键前缀
 *       max-points-per-series: 100000      # redis 单序列最大点数（引擎侧下限 1000）
 *       write-batch-size: 200              # 批量写入分片大小（预留）
 *       http-timeout-seconds: 10           # influxdb HTTP 超时秒数（<=1 按 1 秒）
 * }</pre>
 *
 * @since 4.0.0.55
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.device.timeseries")
public class DeviceTimeSeriesProperties {

    /**
     * 时序引擎类型：{@code bean}（默认，落宿主数据源 MySQL 明细表）/ {@code influxdb} /
     * {@code redis} / {@code none}；{@code off}、{@code disabled} 等价 {@code none}，
     * 未知取值 WARN 后按 {@code bean} 处理。
     *
     * <p>默认值：{@code bean}。</p>
     *
     * <pre>{@code
     * plugin:
     *   device:
     *     timeseries:
     *       engine: bean
     * }</pre>
     */
    private String engine = "bean";

    /**
     * 历史保留天数；{@code <=0} 表示不自动清理。
     *
     * <p>默认值：{@code 30}；每天 03:30 由 {@code DeviceSeriesRecorder#purgeExpired()} 清理过期点。</p>
     *
     * <pre>{@code
     * plugin:
     *   device:
     *     timeseries:
     *       retention-days: 30
     * }</pre>
     */
    private int retentionDays = 30;

    /**
     * bean 引擎的明细表名（只允许字母、数字、下划线，防配置注入）。
     *
     * <p>默认值：{@code device_series_point}。</p>
     *
     * <pre>{@code
     * plugin:
     *   device:
     *     timeseries:
     *       table: device_series_point
     * }</pre>
     */
    private String table = "device_series_point";

    /**
     * InfluxDB 服务地址，例如 {@code http://127.0.0.1:8086}；bean / redis 引擎忽略。
     *
     * <p>默认值：无（{@code null}）；engine=influxdb 时必填，未配置则引擎标记为「不可用」并说明原因。</p>
     *
     * <pre>{@code
     * plugin:
     *   device:
     *     timeseries:
     *       url: http://127.0.0.1:8086
     * }</pre>
     */
    private String url;

    /**
     * InfluxDB 库名。
     *
     * <p>默认值：{@code device}；influxdb 引擎侧留空时同样回落 {@code device}。
     * redis 引擎当前不使用该值——redis 逻辑库由宿主 {@code spring.data.redis.database} 决定，
     * 配置纯数字时仅打印提示日志。</p>
     *
     * <pre>{@code
     * plugin:
     *   device:
     *     timeseries:
     *       database: device
     * }</pre>
     */
    private String database = "device";

    /**
     * InfluxDB 1.x 用户名（可空）；留空则 influxdb 引擎不带 Basic 认证。
     *
     * <p>默认值：无（{@code null}）。</p>
     *
     * <pre>{@code
     * plugin:
     *   device:
     *     timeseries:
     *       username: your-username
     * }</pre>
     */
    private String username;

    /**
     * InfluxDB 1.x 密码（可空）；仅在配置了用户名时参与 Basic 认证。
     *
     * <p>默认值：无（{@code null}）。</p>
     *
     * <pre>{@code
     * plugin:
     *   device:
     *     timeseries:
     *       password: your-password
     * }</pre>
     */
    private String password;

    /**
     * InfluxDB measurement 名。
     *
     * <p>默认值：{@code device_series}；influxdb 引擎侧留空时同样回落该默认值。</p>
     *
     * <pre>{@code
     * plugin:
     *   device:
     *     timeseries:
     *       measurement: device_series
     * }</pre>
     */
    private String measurement = "device_series";

    /**
     * Redis 键前缀。
     *
     * <p>默认值：{@code device:series}；redis 引擎侧留空时同样回落该默认值。</p>
     *
     * <pre>{@code
     * plugin:
     *   device:
     *     timeseries:
     *       key-prefix: device:series
     * }</pre>
     */
    private String keyPrefix = "device:series";

    /**
     * 单条序列在 Redis 中的最大点数（超出时按时间裁剪最旧的）。
     *
     * <p>默认值：{@code 100000}；redis 引擎侧有下限保护，配置小于 {@code 1000} 时按 {@code 1000} 生效。</p>
     *
     * <pre>{@code
     * plugin:
     *   device:
     *     timeseries:
     *       max-points-per-series: 100000
     * }</pre>
     */
    private int maxPointsPerSeries = 100000;

    /**
     * 批量写入分片大小。
     *
     * <p>默认值：{@code 200}；当前各引擎实现按整批写入，该值为预留参数。</p>
     *
     * <pre>{@code
     * plugin:
     *   device:
     *     timeseries:
     *       write-batch-size: 200
     * }</pre>
     */
    private int writeBatchSize = 200;

    /**
     * 时序 HTTP 请求超时（秒）。
     *
     * <p>默认值：{@code 10}；仅 influxdb 引擎使用，配置 {@code <=1} 时按 {@code 1} 秒生效。</p>
     *
     * <pre>{@code
     * plugin:
     *   device:
     *     timeseries:
     *       http-timeout-seconds: 10
     * }</pre>
     */
    private int httpTimeoutSeconds = 10;
}
