package com.chua.starter.test;

import com.chua.starter.oauth.client.support.user.UserResume;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
* 权限攻击面真实测试：路径遍历 / 方法覆盖 / 编码大小写绕过 / 越权等攻击向量
* 均不得绕过权限拦截（无授权一律 403/404/405，绝不 200）。
*
* @author CH
* @since 2026/09/04
 */
@SpringBootTest
@AutoConfigureMockMvc
class PermissionAttackSurfaceTest {

    @Autowired
    private MockMvc mockMvc; // mockmvc

    /**
    * 会话with。
    * @param roles 角色
    * @param permissions 权限
    * @return 会话with的结果
     */
    private MockHttpSession sessionWith(Set<String> roles, Set<String> permissions) {
        UserResume user = new UserResume();
        user.setUserId("1");
        user.setUsername("attacker");
        user.setUid("1");
        user.setRoles(roles);
        if (permissions != null) {
            user.setPermission(permissions);
        }
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userInfo", user);
        return session;
    }

    /** 路径遍历：编码斜杠/点号拼接不得命中受保护接口（归一化后无映射 -> 404） */
    @Test
    void pathTraversalTriesDoNotBypass() throws Exception {
        mockMvc.perform(get("/api/permission/%2e%2e/permission/query")
                        .session(sessionWith(Set.of("USER"), Set.of("user:query"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/%2e%2e/api/permission/query")
                        .session(sessionWith(Set.of("USER"), Set.of("user:query"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/permission/query/../query")
                        .session(sessionWith(Set.of("USER"), Set.of("user:query"))))
                .andExpect(status().isNotFound());
    }

    /** 方法覆盖：X-HTTP-方法-Override 不被框架识别——获取 访问写接口 405，写操作不执行 */
    @Test
    void methodOverrideIgnored() throws Exception {
        mockMvc.perform(get("/api/permission/delete")
                        .header("X-HTTP-Method-Override", "POST")
                        .session(sessionWith(Set.of("USER"), Set.of("user:delete"))))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/api/permission/query")
                        .header("X-HTTP-Method-Override", "GET")
                        .session(sessionWith(Set.of("USER"), Set.of("user:query"))))
                .andExpect(status().isMethodNotAllowed());
    }

    /** 编码/大小写/斜杠变体：无授权用户一律不得 200（未映射 404 / 归一化命中受保护端点 403） */
    @Test
    void encodingCaseTricksDoNotBypass() throws Exception {
        MockHttpSession unauthorized = sessionWith(Set.of("USER"), Set.of());
        mockMvc.perform(get("/api/permission/Query").session(unauthorized))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/permission/%2571uery").session(unauthorized))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/permission/query/").session(unauthorized))
                .andExpect(status().is4xxClientError());
        // 双斜杠归一化命中真实端点：权限检查仍生效 -> 无授权 403（非绕过）
        mockMvc.perform(get("/api//permission/query").session(unauthorized))
                .andExpect(status().isForbidden());
        // 对照：授权用户经归一化路径正常访问（证明是合法访问而非绕过）
        mockMvc.perform(get("/api//permission/query").session(sessionWith(Set.of("USER"), Set.of("user:query"))))
                .andExpect(status().isOk());
    }

    /** 越权：仅持按钮权限码的账号尝试写接口 -> 403（权限码不匹配即拒绝） */
    @Test
    void privilegeEscalationDenied() throws Exception {
        mockMvc.perform(post("/api/permission/delete")
                        .session(sessionWith(Set.of("USER"), Set.of("btn:user:add"))))
                .andExpect(status().isForbidden());
        // 无任何权限码的本地联调语义下也不得借越（无用户上下文注入的是 admin，此处显式受限用户）
        mockMvc.perform(post("/api/permission/delete")
                        .session(sessionWith(Set.of("USER"), Set.of())))
                .andExpect(status().isForbidden());
    }
}
