package com.chua.starter.test;

import com.chua.ueba.support.dto.UebaResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UEBA 控制器真实集成测试。
 * <p>
   * 启动真实 Spring 上下文（含 uebaauto配置），通过 mockmvc 打真实 HTTP 请求
   * 验证 /api/ueba/分析 端点能正常分析正常流量与滥用流量。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@SpringBootTest
@AutoConfigureMockMvc
class UebaControllerRealTest {

    /** Mock MVC 客户端（注入真实 dispatcherservlet） */
    @Autowired
    private MockMvc mockMvc; // mockmvc

    /**
      * 正常 获取 /索引 请求应返回 NORMAL 或低风险等级。
     */
    @Test
    void analyzeNormalEvent() throws Exception {
        String body = """
                {
                  "ip": "10.0.0.1",
                  "path": "/index",
                  "method": "GET",
                  "status": 200,
                  "userAgent": "Mozilla/5.0",
                  "responseTime": 50
                }
                """;
        mockMvc.perform(post("/api/ueba/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskLevel").isNotEmpty())
                .andExpect(jsonPath("$.entityId").isNotEmpty())
                .andExpect(jsonPath("$.riskScore").isNumber())
                .andExpect(jsonPath("$.ipAnomaly.level").isNotEmpty());
    }

    /**
     * 滥用流量（POST /admin 500）应返回非 NORMAL 风险等级。
     */
    @Test
    void analyzeAbuseEvent() throws Exception {
        String body = """
                {
                  "ip": "203.0.113.9",
                  "path": "/admin",
                  "method": "POST",
                  "status": 500,
                  "userAgent": "python-requests/99.1",
                  "responseTime": 3000
                }
                """;
        mockMvc.perform(post("/api/ueba/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskLevel").isNotEmpty())
                .andExpect(jsonPath("$.riskScore").value(org.hamcrest.Matchers.greaterThan(0.0)));
    }

    /**
     * 缺失字段时接口不应抛异常（容错）。
     */
    @Test
    void analyzePartialEvent() throws Exception {
        String body = """
                {
                  "ip": "10.0.0.99"
                }
                """;
        mockMvc.perform(post("/api/ueba/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskLevel").isNotEmpty());
    }
}