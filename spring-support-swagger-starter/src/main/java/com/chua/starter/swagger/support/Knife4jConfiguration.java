package com.chua.starter.swagger.support;

import com.github.xiaoymin.knife4j.spring.annotations.EnableKnife4j;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.webmvc.core.configuration.MultipleOpenApiSupportConfiguration;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.CorsEndpointProperties;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
import org.springframework.boot.actuate.endpoint.ExposableEndpoint;
import org.springframework.boot.actuate.endpoint.web.EndpointLinksResolver;
import org.springframework.boot.actuate.endpoint.web.EndpointMapping;
import org.springframework.boot.actuate.endpoint.web.EndpointMediaTypes;
import org.springframework.boot.actuate.endpoint.web.ExposableWebEndpoint;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.boot.actuate.endpoint.web.servlet.WebMvcEndpointHandlerMapping;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Knife4j 配置
 *
 * @author CH
 */
@Slf4j
@EnableKnife4j
@EnableConfigurationProperties(Knife4jProperties.class)
@Import({MultipleOpenApiSupportConfiguration.class})
@ConditionalOnProperty(prefix = "plugin.swagger", name = "enable", havingValue = "true", matchIfMissing = false)
public class Knife4jConfiguration implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware {

    /**
     * Knife4j配置属性
     */
    private Knife4jProperties knife4jProperties;

    /**
     * 后处理Bean定义注册表
     *
     * @param registry Bean定义注册表
     * @throws BeansException Bean异常
     */
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        List<Knife4jProperties.Knife4j> knife4jList = knife4jProperties.getKnife4j();
        if (CollectionUtils.isEmpty(knife4jList)) {
            return;
        }
        OpenAPI openAPI = new OpenAPI();
        openAPI.setComponents(new Components());
        openAPI.setInfo(new Info()
                .description(knife4jProperties.getDescription())
                .version(knife4jProperties.getVersion())
                .termsOfService(knife4jProperties.getTermsOfService())
        );
        registry.registerBeanDefinition("knife4j-OpenAPI", 
                BeanDefinitionBuilder.genericBeanDefinition(OpenAPI.class, () -> openAPI).getBeanDefinition());
    }

    /**
     * 设置应用上下文
     *
     * @param applicationContext 应用上下文
     * @throws BeansException Bean异常
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        knife4jProperties = Binder.get(applicationContext.getEnvironment())
                .bindOrCreate("plugin.swagger", Knife4jProperties.class);
    }

    /**
     * 增加如下配置可解决Spring Boot与Swagger 3.0.0不兼容问题
     *
     * @param webEndpointsSupplier Web端点提供者
     * @param endpointMediaTypes 端点媒体类型
     * @param corsProperties CORS配置
     * @param webEndpointProperties Web端点属性
     * @param environment 环境变量
     * @return WebMvc端点处理器映射
     */
    @Bean
    public WebMvcEndpointHandlerMapping webEndpointServletHandlerMapping(
            WebEndpointsSupplier webEndpointsSupplier,
            EndpointMediaTypes endpointMediaTypes,
            CorsEndpointProperties corsProperties,
            WebEndpointProperties webEndpointProperties,
            Environment environment) {
        List<ExposableEndpoint<?>> allEndpoints = new ArrayList<>();
        Collection<ExposableWebEndpoint> webEndpoints = webEndpointsSupplier.getEndpoints();
        allEndpoints.addAll(webEndpoints);
        String basePath = webEndpointProperties.getBasePath();
        EndpointMapping endpointMapping = new EndpointMapping(basePath);
        boolean shouldRegisterLinksMapping = shouldRegisterLinksMapping(webEndpointProperties, environment, basePath);
        return new WebMvcEndpointHandlerMapping(
                endpointMapping,
                webEndpoints,
                endpointMediaTypes,
                corsProperties.toCorsConfiguration(),
                new EndpointLinksResolver(allEndpoints, basePath),
                shouldRegisterLinksMapping);
    }

    /**
     * 判断是否应该注册链接映射
     *
     * @param webEndpointProperties Web端点属性
     * @param environment 环境变量
     * @param basePath 基础路径
     * @return 是否应该注册
     */
    private boolean shouldRegisterLinksMapping(WebEndpointProperties webEndpointProperties, 
                                               Environment environment, 
                                               String basePath) {
        return webEndpointProperties.getDiscovery().isEnabled() 
                && (StringUtils.hasText(basePath) 
                || ManagementPortType.get(environment).equals(ManagementPortType.DIFFERENT));
    }

}