package com.chua.starter.datasource.monitor;

import com.chua.sqlite.support.engine.SqliteEngine;
import com.chua.starter.datasource.configuration.SqliteEngineAutoConfiguration;
import com.chua.starter.datasource.properties.SqliteDataSourceProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SQLite 引擎端点自动配置
 * <p>
 * 仅在 {@link SqliteEngine} 可用、Actuator 端点注解存在且 SQLite 启用时注册。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "com.chua.sqlite.support.engine.SqliteEngine")
@ConditionalOnProperty(prefix = SqliteDataSourceProperties.PRE, name = "enable", havingValue = "true")
@AutoConfigureAfter(SqliteEngineAutoConfiguration.class)
public class SqliteEngineEndpointAutoConfiguration {

    /**
     * 注册 SQLite 引擎 Actuator 端点。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
    public SqliteEngineEndpoint sqliteEngineEndpoint(SqliteEngine engine) {
        return new SqliteEngineEndpoint(engine);
    }
}
