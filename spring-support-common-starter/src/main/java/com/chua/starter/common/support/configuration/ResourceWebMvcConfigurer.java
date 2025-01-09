package com.chua.starter.common.support.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * resource
 *
 * @author CH
 */
@Slf4j
public class ResourceWebMvcConfigurer implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        log.info("当前运行目录: {}", new File(".").getAbsolutePath());
        registry.addResourceHandler("/static/**").addResourceLocations("classpath:/static/", "file:./static/");
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/webjars/",
                "file:./webjars/",
                "classpath:/META-INF/resources/webjars/");
    }

}
