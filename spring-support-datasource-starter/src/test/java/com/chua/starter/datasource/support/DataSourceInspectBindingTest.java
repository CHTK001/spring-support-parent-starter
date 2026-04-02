package com.chua.starter.datasource.support;

import com.chua.starter.datasource.properties.MultiDataSourceProperties;
import com.chua.starter.datasource.properties.MultiHikariDataSourceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceInspectBindingTest {

    @Test
    void shouldBindPluginMultiDatasourcePrefix() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("plugin.multi-datasource.data-source[0].name", "master")
                .withProperty("plugin.multi-datasource.data-source[0].url", "jdbc:h2:mem:master")
                .withProperty("plugin.multi-datasource.data-source[0].username", "sa");

        MultiDataSourceProperties properties = DataSourceInspect.bindMultiDataSourceProperties(environment);

        assertThat(properties.getDataSource()).hasSize(1);
        assertThat(properties.getDataSource().get(0).getName()).isEqualTo("master");
        assertThat(properties.getDataSource().get(0).getUrl()).isEqualTo("jdbc:h2:mem:master");
    }

    @Test
    void shouldMergeLegacyPrefixAndLetPluginOverrideSameDatasource() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.multi-datasource.data-source[0].name", "master")
                .withProperty("spring.multi-datasource.data-source[0].url", "jdbc:h2:mem:legacy")
                .withProperty("spring.multi-datasource.data-source[1].name", "slave")
                .withProperty("spring.multi-datasource.data-source[1].url", "jdbc:h2:mem:slave")
                .withProperty("plugin.multi-datasource.data-source[0].name", "master")
                .withProperty("plugin.multi-datasource.data-source[0].url", "jdbc:h2:mem:plugin");

        MultiDataSourceProperties properties = DataSourceInspect.bindMultiDataSourceProperties(environment);

        assertThat(properties.getDataSource()).hasSize(2);
        assertThat(properties.getDataSource())
                .filteredOn(item -> "master".equals(item.getName()))
                .singleElement()
                .extracting(item -> item.getUrl())
                .isEqualTo("jdbc:h2:mem:plugin");
        assertThat(properties.getDataSource())
                .filteredOn(item -> "slave".equals(item.getName()))
                .singleElement()
                .extracting(item -> item.getUrl())
                .isEqualTo("jdbc:h2:mem:slave");
    }

    @Test
    void shouldBindPluginHikariPrefix() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("plugin.multi-datasource.hikari.data-source[0].name", "master")
                .withProperty("plugin.multi-datasource.hikari.data-source[0].url", "jdbc:h2:mem:hikari")
                .withProperty("plugin.multi-datasource.hikari.data-source[0].max-pool-size", "8");

        MultiHikariDataSourceProperties properties = DataSourceInspect.bindMultiHikariDataSourceProperties(environment);

        assertThat(properties.getDataSource()).hasSize(1);
        assertThat(properties.getDataSource().get(0).getName()).isEqualTo("master");
        assertThat(properties.getDataSource().get(0).getUrl()).isEqualTo("jdbc:h2:mem:hikari");
        assertThat(properties.getDataSource().get(0).getMaxPoolSize()).isEqualTo(8);
    }
}
