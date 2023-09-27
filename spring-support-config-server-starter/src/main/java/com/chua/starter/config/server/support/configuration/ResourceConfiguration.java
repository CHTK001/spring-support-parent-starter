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
        registry.addResourceHandler("/assets-config/**").addResourceLocations("classpath:/static/conf/assets-config/")
                .setUseLastModified(true);
        registry.addResourceHandler("/config-ui.html").addResourceLocations("classpath:/static/conf/");
    }

}
