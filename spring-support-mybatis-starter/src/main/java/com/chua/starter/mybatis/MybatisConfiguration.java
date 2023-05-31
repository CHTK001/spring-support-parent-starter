package com.chua.starter.mybatis;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.mybatis.controller.MybatisGeneratorController;
import com.chua.starter.mybatis.endpoint.MybatisEndpoint;
import com.chua.starter.mybatis.interceptor.SqlInterceptor;
import com.chua.starter.mybatis.method.SupportInjector;
import com.chua.starter.mybatis.properties.MybatisProperties;
import com.chua.starter.mybatis.reloader.MapperReload;
import com.chua.starter.mybatis.reloader.Reload;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

/**
 * 处理注解
 *
 * @author CH
 * @version 1.0.0
 * @since 2021/4/12
 */
@Slf4j
@AutoConfigureAfter(SqlSessionFactory.class)
@EnableConfigurationProperties(MybatisProperties.class)
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE + 10)
public class MybatisConfiguration {

    /**
     * SupportInjector
     *
     * @return SupportInjector
     */
    @Bean
    @ConditionalOnMissingBean
    public SupportInjector supportInjector(MybatisProperties mybatisProperties) {
        return new SupportInjector(mybatisProperties);
    }

    /**
     * 分页
     *
     * @return OptimisticLockerInnerInterceptor
     */
    @Bean
    @ConditionalOnMissingBean
    public PaginationInnerInterceptor paginationInnerInterceptor() {
        return new PaginationInnerInterceptor();
    }

    /**
     * 乐观锁
     *
     * @return OptimisticLockerInnerInterceptor
     */
    @Bean
    @ConditionalOnMissingBean
    public OptimisticLockerInnerInterceptor optimisticLockerInnerInterceptor() {
        return new OptimisticLockerInnerInterceptor();
    }
    /**
     * MybatisPlusInterceptor
     *
     * @return MybatisPlusInterceptor
     */
    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor(
            OptimisticLockerInnerInterceptor optimisticLockerInnerInterceptor,
            PaginationInnerInterceptor paginationInnerInterceptor
            ) {
        MybatisPlusInterceptor mybatisPlusInterceptor = new MybatisPlusInterceptor();
        mybatisPlusInterceptor.addInnerInterceptor(optimisticLockerInnerInterceptor);
        mybatisPlusInterceptor.addInnerInterceptor(paginationInnerInterceptor);
        return mybatisPlusInterceptor;
    }

    /**
     * SqlInterceptor
     *
     * @return SqlInterceptor
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = MybatisProperties.PRE, name = "print-sql", havingValue = "true", matchIfMissing = false)
    public SqlInterceptor sqlInterceptor() {
        return new SqlInterceptor();
    }

    /**
     * SqlInterceptor
     *
     * @return SqlInterceptor
     */
    @Bean("mapper-reload")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = MybatisProperties.PRE, name = "xml-reload", havingValue = "true", matchIfMissing = false)
    public Reload xmlReload(List<SqlSessionFactory> sqlSessionFactory, MybatisProperties mybatisProperties) {
        return new MapperReload(sqlSessionFactory, mybatisProperties);
    }

    /**
     * MybatisEndpoint
     *
     * @return MybatisEndpoint MybatisEndpoint
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(name = "mapper-reload")
    public MybatisEndpoint mybatisEndpoint(Reload reload, SqlSessionFactory sqlSessionFactory, SupportInjector supportInjector) {
        return new MybatisEndpoint(reload, sqlSessionFactory.getConfiguration(), supportInjector);
    }

    /**
     * MybatisEndpoint
     *
     * @return MybatisEndpoint MybatisEndpoint
     */
    @Bean
    @ConditionalOnMissingBean
    public MybatisGenerator mybatisGenerator(RequestMappingHandlerMapping requestMappingHandlerMapping) {
        return new MybatisGenerator(requestMappingHandlerMapping);
    }


    public static class MybatisGenerator {

        public MybatisGenerator(RequestMappingHandlerMapping requestMappingHandlerMapping) {
            SpringBeanUtils.registerBean("MybatisGeneratorController", BeanDefinitionBuilder.rootBeanDefinition(MybatisGeneratorController.class).getBeanDefinition());
            SpringBeanUtils.registerController("MybatisGeneratorController", requestMappingHandlerMapping);
        }
    }
}
