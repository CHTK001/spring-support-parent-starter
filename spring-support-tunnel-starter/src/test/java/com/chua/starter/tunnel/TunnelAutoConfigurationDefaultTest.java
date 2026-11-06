package com.chua.starter.tunnel;

import com.chua.starter.tunnel.support.TunnelController;
import com.chua.starter.tunnel.support.TunnelService;
import com.chua.starter.tunnel.spi.TunnelProviderRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 后端约束层 {@link TunnelAutoConfiguration} 的基础行为测试。
 *
 * <p>所有 Bean 默认注册；引入提供方时按需装配（白名单裁剪）。</p>
 * @author CH
 * @since 4.0.0.51
 */
@EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {TunnelAutoConfiguration.class})
@AutoConfigureMockMvc
class TunnelAutoConfigurationDefaultTest {

    @Autowired
    private ConfigurableApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TunnelService tunnelService;

    @Autowired
    private TunnelProviderRegistry registry;

    @Test
    void shouldRegisterControllerServiceAndRegistry() throws Exception {
        // 自动装配的 Bean 一个都不能少。
        assertThat(context.getBeanNamesForType(TunnelController.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(TunnelService.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(TunnelProviderRegistry.class)).hasSize(1);

        assertThat(tunnelService).isNotNull();
        assertThat(registry.isEmpty()).isTrue();

        // 未引入任何提供方的控制台接口仍可访问。
        mockMvc.perform(MockMvcRequestBuilders.get("/ms/tunnel/providers"))
                .andExpect(MockMvcResultMatchers.status().is2xxSuccessful())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("00000"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data").isArray())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data").isEmpty());
    }
}
