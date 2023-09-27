package com.chua.starter.gen.support.configuration;

import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * resource
 *
 * @author CH
 */
public class ResourceConfiguration implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/gen/assets/**").addResourceLocations("classpath:/static/gen/assets/")
                .setUseLastModified(true);
        registry.addResourceHandler("/gen/index.html").addResourceLocations("classpath:/static/");
    }

}
