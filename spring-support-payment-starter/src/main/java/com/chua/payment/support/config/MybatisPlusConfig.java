package com.chua.payment.support.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * MyBatis Plus 配置类
 *
 * @author CH
 * @since 2026-03-18
 */
@Slf4j
@Configuration
@MapperScan("com.chua.payment.support.mapper")
public class MybatisPlusConfig {

    /**
     * 支付模块默认分页拦截器。
     * <p>
     * 完整的 MyBatis Plus 拦截器链会在 {@link #mybatisPlusInterceptor(ObjectProvider)}
     * 中按 Spring Bean 顺序组装，这样 job 模块注入的动态表名拦截器也能一起生效。
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean(PaginationInnerInterceptor.class)
    public PaginationInnerInterceptor paymentPaginationInnerInterceptor() {
        return new PaginationInnerInterceptor(DbType.MYSQL);
    }

    /**
     * 支付模块 MyBatis Plus 拦截器链。
     */
    @Bean
    @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    public MybatisPlusInterceptor mybatisPlusInterceptor(ObjectProvider<List<InnerInterceptor>> innerInterceptorProvider) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        List<InnerInterceptor> innerInterceptors = innerInterceptorProvider.getIfAvailable(List::of);
        for (InnerInterceptor innerInterceptor : innerInterceptors) {
            interceptor.addInnerInterceptor(innerInterceptor);
        }
        log.info("[Payment][MyBatis] 已装配 InnerInterceptor: {}", innerInterceptors.stream()
                .map(item -> item.getClass().getSimpleName())
                .toList());
        return interceptor;
    }
}
