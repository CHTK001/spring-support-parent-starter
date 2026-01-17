package com.chua.starter.common.support.configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;

/*@Slf4j
*
 * resource
 *
 * @author CH
 */
@Slf4j
public class ResourceWebMvcConfigurer implements WebMvcConfigurer {
        @Override
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
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        // 在这里处理资源路径编码问题
                        String decodedPath = URLDecoder.decode(resourcePath, StandardCharsets.UTF_8);
                        return super.getResource(decodedPath, location);
                    }
                });
    }

}

