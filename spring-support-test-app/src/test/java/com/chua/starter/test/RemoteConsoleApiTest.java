package com.chua.starter.test;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 远控管理台后端接口真实测试（面向接口——mockmvc 驱动 HTTP 端点）。
 *
 * <p>覆盖：配置保存/回显（/api/remote/config）、被控端列表（/api/remote/agents）、
 * 接入码保存/回显（/api/远程/access-编码）。测试为真实 Boot 集成，
 * 持久化为文件（运行目录 远程-*.json——运行时数据）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@SpringBootTest
@AutoConfigureMockMvc
class RemoteConsoleApiTest {

    @Autowired
    private MockMvc mockMvc; // mockmvc

    /** 配置：保存后回显一致 */
    @Test
    void config_save_and_echo() throws Exception {
        mockMvc.perform(post("/api/remote/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gatewayUrl\":\"tcp://127.0.0.1:9000\",\"targetAgentId\":\"t-agent\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        mockMvc.perform(get("/api/remote/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gatewayUrl").value("tcp://127.0.0.1:9000"))
                .andExpect(jsonPath("$.targetAgentId").value("t-agent"));
    }

    /** 被控端：列表返回（种子或已保存数据——结构断言） */
    @Test
    void agents_list_shape() throws Exception {
        mockMvc.perform(get("/api/remote/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    /** 接入码：保存后回显一致 */
    @Test
    void access_codes_save_and_echo() throws Exception {
        mockMvc.perform(post("/api/remote/access-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"code\":\"TEST-CODE-1\",\"type\":\"ctrl-token\",\"status\":\"on\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        mockMvc.perform(get("/api/remote/access-codes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("TEST-CODE-1"))
                .andExpect(jsonPath("$[0].status").value("on"));
    }
}
