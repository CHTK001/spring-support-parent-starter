package com.chua.starter.test;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * api 版本/灰度真实测试（@SpringBootTest + MockMvc + 真实版本映射与 ApiControlInterceptor）。
 *
 * <p>版本路由经查询参数/请求头验证；灰度经会话用户名单验证（名单内 200、名单外 403）。</p>
 *
 * @author CH
 * @since 2026/09/04
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiVersionGrayRealTest {

    @Autowired
    private MockMvc mockMvc;

    /** 查询参数路由：apiVersion=1 -> v1 实现 */
    @Test
    void routeByQueryParamV1() throws Exception {
        mockMvc.perform(get("/api/api-version/greet").param("apiVersion", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("1"));
    }

    /** 查询参数路由：apiVersion=2 -> v2 实现 */
    @Test
    void routeByQueryParamV2() throws Exception {
        mockMvc.perform(get("/api/api-version/greet").param("apiVersion", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("2"));
    }

    /** 请求头路由：X-Api-Version: 1 -> v1 实现 */
    @Test
    void routeByHeader() throws Exception {
        mockMvc.perform(get("/api/api-version/greet").header("X-Api-Version", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("1"));
    }

    /** 特殊值：apiVersion=latest -> 最新版本（v2） */
    @Test
    void routeByLatest() throws Exception {
        mockMvc.perform(get("/api/api-version/greet").param("apiVersion", "latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("2"));
    }

    /** 灰度：username 在灰度名单（gray-user）内 -> 放行 */
    @Test
    void grayUserInListAllowed() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("username", "gray-user");
        session.setAttribute("userId", "100");
        mockMvc.perform(get("/api/api-version/preview").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("gray-preview-ok"));
    }

    /** 灰度：username 不在灰度名单 -> 403 拒绝（未获邀体验） */
    @Test
    void grayUserNotInListRejected() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("username", "ordinary-user");
        session.setAttribute("userId", "200");
        mockMvc.perform(get("/api/api-version/preview").session(session))
                .andExpect(status().isForbidden());
    }
}
