package com.chua.starter.swagger.support;

import com.github.xiaoymin.knife4j.spring.annotations.EnableKnife4j;
import com.google.common.collect.Lists;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.CorsEndpointProperties;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
import org.springframework.boot.actuate.endpoint.ExposableEndpoint;
import org.springframework.boot.actuate.endpoint.web.*;
import org.springframework.boot.actuate.endpoint.web.annotation.ControllerEndpointsSupplier;
import org.springframework.boot.actuate.endpoint.web.annotation.ServletEndpointsSupplier;
import org.springframework.boot.actuate.endpoint.web.servlet.WebMvcEndpointHandlerMapping;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * @author CH
 */
@Slf4j
@EnableKnife4j
@EnableConfigurationProperties(Knife4jProperties.class)
//@Import(BeanValidatorPluginsConfiguration.class)
public class Knife4jConfiguration implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware {

    Knife4jProperties knife4jProperties;
    private ApplicationContext applicationContext;
//    @Bean
//    @ConditionalOnMissingBean
//    @Lazy
//    @ConditionalOnProperty(prefix = "plugin.swagger", name = "log", havingValue = "true", matchIfMissing = true)
//    public SwaggerLoggerPointcutAdvisor swaggerLoggerPointcutAdvisor(@Autowired(required = false) LoggerService loggerService) {
//        return new SwaggerLoggerPointcutAdvisor(loggerService);
//    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        for (Knife4jProperties.Knife4j knife4j : Optional.ofNullable(knife4jProperties.getKnife4j())
                .orElse(Lists.newArrayList(new Knife4jProperties.Knife4j()
                        .setGroupName("说明")
                        .setBasePackage("com.hua.demo.controller")
                ))) {
            AutowireCapableBeanFactory autowireCapableBeanFactory = applicationContext.getAutowireCapableBeanFactory();
            if (autowireCapableBeanFactory instanceof ConfigurableListableBeanFactory) {
                ((ConfigurableListableBeanFactory) autowireCapableBeanFactory).registerSingleton(
                        knife4j.getGroupName(),
                        new OpenAPI()
                                .info(new Info()
                                        .description(knife4j.getDescription())
                                        .version(knife4j.getVersion())
                                        .termsOfService(knife4j.getTermsOfService())
                                )
                );
                ((ConfigurableListableBeanFactory) autowireCapableBeanFactory).registerSingleton(
                        knife4j.getGroupName() + "GroupedOpenApi",
                        GroupedOpenApi.builder()
                                .group(knife4j.getGroupName())
                                .packagesToScan(knife4j.getBasePackage())
                                .pathsToMatch(knife4j.getPathsToMatch())
                                .build()
                );
            }
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        knife4jProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate("plugin.swagger", Knife4jProperties.class);
    }


    /**
     * 增加如下配置可解决Spring Boot  与Swagger 3.0.0 不兼容问题
     **/
    @Bean
    public WebMvcEndpointHandlerMapping webEndpointServletHandlerMapping(WebEndpointsSupplier webEndpointsSupplier,
                                                                         ServletEndpointsSupplier servletEndpointsSupplier,
                                                                         ControllerEndpointsSupplier controllerEndpointsSupplier,
                                                                         EndpointMediaTypes endpointMediaTypes,
                                                                         CorsEndpointProperties corsProperties,
                                                                         WebEndpointProperties webEndpointProperties,
                                                                         Environment environment) {
        List<ExposableEndpoint<?>> allEndpoints = new ArrayList();
        Collection<ExposableWebEndpoint> webEndpoints = webEndpointsSupplier.getEndpoints();
        allEndpoints.addAll(webEndpoints);
        allEndpoints.addAll(servletEndpointsSupplier.getEndpoints());
        allEndpoints.addAll(controllerEndpointsSupplier.getEndpoints());
        String basePath = webEndpointProperties.getBasePath();
        EndpointMapping endpointMapping = new EndpointMapping(basePath);
        boolean shouldRegisterLinksMapping = this.shouldRegisterLinksMapping(webEndpointProperties, environment, basePath);
        return new WebMvcEndpointHandlerMapping(endpointMapping, webEndpoints, endpointMediaTypes, corsProperties.toCorsConfiguration(), new EndpointLinksResolver(allEndpoints, basePath), shouldRegisterLinksMapping, null);
    }

    private boolean shouldRegisterLinksMapping(WebEndpointProperties webEndpointProperties, Environment environment, String basePath) {
        return webEndpointProperties.getDiscovery().isEnabled() && (StringUtils.hasText(basePath) || ManagementPortType.get(environment).equals(ManagementPortType.DIFFERENT));
    }

}