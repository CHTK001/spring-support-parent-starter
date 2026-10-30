package com.chua.starter.common.support.configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * resource
 *
 * @author CH
 * @since 4.0.0
 */
public class ResourceWebMvcConfigurer implements WebMvcConfigurer  {
    /**
     * 日志
    */
    private static final Logger log = LoggerFactory.getLogger(ResourceWebMvcConfigurer.class);
        @Override
    /**
     * 添加resource处理器
     * @param registry 参数
     * @return 返回值
    */
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        log.info("当前运行目录: {}", new File(".").getAbsolutePath());
        registry.addResourceHandler("/static/**").addResourceLocations("classpath:/static/", "file:./static/").setCachePeriod(3600);
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/webjars/",
                        "file:./webjars/",
                        "classpath:/META-INF/resources/webjars/")
                .setCachePeriod(3600)
                .resourceChain(true);

        // 直接配置资源处理器
        registry.addResourceHandler("/**")
                .addResourceLocations(
                        "classpath:/META-INF/resources/",
                        "classpath:/resources/",
                        "classpath:/static/",
                        "classpath:/public/"
                )
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    /**
                     * 获取Resource
                     * @param location 参数
                     * @param resourcePath 字符串
                     * @return 返回值
                     * @throws IOException 异常
                    */
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        // 在这里处理资源路径编码问题
                        String decodedPath = URLDecoder.decode(resourcePath, StandardCharsets.UTF_8);
                        return super.getResource(decodedPath, location);
                    }
                });
    }

}

