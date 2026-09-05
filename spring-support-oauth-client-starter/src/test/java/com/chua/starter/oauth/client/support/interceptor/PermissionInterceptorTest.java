package com.chua.starter.oauth.client.support.interceptor;

import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.oauth.client.support.exception.OauthException;
import com.chua.starter.oauth.client.support.execute.AuthSessionUtils;
import com.chua.starter.oauth.client.support.user.UserResume;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 接口权限 + 按钮权限测试（{@link PermissionInterceptor}）。
 *
 * <p>覆盖：权限码放行/拒绝、角色放行/拒绝、管理员与超级管理员旁路、类级
 * {@code @Permission}、本地联调（无用户上下文自动注入 admin）、按钮权限
 * （按钮权限码即接口权限码的复用，前端按钮绑定同一权限码）。</p>
 *
 * @author CH
 * @since 2026/09/04
 */
class PermissionInterceptorTest {

    /** 被测目标：接口权限 + 按钮权限（按钮权限即接口权限码的一种应用形态） */
    public static class TargetService {

        @Permission(value = {"user:query"}, role = {"USER"})
        public String query() {
            return "query-ok";
        }

        @Permission("user:delete")
        public String delete() {
            return "delete-ok";
        }

        /** 按钮权限：前端按钮绑定同一权限码，后端接口同样受拦截器保护 */
        @Permission("btn:user:add")
        public String addButton() {
            return "add-ok";
        }

        @Permission(role = "MANAGER")
        public String managerOnly() {
            return "manager-ok";
        }
    }

    /** 类级 @Permission：类上注解对类内所有方法生效 */
    @Permission("class:read")
    public static class ClassLevelTarget {
        public String read() {
            return "read-ok";
        }
    }

    @BeforeEach
    void setupRequestContext() {
        // AuthSessionUtils 基于 HttpServletRequest 会话存取用户信息：建立请求上下文
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void cleanup() {
        // 清空请求上下文与 ThreadLocal 用户信息，避免测试间串扰
        RequestContextHolder.resetRequestAttributes();
        AuthSessionUtils.setUserInfo(null);
    }

    private PermissionInterceptor newInterceptor() {
        PermissionInterceptor interceptor = new PermissionInterceptor();
        try {
            interceptor.afterPropertiesSet();
        } catch (Exception e) {
            throw new IllegalStateException("初始化权限拦截器失败", e);
        }
        return interceptor;
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(T target, PermissionInterceptor interceptor) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvisor(interceptor);
        return (T) factory.getProxy();
    }

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

    /** 权限码匹配：用户持有 user:query -> 放行 */
    @Test
    void allowWithMatchingPermission() {
        AuthSessionUtils.setUserInfo(user(Set.of("USER"), Set.of("user:query")));
        TargetService service = proxy(new TargetService(), newInterceptor());
        assertEquals("query-ok", service.query(), "持有权限码应放行");
    }

    /** 权限码不匹配：用户仅 user:query -> 调用 user:delete 拒绝 */
    @Test
    void denyWithoutPermission() {
        AuthSessionUtils.setUserInfo(user(Set.of("USER"), Set.of("user:query")));
        TargetService service = proxy(new TargetService(), newInterceptor());
        assertThrows(OauthException.class, service::delete, "缺少权限码应拒绝");
    }

    /** 角色匹配：MANAGER 角色访问 MANAGER-only 接口 -> 放行 */
    @Test
    void allowWithMatchingRole() {
        AuthSessionUtils.setUserInfo(user(Set.of("MANAGER"), null));
        TargetService service = proxy(new TargetService(), newInterceptor());
        assertEquals("manager-ok", service.managerOnly(), "角色匹配应放行");
    }

    /** 角色不匹配：USER 角色访问 MANAGER-only 接口 -> 拒绝 */
    @Test
    void denyWithWrongRole() {
        AuthSessionUtils.setUserInfo(user(Set.of("USER"), Set.of("user:query")));
        TargetService service = proxy(new TargetService(), newInterceptor());
        assertThrows(OauthException.class, service::managerOnly, "角色不匹配应拒绝");
    }

    /** 超级管理员旁路：无视 @Permission 全部放行 */
    @Test
    void superAdminBypass() {
        AuthSessionUtils.setUserInfo(user(Set.of("SUPER_ADMIN"), null));
        TargetService service = proxy(new TargetService(), newInterceptor());
        assertEquals("delete-ok", service.delete(), "超级管理员应放行任意权限");
        assertEquals("manager-ok", service.managerOnly(), "超级管理员应放行任意角色");
    }

    /** 普通管理员旁路：非超管专属接口放行 */
    @Test
    void adminBypassExceptSuperAdminOnly() {
        AuthSessionUtils.setUserInfo(user(Set.of("ADMIN"), null));
        TargetService service = proxy(new TargetService(), newInterceptor());
        assertEquals("delete-ok", service.delete(), "普通管理员应放行一般权限接口");
    }

    /** 本地联调：无用户上下文时注入本地 admin 上下文 -> 放行（配合 whitelist 防未鉴权流量） */
    @Test
    void localDebugInjectsAdminContext() {
        TargetService service = proxy(new TargetService(), newInterceptor());
        assertEquals("delete-ok", service.delete(), "本地联调应注入 admin 上下文放行");
    }

    /** 类级 @Permission：无 class:read 权限拒绝；持有后放行 */
    @Test
    void classLevelPermissionEnforced() {
        AuthSessionUtils.setUserInfo(user(Set.of("USER"), Set.of("user:query")));
        ClassLevelTarget target = proxy(new ClassLevelTarget(), newInterceptor());
        assertThrows(OauthException.class, target::read, "类级权限码缺失应拒绝");

        AuthSessionUtils.setUserInfo(user(Set.of("USER"), Set.of("class:read")));
        assertEquals("read-ok", target.read(), "持有类级权限码应放行");
    }

    /** 按钮权限：按钮权限码（btn:user:add）与接口权限码同机制——持码放行、缺码拒绝 */
    @Test
    void buttonPermissionSameAsInterfacePermission() {
        AuthSessionUtils.setUserInfo(user(Set.of("USER"), Set.of("btn:user:add")));
        TargetService service = proxy(new TargetService(), newInterceptor());
        assertEquals("add-ok", service.addButton(), "按钮权限码应放行");

        AuthSessionUtils.setUserInfo(user(Set.of("USER"), Set.of("user:query")));
        assertThrows(OauthException.class, service::addButton, "缺少按钮权限码应拒绝");
    }
}
