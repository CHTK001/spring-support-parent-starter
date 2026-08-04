package com.chua.starter.spider.configuration;

import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 爬虫中心 Starter 自动配置。
 * <p>启用本 Starter 后自动注册爬虫中心 Service/Controller/Mapper 的 Spring Bean，
 * 并向全局 ServiceInfoRegistry 贡献端点信息。</p>
 *
 * <p>本模块仅提供爬虫的元数据管理（定义/数据记录/执行记录 CRUD），
 * 真正执行由 {@code utils-support-spider-starter} 提供的 {@link com.chua.spider.support.Spider} 完成。</p>
 *
 * <p>同时注册 CORS 过滤器（兜底），因为 common-starter 的 ApiCorsConfiguration
 * 在 META-INF/...AutoConfiguration.imports 中未声明，本模块直接覆盖注册。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@AutoConfiguration
@ComponentScan({
        "com.chua.starter.spider.controller",
        "com.chua.starter.spider.service",
        "com.chua.starter.spider.service.impl",
        "com.chua.starter.spider.executor",
        "com.chua.starter.spider.flow",
        "com.chua.starter.spider.flow.handler"
})
public class SpiderAutoConfiguration {

    /**
     * 爬虫中心服务信息贡献者。
     *
     * @return ServiceInfoContributor
     */
    @Bean
    public ServiceInfoContributor spiderServiceInfoContributor() {
        return registry -> {
            registry.registerEndpoint("爬虫", "爬虫定义", "/spider/definitions/**", "爬虫定义的增删改查、启停切换");
            registry.registerEndpoint("爬虫", "采集数据", "/spider/data-records/**", "采集数据分页查询与详情");
            registry.registerEndpoint("爬虫", "执行记录", "/spider/execution-records/**", "爬虫执行历史分页查询");
            registry.registerEndpoint("爬虫", "执行控制", "/spider/executions/**", "启动/停止/查询爬虫执行进度");
            registry.registerEndpoint("爬虫", "可视化流程", "/spider/flows/**", "RelationGraph 编排的流程定义、保存、执行、调试、历史");
            registry.registerFlag("spider", "爬虫", true, "爬虫中心模块 (定义/数据/执行/可视化编排)");
        };
    }

    /**
     * 兜底 CORS 过滤器：放行 {@code /spider-api/**} 与 {@code /spider/**}，
     * 允许任意 Origin、Method、Header 与 Cookie。
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> spiderCorsFilterRegistrationBean() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*");
        config.setAllowCredentials(true);
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.addExposedHeader("*");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/spider-api/**", config);
        source.registerCorsConfiguration("/spider/**", config);
        source.registerCorsConfiguration("/**", config);

        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        log.info("[SpiderAutoConfiguration] 兜底 CORS 过滤器已注册 (path=/spider-api/**, /spider/**)");
        return bean;
    }
}
