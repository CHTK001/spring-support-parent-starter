package com.chua.starter.hotsport.support.controller;

import com.chua.starter.hotsport.support.HotsportApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * APM REST 接口契约测试 — MockMvc 全上下文加载。
 *
 * <p>验证 5 个读接口 + SSE 端点的响应结构与 {@code ReturnResult} 契约
 * （{@code code=00000 / data / msg}）一致，前端可稳定解包。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@SpringBootTest(classes = HotsportApplication.class)
@AutoConfigureMockMvc
class ApmControllerTest {

    /** MockMvc。 */
    @Autowired
    private MockMvc mockMvc;

    @Test
    void statsShouldReturnReturnResultWithAgentFlags() throws Exception {
        mockMvc.perform(get("/api/apm/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.agentActive").isBoolean())
                .andExpect(jsonPath("$.data.transmissionCount").isNumber())
                .andExpect(jsonPath("$.data.leakCount").isNumber())
                .andExpect(jsonPath("$.data.errorCount").isNumber())
                .andExpect(jsonPath("$.data.traceCount").isNumber())
                .andExpect(jsonPath("$.data.dependencyCount").isNumber());
    }

    @Test
    void transmissionsShouldReturnPagedShape() throws Exception {
        mockMvc.perform(get("/api/apm/transmissions").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.limit").value(3))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void transmissionsTimeRangeFilterAccepted() throws Exception {
        mockMvc.perform(get("/api/apm/transmissions")
                        .param("startTime", "0")
                        .param("endTime", "9999999999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void dependenciesShouldReturnShape() throws Exception {
        mockMvc.perform(get("/api/apm/dependencies").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void leaksShouldReturnPagedShape() throws Exception {
        mockMvc.perform(get("/api/apm/leaks").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void logsShouldReturnPagedShape() throws Exception {
        mockMvc.perform(get("/api/apm/logs").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void agentStatusShouldReturnStatsShape() throws Exception {
        mockMvc.perform(get("/api/apm/agent/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.agentActive").isBoolean());
    }

    @Test
    void sseStreamEndpointShouldBeAvailable() throws Exception {
        // SSE 端点返回 200 + text/event-stream（流不读体，仅验证路由可达与内容类型）
        mockMvc.perform(get("/api/apm/stream").param("intervalMs", "2000"))
                .andExpect(request -> {
                    int code = request.getResponse().getStatus();
                    String ct = String.valueOf(request.getResponse().getContentType());
                    assertThat(code).isEqualTo(200);
                    assertThat(ct).containsIgnoringCase("text/event-stream");
                });
    }
}
