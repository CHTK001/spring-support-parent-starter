package com.chua.starter.config.server.support.configuration;

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
        registry.addResourceHandler("/conf/assets/**").addResourceLocations("classpath:/static/conf/assets")
                .setUseLastModified(true);
        registry.addResourceHandler("/conf/index.html").addResourceLocations("classpath:/static/");
    }

}
