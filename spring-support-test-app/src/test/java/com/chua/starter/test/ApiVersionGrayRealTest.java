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
   * api 版本/灰度真实测试（@springboot测试 + mockmvc + 真实版本映射与 apicontrol拦截器）。
   *
   * <p>版本路由经查询参数/请求头验证；灰度经会话用户名单验证（名单内 200、名单外 403）。
   * {@code ApiConfiguration}/{@code ApiFeatureConfiguration} 未自动注册，此处显式导入
   * （框架观察：api 增强配置需应用侧 @导入 或自行注册）。</p>
   *
   * @author CH
   * @since 2026/09/04
   */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ApiConfiguration.class, ApiFeatureConfiguration.class})
class ApiVersionGrayRealTest {

    @Autowired
    private MockMvc mockMvc; // mockmvc
    @Autowired
    private org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping requestMappingHandlerMapping; /
         / 请求mapping处理器mapping

      /**
       * 接管验证：实际生效的请求映射器应为版本路由映射（webmvcregistrations 接管成功）
      */
    @Test
    void mappingTakeoverVerified() {
        assertNotNull(requestMappingHandlerMapping,
                "RequestMappingHandlerMapping 应存在");
          /**
           * 断言not空。
           * @param (ApiVersionRequestMappingHandlerMapping (api版本请求mapping处理器mapping
           */
        assertNotNull((ApiVersionRequestMappingHandlerMapping) requestMappingHandlerMapping,
                "映射器应为 ApiVersionRequestMappingHandlerMapping（WebMvcRegistrations 接管）");
    }

      /**
       * 查询参数路由：api版本=1 -> v1 实现
      */
    @Test
    void routeByQueryParamV1() throws Exception {
        mockMvc.perform(get("/api/api-version/greet").param("apiVersion", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("1"));
    }

      /**
       * 查询参数路由：api版本=2 -> v2 实现
      */
    @Test
    void routeByQueryParamV2() throws Exception {
        mockMvc.perform(get("/api/api-version/greet").param("apiVersion", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("2"));
    }

      /**
       * 请求头路由：X-Api-版本: 1 -> v1 实现
      */
    @Test
    void routeByHeader() throws Exception {
        mockMvc.perform(get("/api/api-version/greet").header("X-Api-Version", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("1"));
    }

      /**
       * 特殊值：api版本=latest -> 最新版本（v2）
      */
    @Test
    void routeByLatest() throws Exception {
        mockMvc.perform(get("/api/api-version/greet").param("apiVersion", "latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("2"));
    }

      /**
       * 软灰度命中：用户名 在灰度名单（gray-用户）内 -> 放行并返回 X-Gray-Hit 响应头
      */
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

      /**
       * 软灰度未命中：用户名 不在名单内 -> 照常放行（软灰度不阻断）
      */
    @Test
    void grayUserNotInListSoftPasses() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("username", "ordinary-user");
        session.setAttribute("userId", "200");
        mockMvc.perform(get("/api/api-version/preview").session(session))
                .andExpect(status().isOk());
    }

      /**
       * 强制灰度命中：名单内用户 -> 放行
      */
    @Test
    void grayForceUserInListAllowed() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("username", "gray-user");
        session.setAttribute("userId", "100");
        mockMvc.perform(get("/api/api-version/preview-force").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("gray-force-ok"));
    }

      /**
       * 强制灰度未命中：名单外用户 -> 403 拒绝（未获邀体验）
      */
    @Test
    void grayForceUserNotInListRejected() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("username", "ordinary-user");
        session.setAttribute("userId", "200");
        mockMvc.perform(get("/api/api-version/preview-force").session(session))
                .andExpect(status().isForbidden());
    }
}
