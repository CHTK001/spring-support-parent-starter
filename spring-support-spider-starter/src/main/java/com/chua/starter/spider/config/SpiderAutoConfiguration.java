package com.chua.starter.spider.config;

import com.chua.starter.spider.trace.SpiderTraceFilter;
import jakarta.servlet.Filter;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * 爬虫中心自动配置。
 *
 * <p>负责装配组件扫描（service / controller / mapper）与全链路追踪过滤器。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@AutoConfiguration
@AutoConfigureAfter(name = "com.chua.starter.mybatis.MybatisPlusConfiguration")
@ConditionalOnProperty(prefix = "spider", name = "enable", havingValue = "true", matchIfMissing = true)
@MapperScan("com.chua.starter.spider.mapper")
@ComponentScan("com.chua.starter.spider")
public class SpiderAutoConfiguration {

    /**
     * traceId 过滤器注册顺序常量
     */
    private static final int TRACE_FILTER_ORDER = 0;

    /**
     * 注册全链路追踪过滤器 Bean。
     *
     * <p>覆盖所有路径，为请求注入 traceId 到 MDC 与响应头。</p>
     *
     * @return 过滤器注册 Bean
     */
    @Bean
    @ConditionalOnWebApplication
    public FilterRegistrationBean<Filter> spiderTraceFilter() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new SpiderTraceFilter());
        registration.addUrlPatterns("/*");
        registration.setName("spiderTraceFilter");
        registration.setOrder(TRACE_FILTER_ORDER);
        return registration;
    }
}