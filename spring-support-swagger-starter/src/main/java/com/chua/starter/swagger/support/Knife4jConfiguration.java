package com.chua.starter.swagger.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

/**
 * @author CH
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(Knife4jProperties.class)
public class Knife4jConfiguration implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware, CommandLineRunner {

    Knife4jProperties knife4jProperties;
    private ApplicationContext applicationContext;
    @Value("${server.servlet.context-path:}")
    private String context;
    @Value("${server.port:8080}")
    private Integer port;

    @ConditionalOnMissingBean
    @Bean(value = "defaultApi2")
    public Docket defaultApi2() {
        return new Docket(DocumentationType.SWAGGER_2)
                .apiInfo(new ApiInfoBuilder()
                        .description("# Empty")
                        .version("1.0")
                        .build())
                //分组名称
                .groupName("2.X版本")
                .select()
                //这里指定Controller扫描包路径
                .apis(RequestHandlerSelectors.basePackage("com.hua.demo.controller"))
                .paths(PathSelectors.any())
                .build();
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        for (Knife4jProperties.Knife4j knife4j : knife4jProperties.getKnife4j()) {
            AutowireCapableBeanFactory autowireCapableBeanFactory = applicationContext.getAutowireCapableBeanFactory();
            if (autowireCapableBeanFactory instanceof ConfigurableListableBeanFactory) {
                ((ConfigurableListableBeanFactory) autowireCapableBeanFactory).registerSingleton(
                        knife4j.getGroupName(),
                        new Docket(DocumentationType.SWAGGER_2)
                                .apiInfo(new ApiInfoBuilder()
                                        .description(knife4j.getDescription())
                                        .version(knife4j.getVersion())
                                        .build())
                                //分组名称
                                .groupName(knife4j.getGroupName())
                                .select()
                                //这里指定Controller扫描包路径
                                .apis(RequestHandlerSelectors.basePackage(knife4j.getBasePackage()))
                                .paths(PathSelectors.any())
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

    @Override
    public void run(String... args) throws Exception {
        log.info("当前swagger文档地址: http://127.0.0.1: {}/{}/doc.html", context, port);
    }
}