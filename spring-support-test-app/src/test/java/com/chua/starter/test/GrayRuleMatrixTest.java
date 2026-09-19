package com.chua.starter.test;

import com.chua.starter.common.support.api.feature.ApiFeatureConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 灰度规则矩阵真实测试：覆盖 {@code @ApiGray} 除 用户 名单外的全部匹配维度
 * （角色 角色 / ips IP / 头部 请求头 / rule spel / percentage 百分比），
 * 命中返回 {@code X-Gray-Hit: true} 响应头。
 *
 * @author CH
 * @since 2026/09/04
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ApiFeatureConfiguration.class)
class GrayRuleMatrixTest {

    @Autowired
    private MockMvc mockMvc; // mockmvc

    /**
     * 会话。
     * @param username 用户名
     * @param roles 角色
     * @return 会话的结果
     */
    private static MockHttpSession session(String username, List<String> roles) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("username", username);
        session.setAttribute("userId", "100");
        if (roles != null) {
            session.setAttribute("roles", roles);
        }
        return session;
    }

    /**
     * 角色名单：用户角色含 gray-角色 -> 命中；不含 -> 未命中
    */
    @Test
    void rolesDimension() throws Exception {
        mockMvc.perform(get("/api/api-version/preview-roles").session(session("any-user", List.of("gray-role"))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Gray-Hit", "true"));
        mockMvc.perform(get("/api/api-version/preview-roles").session(session("any-user", List.of("USER"))))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Gray-Hit"));
    }

    /**
     * IP 名单：X-远期-For 命中（ip工具 优先取该头）-> 命中；其他 IP -> 未命中
    */
    @Test
    void ipsDimension() throws Exception {
        mockMvc.perform(get("/api/api-version/preview-ips").session(session("any-user", null))
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Gray-Hit", "true"));
        mockMvc.perform(get("/api/api-version/preview-ips").session(session("any-user", null))
                        .with(request -> {
                            request.setRemoteAddr("8.8.8.8");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Gray-Hit"));
    }

    /**
     * 请求头维度：X-Gray-头部=1 精确匹配 -> 命中；缺失/不符 -> 未命中
    */
    @Test
    void headersDimension() throws Exception {
        mockMvc.perform(get("/api/api-version/preview-headers").session(session("any-user", null))
                        .header("X-Gray-Header", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Gray-Hit", "true"));
        mockMvc.perform(get("/api/api-version/preview-headers").session(session("any-user", null)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Gray-Hit"));
    }

    /**
     * spel 规则维度：用户名 == 'spel-用户' -> 命中；其他用户 -> 未命中
    */
    @Test
    void ruleDimension() throws Exception {
        mockMvc.perform(get("/api/api-version/preview-rule").session(session("spel-user", null)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Gray-Hit", "true"));
        mockMvc.perform(get("/api/api-version/preview-rule").session(session("other-user", null)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Gray-Hit"));
    }

    /**
     * 百分比边界：100 -> 恒命中；0 -> 恒未命中（percentage<=0 跳过百分比检查）
    */
    @Test
    void percentageDimension() throws Exception {
        mockMvc.perform(get("/api/api-version/preview-percentage").session(session("any-user", null)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Gray-Hit", "true"));
        mockMvc.perform(get("/api/api-version/preview-percentage-zero").session(session("any-user", null)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Gray-Hit"));
    }
}
