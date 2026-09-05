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
 * 只读优先于所有权限真实测试（READ_ONLY 角色禁止写操作）。
 *
 * <p>策略：PermissionInterceptor 在权限校验前检查 READ_ONLY 角色——拥有该角色的账号
 * 对非安全方法（POST/PUT/DELETE/PATCH）一律拒绝（403），GET 等只读操作放行；
 * 该限制优先于普通权限、管理员与超级管理员旁路。</p>
 *
 * @author CH
 * @since 2026/09/04
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReadOnlyRealTest {

    @Autowired
    private MockMvc mockMvc;

    private MockHttpSession sessionWith(Set<String> roles, Set<String> permissions) {
        UserResume user = new UserResume();
        user.setUserId("1");
        user.setUsername("readonly-user");
        user.setUid("1");
        user.setRoles(roles);
        if (permissions != null) {
            user.setPermission(permissions);
        }
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userInfo", user);
        return session;
    }

    /** 只读角色 + 写权限：GET 放行、POST 拒绝（只读优先于权限码） */
    @Test
    void readOnlyRoleBlocksWriteButAllowsRead() throws Exception {
        MockHttpSession session = sessionWith(Set.of("READ_ONLY"), Set.of("user:query", "user:delete"));
        mockMvc.perform(get("/api/permission/query").session(session)).andExpect(status().isOk());
        mockMvc.perform(post("/api/permission/delete").session(session)).andExpect(status().isForbidden());
    }

    /** 非只读角色：正常权限流（持有 user:delete -> POST 放行） */
    @Test
    void normalRoleAllowsWriteWithPermission() throws Exception {
        MockHttpSession session = sessionWith(Set.of("USER"), Set.of("user:delete"));
        mockMvc.perform(post("/api/permission/delete").session(session)).andExpect(status().isOk());
    }

    /** 只读 + 超管：写操作仍拒绝（只读优先于所有权限，超管也不例外） */
    @Test
    void readOnlyOverridesSuperAdmin() throws Exception {
        MockHttpSession session = sessionWith(Set.of("READ_ONLY", "SUPER_ADMIN"), null);
        mockMvc.perform(post("/api/permission/delete").session(session)).andExpect(status().isForbidden());
    }
}
