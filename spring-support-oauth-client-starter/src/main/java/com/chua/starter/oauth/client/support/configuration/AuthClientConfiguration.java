package com.chua.starter.oauth.client.support.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.common.support.serviceinfo.ServiceInfoRegistry;
import com.chua.starter.oauth.client.support.filter.AuthFilter;
import com.chua.starter.oauth.client.support.interceptor.PermissionInterceptor;
import com.chua.starter.oauth.client.support.interceptor.TokenForTypeInterceptor;
import com.chua.starter.oauth.client.support.oauth.OauthAuthService;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.provider.UserStatisticProvider;
import com.chua.starter.oauth.client.support.ratelimit.RateLimitFilter;
import com.chua.starter.oauth.client.support.runtime.OauthClientRuntimeContext;
import com.chua.starter.oauth.client.support.web.WebRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 */
@Slf4j
@ConditionalOnProperty(prefix = "plugin.oauth", name = "enable", havingValue = "true", matchIfMissing = true)
@Configuration
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
        OauthClientRuntimeContext.setAuthClientProperties(getSpringBean());
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
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public FilterRegistrationBean<AuthFilter> authFilterFilterRegistrationBean(
            RequestMappingHandlerMapping requestMappingHandlerMapping) {
        AuthClientProperties authClientProperties = getSpringBean();
        log.info("[AuthClientConfiguration] 初始化认证过滤器 - 协议: {}, 地址: {}, enable: {}",
                authClientProperties.getProtocol(),
                authClientProperties.getAddress(),
                authClientProperties.isEnable());
        OauthClientRuntimeContext.setAuthClientProperties(authClientProperties);
        // 使用注入的 AuthClientProperties Bean，确保与 WhitelistFileWatcher 同一实例
        FilterRegistrationBean<AuthFilter> authFilterFilterRegistrationBean = new FilterRegistrationBean<>();
        authFilterFilterRegistrationBean.setOrder(Ordered.LOWEST_PRECEDENCE);
        authFilterFilterRegistrationBean.setUrlPatterns(authClientProperties.getBlockAddress());
        authFilterFilterRegistrationBean.setName("authFilterFilterRegistrationBean");
        authFilterFilterRegistrationBean.setAsyncSupported(true);
        // 使用 Spring Bean 确保白名单更新能被感知
        authFilterFilterRegistrationBean.setFilter(authFilter(requestMappingHandlerMapping));

        return authFilterFilterRegistrationBean;
    }

    /**
     * OAuth 认证过滤器 Bean
     * <p>
     * 作为 Spring Bean 暴露，支持注入后动态添加/删除白名单规则：
     * <ul>
     *     <li>{@link AuthFilter#addWhitelist(String)} - 动态添加白名单规则</li>
     *     <li>{@link AuthFilter#removeWhitelist(String)} - 动态删除白名单规则</li>
     *     <li>{@link AuthFilter#clearWhitelist()} - 清空动态白名单</li>
     * </ul>
     * </p>
     *
     * @param requestMappingHandlerMapping Spring MVC请求映射处理器
     * @return 认证过滤器实例
     */
    @Bean
    @ConditionalOnMissingClass("com.chua.starter.oauth.server.support.SsoServer")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public AuthFilter authFilter(RequestMappingHandlerMapping requestMappingHandlerMapping) {
        return new AuthFilter(new WebRequest(getSpringBean()), requestMappingHandlerMapping);
    }

    /**
     * OAuth 限流过滤器注册
     * <p>
     * 置于最高优先级，在认证过滤器之前对包括未认证流量在内的所有请求执行限流检查。
     * </p>
     *
     * @param authClientProperties 客户端配置
     * @return 过滤器注册Bean
     */
    @Bean("rateLimitFilterRegistrationBean")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistrationBean() {
        AuthClientProperties authClientProperties = getSpringBean();
        FilterRegistrationBean<RateLimitFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registrationBean.setUrlPatterns(authClientProperties.getBlockAddress());
        registrationBean.setName("rateLimitFilter");
        registrationBean.setAsyncSupported(true);
        registrationBean.setFilter(new RateLimitFilter(authClientProperties));
        if (authClientProperties.getRateLimit() != null && authClientProperties.getRateLimit().isEnable()) {
            log.info("[AuthClientConfiguration] OAuth限流已启用 - 接口QPS: {}/s, 登录IP限流: {}次/{}s, 账号锁定: 失败{}次锁{}分钟",
                    authClientProperties.getRateLimit().getApi().getQps(),
                    authClientProperties.getRateLimit().getLogin().getIpLimit(),
                    authClientProperties.getRateLimit().getLogin().getIpWindowSeconds(),
                    authClientProperties.getRateLimit().getLogin().getAccountFailLimit(),
                    authClientProperties.getRateLimit().getLogin().getAccountLockMinutes());
        }
        return registrationBean;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        OauthClientRuntimeContext.setApplicationContext(applicationContext);
        OauthClientRuntimeContext.setEnvironment(applicationContext.getEnvironment());
        // 使用 Binder 创建初始配置，后续会被 Spring Bean 替换
        this.authProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(AuthClientProperties.PRE,
                AuthClientProperties.class);
        log.info("[AuthClientConfiguration] setApplicationContext Binder - 协议: {}, 地址: {}",
                this.authProperties.getProtocol(), this.authProperties.getAddress());
        OauthClientRuntimeContext.setAuthClientProperties(this.authProperties);
    }
    
    /**
     * 获取 OAuth 客户端配置实例
     * <p>
     * 优先使用 Spring 管理的 {@link AuthClientProperties} Bean，若该 Bean 因过早实例化未完成
     * 配置绑定，则回退到 Binder 绑定成功的实例，确保 protocol/address/whitelist 等配置有效。
     * </p>
     */
    private AuthClientProperties getSpringBean() {
        try {
            AuthClientProperties bean = applicationContext.getBean(AuthClientProperties.class);
            if (null == bean.getAddress() && null != this.authProperties && null != this.authProperties.getAddress()) {
                log.debug("[AuthClientConfiguration] AuthClientProperties Bean 未绑定配置，回退到 Binder 实例 - 协议: {}, 地址: {}",
                        this.authProperties.getProtocol(), this.authProperties.getAddress());
                return this.authProperties;
            }
            return bean;
        } catch (Exception e) {
            return authProperties;
        }
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        // 使用 Spring Bean 确保白名单更新能被感知
        AuthClientProperties props = getSpringBean();
        var webRequest = new WebRequest(props);
        var beanFactory = OauthClientRuntimeContext.getBeanFactory();
        UserRequestHandlerMethodArgumentResolver userResolver = new UserRequestHandlerMethodArgumentResolver(webRequest);
        userResolver.setConfigurableBeanFactory(beanFactory);
        TokenRequestHandlerMethodArgumentResolver tokenResolver = new TokenRequestHandlerMethodArgumentResolver(webRequest);
        tokenResolver.setConfigurableBeanFactory(beanFactory);
        resolvers.add(userResolver);
        resolvers.add(tokenResolver);
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        BeanDefinitionBuilder definitionBuilder1 = BeanDefinitionBuilder.genericBeanDefinition(WebRequest.class);
        definitionBuilder1.addConstructorArgValue(authProperties);
        registry.registerBeanDefinition(WebRequest.class.getTypeName(), definitionBuilder1.getBeanDefinition());
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        OauthClientRuntimeContext.setBeanFactory(beanFactory);
        // BDRPP 阶段 @ConfigurationProperties Bean 可能尚未完成绑定，优先使用 Binder 绑定成功的实例
        AuthClientProperties bean = (null != this.authProperties && null != this.authProperties.getAddress())
                ? this.authProperties : getSpringBean();
        log.info("[AuthClientConfiguration] postProcessBeanFactory - 协议: {}, 地址: {}", bean.getProtocol(), bean.getAddress());
        OauthClientRuntimeContext.setAuthClientProperties(bean);
    }

    @Bean
    @ConditionalOnMissingBean
    public WhitelistFileWatcher whitelistFileWatcher(AuthClientProperties authClientProperties) {
        return new WhitelistFileWatcher(authClientProperties);
    }

    @Bean
    public ServiceInfoContributor authClientServiceInfoContributor() {
        return registry -> {
            registry.registerEndpoint("认证授权", "认证接口", "/auth/**", "认证接口");
            registry.registerFlag("oauth", "认证授权", true, "OAuth认证模块");
        };
    }
}
