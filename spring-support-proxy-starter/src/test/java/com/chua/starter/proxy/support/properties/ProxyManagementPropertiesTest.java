package com.chua.starter.proxy.support.properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyManagementPropertiesTest {

    @Test
    void defaultValuesShouldMatchModuleContract() {
        ProxyManagementProperties properties = new ProxyManagementProperties();

        assertFalse(properties.isEnable());
        assertTrue(properties.isAutoRestartRunning());
    }
}
