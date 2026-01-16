package com.chua.starter.swagger.support;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Knife4j Web MVC配置器
 * 配置静态资源处理器、视图控制器和枚举属性插件
 *
 * @author CH
 * @since 2024/8/7
 */
@org.springframework.context.annotation.Configuration
public class Knife4jWebMvcConfigurer implements WebMvcConfigurer {

    /**
     * 添加资源处理器
     *
     * @param registry 资源处理器注册表
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/doc.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/swagger-ui.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        // 添加 doc-v2 相关静态资源，使用通配符匹配所有 doc-v2 相关文件
        registry.addResourceHandler("/doc-v2*")
                .addResourceLocations("classpath:/static/");
    }

    /**
     * 添加视图控制器
     * 配置文档页面的路径映射
     *
     * @param registry 视图控制器注册表
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Knife4j 文档页面
        registry.addRedirectViewController("/doc", "/doc.html");
        // 新版文档页面
        registry.addRedirectViewController("/doc-v2", "/doc-v2.html");
    }

    /**
     * 获取Knife4j枚举模型属性插件
     *
     * @return Knife4j枚举模型属性插件
     */
    @Bean
    @ConditionalOnClass(name = "io.swagger.v3.oas.models.media.Schema")
    public Knife4jEnumModelPropertyPlugin getKnife4jEnumModelPropertyPlugin() {
        return new Knife4jEnumModelPropertyPlugin();
    }
}
