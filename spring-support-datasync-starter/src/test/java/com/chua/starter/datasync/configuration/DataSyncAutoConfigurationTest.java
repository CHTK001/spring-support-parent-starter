package com.chua.starter.datasync.configuration;

import com.chua.starter.datasync.mapping.DataSyncMappingManager;
import com.chua.starter.datasync.scanner.DirectoryDataSyncMappingScanner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DataSync 自动配置单元测试。
 *
 * <p>使用 ApplicationContextRunner 验证各 Bean 的条件装配。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class DataSyncAutoConfigurationTest {

    /**
     * 测试上下文运行器
     */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataSyncAutoConfiguration.class));

    @Test
    void enabledCreatesBeans() {
        contextRunner
                .withPropertyValues("plugin.datasync.enabled=true", "plugin.datasync.mapping-source=file")
                .run(context -> {
                    assertThat(context).hasSingleBean(DataSyncMappingManager.class);
                    assertThat(context).hasBean("dataSyncServer");
                    assertThat(context).hasBean("agentServerManager");
                    assertThat(context).hasBean("directoryDataSyncMappingScanner");
                });
    }

    @Test
    void disabledSuppressesBeans() {
        contextRunner
                .withPropertyValues("plugin.datasync.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean("dataSyncServer"));
    }

    @Test
    void fileSourceCreatesScanner() {
        contextRunner
                .withPropertyValues("plugin.datasync.enabled=true", "plugin.datasync.mapping-source=file")
                .run(context -> assertThat(context).hasBean("directoryDataSyncMappingScanner"));
    }

    @Test
    void databaseEnabledCreatesLoaderWhenDataSourcePresent() {
        contextRunner
                .withBean(javax.sql.DataSource.class, () ->
                        new org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder()
                                .setType(org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType.H2)
                                .build())
                .withPropertyValues(
                        "plugin.datasync.enabled=true",
                        "plugin.datasync.mapping-source=database",
                        "plugin.datasync.database-mapping.enabled=true")
                .run(context -> assertThat(context).hasBean("databaseMappingLoader"));
    }

    @Test
    void databaseDisabledSkipsLoader() {
        contextRunner
                .withPropertyValues(
                        "plugin.datasync.enabled=true",
                        "plugin.datasync.mapping-source=database",
                        "plugin.datasync.database-mapping.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean("databaseMappingLoader"));
    }

    @Test
    void defaultPropertiesBound() {
        contextRunner
                .run(context -> {
                    DatasyncProperties properties = context.getBean(DatasyncProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getMappingSource()).isEqualTo("file");
                    assertThat(properties.getFileMapping().getDirectoryPath()).isEqualTo("./datasync/mappings");
                });
    }
}
