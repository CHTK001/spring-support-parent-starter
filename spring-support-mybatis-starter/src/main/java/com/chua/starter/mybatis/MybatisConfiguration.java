package com.chua.starter.mybatis;

import com.chua.starter.mybatis.endpoint.MybatisEndpoint;
import com.chua.starter.mybatis.interceptor.SqlInterceptor;
import com.chua.starter.mybatis.method.SupportInjector;
import com.chua.starter.mybatis.properties.MybatisProperties;
import com.chua.starter.mybatis.reloader.MapperReload;
import com.chua.starter.mybatis.reloader.Reload;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

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
}
