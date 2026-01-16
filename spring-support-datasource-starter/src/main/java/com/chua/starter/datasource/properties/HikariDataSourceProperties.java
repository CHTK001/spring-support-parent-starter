package com.chua.starter.datasource.properties;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * Hikari数据源配置类，继承自DataSourceProperties，用于配置Hikari连接池的相关属性。
 * 该类使用了Lombok注解，自动生成equals、hashCode、getter和setter方法。
 *
 * @author CH
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class HikariDataSourceProperties extends DataSourceProperties {

    /**
     * 连接在池中空闲的最长时间，超过此时间的连接将被关闭。
     * 单位为毫秒。
     */
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
    private volatile long maxLifetime = MINUTES.toMillis(30);

    /**
     * 连接池中允许的最大连接数。
     */
    private volatile int maxPoolSize = 100;

    /**
     * 连接池中保持的最小空闲连接数。
     */
    private volatile int minIdle = 10;
}

