package com.chua.starter.test;

import com.chua.starter.common.support.api.feature.ApiFeatureConfiguration;
import com.chua.starter.console.scan.AnnotationSwitchScanner;
import com.chua.starter.console.store.ConsoleSqliteStore;
import com.chua.starter.console.store.ConsoleStartupResetter;
import com.chua.starter.oauth.client.support.user.UserResume;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理控制台真实测试（接口级）：FTL 页面鉴权（@Permission console:manage）、
 * 页面临时调整覆盖 @ApiFeature 生效（经 console API -> SQLite -> 拦截器判定）、键值 CRUD、
 * 注解指纹比对启动重置（ApplicationRunner 机制，不可经 HTTP 驱动——直接调用并注明原因）。
 *
 * @author CH
 * @since 2026/09/04
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ApiFeatureConfiguration.class)
class ConsoleRealTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConsoleSqliteStore store;

    @Autowired
    private ConsoleStartupResetter resetter;

    @Autowired
    private AnnotationSwitchScanner scanner;

    private MockHttpSession sessionWith(Set<String> permissions) {
        UserResume user = new UserResume();
        user.setUserId("1");
        user.setUsername("console-admin");
        user.setUid("1");
        user.setRoles(Set.of("USER"));
        user.setPermission(permissions);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userInfo", user);
        return session;
    }

    /** FTL 页面鉴权：无 console:manage 权限 403；持有权限 200 且渲染页面 */
    @Test
    void pageAuthEnforced() throws Exception {
        mockMvc.perform(get("/console/features").session(sessionWith(Set.of("user:query"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/console/features").session(sessionWith(Set.of("console:manage"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("功能开关")));
    }

    /** 覆盖生效（端到端）：console 把默认禁用的 beta-feature 覆盖为启用 -> 原 503 端点变 200 */
    @Test
    void featureOverrideTakesEffect() throws Exception {
        // 基线：未覆盖时 503
        mockMvc.perform(get("/api/feature/beta").session(sessionWith(Set.of("console:manage"))))
                .andExpect(status().isServiceUnavailable());
        // 控制台覆盖为启用（写入 SQLite）
        mockMvc.perform(post("/console/api/features").session(sessionWith(Set.of("console:manage")))
                        .param("featureId", "beta-feature")
                        .param("enabled", "true"))
                .andExpect(status().is3xxRedirection());
        // 覆盖生效：拦截器经 ConsoleOverrideProvider 读到启用 -> 200
        mockMvc.perform(get("/api/feature/beta").session(sessionWith(Set.of("console:manage"))))
                .andExpect(status().isOk());
        // 清理：移除覆盖恢复 503
        mockMvc.perform(post("/console/api/features/delete").session(sessionWith(Set.of("console:manage")))
                        .param("featureId", "beta-feature"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/api/feature/beta").session(sessionWith(Set.of("console:manage"))))
                .andExpect(status().isServiceUnavailable());
    }

    /** 灰度覆盖生效（端到端）：users 白名单未命中 -> console 覆盖 percentage=100 -> X-Gray-Hit 命中；清理后恢复 */
    @Test
    void grayOverrideTakesEffect() throws Exception {
        MockHttpSession session = sessionWith(Set.of("console:manage"));
        // 基线：会话用户非 gray-user -> users 维度未命中（无 X-Gray-Hit 头）
        mockMvc.perform(get("/api/api-version/preview").session(session))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Gray-Hit"));
        // 控制台覆盖灰度（演示端点 @ApiGray 注解 value 默认空串）：percentage=100
        mockMvc.perform(post("/console/api/grays").session(session)
                        .param("pattern", "")
                        .param("percentage", "100"))
                .andExpect(status().is3xxRedirection());
        // 覆盖生效：拦截器动态代理合并后 percentage=100 恒命中 -> X-Gray-Hit: true
        mockMvc.perform(get("/api/api-version/preview").session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Gray-Hit", "true"));
        // 清理：移除覆盖恢复未命中（避免污染同上下文的其他灰度用例）
        mockMvc.perform(post("/console/api/grays/delete").session(session)
                        .param("pattern", ""))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/api/api-version/preview").session(session))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Gray-Hit"));
    }

    /** 键值设置 CRUD（HTTP 接口）+ SQLite 持久化 */
    @Test
    void kvCrud() throws Exception {
        mockMvc.perform(post("/console/api/kv").session(sessionWith(Set.of("console:manage")))
                        .param("key", "console.test.key")
                        .param("value", "v1"))
                .andExpect(status().is3xxRedirection());
        assertEquals("v1", store.getKv("console.test.key"), "键值应写入 SQLite");

        mockMvc.perform(post("/console/api/kv/delete").session(sessionWith(Set.of("console:manage")))
                        .param("key", "console.test.key"))
                .andExpect(status().is3xxRedirection());
        assertNull(store.getKv("console.test.key"), "键值删除应持久化");
    }

    /**
     * 注解指纹比对启动重置（ApplicationRunner 于上下文就绪后执行，不可经 HTTP 驱动——
     * 直接调用并注明原因）：注解指纹变化 -> 覆盖库清空；键值属用户数据保留；落新指纹。
     */
    @Test
    void fingerprintChangeResetsOverrides() {
        store.upsertFeatureOverride("beta-feature", false, null, -1);
        store.setKv("console.test.keep", "keep");

        // 模拟注解更新：篡改存储指纹（与当前扫描指纹不一致）
        store.setFingerprint("stale-fingerprint");
        resetter.run(new DefaultApplicationArguments(new String[0]));

        assertNull(store.getFeatureOverride("beta-feature"), "指纹变化应清空功能覆盖");
        assertEquals("keep", store.getKv("console.test.keep"), "键值属用户数据不应被重置");
        assertEquals(scanner.scan().getFingerprint(), store.getFingerprint(), "重置后应落新指纹");

        store.deleteKv("console.test.keep");
    }
}
