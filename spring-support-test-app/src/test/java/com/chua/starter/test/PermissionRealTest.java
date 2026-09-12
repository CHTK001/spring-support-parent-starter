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
* 接口权限 + 按钮权限真实测试（@springboot测试 + mockmvc + 真实 权限拦截器）。
*
* <p>用户上下文经会话注入（{@code AuthSessionUtils.SESSION_USER_INFO}），
* 与生产请求路径一致；拒绝场景由 {@code OauthExceptionAdvice} 映射为 403。</p>
*
* @author CH
* @since 2026/09/04
 */
@SpringBootTest
@AutoConfigureMockMvc
class PermissionRealTest {

    @Autowired
    private MockMvc mockMvc; // mockmvc

    /**
    * 会话with。
    * @param user 用户
    * @return 会话with的结果
     */
    private MockHttpSession sessionWith(UserResume user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userInfo", user);
        return session;
    }

    /**
    * 用户。
    * @param roles 角色
    * @param permissions 权限
    * @return 用户的结果
     */
    private static UserResume user(Set<String> roles, Set<String> permissions) {
        UserResume user = new UserResume();
        user.setUserId("1");
        user.setUsername("tester");
        user.setUid("1");
        user.setRoles(roles);
        if (permissions != null) {
            user.setPermission(permissions);
        }
        return user;
    }

    /** 本地联调：无会话用户 -> 注入本地 admin 上下文 -> 全部放行（配合 whitelist 防未鉴权流量） */
    @Test
    void localDebugAllowsWithoutUser() throws Exception {
        mockMvc.perform(get("/api/permission/query")).andExpect(status().isOk());
        mockMvc.perform(post("/api/permission/delete")).andExpect(status().isOk());
        mockMvc.perform(post("/api/permission/button-add")).andExpect(status().isOk());
    }

    /** 接口权限：持有权限码 -> 放行 */
    @Test
    void allowWithPermission() throws Exception {
        MockHttpSession session = sessionWith(user(Set.of("USER"), Set.of("user:query", "user:delete")));
        mockMvc.perform(get("/api/permission/query").session(session)).andExpect(status().isOk());
        mockMvc.perform(post("/api/permission/delete").session(session)).andExpect(status().isOk());
    }

    /** 接口权限：缺权限码 -> 403 拒绝 */
    @Test
    void denyWithoutPermission() throws Exception {
        MockHttpSession session = sessionWith(user(Set.of("USER"), Set.of("user:query")));
        mockMvc.perform(get("/api/permission/query").session(session)).andExpect(status().isOk());
        mockMvc.perform(post("/api/permission/delete").session(session)).andExpect(status().isForbidden());
    }

    /** 按钮权限：按钮权限码（btn:用户:添加）与接口权限码同机制——持码放行、缺码 403 */
    @Test
    void buttonPermission() throws Exception {
        MockHttpSession withButton = sessionWith(user(Set.of("USER"), Set.of("btn:user:add")));
        mockMvc.perform(post("/api/permission/button-add").session(withButton)).andExpect(status().isOk());

        MockHttpSession withoutButton = sessionWith(user(Set.of("USER"), Set.of("user:query")));
        mockMvc.perform(post("/api/permission/button-add").session(withoutButton)).andExpect(status().isForbidden());
    }
}
