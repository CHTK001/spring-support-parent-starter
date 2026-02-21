package com.chua.starter.oauth.client.support.configuration;

import com.chua.spring.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.oauth.client.support.filter.AuthFilter;
import com.chua.starter.oauth.client.support.interceptor.PermissionInterceptor;
import com.chua.starter.oauth.client.support.interceptor.TokenForTypeInterceptor;
import com.chua.starter.oauth.client.support.oauth.OauthAuthService;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.provider.UserStatisticProvider;
import com.chua.starter.oauth.client.support.web.WebRequest;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

/**
 * OAuth客户端自动配置类
 *
 * 提供OAuth客户端的完整配置，包括：
 * 1. 认证服务配置
 * 2. 过滤器配置
 * 3. Principal支持
 * 4. HttpServletRequest增强
 * 5. 权限拦截器配置
 *
 * @author CH
 * @since 2022/7/23 8:51
@ConditionalOnProperty(prefix = "plugin.oauth.client", name = "enable", havingValue = "true", matchIfMissing = true)
 */
@EnableConfigurationProperties({ AuthClientProperties.class })
public class AuthClientConfiguration
        implements ApplicationContextAware, BeanDefinitionRegistryPostProcessor, WebMvcConfigurer {

    /**
     * Spring 管理的 AuthClientProperties Bean
     * 确保与 WhitelistFileWatcher 使用同一个实例
     */
    private AuthClientProperties authProperties;
    
    private ApplicationContext applicationContext;

    /**
     * OAuth认证服务
     *
     * 提供用户认证和权限管理功能，集成OAuth用户信息。
     * 支持获取当前用户信息、角色验证、权限检查等功能。
     *
     * @return OAuth认证服务实例
     */
    @Bean(name = "authService")
    @Primary
    public AuthService authService() {
        return new OauthAuthService();
    }

    /**
     * 权限拦截器
     *
     * 提供基于注解的权限控制功能，支持方法级别的权限验证。
     * 集成OAuth用户信息进行权限检查。
     *
     * @return 权限拦截器实例
     */
    @Bean
    @ConditionalOnMissingBean
    public PermissionInterceptor permissionProxyFactoryBean() {
        return new PermissionInterceptor();
    }

    /**
     * Token类型拦截器
     *
     * 提供基于Token类型的拦截功能，支持不同类型Token的处理。
     * 根据@TokenForType注解自动处理Token类型转换和验证。
     *
     * @return Token类型拦截器实例
     */
    @Bean
    @ConditionalOnMissingBean
    public TokenForTypeInterceptor tokenForTypeInterceptor() {
        return new TokenForTypeInterceptor();
    }

    /**
     * 用户统计提供者
     *
     * 提供用户统计和临时数据处理功能。
     *
     * @return 用户统计提供者实例
     */
    @Bean
    @ConditionalOnProperty(name = "plugin.oauth.temp.open", havingValue = "true", matchIfMissing = false)
    public UserStatisticProvider tempProvider() {
        return new UserStatisticProvider();
    }

    /**
     * OAuth认证过滤器注册
     *
     * 注册OAuth认证过滤器，提供以下功能：
     * 1. 用户身份验证
     * 2. Principal支持（java.security.Principal）
     * 3. HttpServletRequest增强（isUserInRole、getAuthType、logout、login）
     * 4. Session管理
     * 5. 用户信息存储
     *
     * 过滤器会创建OAuthHttpServletRequestWrapper来增强HttpServletRequest，
     * 使其支持标准的Java EE安全API。
     *
     * @param requestMappingHandlerMapping Spring MVC请求映射处理器
     * @return 过滤器注册Bean
     */
    @Bean("authFilterFilterRegistrationBean")
    @ConditionalOnMissingClass("com.chua.starter.oauth.server.support.SsoServer")
    public FilterRegistrationBean<AuthFilter> authFilterFilterRegistrationBean(
            RequestMappingHandlerMapping requestMappingHandlerMapping,
            AuthClientProperties authClientProperties) {
        // 使用注入的 AuthClientProperties Bean，确保与 WhitelistFileWatcher 同一实例
        FilterRegistrationBean<AuthFilter> authFilterFilterRegistrationBean = new FilterRegistrationBean<>();
        authFilterFilterRegistrationBean.setOrder(Ordered.LOWEST_PRECEDENCE);
        authFilterFilterRegistrationBean.setUrlPatterns(authClientProperties.getBlockAddress());
        authFilterFilterRegistrationBean.setName("authFilterFilterRegistrationBean");
        authFilterFilterRegistrationBean.setAsyncSupported(true);
        SpringBeanUtils.setRequestMappingHandlerMapping(requestMappingHandlerMapping);

        // 创建增强的AuthFilter，支持Principal和HttpServletRequest增强
        // 使用 Spring Bean 确保白名单更新能被感知
        authFilterFilterRegistrationBean
                .setFilter(new AuthFilter(new WebRequest(authClientProperties), requestMappingHandlerMapping));

        return authFilterFilterRegistrationBean;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        // 使用 Binder 创建初始配置，后续会被 Spring Bean 替换
        this.authProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(AuthClientProperties.PRE,
                AuthClientProperties.class);
    }
    
    /**
     * 获取 Spring 管理的 AuthClientProperties Bean
     * 确保与 WhitelistFileWatcher 使用同一个实例
     */
    private AuthClientProperties getSpringBean() {
        try {
            return applicationContext.getBean(AuthClientProperties.class);
        } catch (Exception e) {
            return authProperties;
        }
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        // 使用 Spring Bean 确保白名单更新能被感知
        AuthClientProperties props = getSpringBean();
        resolvers.add(new UserRequestHandlerMethodArgumentResolver(new WebRequest(props)));
        resolvers.add(new TokenRequestHandlerMethodArgumentResolver(new WebRequest(props)));
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        BeanDefinitionBuilder definitionBuilder1 = BeanDefinitionBuilder.genericBeanDefinition(WebRequest.class);
        definitionBuilder1.addConstructorArgValue(authProperties);
        registry.registerBeanDefinition(WebRequest.class.getTypeName(), definitionBuilder1.getBeanDefinition());
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }
}
