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
 * <p>示例：</p>
 * <pre>{@code
 * plugin:
 *   device:
 *     timeseries:
 *       engine: bean            # bean / influxdb / redis / none
 *       retention-days: 30
 *       url: http://172.16.0.40:8086   # influxdb / redis 使用
 *       database: device               # influxdb 库名；redis 逻辑库下标
 *       username: admin
 *       password: secret
 * }</pre>
 *
 * @since 4.0.0.55
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.device.timeseries")
public class DeviceTimeSeriesProperties {

    /**
     * 时序引擎：{@code bean}（默认，MySQL 明细表）/ {@code influxdb} / {@code redis} / {@code none}
     */
    private String engine = "bean";

    /**
     * 历史保留天数；{@code <=0} 表示不自动清理
     */
    private int retentionDays = 30;

    /**
     * bean 引擎的明细表名（只允许字母、数字、下划线）
     */
    private String table = "device_series_point";

    /**
     * InfluxDB 服务地址，例如 {@code http://127.0.0.1:8086}；redis 引擎忽略
     */
    private String url;

    /**
     * InfluxDB 库名；redis 引擎用作逻辑库下标（非数字则忽略）
     */
    private String database = "device";

    /**
     * InfluxDB 1.x 用户名（可空）
     */
    private String username;

    /**
     * InfluxDB 1.x 密码（可空）
     */
    private String password;

    /**
     * InfluxDB measurement 名
     */
    private String measurement = "device_series";

    /**
     * Redis 键前缀
     */
    private String keyPrefix = "device:series";

    /**
     * 单条序列在 Redis 中的最大点数（超出时按时间裁剪最旧的）
     */
    private int maxPointsPerSeries = 100000;

    /**
     * 批量写入分片大小
     */
    private int writeBatchSize = 200;

    /**
     * 时序 HTTP 请求超时（秒）
     */
    private int httpTimeoutSeconds = 10;
}
