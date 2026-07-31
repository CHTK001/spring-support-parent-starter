package com.chua.starter.datasync.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DatasyncProperties 单元测试。
 *
 * @author CH
 * @since 4.0.0.42
 */
class DatasyncPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @EnableConfigurationProperties(DatasyncProperties.class)
    static class TestConfig {
    }

    @Test
    void defaultProperties() {
        contextRunner.run(context -> {
            DatasyncProperties props = context.getBean(DatasyncProperties.class);
            assertThat(props.isEnabled()).isTrue();
            assertThat(props.getMappingSource()).isEqualTo("file");
            assertThat(props.getFileMapping().getDirectoryPath()).isEqualTo("./datasync/mappings");
            assertThat(props.getDatabaseMapping().isEnabled()).isFalse();
            assertThat(props.getDatabaseMapping().getTablePrefix()).isEqualTo("datasync_");
            assertThat(props.getDatabaseMapping().isAutoInitTable()).isTrue();
        });
    }

    @Test
    void databaseMode() {
        contextRunner
                .withPropertyValues(
                        "plugin.datasync.enabled=false",
                        "plugin.datasync.mapping-source=database",
                        "plugin.datasync.database-mapping.enabled=true",
                        "plugin.datasync.database-mapping.table-prefix=my_",
                        "plugin.datasync.database-mapping.auto-init-table=false"
                )
                .run(context -> {
                    DatasyncProperties props = context.getBean(DatasyncProperties.class);
                    assertThat(props.isEnabled()).isFalse();
                    assertThat(props.getMappingSource()).isEqualTo("database");
                    assertThat(props.getDatabaseMapping().isEnabled()).isTrue();
                    assertThat(props.getDatabaseMapping().getTablePrefix()).isEqualTo("my_");
                    assertThat(props.getDatabaseMapping().isAutoInitTable()).isFalse();
                });
    }
}
