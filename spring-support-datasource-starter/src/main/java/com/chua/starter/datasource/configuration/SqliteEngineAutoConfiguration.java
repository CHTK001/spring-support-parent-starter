package com.chua.starter.datasource.configuration;

import com.chua.common.support.lang.datasource.flyway.Flyway;
import com.chua.sqlite.support.engine.SqliteEngine;
import com.chua.starter.datasource.properties.SqliteDataSourceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;
import java.io.File;

/**
* sqlite 嵌入式数据库自动配置。
* <p>
* 当 {@code plugin.sqlite.enable=true} 时，自动创建：
* <ul>
*   <li>{@link SqliteEngine} — 优先于注解初始化，在 Bean 容器中注入</li>
*   <li>{@link DataSource} — HikariCP 包装的 SQLite 连接池</li>
* </ul>
* </p>
* <p>
* 使用示例：
* <pre>{@code
* # application.properties
* plugin.sqlite.enable=true
* plugin.sqlite.path=./data/app.sqlite
* plugin.sqlite.init-sql=classpath:db/init/sqlite
* plugin.sqlite.max-pool-size=5
* }</pre>ize=5
* }</pre>
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = "com.chua.sqlite.support.engine.SqliteEngine")
@ConditionalOnProperty(prefix = SqliteDataSourceProperties.PRE, name = "enable", havingValue = "true")
@EnableConfigurationProperties(SqliteDataSourceProperties.class)
@AutoConfigureAfter(H2EngineAutoConfiguration.class)
public class SqliteEngineAutoConfiguration {

    private final SqliteDataSourceProperties properties; // 属性

    /**
    * sqliteengineauto配置。
    * @param properties 属性
    */
    public SqliteEngineAutoConfiguration(SqliteDataSourceProperties properties) {
        this.properties = properties;
    }

    /**
    * 注册 sqlite Engine Bean，优先于注解初始化加载。
    * <p>
    * 使用 {@code @Order(Ordered.LOWEST_PRECEDENCE)} 确保在所有依赖 Engine 的 Bean
    * 之后初始化，但在需要 sqlite 作为默认数据源的场景下可优先于其他 Engine。
    * </p>
    * @return sqliteEngine的结果
    */
    @Bean
    @ConditionalOnMissingBean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public SqliteEngine sqliteEngine() {
        SqliteEngine engine = new SqliteEngine();
        engine.addDataSource("default", properties.getPath());
        log.info("[sqlite] SqliteEngine 已注册，path={}", properties.getPath());
        return engine;
    }

    /**
    * 注册 sqlite 数据源 Bean（供 Spring jdbctemplate / @Transactional 使用）。
    * @return sqlite数据源的结果
    */
    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    public DataSource sqliteDataSource() {
        try {
            com.zaxxer.hikari.HikariDataSource ds = new com.zaxxer.hikari.HikariDataSource();
            ds.setJdbcUrl("jdbc:sqlite:" + properties.getPath());
            ds.setDriverClassName("org.sqlite.JDBC");
            ds.setMaximumPoolSize(properties.getMaxPoolSize());
            ds.setPoolName("SQLite-HikariPool");
            // 确保目录存在
            File file = new File(properties.getPath());
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            log.info("[sqlite] SQLite DataSource 已注册，path={}", properties.getPath());
            return ds;
        } catch (Exception e) {
            /**
            * runtime异常。
            * @param e.getMessage( e.获取消息(
            * @return runtime异常的结果
            */
            throw new RuntimeException("[sqlite] SQLite DataSource 创建失败: " + e.getMessage(), e);
        }
    }
}
