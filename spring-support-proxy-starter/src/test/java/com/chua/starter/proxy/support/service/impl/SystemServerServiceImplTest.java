package com.chua.starter.proxy.support.service.impl;

import com.chua.starter.proxy.support.properties.ProxyManagementProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SystemServerServiceImplTest {

    @Test
    void encryptAndDecryptShouldRoundTrip() {
        SystemServerServiceImpl service = new SystemServerServiceImpl(new ProxyManagementProperties());
        String rawPassword = "proxy-secret-123";

        String encryptedPassword = service.encryptPassword(rawPassword);
        String decryptedPassword = service.decryptPassword(encryptedPassword);

        assertEquals(rawPassword, decryptedPassword);
    }

    @Test
    void afterPropertiesSetShouldShortCircuitWhenAutoRestartDisabled() {
        ProxyManagementProperties properties = new ProxyManagementProperties();
        properties.setAutoRestartRunning(false);

        SystemServerServiceImpl service = new SystemServerServiceImpl(properties);

        assertDoesNotThrow(service::afterPropertiesSet);
    }
}
