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
* @since 2026-03-18
* @author CH
 */
@Slf4j
@Configuration
@MapperScan("com.chua.payment.support.mapper")
public class MybatisPlusConfig {

    /**
    * 支付模块默认分页拦截器。
    * <p>
    * 完整的 MyBatis Plus 拦截器链会在 {@link #mybatisPlusInterceptor(ObjectProvider)}
    * 中按 Spring Bean 顺序组装，这样 作业 模块注入的动态表名拦截器也能一起生效。
    * </p>
    * @return 薪酬pagination内部拦截器的结果
     */
    @Bean
    @ConditionalOnMissingBean(PaginationInnerInterceptor.class)
    public PaginationInnerInterceptor paymentPaginationInnerInterceptor() {
        /**
        * pagination内部拦截器。
        * @param DbType.MYSQL db类型.MySQL
        * @return pagination内部拦截器的结果
         */
        return new PaginationInnerInterceptor(DbType.MYSQL);
    }

    /**
    * 支付模块 MyBatis Plus 拦截器链。
    * @param innerInterceptorProvider 内部拦截器提供者
    * @return mybatisplus拦截器的结果
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
