package com.chua.starter.test;

import com.chua.starter.common.support.api.feature.ApiFeatureConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * api 功能开关（@api特征）真实测试：经真实 apicontrol拦截器 拦截链验证
 * 开关开放/关闭的放行与拒绝路径。
 *
 * @author CH
 * @since 2026/09/04
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ApiFeatureConfiguration.class)
class ApiFeatureRealTest {

    @Autowired
    private MockMvc mockMvc; // mockmvc

    /** 实验性功能（默认已启用=false）：拦截返回 503 + 未开放提示（申报结果.msg 字段） */
    @Test
    void disabledFeatureReturns503() throws Exception {
        mockMvc.perform(get("/api/feature/beta"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.msg").value("此功能暂未开放"));
    }

    /** 常规功能（默认已启用=true）：正常放行 200 */
    @Test
    void enabledFeatureReturns200() throws Exception {
        mockMvc.perform(get("/api/feature/open"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("open-ok"));
    }
}
