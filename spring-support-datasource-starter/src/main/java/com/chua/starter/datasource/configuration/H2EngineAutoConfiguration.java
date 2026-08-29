package com.chua.starter.datasource.configuration;

import com.chua.common.support.lang.datasource.flyway.Flyway;
import com.chua.datasource.support.engine.JdbcReactorEngine;
import com.chua.h2.support.cleanup.H2CleanupPlugin;
import com.chua.h2.support.dialect.H2Dialect;
import com.chua.h2.support.engine.H2Engine;
import com.chua.starter.datasource.properties.H2DataSourceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;

/**
 * H2 嵌入式数据库自动配置。
 * <p>
 * 当 {@code plugin.h2.enable=true} 时，自动创建：
 * <ul>
 *   <li>{@link H2Engine} — SPI 注册，支持 Lambda 链式查询和全文检索</li>
 *   <li>{@link DataSource} — HikariCP 包装的 H2 连接池</li>
 *   <li>{@link Flyway} — SQL 迁移执行器（含 H2 DDL Normalizer）</li>
 *   <li>{@link H2CleanupPlugin} — 测试清理插件（可选）</li>
 * </ul>
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * # application.properties
 * plugin.h2.enable=true
 * plugin.h2.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
 * plugin.h2.script-path=classpath:db/migration/h2
 * plugin.h2.cleanup-after-test=true
 * }</pre>
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Configuration
@ConditionalOnClass(H2Engine.class)
@ConditionalOnProperty(prefix = H2DataSourceProperties.PRE, name = "enable", havingValue = "true")
@EnableConfigurationProperties(H2DataSourceProperties.class)
public class H2EngineAutoConfiguration {

    private final H2DataSourceProperties properties;

    public H2EngineAutoConfiguration(H2DataSourceProperties properties) {
        this.properties = properties;
    }

    /**
     * 注册 H2 Engine Bean，通过 SPI "h2" 获取。
     */
    @Bean
    @ConditionalOnMissingBean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public H2Engine h2Engine() {
        H2Engine engine = new H2Engine();
        engine.addDataSource("default", properties.getUrl());
        log.info("[h2] H2Engine 已注册，URL={}", properties.getUrl());
        return engine;
    }

    /**
     * 注册 H2 DataSource Bean（供 Spring JdbcTemplate /@Transactional 使用）。
     */
    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    public DataSource h2DataSource() {
        try {
            com.zaxxer.hikari.HikariDataSource ds = new com.zaxxer.hikari.HikariDataSource();
            ds.setJdbcUrl(properties.getUrl());
            ds.setDriverClassName("org.h2.Driver");
            ds.setUsername(properties.getUsername());
            ds.setPassword(properties.getPassword());
            ds.setMaximumPoolSize(5);
            ds.setPoolName("H2-HikariPool");
            log.info("[h2] H2 DataSource 已注册，URL={}", properties.getUrl());
            return ds;
        } catch (Exception e) {
            throw new RuntimeException("[h2] H2 DataSource 创建失败: " + e.getMessage(), e);
        }
    }

    /**
     * 注册 H2 Flyway 迁移执行器。
     * <p>
     * 如果配置了 {@code script-path}，则在 Bean 初始化时自动执行迁移。
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean
    @Order(Ordered.LOWEST_PRECEDENCE + 1)
    public Flyway h2Flyway(H2Engine engine) {
        com.chua.h2.support.flyway.H2Flyway flyway = new com.chua.h2.support.flyway.H2Flyway(
                toJdbcReactorEngine(engine));
        if (properties.getScriptPath() != null && !properties.getScriptPath().isBlank()) {
            flyway.location(properties.getScriptPath());
            int count = flyway.migrate();
            log.info("[h2] 迁移完成，执行了 {} 个脚本", count);
        }
        return flyway;
    }

    /**
     * 注册 H2 清理插件（可选）。
     */
    @Bean
    @ConditionalOnMissingBean
    public H2CleanupPlugin h2CleanupPlugin(H2Engine engine) {
        return new H2CleanupPlugin(engine);
    }

    /**
     * 将 H2Engine 桥接到 JdbcReactorEngine（用于 flyway）。
     * <p>
     * H2Engine 本身不实现 ReactorEngine，通过 SPI 获取的 JdbcReactorEngine 作为桥接目标。
     * </p>
     */
    private JdbcReactorEngine toJdbcReactorEngine(H2Engine engine) {
        // H2Engine 通过 SPI 注册为 "h2"，返回自身；JdbcReactorEngine 通过 "jdbc" 获取
        // 直接用 H2Engine.execute() 包装为简单桥接
        return new JdbcReactorEngine() {
            @Override
            public <T> com.chua.datasource.support.wrapper.ReactorLambdaQueryWrapper<T> query(Class<T> entityClass) {
                throw new UnsupportedOperationException("H2 Flyway 不使用 Lambda 查询");
            }

            @Override
            public <T> com.chua.datasource.support.wrapper.ReactorLambdaUpdateWrapper<T> update(Class<T> entityClass) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> com.chua.datasource.support.wrapper.ReactorLambdaDeleteWrapper<T> delete(Class<T> entityClass) {
                throw new UnsupportedOperationException();
            }

            @Override
            public reactor.core.publisher.Flux<java.util.Map<String, Object>> query(String sql, Object... params) {
                return reactor.core.publisher.Flux.defer(() -> {
                    try {
                        java.sql.Connection conn = getConnection();
                        java.sql.PreparedStatement ps = conn.prepareStatement(sql);
                        for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
                        java.sql.ResultSet rs = ps.executeQuery();
                        java.sql.ResultSetMetaData meta = rs.getMetaData();
                        int cols = meta.getColumnCount();
                        java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
                        while (rs.next()) {
                            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                            for (int i = 1; i <= cols; i++) {
                                row.put(meta.getColumnLabel(i), rs.getObject(i));
                            }
                            rows.add(row);
                        }
                        rs.close();
                        ps.close();
                        conn.close();
                        return reactor.core.publisher.Flux.fromIterable(rows);
                    } catch (Exception e) {
                        return reactor.core.publisher.Flux.error(e);
                    }
                });
            }

            @Override
            public <T> reactor.core.publisher.Flux<T> query(String sql, Class<T> rowType, Object... params) {
                throw new UnsupportedOperationException();
            }

            @Override
            public reactor.core.publisher.Mono<Integer> execute(String sql, Object... params) {
                return reactor.core.publisher.Mono.fromCallable(() -> {
                    try (java.sql.Connection conn = getConnection();
                         java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
                        for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
                        return ps.executeUpdate();
                    }
                });
            }

            @Override
            public reactor.core.publisher.Flux<Integer> batch(String sql, java.util.List<Object[]> batchParams) {
                throw new UnsupportedOperationException();
            }

            private java.sql.Connection getConnection() throws Exception {
                return DriverManager.getConnection(properties.getUrl(),
                        properties.getUsername(), properties.getPassword());
            }
        };
    }
}
