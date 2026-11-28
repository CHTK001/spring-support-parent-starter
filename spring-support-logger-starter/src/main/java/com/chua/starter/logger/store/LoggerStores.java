// [P3C A20 豁免] A20/A30/A31: 含 SQL/引擎字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.logger.store;

import com.chua.common.support.lang.datasource.dialect.Dialect;
import com.chua.common.support.lang.datasource.engine.Engine;
import com.chua.common.support.lang.datasource.engine.EngineDataSource;
import com.chua.common.support.lang.datasource.series.SeriesEngine;
import com.chua.common.support.lang.datasource.series.impl.DataSourceSeriesEngine;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.datasource.support.engine.JdbcEngine;
import com.chua.datasource.support.kv.JdbcKvEngine;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * 日志存储引擎工厂：按 {@code plugin.logger.storage.engine} 装配 {@link LoggerEventStore}。
 *
 * <ul>
 *   <li>{@code datasource-bean}（默认）：复用容器 {@link DataSource}，原始事件落
 *       {@link JdbcKvEngine}，事件速率落 JDBC {@link DataSourceSeriesEngine}；</li>
 *   <li>{@code redis-bean}：走 utils SPI 发现 Redis 版 KvEngine（需宿主引入对应 starter
 *       并注册扩展），缺失时回落 memory；</li>
 *   <li>{@code memory}：有界内存，开发/联调用。</li>
 * </ul>
 *
 * <p>{@link SeriesEngine} 与 Spring {@link DataSource} 的接线方式与 monitor 模块一致：
 * 用 DataSource 独立构造 JDBC Engine，再包一层 DataSourceSeriesEngine，不注册为 Bean。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public final class LoggerStores {

    private LoggerStores() {
    }

    /**
     * 按配置装配日志存储。
     *
     * @param engineName   存储引擎名
     * @param capacity     memory 引擎容量
     * @param retentionHrs 持久化保留时长（小时）
     * @param dataSource   容器数据源，可为 null
     * @return 存储实例（永不为 null：引擎不可用时回落 memory 并告警）
     */
    public static LoggerEventStore create(String engineName, int capacity, long retentionHrs, DataSource dataSource) {
        String engine = engineName == null ? "datasource-bean" : engineName.trim().toLowerCase();
        switch (engine) {
            case "memory":
                log.info("[logger] 日志存储引擎: memory (capacity={})", capacity);
                return new MemoryLoggerEventStore(capacity);
            case "redis-bean":
                KvEngineHolder redis = redisEngine();
                if (redis != null) {
                    log.info("[logger] 日志存储引擎: redis-bean (KvEngine={})", redis.kv.getClass().getName());
                    return new KvLoggerEventStore(redis.kv, redis.series, retentionHrs);
                }
                log.warn("[logger] storage.engine=redis-bean 但 SPI 无 redis KvEngine 实现，回落 memory");
                return new MemoryLoggerEventStore(capacity);
            case "datasource-bean":
            default:
                if (dataSource == null) {
                    log.warn("[logger] storage.engine={} 但容器无 DataSource，回落 memory", engine);
                    return new MemoryLoggerEventStore(capacity);
                }
                KvLoggerEventStore store = new KvLoggerEventStore(
                        new JdbcKvEngine(dataSource), jdbcSeriesEngine(dataSource), retentionHrs);
                log.info("[logger] 日志存储引擎: datasource-bean (JdbcKvEngine + JDBC SeriesEngine, retention={}h)",
                        retentionHrs);
                return store;
        }
    }

    /**
     * 创建 SDK trace 状态存储。trace 状态不跟随 memory 日志引擎进入堆内，优先复用
     * JDBC/Redis 的持久化 KV；两者均不可用时落本地磁盘文件。
     *
     * @param engineName 日志存储引擎名
     * @param traceStatePath 本地磁盘兜底目录
     * @param dataSource 容器数据源，可为空
     * @return 持久化状态存储
     */
    public static LoggerTraceStateStore createTraceStateStore(String engineName, String traceStatePath,
                                                              DataSource dataSource) {
        if (dataSource != null) {
            return new KvLoggerTraceStateStore(new JdbcKvEngine(dataSource));
        }
        if ("redis-bean".equalsIgnoreCase(engineName)) {
            KvEngineHolder redis = redisEngine();
            if (redis != null) {
                return new KvLoggerTraceStateStore(redis.kv);
            }
        }
        log.warn("[logger] trace 状态没有可共享的 JDBC/Redis KV，使用本地磁盘目录: {}", traceStatePath);
        return new FileLoggerTraceStateStore(traceStatePath);
    }

    /**
     * SPI 探测 Redis 版 KvEngine（含可用的 SeriesEngine 时一并返回）。
     */
    private static KvEngineHolder redisEngine() {
        try {
            com.chua.common.support.lang.datasource.kv.KvEngine kv =
                    ServiceProvider.of(com.chua.common.support.lang.datasource.kv.KvEngine.class)
                            .getNewExtension("redis");
            if (kv == null) {
                return null;
            }
            SeriesEngine series = null;
            try {
                series = ServiceProvider.of(SeriesEngine.class).getNewExtension("redis");
            } catch (Exception ignored) {
                // series 缺席时 eps 由事件扫描聚合
            }
            return new KvEngineHolder(kv, series);
        } catch (Exception e) {
            log.debug("[logger] redis KvEngine SPI 探测失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 用 Spring DataSource 独立构造 JDBC 时序引擎（不注册为 Bean）。
     *
     * @param dataSource Spring 共享数据源
     * @return 时序引擎
     */
    static SeriesEngine jdbcSeriesEngine(DataSource dataSource) {
        Engine jdbcEngine = new JdbcEngine() {
        };
        jdbcEngine.addDataSource("default", new SharedDataSource(dataSource));
        return new DataSourceSeriesEngine(jdbcEngine);
    }

    private record KvEngineHolder(com.chua.common.support.lang.datasource.kv.KvEngine kv, SeriesEngine series) {
    }

    /**
     * 共享数据源适配器：不拥有底层连接池，{@link #close()} 为空，避免内部引擎关闭时
     * 误关 Spring 管理的连接池。
     */
    private static final class SharedDataSource implements EngineDataSource<DataSource> {

        /**
         * Spring 共享数据源
         */
        private final DataSource dataSource;

        /**
         * 构造共享数据源适配器。
         *
         * @param dataSource Spring 共享数据源
         */
        private SharedDataSource(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public String name() {
            return "default";
        }

        @Override
        public DataSource getSource() {
            return dataSource;
        }

        @Override
        public EngineDataSource<DataSource> setSource(Object source) {
            return this;
        }

        @Override
        public Dialect getDialect() {
            return null;
        }

        @Override
        public EngineDataSource<DataSource> setDialect(Dialect dialect) {
            return this;
        }

        @Override
        public String url() {
            return null;
        }

        @Override
        public String username() {
            return null;
        }

        @Override
        public String password() {
            return null;
        }

        @Override
        public void close() {
            // 不关闭共享连接池
        }
    }
}
