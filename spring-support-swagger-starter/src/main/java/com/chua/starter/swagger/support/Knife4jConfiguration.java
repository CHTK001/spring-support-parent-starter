package com.chua.starter.swagger.support;

import com.chua.starter.swagger.support.customize.CustomKnife4jOpenApiCustomizer;
import com.github.xiaoymin.knife4j.spring.annotations.EnableKnife4j;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.utils.Constants;
import org.springdoc.webmvc.core.configuration.MultipleOpenApiSupportConfiguration;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.CorsEndpointProperties;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
import org.springframework.boot.actuate.endpoint.ExposableEndpoint;
import org.springframework.boot.actuate.endpoint.web.*;
import org.springframework.boot.actuate.endpoint.web.servlet.WebMvcEndpointHandlerMapping;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Knife4j配置
 * @author CH
 */
@Slf4j
@EnableKnife4j
@EnableConfigurationProperties(Knife4jProperties.class)
//@Import(BeanValidatorPluginsConfiguration.class)
@Import({MultipleOpenApiSupportConfiguration.class})
public class Knife4jConfiguration implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware, BeanPostProcessor {

    Knife4jProperties knife4jProperties;
    private ApplicationContext applicationContext;
    private com.github.xiaoymin.knife4j.spring.configuration.Knife4jProperties knife4j;
    private SpringDocConfigProperties springDocConfigProperties;

//
//    /**
//     * 创建自定义的ModelResolver
//     *
//     * @return
//     */
//    @Bean
//    public CustomModelResolver resolver(ObjectMapper  objectMapper) {
//        return new CustomModelResolver(objectMapper);
//    }

//    @Bean
//    @Primary
//    public CustomOperationCustomizer customOperationCustomizer() {
//        return new CustomOperationCustomizer(knife4jProperties);
//    }


//    @Bean
//    @Primary
//    public CustomKnife4jOpenApiCustomizer customKnife4jOpenApiCustomizer(com.github.xiaoymin.knife4j.spring.configuration.Knife4jProperties  knife4jProperties, SpringDocConfigProperties springDocConfigProperties) {
//        return new CustomKnife4jOpenApiCustomizer(knife4jProperties, springDocConfigProperties);
//    }


    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        Class<?> aClass = bean.getClass();
        if (aClass == com.github.xiaoymin.knife4j.spring.extension.Knife4jOpenApiCustomizer.class) {
            return new CustomKnife4jOpenApiCustomizer(knife4j, springDocConfigProperties);
        }
        return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        List<Knife4jProperties.Knife4j> knife4j1 = knife4jProperties.getKnife4j();
        if(CollectionUtils.isEmpty(knife4j1)) {
            return;
        }
        OpenAPI openAPI = new OpenAPI();
        openAPI.setComponents(new Components());
        openAPI.setInfo(new Info()
                .description(knife4jProperties.getDescription())
                .version(knife4jProperties.getVersion())
                .termsOfService(knife4jProperties.getTermsOfService())
        );
//        registry.registerBeanDefinition("knife4jOpenApiCustomizer2" , BeanDefinitionBuilder
//                .genericBeanDefinition(com.github.xiaoymin.knife4j.spring.extension.Knife4jOpenApiCustomizer.class,
//                        () -> new Knife4jOpenApiCustomizer(knife4j, springDocConfigProperties))
//                .addConstructorArgValue(knife4j)
//                .addConstructorArgValue(springDocConfigProperties)
//                .getBeanDefinition()
//        );
        registry.registerBeanDefinition("knife4j-OpenAPI", BeanDefinitionBuilder.genericBeanDefinition(OpenAPI.class, () -> openAPI).getBeanDefinition());
//        for (Knife4jProperties.Knife4j knife4j : knife4j1) {
//            GroupedOpenApi openApi = GroupedOpenApi.builder()
//                    .group(knife4j.getGroupName())
//                    .displayName(knife4j.getGroupName())
//                    .packagesToScan(knife4j.getBasePackage())
//                    .pathsToMatch(Optional.ofNullable(knife4j.getPathsToMatch()).orElse((new String[]{"/**"}))
//                    ).build();
//            BeanDefinition beanDefinition = BeanDefinitionBuilder.genericBeanDefinition(GroupedOpenApi.class, () -> openApi).getBeanDefinition();
//            registry.registerBeanDefinition(String.format("%sGroupedOpenApi",openApi.getGroup()), beanDefinition);
//        }
    }



    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        knife4jProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate("plugin.swagger", Knife4jProperties.class);
        knife4j = Binder.get(applicationContext.getEnvironment()).bindOrCreate("knife4j", com.github.xiaoymin.knife4j.spring.configuration.Knife4jProperties.class);
        springDocConfigProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(Constants.SPRINGDOC_PREFIX, SpringDocConfigProperties.class);
    }


    /**
     * 增加如下配置可解决Spring Boot  与Swagger 3.0.0 不兼容问题
     **/
    @Bean
    public WebMvcEndpointHandlerMapping webEndpointServletHandlerMapping(WebEndpointsSupplier webEndpointsSupplier,
//                                                                         ServletEndpointsSupplier servletEndpointsSupplier,
//                                                                         ControllerEndpointsSupplier controllerEndpointsSupplier,
                                                                         EndpointMediaTypes endpointMediaTypes,
                                                                         CorsEndpointProperties corsProperties,
                                                                         WebEndpointProperties webEndpointProperties,
                                                                         Environment environment) {
        List<ExposableEndpoint<?>> allEndpoints = new ArrayList();
        Collection<ExposableWebEndpoint> webEndpoints = webEndpointsSupplier.getEndpoints();
        allEndpoints.addAll(webEndpoints);
//        allEndpoints.addAll(servletEndpointsSupplier.getEndpoints());
//        allEndpoints.addAll(controllerEndpointsSupplier.getEndpoints());
        String basePath = webEndpointProperties.getBasePath();
        EndpointMapping endpointMapping = new EndpointMapping(basePath);
        boolean shouldRegisterLinksMapping = this.shouldRegisterLinksMapping(webEndpointProperties, environment, basePath);
        return new WebMvcEndpointHandlerMapping(endpointMapping, webEndpoints, endpointMediaTypes, corsProperties.toCorsConfiguration(), new EndpointLinksResolver(allEndpoints, basePath), shouldRegisterLinksMapping);
    }

    private boolean shouldRegisterLinksMapping(WebEndpointProperties webEndpointProperties, Environment environment, String basePath) {
        return webEndpointProperties.getDiscovery().isEnabled() && (StringUtils.hasText(basePath) || ManagementPortType.get(environment).equals(ManagementPortType.DIFFERENT));
    }

}