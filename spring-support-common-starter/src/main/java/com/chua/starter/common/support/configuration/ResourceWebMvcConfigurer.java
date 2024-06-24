package com.chua.starter.common.support.configuration;

import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * resource
 *
 * @author CH
 */
public class ResourceWebMvcConfigurer implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**").addResourceLocations("classpath:/static/");
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/webjars/", "classpath:/META-INF/resources/webjars/");
        registry.addResourceHandler("doc.html").addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("swagger-ui.html").addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/storage/**").addResourceLocations("classpath:/static/storage/assets/").setUseLastModified(true);
        registry.addResourceHandler("/storage-ui.html").addResourceLocations("classpath:/static/storage/");
        registry.addResourceHandler("/markdown.html").addResourceLocations("classpath:/static/storage/");
        registry.addResourceHandler("/text.html").addResourceLocations("classpath:/static/storage/");
        registry.addResourceHandler("/fileView.html").addResourceLocations("classpath:/static/storage/");
        registry.addResourceHandler("/xml.html").addResourceLocations("classpath:/static/storage/");
        registry.addResourceHandler("/json.html").addResourceLocations("classpath:/static/storage/");
        registry.addResourceHandler("/excel.html").addResourceLocations("classpath:/static/storage/");
        registry.addResourceHandler("/word.html").addResourceLocations("classpath:/static/storage/");
        registry.addResourceHandler("/archive.html").addResourceLocations("classpath:/static/storage/");

    }

}
