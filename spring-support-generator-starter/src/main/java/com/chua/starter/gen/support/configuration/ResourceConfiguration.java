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
        registry.addResourceHandler("/assets-gen/**").addResourceLocations("classpath:/static/gen/assets-gen/")
                .setUseLastModified(true);
        registry.addResourceHandler("/gen-ui.html").addResourceLocations("classpath:/static/gen/");
    }

}
