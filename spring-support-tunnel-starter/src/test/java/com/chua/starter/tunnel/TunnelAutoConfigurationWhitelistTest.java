package com.chua.starter.tunnel;

import com.chua.starter.tunnel.support.TestTunnelProvider;
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 白名单裁剪：由 {@code plugin.tunnel.providers} 决定注册表放哪些提供方。
 * @author CH
 * @since 4.0.0.51
 */
@EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {TunnelAutoConfigurationWhitelistTest.Config.class, TunnelAutoConfiguration.class},
        properties = "plugin.tunnel.providers=tunnel-white,tunnel-test")
@AutoConfigureMockMvc
class TunnelAutoConfigurationWhitelistTest {

    /**
     * 可用的提供方与不可用的提供方各一个。
     */
    @TestConfiguration
    static class Config {

        /**
         * 白名单内的提供方。
         */
        @Bean
        public TestTunnelProvider white() {
            return new TestTunnelProvider("tunnel-white", true);
        }

        /**
         * 白名单内的另一个提供方。
         */
        @Bean
        public TestTunnelProvider test() {
            return new TestTunnelProvider("tunnel-test", true);
        }

        /**
         * 白名单外的提供方，应被裁剪掉。
         */
        @Bean
        public TestTunnelProvider blocked() {
            return new TestTunnelProvider("tunnel-blocked", false);
        }
    }

    @Autowired
    private ConfigurableApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TunnelProviderRegistry registry;

    @Test
    void shouldFilterByWhitelist() throws Exception {
        // 白名单里和限制名不存在的提供方被裁剪。
        assertThat(registry.names()).containsExactly("tunnel-white", "tunnel-test");
        assertThat(registry.all()).hasSize(2);
        assertThat(registry.find("tunnel-white")).isPresent();
        assertThat(registry.find("tunnel-blocked")).isEmpty();

        // 页面看到的提供方列表是裁剪后的结果。
        mockMvc.perform(MockMvcRequestBuilders.get("/ms/tunnel/providers"))
                .andExpect(MockMvcResultMatchers.status().is2xxSuccessful())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("00000"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.length()").value(2));
    }
}
