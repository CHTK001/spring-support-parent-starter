package com.chua.starter.device.support.configuration;

import com.chua.starter.device.support.timeseries.DeviceSeriesRecorder;
import com.chua.starter.device.support.timeseries.DeviceTimeSeriesEngine;
import com.chua.starter.device.support.timeseries.DeviceTimeSeriesProperties;
import com.chua.starter.device.support.timeseries.impl.BeanTimeSeriesEngine;
import com.chua.starter.device.support.timeseries.impl.DisabledTimeSeriesEngine;
import com.chua.starter.device.support.timeseries.impl.InfluxdbTimeSeriesEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Locale;

/**
 * 设备时序引擎装配。
 *
 * <p>按 {@code plugin.device.timeseries.engine} 选一个实现，默认 {@code bean}：</p>
 * <table border="1">
 *   <caption>引擎取值</caption>
 *   <tr><th>取值</th><th>存储</th><th>额外依赖</th></tr>
 *   <tr><td>{@code bean}（默认）</td><td>MySQL 明细表 {@code device_series_point}</td><td>无</td></tr>
 *   <tr><td>{@code influxdb}</td><td>InfluxDB 1.x（HTTP /write /query）</td><td>InfluxDB 服务</td></tr>
 *   <tr><td>{@code redis}</td><td>Redis ZSET</td><td>宿主引入 spring-data-redis</td></tr>
 *   <tr><td>{@code none}</td><td>不存储（读写走空语义）</td><td>无</td></tr>
 * </table>
 *
 * <p>宿主自定义了 {@link DeviceTimeSeriesEngine} Bean 时本装配整体退避。</p>
 *
 * @since 4.0.0.55
 * @author CH
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(DeviceTimeSeriesProperties.class)
public class DeviceTimeSeriesConfiguration {

    /**
     * 装配唯一时序引擎。
     *
     * @param properties   时序配置
     * @param jdbcProvider 宿主 JDBC 模板（可能不存在）
     * @param dataSource   宿主数据源（可能不存在）
     * @param context      Spring 上下文（redis 引擎取宿主模板）
     * @return 时序引擎
     */
    @Bean
    @ConditionalOnMissingBean(DeviceTimeSeriesEngine.class)
    public DeviceTimeSeriesEngine deviceTimeSeriesEngine(DeviceTimeSeriesProperties properties,
                                                         ObjectProvider<JdbcTemplate> jdbcProvider,
                                                         ObjectProvider<DataSource> dataSource,
                                                         ApplicationContext context) {
        String engine = properties.getEngine() == null
                ? "bean" : properties.getEngine().trim().toLowerCase(Locale.ROOT);
        DeviceTimeSeriesEngine result = switch (engine) {
            case "influxdb" -> new InfluxdbTimeSeriesEngine(properties);
            case "redis" -> redisEngine(properties, context);
            case "none", "off", "disabled" -> new DisabledTimeSeriesEngine("none",
                    "plugin.device.timeseries.engine=none，设备时序记录已关闭");
            case "bean" -> beanEngine(properties, jdbcProvider, dataSource);
            default -> {
                log.warn("[Device][Series] 未知的时序引擎配置 engine={}，按 bean 处理", engine);
                yield beanEngine(properties, jdbcProvider, dataSource);
            }
        };
        log.info("[Device][Series] 时序引擎已装配: engine={}, available={}{}",
                result.engine(), result.isAvailable(),
                result.isAvailable() ? "" : "，原因: " + result.unavailableReason());
        return result;
    }

    /**
     * 装配设备时序写入器。
     *
     * @param engine     时序引擎
     * @param properties 时序配置
     * @return 写入器
     */
    @Bean
    @ConditionalOnMissingBean
    public DeviceSeriesRecorder deviceSeriesRecorder(DeviceTimeSeriesEngine engine,
                                                     DeviceTimeSeriesProperties properties) {
        return new DeviceSeriesRecorder(engine, properties);
    }

    /**
     * 构造 Redis 引擎。
     *
     * <p>用反射加载工厂：`spring-boot-starter-data-redis` 在本模块是 {@code optional}，
     * 宿主没引入时 JVM 不应该因为「类里引用了 StringRedisTemplate」而在校验阶段就抛
     * {@code NoClassDefFoundError}。反射把「缺类」变成可捕获的 {@link Throwable}，
     * 返回一个说明原因的不可用引擎。</p>
     *
     * @param properties 时序配置
     * @param context    Spring 上下文
     * @return Redis 引擎；缺依赖时返回不可用引擎
     */
    private static DeviceTimeSeriesEngine redisEngine(DeviceTimeSeriesProperties properties,
                                                      ApplicationContext context) {
        try {
            Class<?> factory = Class.forName(
                    "com.chua.starter.device.support.timeseries.impl.RedisTimeSeriesEngineFactory");
            Object engine = factory.getMethod("create", DeviceTimeSeriesProperties.class, ApplicationContext.class)
                    .invoke(null, properties, context);
            return (DeviceTimeSeriesEngine) engine;
        } catch (Throwable ex) {
            log.info("[Device][Series] Redis 时序引擎装配失败，按不可用处理: {}", ex.toString());
            return new DisabledTimeSeriesEngine("redis",
                    "宿主未引入 spring-data-redis，无法使用 Redis 时序引擎");
        }
    }

    /**
     * 构造 bean 引擎：优先复用宿主的 {@link JdbcTemplate}，没有就按数据源自己建一个。
     *
     * @param properties   时序配置
     * @param jdbcProvider 宿主 JDBC 模板
     * @param dataSource   宿主数据源
     * @return bean 引擎
     */
    private static DeviceTimeSeriesEngine beanEngine(DeviceTimeSeriesProperties properties,
                                                     ObjectProvider<JdbcTemplate> jdbcProvider,
                                                     ObjectProvider<DataSource> dataSource) {
        JdbcTemplate jdbcTemplate = jdbcProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            DataSource source = dataSource.getIfAvailable();
            jdbcTemplate = source == null ? null : new JdbcTemplate(source);
        }
        return new BeanTimeSeriesEngine(jdbcTemplate, properties.getTable());
    }
}
