package com.xxl.job.admin.configuration;

import com.xxl.job.admin.properties.SchedulerProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * freemarker
 *
 * @author CH
 */
@EnableConfigurationProperties(SchedulerProperties.class)
@MapperScan("com.xxl.job.admin.dao")
@ComponentScan(basePackages = {
        "com.xxl.job.admin.core.conf",
        "com.xxl.job.admin.core.alarm",
        "com.xxl.job.admin.controller",
        "com.xxl.job.admin.service"})
public class SchedulerConfiguration  implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**").addResourceLocations("classpath:/static/scheduler/assets/")
                .setUseLastModified(true);
        registry.addResourceHandler("index.html").addResourceLocations("classpath:/static/scheduler/");
    }
}
