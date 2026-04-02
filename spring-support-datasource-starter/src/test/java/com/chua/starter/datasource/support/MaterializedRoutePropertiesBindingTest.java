package com.chua.starter.datasource.support;

import com.chua.starter.datasource.properties.MaterializedRouteProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class MaterializedRoutePropertiesBindingTest {

    @Test
    void shouldBindPluginPrefixFirst() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("plugin.datasource.materialized.enabled", "true")
                .withProperty("plugin.datasource.materialized.default-threshold", "256")
                .withProperty("plugin.datasource.materialized.refresh-interval-seconds", "120")
                .withProperty("plugin.datasource.materialized.cache-data-source-prefix", "mem#");

        MaterializedRouteProperties properties = MaterializedRouteProperties.bind(environment);

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getDefaultThreshold()).isEqualTo(256L);
        assertThat(properties.getRefreshIntervalSeconds()).isEqualTo(120L);
        assertThat(properties.getCacheDataSourcePrefix()).isEqualTo("mem#");
    }

    @Test
    void shouldFallbackToLegacyPrefixWhenPluginPrefixMissing() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.materialized.enabled", "true")
                .withProperty("spring.datasource.materialized.default-threshold", "512")
                .withProperty("spring.datasource.materialized.refresh-interval-seconds", "60");

        MaterializedRouteProperties properties = MaterializedRouteProperties.bind(environment);

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getDefaultThreshold()).isEqualTo(512L);
        assertThat(properties.getRefreshIntervalSeconds()).isEqualTo(60L);
    }
}
