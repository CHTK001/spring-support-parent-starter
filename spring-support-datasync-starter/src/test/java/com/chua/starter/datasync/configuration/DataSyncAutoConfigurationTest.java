package com.chua.starter.datasync.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * DataSyncAutoConfiguration 单元测试。
 *
 * @author CH
 * @since 4.0.0.42
 */
class DataSyncAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class, DataSyncAutoConfiguration.class)
            .withPropertyValues("plugin.datasync.enabled=true");

    @Configuration
    @EnableConfigurationProperties(DatasyncProperties.class)
    static class TestConfig {
    }

    @Test
    void whenEnabled_thenDataSyncServerBeanCreated() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(com.chua.starter.datasync.DataSyncServer.class);
            assertThat(context).hasSingleBean(com.chua.starter.datasync.agent.AgentServerManager.class);
        });
    }

    @Test
    void whenDisabled_thenNoDataSyncBeans() {
        contextRunner
                .withPropertyValues("plugin.datasync.enabled=false")
                .run(context -> {
                    assertThatExceptionOfType(NoSuchBeanDefinitionException.class)
                            .isThrownBy(() -> context.getBean(com.chua.starter.datasync.DataSyncServer.class));
                });
    }

    @Test
    void whenFileMappingSource_thenScannerBeanCreated() {
        contextRunner
                .withPropertyValues("plugin.datasync.mapping-source=file")
                .run(context -> {
                    assertThat(context).hasSingleBean(com.chua.starter.datasync.scanner.DirectoryDataSyncMappingScanner.class);
                });
    }

    @Test
    void whenDatabaseMappingEnabled_thenLoaderBeanCreated() {
        com.chua.starter.datasync.mapping.DefaultDataSyncMappingManager mgr =
                new com.chua.starter.datasync.mapping.DefaultDataSyncMappingManager();
        contextRunner
                .withPropertyValues(
                        "plugin.datasync.database-mapping.enabled=true",
                        "spring.datasource.url=jdbc:h2:mem:testdb"
                )
                .withBean(javax.sql.DataSource.class, () -> {
                    org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
                    ds.setURL("jdbc:h2:mem:testdb");
                    ds.setUser("sa");
                    ds.setPassword("");
                    return ds;
                })
                .withBean(com.chua.starter.datasync.mapping.DataSyncMappingManager.class, () -> mgr)
                .run(context -> {
                    assertThat(context).hasSingleBean(DatabaseMappingLoader.class);
                });
    }
}
