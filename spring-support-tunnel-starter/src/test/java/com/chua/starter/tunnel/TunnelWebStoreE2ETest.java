package com.chua.starter.tunnel;

import com.chua.starter.tunnel.support.TestTunnelProvider;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 映射存储初始化态：应用启动后，空目录下没有映射。
 *
 * <p>单独的类、单独的 Spring 容器与单独的存储目录，这样即使
 * {@link TunnelWebE2ETest} 在同一 JVM 中运行，这里看到的也是原生环境。</p>
 * @author CH
 * @since 4.0.0.51
 */
@TestPropertySource(properties = "plugin.tunnel.storeDir=target/store-list-e2e")
@EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {TunnelWebStoreE2ETest.StoreTestConfig.class, TunnelAutoConfiguration.class})
@AutoConfigureMockMvc
class TunnelWebStoreE2ETest {

    private static final Path STORE_DIR = Path.of(System.getProperty("user.dir")).resolve("target/store-list-e2e");

    /**
     * 在 Spring 启动前置空映射目录，保证 {@code storeDir} 读到的是无尘状态。
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
    static class StoreTestConfig {

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
    void shouldListNoMapAtFirst() throws Exception {
        // 目录状态：存储目录里没有任何 .json 文件。
        assertThat(Files.list(STORE_DIR).toArray()).isEmpty();

        mockMvc.perform(MockMvcRequestBuilders.get("/ms/tunnel"))
                .andExpect(MockMvcResultMatchers.status().is2xxSuccessful())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("00000"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data").isArray())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data").isEmpty());
    }
}
