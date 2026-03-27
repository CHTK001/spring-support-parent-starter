package com.chua.starter.sync.data.support.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 数据源适配器单元测试（编译与基础行为）
 */
class AdapterTest {

    private DataSourceConfig config;

    @BeforeEach
    void setUp() {
        config = new DataSourceConfig();
        config.setType("MYSQL");
        config.setUrl("jdbc:h2:mem:testdb");
        config.setUsername("sa");
        config.setPassword("");
    }

    @Test
    void testDataSourceConfigProperties() {
        assertEquals("MYSQL", config.getType());
        assertEquals("jdbc:h2:mem:testdb", config.getUrl());
    }

    @Test
    void testFactoryBuiltInAdapters() {
        DataSourceAdapterFactory factory = new DataSourceAdapterFactory();
        assertNotNull(factory.getAdapter("MYSQL"));
        assertNotNull(factory.getAdapter("MONGODB"));
        assertNotNull(factory.getAdapter("FILE"));
        factory.releaseAll();
    }

    @Test
    void testFactoryCustomRegistration() {
        DataSourceAdapterFactory factory = new DataSourceAdapterFactory();
        factory.register("CUSTOM", NoopDataSourceAdapter.class);
        DataSourceAdapter adapter = factory.getAdapter("CUSTOM");
        assertInstanceOf(NoopDataSourceAdapter.class, adapter);
        factory.releaseAdapter("CUSTOM");
    }

    private static final class NoopDataSourceAdapter implements DataSourceAdapter {

        @Override
        public void connect(DataSourceConfig config) {
            // no-op
        }

        @Override
        public Stream<Map<String, Object>> read(ReadConfig config) {
            return Stream.empty();
        }

        @Override
        public void write(List<Map<String, Object>> records, WriteConfig config) {
            // no-op
        }

        @Override
        public boolean testConnection() {
            return true;
        }

        @Override
        public void close() {
            // no-op
        }

        @Override
        public DataSourceMetadata getMetadata() {
            return new DataSourceMetadata();
        }
    }
}
