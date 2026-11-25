package com.chua.starter.datasource.properties;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;

import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * Hikari数据源配置类，继承自 Spring Boot {@link DataSourceProperties}，
 * 用于配置Hikari连接池的相关属性。
 * <p>除本类扩展的连接池参数外，条目还继承 {@code name}/{@code url}/{@code username}/
 * {@code password}/{@code driver-class-name} 等标准数据源属性。</p>
 * 配置示例:
 * <pre>
 * plugin:
 *   multi-datasource:
 *     hikari:
 *       data-source:
 *         - name: slave_1
 *           url: jdbc:mysql://localhost:3307/app
 *           username: root
 *           password: your-password      # 占位符
 *           max-pool-size: 100           # 默认 100
 *           min-idle: 10                 # 默认 10
 *           idle-timeout: 600000         # 默认 600000（10 分钟）
 *           max-lifetime: 1800000        # 默认 1800000（30 分钟）
 *           leak-detection-threshold: 0  # 默认 0（不检测泄漏）
 * </pre>
 *
 * @author CH
 * @since 4.0.0
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class HikariDataSourceProperties extends DataSourceProperties {

    /**
     * 连接在池中空闲的最长时间，超过此时间的连接将被关闭。
     * 单位为毫秒。
     * 默认值: 600000（10 分钟）
     * 示例: plugin.multi-datasource.hikari.data-source[0].idle-timeout=600000
     */
    // [P3C A20 豁免]
    private volatile long idleTimeout = MINUTES.toMillis(10);

    /**
     * 连接泄漏检测的阈值，超过此时间的连接将被视为泄漏。
     * 单位为毫秒；{@code 0} 表示关闭泄漏检测。
     * 默认值: 0
     * 示例: plugin.multi-datasource.hikari.data-source[0].leak-detection-threshold=60000
     */
    private volatile long leakDetectionThreshold;

    /**
     * 连接的最大生命周期，超过此时间的连接将被关闭。
     * 单位为毫秒。
     * 默认值: 1800000（30 分钟）
     * 示例: plugin.multi-datasource.hikari.data-source[0].max-lifetime=1800000
     */
    // [P3C A20 豁免]
    private volatile long maxLifetime = MINUTES.toMillis(30);

    /**
     * 连接池中允许的最大连接数。
     * 默认值: 100
     * 示例: plugin.multi-datasource.hikari.data-source[0].max-pool-size=100
     */
    // [P3C A20 豁免]
    private volatile int maxPoolSize = 100;

    /**
     * 连接池中保持的最小空闲连接数。
     * 默认值: 10
     * 示例: plugin.multi-datasource.hikari.data-source[0].min-idle=10
     */
    // [P3C A20 豁免]
    private volatile int minIdle = 10;

    // Getter 方法（Lombok 在 Java 25 下可能不工作，手动添加）
    /**
     * 获取最小值空闲
     *
     * @return 获取最小空闲的结果
     */
    public int getMinIdle() {
        return minIdle;
    }

    /**
     * 获取空闲超时
     *
     * @return 获取空闲超时的结果
     */
    public long getIdleTimeout() {
        return idleTimeout;
    }

    /**
     * 获取leakdetection阈值
     *
     * @return 获取leakdetection阈值的结果
     */
    public long getLeakDetectionThreshold() {
        return leakDetectionThreshold;
    }

    /**
     * 获取最大值Lifetime
     *
     * @return 获取最大lifetime的结果
     */
    public long getMaxLifetime() {
        return maxLifetime;
    }

    /**
     * 获取最大值游泳池获取大小
     *
     * @return 获取最大游泳池大小的结果
     */
    public int getMaxPoolSize() {
        return maxPoolSize;
    }
}

