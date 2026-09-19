package com.chua.starter.test.config;

import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.common.support.oauth.CurrentUser;
import org.springframework.stereotype.Component;

/**
 * 可控的当前用户提供者：测试按场景切换 {@link CurrentUser}。
 *
 * <p>作为名为 {@code authService} 的 Bean 提供，供数据权限拦截器
 * （{@code MybatisPlusDataPermissionHandler} 的 认证服务供应商）读取。</p>
 *
 * @author CH
 * @since 2026/09/04
 */
@Component("authService")
public class DataScopeAuthService implements AuthService {

    /** 当前用户持有器（测试按用例设置） */
    private static volatile CurrentUser currentUser;

    /**
     * 设置当前用户。
     * @param user 用户
     */
    public static void setCurrentUser(CurrentUser user) {
        currentUser = user;
    }

    @Override
    public CurrentUser getCurrentUser() {
        return currentUser;
    }
}
