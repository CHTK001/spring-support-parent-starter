package com.chua.starter.datasource.properties;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;

import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * Hikari数据源配置类，继承自数据源属性，用于配置Hikari连接池的相关属性。
 * 该类使用了Lombok注解，自动生成equals、哈希编码、getter和setter方法。
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
     */
    // [P3C A20 豁免]
    private volatile long idleTimeout = MINUTES.toMillis(10);

    /**
     * 连接泄漏检测的阈值，超过此时间的连接将被视为泄漏。
     * 单位为毫秒。
     */
    private volatile long leakDetectionThreshold;

    /**
     * 连接的最大生命周期，超过此时间的连接将被关闭。
     * 单位为毫秒。
     */
    // [P3C A20 豁免]
    private volatile long maxLifetime = MINUTES.toMillis(30);

    /**
     * 连接池中允许的最大连接数。
     */
    // [P3C A20 豁免]
    private volatile int maxPoolSize = 100;

    /**
     * 连接池中保持的最小空闲连接数。
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

