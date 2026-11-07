package com.chua.starter.lowcode;

import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.common.support.oauth.CurrentUser;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 测试用认证上下文：给审计字段填充提供确定的登录用户。
 *
 * <p>放在独立的 {@code @TestConfiguration} 里被各测试类共享，避免每个测试类各写一份
 * 同名 bean 导致上下文复用时 BeanDefinitionOverrideException。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@TestConfiguration
public class StubAuthConfiguration {

    /**
     * 可切换当前用户的认证服务替身。
     *
     * @return 替身
     */
    @Bean
    @Primary
    public StubAuthService stubAuthService() {
        return new StubAuthService();
    }

    /**
     * 返回测试指定用户的 AuthService。
     */
    public static class StubAuthService implements AuthService {

        private CurrentUser user;

        /**
         * 设置当前用户。
         *
         * @param user 用户，null 表示未登录
         */
        public void setUser(CurrentUser user) {
            this.user = user;
        }

        @Override
        public CurrentUser getCurrentUser() {
            return user;
        }

        /**
         * 构造一个登录用户。
         *
         * @param name 用户名
         * @param id   用户ID
         * @return 用户
         */
        public static CurrentUser user(String name, String id) {
            CurrentUser u = new CurrentUser();
            u.setName(name);
            u.setUserId(id);
            return u;
        }
    }
}
