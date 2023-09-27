package com.xxl.job.admin.configuration;

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
        registry.addResourceHandler("/assets/**").addResourceLocations("classpath:/static/job/assets/")
                .setUseLastModified(true);
        registry.addResourceHandler("/job-ui.html").addResourceLocations("classpath:/static/job/");
    }

}
