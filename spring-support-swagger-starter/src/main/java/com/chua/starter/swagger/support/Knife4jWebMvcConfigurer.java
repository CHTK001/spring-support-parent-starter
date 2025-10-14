package com.chua.starter.swagger.support;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * knife4j 资源
 * @author CH
 * @since 2024/8/7
 */
public class Knife4jWebMvcConfigurer implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("doc.html").addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("swagger-ui.html").addResourceLocations("classpath:/META-INF/resources/");
    }


    @Bean
    @ConditionalOnClass(name = "io.swagger.v3.oas.models.media.Schema")
    public Knife4jEnumModelPropertyPlugin getKnife4jEnumModelPropertyPlugin() {
        return new Knife4jEnumModelPropertyPlugin();
    }
}
