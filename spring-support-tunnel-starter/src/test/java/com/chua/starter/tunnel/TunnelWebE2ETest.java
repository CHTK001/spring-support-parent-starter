package com.chua.starter.tunnel;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.tunnel.TunnelView;
import com.chua.starter.tunnel.support.TestTunnelProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 统一入口 REST（{@code /ms/tunnel}）集成测试。
 *
 * <p>在嵌入式 Tomcat 中启动 {@link TunnelAutoConfiguration} 以及两个测试用提供方
 * （一个可用、一个不可用），覆盖提供方列表、映射创建与启停、更新与删除。
 * 公共数据源自动配置被排除，避免无意义建库。</p>
 *
 * <p>使用 {@link TestPropertySource} 将映射持久化到 {@code target/e2e-store}（
 * {@link #cleanStoreDir()} 在 Spring 启动前置空目录），只对当前类生效，避免跨类
 * 污染家目录或同类测试的共享状态。</p>
 * @author CH
 * @since 4.0.0.51
 */
@TestPropertySource(properties = "plugin.tunnel.storeDir=target/e2e-store")
@EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {TunnelWebE2ETest.TunnelTestConfig.class, TunnelAutoConfiguration.class})
@AutoConfigureMockMvc
class TunnelWebE2ETest {

    private static final Path STORE_DIR = Path.of(System.getProperty("user.dir")).resolve("target/e2e-store");

    /** 映射创建与断言的本地端口。 */
    private static final int TEST_LOCAL_PORT = 8080;

    /**
     * 在 Spring 启动前置空映射目录，避免测试之间相互影响。
     */
    @BeforeAll
    static void cleanStoreDir() throws Exception {
        if (Files.isDirectory(STORE_DIR)) {
            try (var walk = Files.walk(STORE_DIR)) {
                walk.sorted(Comparator.comparingLong(Path::getNameCount).reversed())
                        .map(Path::toFile)
                        .forEach(java.io.File::delete);
            }
        }
        Files.createDirectories(STORE_DIR);
    }

    /**
     * 注册测试用提供方。
     */
    @TestConfiguration
    static class TunnelTestConfig {

        @Bean
        public TestTunnelProvider testTunnelProvider() {
            return new TestTunnelProvider("tunnel-test", true);
        }

        @Bean
        public TestTunnelProvider blockedTunnelProvider() {
            return new TestTunnelProvider("tunnel-blocked", false);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldListAllProviders() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/ms/tunnel/providers"))
                .andExpect(MockMvcResultMatchers.status().is2xxSuccessful())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("00000"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.length()").value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data[0].name").value("tunnel-test"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data[0].displayName").value("tunnel-test Test"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data[0].modes[0]").value("http"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data[0].modes[1]").value("tcp"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data[0].defaultMode").value("http"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data[0].available").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data[1].name").value("tunnel-blocked"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data[1].available").value(false))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("tunnel-test", "tunnel-blocked", "http", "tcp");
    }

    @Test
    void shouldCreateMap() throws Exception {
        TunnelView created = create();
        assertThat(created.getProvider()).isEqualTo("tunnel-test");
        assertThat(created.getName()).isEqualTo("web-test");
        assertThat(created.getLocalPort()).isEqualTo(TEST_LOCAL_PORT);
        assertThat(created.isRunning()).isFalse();
        assertThat(created.getError()).isNull();
    }

    @Test
    void shouldStartStopMap() throws Exception {
        TunnelView created = create();

        // 启动。
        mockMvc.perform(MockMvcRequestBuilders.post("/ms/tunnel/" + created.getId() + "/start"))
                .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());

        TunnelView running = getMap(created.getId());
        assertThat(running.isRunning()).isTrue();
        assertThat(running.getPublicUrls()).containsExactly("http://public.tunnel-test.tunnel");
        assertThat(running.getError()).isNull();
        assertThat(running.getStartedAt()).isNotNull();

        // 停止。
        mockMvc.perform(MockMvcRequestBuilders.post("/ms/tunnel/" + created.getId() + "/stop"))
                .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());

        TunnelView stopped = getMap(created.getId());
        assertThat(stopped.isRunning()).isFalse();
        assertThat(stopped.getPublicUrls()).isEmpty();
    }

    @Test
    void shouldUpdateMap() throws Exception {
        TunnelView created = create();

        mockMvc.perform(MockMvcRequestBuilders.put("/ms/tunnel/" + created.getId())
                        .contentType("application/json")
                        .content("{\"localPort\":8080,\"name\":\"web-update\",\"mode\":\"http\",\"remark\":\"备注\"}"))
                .andExpect(MockMvcResultMatchers.status().is2xxSuccessful())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("00000"));

        TunnelView updated = getMap(created.getId());
        assertThat(updated.getName()).isEqualTo("web-update");
        assertThat(updated.getRemark()).isEqualTo("备注");
        assertThat(updated.getProvider()).isEqualTo("tunnel-test");
    }

    @Test
    void shouldDeleteMap() throws Exception {
        TunnelView created = create();

        mockMvc.perform(MockMvcRequestBuilders.delete("/ms/tunnel/" + created.getId()))
                .andExpect(MockMvcResultMatchers.status().is2xxSuccessful())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("00000"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data").value(true));

        mockMvc.perform(MockMvcRequestBuilders.get("/ms/tunnel"))
                .andExpect(MockMvcResultMatchers.status().is2xxSuccessful())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data").isEmpty());
    }

    @Test
    void shouldFailToCreateUnknownProvider() throws Exception {
        // 4xx 未实现：ReturnResult.error 返回 200 + 错误码（C0400S0000 = REQUEST_PARAM_ERROR）。
        // 这里显式传 provider = "nope" 进入注册表裁剪，验证提供方不在可选范围。
        mockMvc.perform(MockMvcRequestBuilders.post("/ms/tunnel")
                        .contentType("application/json")
                        .content("{\"localPort\":8080,\"name\":\"web-test\",\"provider\":\"nope\"}"))
                .andExpect(MockMvcResultMatchers.status().is2xxSuccessful())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("C0400S0000"))
                .andReturn();
    }

    private TunnelView create() throws Exception {
        // 注册表里有两个提供方：必须显式指定 provider，否则统一服务拒绝创建。
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/ms/tunnel")
                        .contentType("application/json")
                        .content("{\"localPort\":8080,\"name\":\"web-test\",\"mode\":\"http\",\"provider\":\"tunnel-test\"}"))
                .andExpect(MockMvcResultMatchers.status().is2xxSuccessful())
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        // 统一接口返回 ReturnResult<TunnelView> 信封，实际映射在 data 里。
        ReturnResult<TunnelView> r = objectMapper.readValue(body,
                new TypeReference<ReturnResult<TunnelView>>() {});
        return r.getData();
    }

    /**
     * 方法未提供文档。
     */
    private TunnelView getMap(String id) throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/ms/tunnel/" + id))
                .andExpect(MockMvcResultMatchers.status().is2xxSuccessful())
                .andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        ReturnResult<TunnelView> r = objectMapper.readValue(body,
                new TypeReference<ReturnResult<TunnelView>>() {});
        return r.getData();
    }
}
