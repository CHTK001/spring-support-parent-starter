package com.chua.starter.monitor.support.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redis 时序引擎连接串解析。
 *
 * <p>钉住两条容易踩的边界：</p>
 * <ul>
 *   <li>{@code monitor.timeseries.url} 默认是 InfluxDB 的 {@code http://...}，
 *       不能被当作 Redis 地址塞给 Lettuce，否则连接必失败；</li>
 *   <li>显式配置的 {@code redis://} / {@code rediss://} / {@code host:port} 必须生效，
 *       从而让 redis 也支持 {@code monitor.timeseries.url}。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@DisplayName("Redis 时序引擎连接串解析")
class SeriesEngineConfigurationRedisUrlTest {

    @Test
    @DisplayName("显式 redis:// 覆盖 spring.data.redis.*")
    void explicitRedisUrlWins() {
        MonitorTimeseriesProperties properties = new MonitorTimeseriesProperties();
        properties.setUrl("redis://10.0.0.9:6380");

        StandardEnvironment environment = environment(Map.of(
                "spring.data.redis.host", "127.0.0.1",
                "spring.data.redis.port", "6379"));

        assertEquals("redis://10.0.0.9:6380", SeriesEngineConfiguration.redisUrl(properties, environment));
    }

    @Test
    @DisplayName("无协议的 host:port 也可作为 Redis 地址")
    void bareHostPortWins() {
        MonitorTimeseriesProperties properties = new MonitorTimeseriesProperties();
        properties.setUrl("10.0.0.9:6380");

        StandardEnvironment environment = environment(Map.of());

        assertEquals("10.0.0.9:6380", SeriesEngineConfiguration.redisUrl(properties, environment));
    }

    @Test
    @DisplayName("InfluxDB 默认 http 地址回退到 spring.data.redis.*")
    void httpUrlFallsBackToSpringRedis() {
        MonitorTimeseriesProperties properties = new MonitorTimeseriesProperties();
        properties.setUrl("http://127.0.0.1:8086");

        StandardEnvironment environment = environment(Map.of(
                "spring.data.redis.host", "10.0.0.1",
                "spring.data.redis.port", "6380",
                "spring.data.redis.password", "secret"));

        assertEquals("redis://:secret@10.0.0.1:6380", SeriesEngineConfiguration.redisUrl(properties, environment));
    }

    @Test
    @DisplayName("spring.data.redis.ssl.enabled=true 生成 rediss://")
    void sslProducesRedissScheme() {
        MonitorTimeseriesProperties properties = new MonitorTimeseriesProperties();
        properties.setUrl("");

        StandardEnvironment environment = environment(Map.of(
                "spring.data.redis.host", "10.0.0.1",
                "spring.data.redis.port", "6380",
                "spring.data.redis.ssl.enabled", "true"));

        assertEquals("rediss://10.0.0.1:6380", SeriesEngineConfiguration.redisUrl(properties, environment));
    }

    @Test
    @DisplayName("redis 的 url 与 database 透传给 SPI 引擎")
    void redisPropertiesPropagated() {
        MonitorTimeseriesProperties properties = new MonitorTimeseriesProperties();
        properties.setEngine("redis");
        properties.setUrl("redis://10.0.0.9:6380");
        properties.setDatabase("3");

        SeriesEngineConfiguration configuration = new SeriesEngineConfiguration();
        Properties spi = configuration.spiProperties("redis", properties, environment(Map.of()));

        assertEquals("redis://10.0.0.9:6380", spi.getProperty("url"));
        assertEquals("3", spi.getProperty("database"));
    }

    @Test
    @DisplayName("influxdb/questdb 仍透传 url 与 database")
    void enginePropertiesPropagated() {
        MonitorTimeseriesProperties properties = new MonitorTimeseriesProperties();
        properties.setUrl("http://127.0.0.1:9000");
        properties.setDatabase("qdb");

        SeriesEngineConfiguration configuration = new SeriesEngineConfiguration();
        Properties spi = configuration.spiProperties("questdb", properties, environment(Map.of()));

        assertEquals("http://127.0.0.1:9000", spi.getProperty("url"));
        assertEquals("qdb", spi.getProperty("database"));
    }

    @Test
    @DisplayName("地址判定：http(s) 与非地址一律拒绝")
    void addressDetection() {
        assertFalse(SeriesEngineConfiguration.isRedisAddress(null));
        assertFalse(SeriesEngineConfiguration.isRedisAddress(""));
        assertFalse(SeriesEngineConfiguration.isRedisAddress("   "));
        assertFalse(SeriesEngineConfiguration.isRedisAddress("http://127.0.0.1:8086"));
        assertFalse(SeriesEngineConfiguration.isRedisAddress("https://redis.example.com"));

        assertTrue(SeriesEngineConfiguration.isRedisAddress("redis://127.0.0.1:6379"));
        assertTrue(SeriesEngineConfiguration.isRedisAddress("rediss://127.0.0.1:6379"));
        assertTrue(SeriesEngineConfiguration.isRedisAddress("redis-socket:///tmp/redis.sock"));
        assertTrue(SeriesEngineConfiguration.isRedisAddress("redis-sentinel://127.0.0.1:26379"));
        assertTrue(SeriesEngineConfiguration.isRedisAddress("127.0.0.1:6379"));
    }

    /**
     * 构造带指定属性的环境。
     *
     * @param values 属性键值
     * @return 环境
     */
    private StandardEnvironment environment(Map<String, Object> values) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", values));
        return environment;
    }
}
