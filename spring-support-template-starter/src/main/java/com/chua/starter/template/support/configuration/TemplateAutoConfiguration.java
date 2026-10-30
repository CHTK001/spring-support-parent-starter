package com.chua.starter.template.support.configuration;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.template.support.printer.PrinterConfig;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 模板模块自动配置类
 * <p>负责组件扫描与 Mapper 扫描。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Slf4j
@AutoConfiguration
@ComponentScan({
        "com.chua.starter.template.support.controller",
        "com.chua.starter.template.support.service",
        "com.chua.starter.template.support.printer",
        "com.chua.starter.template.support.configuration"
})
@MapperScan("com.chua.starter.template.support.mapper")
/**
 * template配置类。
 *
 * @author CH
 * @since 4.0.0.52
 */
public class TemplateAutoConfiguration {

    /**
     * 分页插件兜底。
     *
     * <p>模块的 XML 分页 SQL 依赖 MyBatis-Plus 的 {@link PaginationInnerInterceptor}；
     * 宿主若已通过 spring-support-mybatis-starter 注册同名 Bean，本方法自动退避，
     * 不改变既有装配。模块独立运行（仅引入 template-starter）时也能正确返回 total。</p>
     *
     * @return MyBatis-Plus 拦截器
     */
    @Bean
    @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    public MybatisPlusInterceptor templateMybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 不指定 DbType，由连接自动识别，兼容 MySQL / H2(MySQL 模式)
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        log.info("[Template] 未发现宿主分页插件，已注册模板模块分页兜底");
        return interceptor;
    }

    /**
     * 注册模板模块服务信息。
     *
     * @return 模板服务信息contributor的结果
     */
    @Bean
    public ServiceInfoContributor templateServiceInfoContributor() {
        return registry -> {
            registry.registerEndpoint("模板", "模板库", "/api/template/**", "模板增删改查与设计器数据");
            registry.registerEndpoint("模板", "模板设计", "/api/template/printer/**", "打印机发现/默认打印");
            registry.registerFlag("template", "模板", true, "模板库与 hiprint 打印管理模块");
        };
    }
}
