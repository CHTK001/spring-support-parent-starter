package com.chua.starter.test;

import com.chua.starter.common.support.api.configuration.ApiConfiguration;
import com.chua.starter.common.support.api.control.ApiVersionRequestMappingHandlerMapping;
import com.chua.starter.common.support.api.feature.ApiFeatureConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * api 版本/灰度真实测试（@SpringBootTest + MockMvc + 真实版本映射与 ApiControlInterceptor）。
 *
 * <p>版本路由经查询参数/请求头验证；灰度经会话用户名单验证（名单内 200、名单外 403）。
 * {@code ApiConfiguration}/{@code ApiFeatureConfiguration} 未自动注册，此处显式导入
 * （框架观察：api 增强配置需应用侧 @Import 或自行注册）。</p>
 *
 * @author CH
 * @since 2026/09/04
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ApiConfiguration.class, ApiFeatureConfiguration.class})
class ApiVersionGrayRealTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired(required = false)
    private ApiVersionRequestMappingHandlerMapping versionMapping;

    /** 诊断：自定义版本映射 Bean 是否注册成功 */
    @Test
    void versionMappingRegistered() {
        assertNotNull(versionMapping, "ApiVersionRequestMappingHandlerMapping 应注册为 Bean");
    }

    /** 软灰度命中：username 在灰度名单（gray-user）内 -> 放行并返回 X-Gray-Hit 响应头 */
    @Test
    void grayUserInListAllowed() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("username", "gray-user");
        session.setAttribute("userId", "100");
        mockMvc.perform(get("/api/api-version/preview").session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Gray-Hit", "true"))
                .andExpect(jsonPath("$.result").value("gray-preview-ok"));
    }

    /** 软灰度未命中：username 不在名单内 -> 照常放行（软灰度不阻断） */
    @Test
    void grayUserNotInListSoftPasses() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("username", "ordinary-user");
        session.setAttribute("userId", "200");
        mockMvc.perform(get("/api/api-version/preview").session(session))
                .andExpect(status().isOk());
    }

    /** 强制灰度命中：名单内用户 -> 放行 */
    @Test
    void grayForceUserInListAllowed() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("username", "gray-user");
        session.setAttribute("userId", "100");
        mockMvc.perform(get("/api/api-version/preview-force").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("gray-force-ok"));
    }

    /** 强制灰度未命中：名单外用户 -> 403 拒绝（未获邀体验） */
    @Test
    void grayForceUserNotInListRejected() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("username", "ordinary-user");
        session.setAttribute("userId", "200");
        mockMvc.perform(get("/api/api-version/preview-force").session(session))
                .andExpect(status().isForbidden());
    }
}
