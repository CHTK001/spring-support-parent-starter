package com.chua.starter.datasync.configuration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DataSync 配置属性单元测试。
 *
 * <p>覆盖默认值与属性绑定。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class DatasyncPropertiesTest {

    @Test
    void defaultValues() {
        DatasyncProperties properties = new DatasyncProperties();
        assertTrue(properties.isEnabled());
        assertEquals("file", properties.getMappingSource());
        assertEquals("./datasync/mappings", properties.getFileMapping().getDirectoryPath());
        assertFalse(properties.getDatabaseMapping().isEnabled());
        assertEquals("datasync_", properties.getDatabaseMapping().getTablePrefix());
        assertEquals("0/5 * * * * ?", properties.getDatabaseMapping().getCron());
    }

    @Test
    void setters() {
        DatasyncProperties properties = new DatasyncProperties();
        properties.setEnabled(false);
        properties.setMappingSource("database");
        properties.getFileMapping().setDirectoryPath("/tmp/mappings");
        properties.getDatabaseMapping().setEnabled(true);
        properties.getDatabaseMapping().setTablePrefix("sync_");

        assertFalse(properties.isEnabled());
        assertEquals("database", properties.getMappingSource());
        assertEquals("/tmp/mappings", properties.getFileMapping().getDirectoryPath());
        assertTrue(properties.getDatabaseMapping().isEnabled());
        assertEquals("sync_", properties.getDatabaseMapping().getTablePrefix());
    }
}
