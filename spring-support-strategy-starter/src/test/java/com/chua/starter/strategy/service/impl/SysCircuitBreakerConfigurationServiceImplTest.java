package com.chua.starter.strategy.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.chua.starter.strategy.entity.SysCircuitBreakerConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

class SysCircuitBreakerConfigurationServiceImplTest {

    @Test
    void shouldFailOpenWhenConfigurationQueryThrows() {
        SysCircuitBreakerConfigurationServiceImpl service = spy(new SysCircuitBreakerConfigurationServiceImpl());
        doThrow(new IllegalStateException("db down"))
                .when(service)
                .list(org.mockito.ArgumentMatchers.<Wrapper<SysCircuitBreakerConfiguration>>any());

        List<SysCircuitBreakerConfiguration> result = service.listEnabledConfigurations();

        assertThat(result).isEmpty();
    }

    @Test
    void shouldMatchFirstEnabledConfigurationByPath() {
        SysCircuitBreakerConfigurationServiceImpl service = spy(new SysCircuitBreakerConfigurationServiceImpl());
        doReturn(List.of(
                config("payment", "/api/payment/**"),
                config("fallback", "/**")
        )).when(service).listEnabledConfigurations();

        SysCircuitBreakerConfiguration result = service.getByPath("/api/payment/notify");

        assertThat(result).isNotNull();
        assertThat(result.getSysCircuitBreakerName()).isEqualTo("payment");
    }

    private SysCircuitBreakerConfiguration config(String name, String path) {
        SysCircuitBreakerConfiguration configuration = new SysCircuitBreakerConfiguration();
        configuration.setSysCircuitBreakerName(name);
        configuration.setSysCircuitBreakerPath(path);
        configuration.setSysCircuitBreakerStatus(1);
        return configuration;
    }
}
