package com.chua.starter.hotsport.support.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SPA 前端路由回退。
 *
 * <p>未命中 /api/** 的请求（根路径 / 与前端子路由 /dashboard /traces /dependencies
 * /leaks）转发到 classpath:/static/index.html，使 vue-router history 模式可深链直达、刷新不 404。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 根路径 + 已知前端路由，统一回退 index.html
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.addViewController("/dashboard").setViewName("forward:/index.html");
        registry.addViewController("/traces").setViewName("forward:/index.html");
        registry.addViewController("/dependencies").setViewName("forward:/index.html");
        registry.addViewController("/leaks").setViewName("forward:/index.html");
        registry.addViewController("/logs").setViewName("forward:/index.html");
    }
}
