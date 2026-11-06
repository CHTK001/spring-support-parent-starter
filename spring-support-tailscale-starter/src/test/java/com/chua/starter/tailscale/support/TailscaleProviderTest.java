// [P3C A20 豁免] A20/A30/A31: 测试内用于 endpoint 权限制约的非业务映射值，已含备注。[P3C E1 豁免] E1.2: 测试代码允许 System.out（仅脚本/测试/调试例外）。
package com.chua.starter.tailscale.support;

import com.chua.starter.tunnel.TunnelAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tailscale Funnel 提供方：在嵌入式 Web 环境中验证统一入口 {@code GET /ms/tunnel/providers}
 * 已把 Tailscale 作为新提供方列出来。
 *
 * <p>上下文显式导入隧道统一入口自动配置 + Tailscale 自动配置，避免受工程里
 * ngrok / cloudflared 是否引入的干扰；客户端能力由 {@code TailscaleTool} 感知
 * （本机 PATH 上有 tailscale.exe）。</p>
 *
 * <p>注：公共 starter 转引入了 {@code spring-boot-starter-jdbc}（HikariCP 在类路径上），
 * 但本测试不需要数据源，因此排除 {@code DataSourceAutoConfiguration}。</p>
 * @author CH
 * @since 4.0.0.51
 */
@EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {TunnelAutoConfiguration.class, TailscaleAutoConfiguration.class})
@AutoConfigureMockMvc
class TailscaleProviderTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void providersEndpointContainsTailscale() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/ms/tunnel/providers"))
                .andExpect(MockMvcResultMatchers.status().is2xxSuccessful())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("00000"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data[*].name").value("tailscale"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data[*].displayName").value("Tailscale Funnel"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data[*].defaultMode").value("http"))
                // 注意：indefinite path（[*]）下 modes 会包一层 JSONArray，数组内的每个元素
                // 是 modes 子数组本身（["http","tcp"]），不能直接用 hasItems 匹配字符串。
                // 改用下标指向每个具体的字符串。
                .andExpect(MockMvcResultMatchers.jsonPath("$.data[0].modes[0]").value("http"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data[0].modes[1]").value("tcp"))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("tailscale", "Tailscale Funnel", "http", "tcp");
        System.out.println("providers> " + body);
    }
}
