package com.chua.starter.common.support.api.permission;

import com.chua.starter.common.support.oauth.AuthService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * {@link PermissionControlInterceptor} 的自动配置。
 *
 * <p>独立于 {@code ApiFeatureConfiguration}：后者要应用扫描
 * {@code com.chua.starter.common.support.api.feature} 才会装配，而各应用的
 * {@code @ComponentScan} 只覆盖自己的 {@code com.chua.starter.<模块>.support}，
 * 于是 {@code @Permission} 在业务应用里从来没有拦截器层判定。鉴权不能依赖可选的
 * 功能开关子系统，故单独注册。</p>
 *
 * <h3>配置示例</h3>
 * <pre>
 *   plugin:
 *     api:
 *       permission:
 *         enable: false   # 关闭 @Permission 拦截（默认开启）
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Configuration
@ConditionalOnProperty(name = "plugin.api.permission.enable", havingValue = "true", matchIfMissing = true)
public class PermissionControlConfiguration implements WebMvcConfigurer {

    /**
     * 排除路径：Spring Boot 默认错误页
     */
    private static final String PATH_PATTERN_ERROR = "/error";

    /**
     * 排除路径：Actuator 端点自带鉴权
     */
    private static final String PATH_PATTERN_ACTUATOR = "/actuator/**";

    @Autowired
    private ObjectProvider<AuthService> authServiceProvider; // 当前身份提供者

    /**
     * 创建权限拦截器
     *
     * @return 权限拦截器实例
     */
    @Bean
    public PermissionControlInterceptor permissionControlInterceptor() {
        return new PermissionControlInterceptor(authServiceProvider);
    }

    /**
     * 注册权限拦截器，覆盖全部路径。
     *
     * @param registry Spring MVC 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(permissionControlInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(PATH_PATTERN_ERROR, PATH_PATTERN_ACTUATOR);
    }
}
