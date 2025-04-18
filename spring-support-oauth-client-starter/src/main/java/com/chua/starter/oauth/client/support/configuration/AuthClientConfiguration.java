package com.chua.starter.oauth.client.support.configuration;

import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.oauth.client.support.filter.AuthFilter;
import com.chua.starter.oauth.client.support.interceptor.PermissionPointcut;
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
import org.springframework.core.Ordered;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

/**
 * 鉴权
 *
 * @author CH
 * @since 2022/7/23 8:51
 */
@EnableConfigurationProperties({AuthClientProperties.class})
public class AuthClientConfiguration implements ApplicationContextAware, BeanDefinitionRegistryPostProcessor, WebMvcConfigurer {


    private AuthClientProperties authProperties;


    /**
     * 鉴权服务
     *
     * @return 鉴权服务
     */
    @Bean
    public AuthService authService() {
        return new OauthAuthService();
    }

    @Bean
    @ConditionalOnMissingBean
    public PermissionPointcut permissionProxyFactoryBean() {
        return new PermissionPointcut();
    }
    /**
     * 鉴权过滤器
     *
     * @return 鉴权过滤器
     */
    @Bean
    @ConditionalOnProperty(name = "plugin.oauth.temp.open", havingValue = "true", matchIfMissing = true)
    public UserStatisticProvider tempProvider() {
        return new UserStatisticProvider();
    }
    /**
     * 鉴权过滤器
     *
     * @return 鉴权过滤器
     */
    @Bean("authFilterFilterRegistrationBean")
    @ConditionalOnMissingClass("com.chua.starter.oauth.server.support.SsoServer")
    public FilterRegistrationBean<AuthFilter> authFilterFilterRegistrationBean(RequestMappingHandlerMapping requestMappingHandlerMapping) {
        FilterRegistrationBean<AuthFilter> authFilterFilterRegistrationBean = new FilterRegistrationBean<>();
        authFilterFilterRegistrationBean.setOrder(Ordered.LOWEST_PRECEDENCE);
        authFilterFilterRegistrationBean.setUrlPatterns(authProperties.getBlockAddress());
        authFilterFilterRegistrationBean.setName("authFilterFilterRegistrationBean");
        authFilterFilterRegistrationBean.setAsyncSupported(true);
        SpringBeanUtils.setRequestMappingHandlerMapping(requestMappingHandlerMapping);
        authFilterFilterRegistrationBean.setFilter(new AuthFilter(new WebRequest(authProperties), requestMappingHandlerMapping));

        return authFilterFilterRegistrationBean;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.authProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(AuthClientProperties.PRE, AuthClientProperties.class);
    }


    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new UserRequestHandlerMethodArgumentResolver(new WebRequest(authProperties)));
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
