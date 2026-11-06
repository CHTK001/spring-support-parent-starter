package com.chua.starter.tunnel;

import com.chua.starter.tunnel.support.TunnelController;
import com.chua.starter.tunnel.support.TunnelService;
import com.chua.starter.tunnel.spi.TunnelProviderRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 开关关闭时，统一入口的一切、包括提供方注册表与 REST 接口都应消失。
 * @author CH
 * @since 4.0.0.51
 */
@EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {TunnelAutoConfigurationDisabledTest.Config.class, TunnelAutoConfiguration.class},
        properties = "plugin.tunnel.enable=false")
@AutoConfigureMockMvc
class TunnelAutoConfigurationDisabledTest {

    /**
     * 注册一个不可用的提供方，验证关闭开关之后不会被加载。
     */
    @TestConfiguration
    static class Config {

        @Bean
        public com.chua.starter.tunnel.support.TestTunnelProvider provider() {
            return new com.chua.starter.tunnel.support.TestTunnelProvider("tunnel-test", true);
        }
    }

    @Autowired
    private ConfigurableApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldDisableAllWhenEnableIsFalse() throws Exception {
        assertThat(context.getBeanNamesForType(TunnelController.class)).isEmpty();
        assertThat(context.getBeanNamesForType(TunnelService.class)).isEmpty();
        assertThat(context.getBeanNamesForType(TunnelProviderRegistry.class)).isEmpty();

        // 控制器不存在，页面背后也没有 /ms/tunnel 路由。
        mockMvc.perform(MockMvcRequestBuilders.get("/ms/tunnel/providers"))
                .andReturn()
                .getResponse();
    }
}
