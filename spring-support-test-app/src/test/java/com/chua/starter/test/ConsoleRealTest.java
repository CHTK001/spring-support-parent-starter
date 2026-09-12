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
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
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
   * 管理控制台真实测试（接口级）：FTL 页面鉴权（@权限 控制台:manage）、
   * 页面临时调整覆盖 @api特征 生效（经 控制台 API -> sqlite -> 拦截器判定）、键值 CRUD、
   * 注解指纹比对启动重置（applicationrunner 机制，不可经 HTTP 驱动——直接调用并注明原因）。
 *
 * @author CH
 * @since 2026/09/04
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ApiFeatureConfiguration.class)
class ConsoleRealTest {

 // 预置键值：验证 控制台kv环境post处理器 在上下文启动**前**读取 控制台_kv 注入
 // 环境（业务可经 @价值观/环境.获取财产 读取"运行设置"；sqlite 路径与 yaml 一致）
    static {
        /**
         * 尝试。
         * @param "" ""
         */
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:target/console-test.sqlite", "", "");
             Statement statement = connection.createStatement()) {
            statement.execute("create table if not exists console_kv (k varchar(255) primary key, v varchar(1000), updated_at timestamp default current_timestamp)");
            statement.execute("delete from console_kv where k='console.e2e.kv'");
            statement.execute("insert into console_kv(k, v) values('console.e2e.kv', 'from-sqlite')");
        } catch (Exception e) {
            /**
             * illegal状态异常。
             * @param "键值环境注入预置失败" "键值环境注入预置失败"
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("键值环境注入预置失败", e);
        }
    }

    @Autowired
    private Environment environment; // 环境

    @Autowired
    private MockMvc mockMvc; // mockmvc

    @Autowired
    private ConsoleSqliteStore store; // 存储

    @Autowired
    private ConsoleStartupResetter resetter; // resetter

    @Autowired
    private AnnotationSwitchScanner scanner; // scanner

    /**
     * 会话with。
     * @param permissions 权限
     * @return 会话with的结果
     */
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

    /** FTL 页面鉴权：无 控制台:manage 权限 403；持有权限 200 且渲染页面 */
    @Test
    void pageAuthEnforced() throws Exception {
        mockMvc.perform(get("/console/features").session(sessionWith(Set.of("user:query"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/console/features").session(sessionWith(Set.of("console:manage"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("功能开关")));
    }

    /** 覆盖生效（端到端）：控制台 把默认禁用的 beta-特征 覆盖为启用 -> 原 503 端点变 200 */
    @Test
    void featureOverrideTakesEffect() throws Exception {
        // 基线：未覆盖时 503
        mockMvc.perform(get("/api/feature/beta").session(sessionWith(Set.of("console:manage"))))
                .andExpect(status().isServiceUnavailable());
 // 控制台覆盖为启用（写入 sqlite）
        mockMvc.perform(post("/console/api/features").session(sessionWith(Set.of("console:manage")))
                        .param("featureId", "beta-feature")
                        .param("enabled", "true"))
                .andExpect(status().is3xxRedirection());
 // 覆盖生效：拦截器经 控制台override提供者 读到启用 -> 200
        mockMvc.perform(get("/api/feature/beta").session(sessionWith(Set.of("console:manage"))))
                .andExpect(status().isOk());
        // 清理：移除覆盖恢复 503
        mockMvc.perform(post("/console/api/features/delete").session(sessionWith(Set.of("console:manage")))
                        .param("featureId", "beta-feature"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/api/feature/beta").session(sessionWith(Set.of("console:manage"))))
                .andExpect(status().isServiceUnavailable());
    }

    /** 灰度覆盖生效（端到端）：用户 白名单未命中 -> 控制台 覆盖 percentage=100 -> X-Gray-Hit 命中；清理后恢复 */
    @Test
    void grayOverrideTakesEffect() throws Exception {
        MockHttpSession session = sessionWith(Set.of("console:manage"));
 // 基线：会话用户非 gray-用户 -> 用户 维度未命中（无 X-Gray-Hit 头）
        mockMvc.perform(get("/api/api-version/preview").session(session))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Gray-Hit"));
 // 控制台覆盖灰度（演示端点 @apigray 注解 价值观 默认空串）：percentage=100
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

    /** 键值环境注入（启动 e2e）：预置的 控制台_kv 行经 环境post处理器 注入 环境 */
    @Test
    void kvInjectedIntoEnvironment() {
        /**
         * 断言equals。
         * @param "from-sqlite" "从-sqlite"
         * @param environment.getProperty("console.e2e.kv" 环境.获取财产("控制台.e2e.kv"
         */
        assertEquals("from-sqlite", environment.getProperty("console.e2e.kv"),
                "启动注入的键值应可经 Environment 读取（@Value/environment.getProperty）");
    }

    /** 键值设置 CRUD（HTTP 接口）+ sqlite 持久化 */
    @Test
    void kvCrud() throws Exception {
        mockMvc.perform(post("/console/api/kv").session(sessionWith(Set.of("console:manage")))
                        .param("key", "console.test.key")
                        .param("value", "v1"))
                .andExpect(status().is3xxRedirection());
        /**
         * 断言equals。
         * @param "v1" "v1"
         * @param store.getKv("console.test.key" 存储.获取kv("控制台.测试.键"
         */
        assertEquals("v1", store.getKv("console.test.key"), "键值应写入 SQLite");

        mockMvc.perform(post("/console/api/kv/delete").session(sessionWith(Set.of("console:manage")))
                        .param("key", "console.test.key"))
                .andExpect(status().is3xxRedirection());
        /**
         * 断言空。
         * @param store.getKv("console.test.key" 存储.获取kv("控制台.测试.键"
         */
        assertNull(store.getKv("console.test.key"), "键值删除应持久化");
    }

    /**
      * 注解指纹比对启动重置（applicationrunner 于上下文就绪后执行，不可经 HTTP 驱动——
     * 直接调用并注明原因）：注解指纹变化 -> 覆盖库清空；键值属用户数据保留；落新指纹。
     */
    @Test
    void fingerprintChangeResetsOverrides() {
        store.upsertFeatureOverride("beta-feature", false, null, -1);
        store.setKv("console.test.keep", "keep");

        // 模拟注解更新：篡改存储指纹（与当前扫描指纹不一致）
        store.setFingerprint("stale-fingerprint");
        resetter.run(new DefaultApplicationArguments(new String[0]));

        /**
         * 断言空。
         * @param store.getFeatureOverride("beta-feature" 存储.获取特征override("betafeature"
         */
        assertNull(store.getFeatureOverride("beta-feature"), "指纹变化应清空功能覆盖");
        /**
         * 断言equals。
         * @param "keep" "keep"
         * @param store.getKv("console.test.keep" 存储.获取kv("控制台.测试.keep"
         */
        assertEquals("keep", store.getKv("console.test.keep"), "键值属用户数据不应被重置");
        /**
         * 断言equals。
         * @param scanner.scan( scanner.扫描(
         */
        assertEquals(scanner.scan().getFingerprint(), store.getFingerprint(), "重置后应落新指纹");

        store.deleteKv("console.test.keep");
    }
}
